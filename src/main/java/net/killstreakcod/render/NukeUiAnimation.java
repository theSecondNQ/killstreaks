package net.killstreakcod.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Compiled MP NukeInbound state; property channels run in parallel, materials switch discretely.
 * 编译多人模式核弹来袭状态；属性通道并行，材质和可见性在关键点切换。
 */
public final class NukeUiAnimation {
    public record Rect(float x, float y, float width, float height) {
    }

    public record Frame(String id, Rect bounds, String image, int rgb, float alpha, float textSize, String alignment,
            boolean additive, boolean visible) {
    }

    private record Step(float duration, Object value, String easing) {
    }

    private record Node(String id, float[] anchors, Map<String, Object> initial, Map<String, List<Step>> channels) {
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final float duration, fadeStart, fadeDuration;

    public NukeUiAnimation(JsonObject source) {
        if (source.get("format").getAsInt() != 1)
            throw new IllegalArgumentException("Unsupported nuke UI format");
        duration = source.get("duration").getAsFloat();
        fadeStart = source.get("fadeStart").getAsFloat();
        fadeDuration = source.get("fadeDuration").getAsFloat();
        for (var element : source.getAsJsonArray("nodes")) {
            var raw = element.getAsJsonObject();
            String id = raw.get("id").getAsString();
            float[] anchors = new float[8];
            for (int i = 0; i < anchors.length; i++)
                anchors[i] = raw.getAsJsonArray("anchors").get(i).getAsFloat();
            Map<String, Object> initial = new HashMap<>();
            raw.getAsJsonObject("initial").entrySet()
                    .forEach(entry -> initial.put(entry.getKey(), literal(entry.getValue())));
            Map<String, List<Step>> channels = new HashMap<>();
            for (var channel : raw.getAsJsonObject("channels").entrySet()) {
                List<Step> steps = new ArrayList<>();
                for (var elementStep : channel.getValue().getAsJsonArray()) {
                    var step = elementStep.getAsJsonObject();
                    steps.add(new Step(step.get("duration").getAsFloat(), literal(step.get("value")),
                            step.get("easing").getAsString()));
                }
                channels.put(channel.getKey(), List.copyOf(steps));
            }
            nodes.put(id, new Node(id, anchors, Map.copyOf(initial), Map.copyOf(channels)));
        }
    }

    private static Object literal(JsonElement element) {
        return element.getAsJsonPrimitive().isNumber() ? element.getAsFloat() : element.getAsString();
    }

    public List<String> elements() {
        return List.copyOf(nodes.keySet());
    }

    public float duration() {
        return duration;
    }

    public float opacity(float milliseconds) {
        return Math.max(0, Math.min(1, 1 - (milliseconds - fadeStart) / fadeDuration));
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number numeric ? numeric.floatValue() : fallback;
    }

    private static int color(float from, float to, float fraction) {
        int a = (int) from, b = (int) to, result = 0;
        for (int shift : new int[] { 16, 8, 0 }) {
            int start = a >> shift & 255, end = b >> shift & 255;
            result |= Math.round(start + (end - start) * fraction) << shift;
        }
        return result;
    }

    private Object sample(Node node, String property, float milliseconds, Object fallback) {
        Object previous = node.initial.getOrDefault(property, fallback);
        float start = 0;
        for (Step step : node.channels.getOrDefault(property, List.of())) {
            float end = start + step.duration;
            if (milliseconds < end) {
                if (previous instanceof Number a && step.value instanceof Number b) {
                    float fraction = Math.max(0, (milliseconds - start) / step.duration);
                    fraction = switch (step.easing) {
                        case "inQuadratic" -> fraction * fraction;
                        case "outQuadratic" -> 1 - (1 - fraction) * (1 - fraction);
                        case "snap" -> 0;
                        default -> fraction;
                    };
                    return property.equals("RGB") ? color(a.floatValue(), b.floatValue(), fraction)
                            : a.floatValue() + (b.floatValue() - a.floatValue()) * fraction;
                }
                return previous;
            }
            previous = step.value;
            start = end;
        }
        return previous;
    }

    public Frame frame(String id, float milliseconds) {
        Node node = nodes.get(id);
        if (node == null)
            throw new IllegalArgumentException("Unknown nuke UI element: " + id);
        milliseconds = Math.max(0, milliseconds);
        float[] a = node.anchors;
        float left = a[0] * 1920 + number(sample(node, "Left", milliseconds, a[4]), a[4]);
        float right = (1 - a[1]) * 1920 + number(sample(node, "Right", milliseconds, a[5]), a[5]);
        float top = a[2] * 104 + number(sample(node, "Top", milliseconds, a[6]), a[6]);
        float bottom = (1 - a[3]) * 104 + number(sample(node, "Bottom", milliseconds, a[7]), a[7]);
        return new Frame(id, new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top)),
                (String) sample(node, "Material", milliseconds, ""),
                (int) number(sample(node, "RGB", milliseconds, 0xffffff), 0xffffff),
                number(sample(node, "Alpha", milliseconds, 1), 1) * opacity(milliseconds),
                number(sample(node, "Text_Size", milliseconds, 28), 28),
                (String) node.initial.getOrDefault("Alignment", "Left"),
                "addWithAlpha".equals(sample(node, "Blend_Mode", milliseconds, "blend")),
                "Visible".equals(sample(node, "Visibility", milliseconds, "Visible")));
    }
}
