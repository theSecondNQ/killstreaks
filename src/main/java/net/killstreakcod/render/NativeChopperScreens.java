package net.killstreakcod.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Native Apache layers and sequence states on the three recovered render-target canvases.
 * 按恢复的三块渲染目标画布和动画状态合成原始攻击直升机屏幕图层。
 */
public final class NativeChopperScreens {
    private static NativeImage leftBase, centerBase, centerReticle, centerArrow, rightBase, rightSquare;
    private static DynamicTexture left, center, right;
    private static ResourceLocation leftId, centerId, rightId;
    private static Map<String, ResourceLocation> materials = Map.of();
    private static boolean loaded;
    private static long lastUpdate = Long.MIN_VALUE;
    private static long started, phaseStarted;
    private static String phase = "Normal";

    private NativeChopperScreens() {}

    private static NativeImage read(String name) throws Exception {
        var location = NativeRemoteHud.screenTexture(name);
        if (location == null)
            throw new IllegalStateException("Missing cockpit layer " + name);
        try (var stream = Minecraft.getInstance().getResourceManager().open(location)) {
            return NativeImage.read(stream);
        }
    }

    static Map<String, ResourceLocation> materials() {
        if (!loaded) {
            loaded = true;
            try {
                centerBase = read("tablet_chopper_gunner_center_background");
                centerReticle = read("tablet_chopper_gunner_center_reticle");
                leftBase = read("tablet_chopper_gunner_left_background");
                centerArrow = read("tablet_chopper_gunner_center_arrow");
                rightBase = read("tablet_chopper_gunner_right_background");
                rightSquare = read("tablet_chopper_gunner_right_square");
                // Native Slot03 is 512², Slot04 and Slot05 are 256²; source PNG dimensions differ from RT size.
                // 原 Slot03 为 512²、Slot04/05 为 256²；源 PNG 的尺寸与渲染目标画布并不相同。
                left = new DynamicTexture(new NativeImage(256, 256, false));
                center = new DynamicTexture(new NativeImage(512, 512, false));
                right = new DynamicTexture(new NativeImage(256, 256, false));
                left.setFilter(true, false);
                center.setFilter(true, false);
                right.setFilter(true, false);
                var manager = Minecraft.getInstance().getTextureManager();
                leftId = manager.register("killstreak_apache_left", left);
                centerId = manager.register("killstreak_apache_center", center);
                rightId = manager.register("killstreak_apache_right", right);
                started = phaseStarted = Util.getMillis();
                Map<String, ResourceLocation> bindings = new HashMap<>();
                for (var entry : NativeRemoteHud.cockpitMaterials().entrySet())
                    bindings.put(entry.getKey(), switch (entry.getValue()) {
                        case "left" -> leftId;
                        case "center" -> centerId;
                        case "right" -> rightId;
                        default -> throw new IllegalArgumentException("Unknown Apache screen role");
                    });
                materials = Map.copyOf(bindings);
            }
            catch (Exception failure) {
                reset();
                loaded = true;
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Original Apache screen layers unavailable; keeping the original mesh materials", failure);
            }
        }
        update();
        return materials;
    }

