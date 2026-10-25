package net.killstreakcod.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Native property channels and callback timing, independent of the game renderer.
 * 原生属性轨道与回调时序，独立于游戏绘制器，按属性并行累计时长。
 */
public final class EarnedSplashAnimation {
    public record Rect(float x, float y, float width, float height) {
    }

    private record Step(float duration, float value, String easing) {
    }

    private record Sequence(String name, float start) {
    }

    private final Map<String, float[]> layouts = new HashMap<>();
    private final Map<String, Map<String, Map<String, List<Step>>>> tracks = new HashMap<>();
    private final Map<String, List<Sequence>> schedules = new HashMap<>();
    private final int displayTime;
    private final int hideTime;
    private final int messageDelay;
    private final int hideAt;
    private final float top;

    public EarnedSplashAnimation(JsonObject root) {
        if (root.get("format").getAsInt() != 1)
            throw new IllegalArgumentException("Unsupported earned-splash format");
        var manager = root.getAsJsonObject("manager");
        displayTime = manager.get("displayTime").getAsInt();
        hideTime = manager.get("hideTime").getAsInt();
        messageDelay = manager.get("nextMessageDelay").getAsInt();
        top = root.get("top").getAsFloat();
        for (var entry : root.getAsJsonObject("layouts").entrySet()) {
            var array = entry.getValue().getAsJsonArray();
            float[] values = new float[8];
            for (int i = 0; i < values.length; i++)
                values[i] = array.get(i).getAsFloat();
            layouts.put(entry.getKey(), values);
        }
        for (var element : root.getAsJsonObject("tracks").entrySet()) {
            Map<String, Map<String, List<Step>>> sequences = new HashMap<>();
            for (var sequence : element.getValue().getAsJsonObject().entrySet()) {
                Map<String, List<Step>> channels = new HashMap<>();
                for (var channel : sequence.getValue().getAsJsonObject().entrySet()) {
                    List<Step> steps = new ArrayList<>();
                    for (var value : channel.getValue().getAsJsonArray()) {
                        var step = value.getAsJsonObject();
                        steps.add(new Step(step.get("duration").getAsFloat(), step.get("value").getAsFloat(),
                                step.get("easing").getAsString()));
                    }
                    channels.put(channel.getKey(), List.copyOf(steps));
                }
                sequences.put(sequence.getKey(), Map.copyOf(channels));
            }
            tracks.put(element.getKey(), Map.copyOf(sequences));
        }
        var callbacks = root.getAsJsonObject("callbacks");
        int content = callbacks.get("introContent").getAsInt();
        int iconOut = displayTime + callbacks.get("outroIcon").getAsInt();
        int dividerOut = displayTime + callbacks.get("outroDivider").getAsInt();
        int bannerOut = displayTime + callbacks.get("outroBanner").getAsInt();
        hideAt = displayTime + callbacks.get("hide").getAsInt();
        for (String element : List.of("splash.StreakIcon", "splash.Diamond")) {
            schedule(element, "IntroIconDiamond", 0);
            schedule(element, "OutroIconDiamond", iconOut);
        }
        schedule("content.Divider", "IntroDivider", content);
        schedule("content.Divider", "OutroDivider", dividerOut);
        for (String element : List.of("content.UpperBacker", "content.LowerBacker", "content.Chevron1",
                "content.Chevron2", "content.MainText"))
            schedule(element, "IntroUpperBanner", content);
        schedule("content.HintText", "IntroHintTextBlink", callbacks.get("introHint").getAsInt());
        for (String element : List.of("content.HintText", "content.MainText")) {
            schedule(element, "OutroHintTextBlink", displayTime);
            schedule(element, "OutroHintTextBlink", dividerOut);
        }
        for (String element : List.of("content.UpperBacker", "content.LowerBacker"))
            schedule(element, "OutroBannerTest", bannerOut);
        // 原回调没有对应的 OutroChevronFlicker 序列，保留缺失事实，不调用另一名称的轨道。
    }

    private void schedule(String element, String name, float start) {
        schedules.computeIfAbsent(element, ignored -> new ArrayList<>()).add(new Sequence(name, start));
    }

    public int displayTime() {
        return displayTime;
    }

    public int totalTime() {
        return displayTime + hideTime;
    }

    public int messageDelay() {
        return messageDelay;
    }

    public float top() {
        return top;
    }

    public boolean visible(float milliseconds) {
        return milliseconds >= 0 && milliseconds < hideAt;
    }

    public float value(String element, String property, float milliseconds, float initial) {
        float value = initial;
        for (Sequence sequence : schedules.getOrDefault(element, List.of())) {
            if (milliseconds < sequence.start)
                break;
            value = sample(element, sequence.name, property, milliseconds - sequence.start, value);
        }
        return value;
    }

    public float sample(String element, String sequence, String property, float milliseconds, float initial) {
        if (milliseconds < 0)
            return initial;
        var channels = tracks.getOrDefault(element, Map.of()).getOrDefault(sequence, Map.of());
        float start = 0, previous = initial;
        for (Step step : channels.getOrDefault(property, List.of())) {
            float end = start + step.duration;
            if (milliseconds < end) {
                float fraction = (milliseconds - start) / step.duration;
                fraction = switch (step.easing) {
                    case "inQuadratic" -> fraction * fraction;
                    case "outQuadratic" -> 1 - (1 - fraction) * (1 - fraction);
                    case "snap" -> 0;
                    default -> fraction;
                };
                return property.equals("RGB") ? color(previous, step.value, fraction)
                        : previous + (step.value - previous) * fraction;
            }
            previous = step.value;
            start = end;
        }
        return previous;
    }

    private static int color(float from, float to, float fraction) {
        int a = (int) from, b = (int) to, result = 0;
        for (int shift : new int[] { 16, 8, 0 }) {
            int left = a >> shift & 255, right = b >> shift & 255;
            result |= Math.round(left + (right - left) * fraction) << shift;
        }
        return result;
    }

    public Rect bounds(String element, float milliseconds) {
        float[] layout = layouts.get(element);
        if (layout == null)
            throw new IllegalArgumentException("Missing earned-splash layout: " + element);
        float width = element.startsWith("content.") ? 500 : 1920;
        float left = value(element, "Left_Anchor", milliseconds, layout[0]) * width
                + value(element, "Left", milliseconds, layout[4]);
        float right = (1 - value(element, "Right_Anchor", milliseconds, layout[1])) * width
                + value(element, "Right", milliseconds, layout[5]);
        float upper = value(element, "Top_Anchor", milliseconds, layout[2]) * 128
                + value(element, "Top", milliseconds, layout[6]);
        float lower = (1 - value(element, "Bottom_Anchor", milliseconds, layout[3])) * 128
                + value(element, "Bottom", milliseconds, layout[7]);
        return new Rect(left, upper, Math.max(0, right - left), Math.max(0, lower - upper));
    }
}
