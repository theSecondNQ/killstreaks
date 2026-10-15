package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletCanvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Numeric LUI anchors and sequence channels only; extracted Lua is never executed.
 * 只读取 LUI 的数值锚点和动画序列通道，不执行导出的 Lua 脚本。
 */
public final class NativeSceneUi {
    public record Rect(float x, float y, float w, float h) {
    }

    public record Layer(String image, Rect rect, float alpha, float rotation, float scale, boolean sprite) {
    }

    private static JsonObject data, textData;
    private static final Map<String, Optional<NativeImage>> pixels = new HashMap<>();
    private static final Map<String, Optional<ImageAsset>> IMAGES = new HashMap<>();
    // 按源通道对象身份查找，避免递归计算 JSON 内容哈希；数据只在资源重载时替换。
    private static final Map<JsonArray, List<Timeline>> TIMELINES = new IdentityHashMap<>();
    private static final Pattern FRAME_KEY = Pattern.compile("[0-9]+");
    private static final Pattern REGISTER_MATERIAL = Pattern.compile("RegisterMaterial\\( [\"]([^\"]+)[\"] \\)");
    private static final Pattern MATERIAL_LITERAL = Pattern.compile("^material\\(([A-Za-z0-9_]+)\\)$");

    private enum Visibility {
        UNCHANGED, HIDDEN, VISIBLE
    }

    private record Step(float duration, String easing, boolean numeric, float target, String image,
            Visibility visibility) {
    }

    private record Timeline(String property, List<Step> steps) {
    }

    private record ImageAsset(ResourceLocation texture, int width, int height) {
    }

    private NativeSceneUi() {}

    static void reset() {
        data = textData = null;
        TIMELINES.clear();
        IMAGES.clear();
        pixels.values().forEach(image -> image.ifPresent(NativeImage::close));
        pixels.clear();
    }

    public static void tabletText(TabletCanvas canvas, String field, net.minecraft.network.chat.Component value,
            String sequence, float time, int width, int height) {
        if (textData == null)
            try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(
                    ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/tablet-text.json"))) {
                textData = JsonParser.parseReader(reader).getAsJsonObject();
            }
            catch (Exception failure) {
                textData = new JsonObject();
                com.mojang.logging.LogUtils.getLogger().warn("Tablet text layout unavailable", failure);
            }
        if (!textData.has(field))
            return;
        JsonObject style = textData.getAsJsonObject(field);
        Layer layer = state("StreakActivationTablet", field, sequence, time, width, height);
        if (layer == null || layer.alpha <= 0)
            return;
        Rect r = layer.rect;
        canvas.text(value, new TabletCanvas.Box(Math.round(r.x), Math.round(r.y), Math.round(r.w), Math.round(r.h)),
                Math.round(style.get("fontSize").getAsFloat() * width / 768), 0xffb7d889, layer.alpha,
                style.get("alignment").getAsString().equals("Center"));
    }