    private static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (left == null || center == null || right == null || mc.level == null)
            return;
        long tick = mc.level.getGameTime();
        if (lastUpdate != Long.MIN_VALUE && tick >= lastUpdate && tick - lastUpdate < 2)
            return;
        lastUpdate = tick;
        NativeImage l = left.getPixels(), image = center.getPixels(), r = right.getPixels();
        if (l == null || image == null || r == null)
            return;
        long now = Util.getMillis();
        String next = AircraftCrashClient.active() ? "Crashing"
                : StreakPresentation.currentStage().equals("exit") ? "Hide" : "Normal";
        if (!next.equals(phase)) {
            phase = next;
            phaseStarted = now;
        }
        l.fillRect(0, 0, 256, 256, 0xff000000);
        image.fillRect(0, 0, 512, 512, 0xff000000);
        r.fillRect(0, 0, 256, 256, 0xff000000);
        if (!phase.equals("Hide")) {
            composite(l, leftBase, new NativeSceneUi.Layer("", new NativeSceneUi.Rect(0, 0, 256, 256), 1, 0, 1, false));
            composite(image, centerBase,
                    new NativeSceneUi.Layer("", new NativeSceneUi.Rect(0, 0, 512, 512), 1, 0, 1, false));
        }
        if (NativeSceneUi.widget("ApacheScreen02") != null) {
            draw(image, centerReticle, "ApacheScreen02", "ScreenOverlay", "OverlayRotation", now);
            draw(image, centerArrow, "ApacheScreen02", "Arrow", "OverlayRotation", now);
            draw(r, rightBase, "ApacheScreen03", "Screen", "", now);
            draw(r, rightSquare, "ApacheScreen03", "FlashBlock", "FlashLoop", now);
        } else if (!phase.equals("Hide")) {
            composite(image, centerReticle,
                    new NativeSceneUi.Layer("", new NativeSceneUi.Rect(0, 0, 512, 512), 1, 0, 1, false));
            composite(r, rightBase,
                    new NativeSceneUi.Layer("", new NativeSceneUi.Rect(0, 0, 256, 256), 1, 0, 1, false));
        }
        left.upload();
        center.upload();
        right.upload();
    }

    /**
     * NativeImage uses ABGR integers; source pixels remain intact, composed only in runtime buffers.
     * NativeImage 的整数像素格式为 ABGR；保留源图像像素，仅在运行时缓冲中合成。
     */
    private static void draw(NativeImage target, NativeImage source, String widget, String field, String loop,
            long now) {
        var normal = NativeSceneUi.state(widget, field, phase, now - phaseStarted, target.getWidth(),
                target.getHeight());
        if (normal == null)
            return;
        var motion = loop.isEmpty() ? normal
                : NativeSceneUi.state(widget, field, loop,
                        (now - started) % Math.max(1, NativeSceneUi.sequenceLength(widget, loop, 1000)),
                        target.getWidth(), target.getHeight());
        if (motion == null)
            motion = normal;
        composite(target, source, new NativeSceneUi.Layer("", motion.rect(),
                normal.alpha() * (loop.isEmpty() ? 1 : motion.alpha()), motion.rotation(), motion.scale(), false));
    }

    private static void composite(NativeImage target, NativeImage source, NativeSceneUi.Layer layer) {
        if (layer.alpha() <= 0)
            return;
        var box = layer.rect();
        double angle = Math.toRadians(layer.rotation()), cos = Math.cos(angle), sin = Math.sin(angle);
        float width = box.w() * layer.scale(), height = box.h() * layer.scale();
        if (width <= 0 || height <= 0)
            return;
        double cx = box.x() + box.w() / 2, cy = box.y() + box.h() / 2;
        double radius = Math.sqrt(width * width + height * height) / 2;
        int left = Math.max(0, (int) Math.floor(cx - radius)), top = Math.max(0, (int) Math.floor(cy - radius));
        int right = Math.min(target.getWidth(), (int) Math.ceil(cx + radius));
        int bottom = Math.min(target.getHeight(), (int) Math.ceil(cy + radius));
        for (int ty = top; ty < bottom; ty++)
            for (int tx = left; tx < right; tx++) {
                double x = tx + .5 - cx, y = ty + .5 - cy;
                int sx = (int) Math.floor(((x * cos + y * sin) / width + .5) * source.getWidth());
                int sy = (int) Math.floor(((-x * sin + y * cos) / height + .5) * source.getHeight());
                if (sx < 0 || sy < 0 || sx >= source.getWidth() || sy >= source.getHeight())
                    continue;
                int s = source.getPixelRGBA(sx, sy), sa = Math.round((s >>> 24) * layer.alpha());
                if (sa == 0)
                    continue;
                int d = target.getPixelRGBA(tx, ty), da = d >>> 24, a = sa + (da * (255 - sa) + 127) / 255,
                        result = a << 24;
                for (int shift = 0; shift <= 16; shift += 8) {
                    int sc = (s >>> shift) & 255, dc = (d >>> shift) & 255;
                    int c = (sc * sa + (dc * da * (255 - sa) + 127) / 255 + a / 2) / Math.max(1, a);
                    result |= Mth.clamp(c, 0, 255) << shift;
                }
                target.setPixelRGBA(tx, ty, result);
            }
    }

    static void reset() {
        var manager = Minecraft.getInstance().getTextureManager();
        if (leftId != null)
            manager.release(leftId);
        else if (left != null)
            left.close();
        if (centerId != null)
            manager.release(centerId);
        else if (center != null)
            center.close();
        if (rightId != null)
            manager.release(rightId);
        else if (right != null)
            right.close();
        for (NativeImage image : new NativeImage[] { leftBase, centerBase, centerReticle, centerArrow, rightBase,
                rightSquare })
            if (image != null)
                image.close();
        leftBase = centerBase = centerReticle = centerArrow = rightBase = rightSquare = null;
        left = center = right = null;
        leftId = centerId = rightId = null;
        materials = Map.of();
        loaded = false;
        lastUpdate = Long.MIN_VALUE;
        started = phaseStarted = 0;
        phase = "Normal";
    }
}