    private static JsonObject data() {
        if (data == null)
            try (var input = Minecraft.getInstance().getResourceManager()
                    .getResourceOrThrow(
                            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/scene-ui.json"))
                    .openAsReader()) {
                data = JsonParser.parseReader(input).getAsJsonObject();
            }
            catch (Exception failure) {
                data = new JsonObject();
                com.mojang.logging.LogUtils.getLogger().warn("Native scene UI data unavailable", failure);
            }
        if (!data.has("cruisePhoneLoaded")) {
            data.addProperty("cruisePhoneLoaded", true);
            try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(
                    ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/cruise-phone-ui.json"))) {
                var extra = JsonParser.parseReader(reader).getAsJsonObject();
                for (String field : List.of("widgets", "images")) {
                    if (!data.has(field))
                        data.add(field, new JsonObject());
                    for (var entry : extra.getAsJsonObject(field).entrySet())
                        data.getAsJsonObject(field).add(entry.getKey(), entry.getValue());
                }
            }
            catch (Exception failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Cruise and phone UI unavailable", failure);
            }
        }
        return data;
    }

    static JsonObject widget(String name) {
        JsonObject widgets = data().getAsJsonObject("widgets");
        return widgets == null ? null : widgets.getAsJsonObject(name);
    }

    public static Rect bounds(String widget, String name, float width, float height) {
        JsonObject w = widget(widget);
        if (w == null || !w.getAsJsonObject("nodes").has(name))
            return null;
        return layer(w, name, "", 0, width, height).rect;
    }

    public static Layer state(String widget, String name, String sequence, float time, float width, float height) {
        JsonObject w = widget(widget);
        return w == null || !w.getAsJsonObject("nodes").has(name) ? null
                : layer(w, name, sequence, time, width, height);
    }

    public static List<Layer> layers(String name, String sequence, float milliseconds, float width, float height,
            boolean nuke) {
        JsonObject w = widget(name);
        if (w == null)
            return List.of();
        List<Layer> layers = new ArrayList<>();
        for (var entry : w.getAsJsonObject("nodes").entrySet()) {
            String key = entry.getKey();
            JsonObject node = entry.getValue().getAsJsonObject();
            if (key.contains("Mask") || key.contains("Blackout"))
                continue;
            if (!nuke && (key.contains("Keypad") || key.contains("Numpad") || key.contains("Marker")
                    || key.contains("Glow") || key.equals("NukeIcons") || key.equals("Arrows")
                    || key.equals("BoxesFill") || key.equals("BoxesOutline") || key.equals("BackgroundFluff")
                    || key.equals("ButtonOutline")))
                continue;
            Layer layer = layer(w, key, sequence, milliseconds, width, height);
            if (layer.image != null && layer.alpha > .001F)
                layers.add(layer);
        }
        return layers;
    }

    private static Layer layer(JsonObject widget, String field, String sequence, float time, float width,
            float height) {
        JsonObject node = widget.getAsJsonObject("nodes").getAsJsonObject(field);
        JsonArray anchors = node.getAsJsonArray("anchors");
        float[] a = new float[8];
        for (int i = 0; i < 8; i++)
            a[i] = anchors.get(i).getAsFloat();
        float alpha = node.has("alpha") ? node.get("alpha").getAsFloat() : 1, rotation = 0,
                scale = node.has("scale") ? node.get("scale").getAsFloat() : 0;
        String image = node.has("image") && !node.get("image").isJsonNull() ? node.get("image").getAsString() : null;
        JsonObject selected = widget.getAsJsonObject("sequences").getAsJsonObject(sequence);
        JsonArray channels = selected == null ? null : selected.getAsJsonArray(field);
        boolean visible = true;
        if (channels != null)
            for (Timeline timeline : TIMELINES.computeIfAbsent(channels, NativeSceneUi::compileTimelines)) {
                String property = timeline.property;
                float elapsed = 0;
                float base = property.equals("Alpha") ? alpha
                        : property.equals("ZRotation") ? rotation
                                : property.equals("Scale") ? scale : anchor(property, a);
                for (Step step : timeline.steps) {
                    float fraction = step.duration <= 0 ? 1 : Mth.clamp((time - elapsed) / step.duration, 0, 1);
                    if (step.numeric) {
                        float value = Mth.lerp(ease(fraction, step.easing), base, step.target);
                        if (property.equals("Alpha"))
                            alpha = value;
                        else if (property.equals("ZRotation"))
                            rotation = value;
                        else if (property.equals("Scale"))
                            scale = value;
                        else
                            setAnchor(property, a, value);
                        base = step.target;
                    } else if (fraction >= 1) {
                        if (step.image != null)
                            image = step.image;
                        if (step.visibility != Visibility.UNCHANGED)
                            visible = step.visibility == Visibility.VISIBLE;
                    }
                    if (time < elapsed + step.duration)
                        break;
                    elapsed += step.duration;
                }
            }
        float sx = width / widget.get("width").getAsFloat(), sy = height / widget.get("height").getAsFloat();
        if (widget.get("height").getAsInt() == 1080)
            sx = sy;
        float left = width * a[0] + a[4] * sx, right = width * (1 - a[1]) + a[5] * sx, top = height * a[2] + a[6] * sy,
                bottom = height * (1 - a[3]) + a[7] * sy;
        if (widget.has("fixedPixelFieldsCentered") && widget.get("fixedPixelFieldsCentered").getAsBoolean()
                && Math.abs(a[0] + a[1] - 1) < .0001F) {
            float pad = (width - widget.get("width").getAsFloat() * sx) / 2, offset = pad * (1 - 2 * a[0]);
            left += offset;
            right += offset;
        }
        return new Layer(image, new Rect(left, top, right - left, bottom - top), visible ? Mth.clamp(alpha, 0, 1) : 0,
                rotation, Math.max(.01F, 1 + scale), node.has("sprite"));
    }

    /**
     * Compile source channels once; sampling retains source channel and property order.
     * 源通道只编译一次，采样时保留通道及属性顺序，避免每帧排序、分组和解析材质表达式。
     */
    private static List<Timeline> compileTimelines(JsonArray channels) {
        List<Timeline> result = new ArrayList<>();
        for (var channel : channels) {
            JsonObject series = channel.getAsJsonObject();
            List<String> keys = new ArrayList<>(series.keySet());
            keys.removeIf(key -> !FRAME_KEY.matcher(key).matches());
            keys.sort(Comparator.comparingInt(Integer::parseInt));
            Map<String, List<Step>> properties = new LinkedHashMap<>();
            for (String key : keys) {
                JsonObject step = series.getAsJsonObject(key);
                String property = step.getAsJsonObject("property").get("expression").getAsString();
                property = property.substring(property.lastIndexOf('.') + 1);
                properties.computeIfAbsent(property, ignored -> new ArrayList<>()).add(compileStep(property, step));
            }
            properties.forEach((property, steps) -> result.add(new Timeline(property, List.copyOf(steps))));
        }
        return List.copyOf(result);
    }

    private static Step compileStep(String property, JsonObject step) {
        float duration = step.has("duration") ? step.get("duration").getAsFloat() : 0;
        String easing = step.has("easing") ? step.getAsJsonObject("easing").get("expression").getAsString() : "";
        boolean numeric = step.has("numericValue") && step.get("numericValue").isJsonPrimitive();
        float target = numeric ? step.get("numericValue").getAsFloat() : 0;
        String image = null;
        Visibility visibility = Visibility.UNCHANGED;
        if (!numeric && step.has("value") && step.get("value").isJsonObject()) {
            JsonObject value = step.getAsJsonObject("value");
            String expression = value.has("expression") ? value.get("expression").getAsString() : "";
            if (property.equals("Image") || property.equals("Material")) {
                var material = REGISTER_MATERIAL.matcher(expression);
                if (material.find())
                    image = material.group(1);
                var literal = MATERIAL_LITERAL.matcher(expression);
                if (literal.matches())
                    image = literal.group(1);
            } else if (property.equals("Visibility")) {
                if (expression.endsWith(".Hidden") || expression.endsWith(".Collapsed"))
                    visibility = Visibility.HIDDEN;
                else if (expression.endsWith(".Visible"))
                    visibility = Visibility.VISIBLE;
            }
        }
        return new Step(duration, easing, numeric, target, image, visibility);
    }

    private static float anchor(String property, float[] a) {
        return switch (property) {
            case "Left_Anchor" -> a[0];
            case "Right_Anchor" -> a[1];
            case "Top_Anchor" -> a[2];
            case "Bottom_Anchor" -> a[3];
            case "Left" -> a[4];
            case "Right" -> a[5];
            case "Top" -> a[6];
            case "Bottom" -> a[7];
            default -> 0;
        };
    }

    private static void setAnchor(String property, float[] a, float v) {
        switch (property) {
            case "Left_Anchor" -> a[0] = v;
            case "Right_Anchor" -> a[1] = v;
            case "Top_Anchor" -> a[2] = v;
            case "Bottom_Anchor" -> a[3] = v;
            case "Left" -> a[4] = v;
            case "Right" -> a[5] = v;
            case "Top" -> a[6] = v;
            case "Bottom" -> a[7] = v;
            default -> {
            }
        }
    }

    private static float ease(float t, String name) {
        if (name.endsWith("snap"))
            return t >= 1 ? 1 : 0;
        if (name.endsWith("inQuadratic"))
            return t * t;
        if (name.endsWith("outQuadratic"))
            return 1 - (1 - t) * (1 - t);
        if (name.endsWith("inCubic"))
            return t * t * t;
        if (name.endsWith("outCubic"))
            return 1 - (float) Math.pow(1 - t, 3);
        if (name.endsWith("inOutQuadratic"))
            return t < .5F ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
        return t;
    }

    private static Optional<ImageAsset> image(String name) {
        return IMAGES.computeIfAbsent(name, key -> {
            JsonObject images = data().getAsJsonObject("images");
            if (images == null || !images.has(key))
                return Optional.empty();
            JsonObject meta = images.getAsJsonObject(key);
            return Optional.of(new ImageAsset(ResourceLocation.parse(meta.get("texture").getAsString()),
                    meta.get("width").getAsInt(), meta.get("height").getAsInt()));
        });
    }

    static ResourceLocation texture(String name) {
        ImageAsset asset = image(name).orElse(null);
        return asset == null ? null : asset.texture;
    }

    public static NativeImage pixels(String name) {
        return pixels.computeIfAbsent(name, k -> {
            ResourceLocation location = texture(k);
            if (location == null)
                return Optional.empty();
            try (var input = Minecraft.getInstance().getResourceManager().open(location)) {
                return Optional.of(NativeImage.read(input));
            }
            catch (Exception failure) {
                // 缺失图像也缓存到下次重载，避免每帧重新访问资源并重复触发异常。
                return Optional.empty();
            }
        }).orElse(null);
    }

    static void draw(GuiGraphics graphics, String image, Rect box, float alpha) {
        ImageAsset asset = image(image).orElse(null);
        if (asset == null || box == null || box.w <= 0 || box.h <= 0 || alpha <= 0)
            return;
        try (BlendGuard guard = new BlendGuard(graphics, alpha)) {
            graphics.blit(asset.texture, Math.round(box.x), Math.round(box.y), Math.round(box.w), Math.round(box.h), 0,
                    0,
                    asset.width, asset.height, asset.width, asset.height);
        }
    }

    static void drawLayer(GuiGraphics graphics, Layer layer) {
        Rect r = layer.rect();
        if (layer.scale() == 1 && layer.rotation() == 0) {
            draw(graphics, layer.image(), r, layer.alpha());
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(r.x() + r.w() / 2, r.y() + r.h() / 2, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(layer.rotation()));
            graphics.pose().scale(layer.scale(), layer.scale(), 1);
            draw(graphics, layer.image(), new Rect(-r.w() / 2, -r.h() / 2, r.w(), r.h()), layer.alpha());
        } finally {
            graphics.pose().popPose();
        }
    }

    private static final class BlendGuard implements AutoCloseable {
        private final GuiGraphics gui;
        private final boolean enabled;
        private final int sr, dr, sa, da;
        private final float[] color;

        BlendGuard(GuiGraphics g, float alpha) {
            gui = g;
            g.flush();
            enabled = GL11.glIsEnabled(GL11.GL_BLEND);
            sr = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            dr = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            sa = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            da = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            color = RenderSystem.getShaderColor().clone();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
        }

        public void close() {
            gui.flush();
            gui.setColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(sr, dr, sa, da);
            if (!enabled)
                RenderSystem.disableBlend();
        }
    }
}
