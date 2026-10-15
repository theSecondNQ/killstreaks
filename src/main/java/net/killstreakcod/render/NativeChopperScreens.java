package net.killstreakcod.render;

import net.killstreakcod.universal.TacticalVisibility;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Six native Apache layers on the original cockpit screen meshes; no full-screen green panel.
 * 在原座舱屏幕网格上合成六层攻击直升机原资源界面，不绘制覆盖全屏的绿色面板。
 */
public final class NativeChopperScreens {
    private static NativeImage centerBase, centerReticle, centerArrow, rightBase, rightSquare;
    private static DynamicTexture center, right;
    private static ResourceLocation centerId, rightId;
    private static Map<String, ResourceLocation> materials = Map.of();
    private static boolean loaded;
    private static long lastUpdate = Long.MIN_VALUE;

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
                centerArrow = read("tablet_chopper_gunner_center_arrow");
                rightBase = read("tablet_chopper_gunner_right_background");
                rightSquare = read("tablet_chopper_gunner_right_square");
                center = new DynamicTexture(new NativeImage(centerBase.getWidth(), centerBase.getHeight(), false));
                right = new DynamicTexture(new NativeImage(rightBase.getWidth(), rightBase.getHeight(), false));
                center.setFilter(true, false);
                right.setFilter(true, false);
                var manager = Minecraft.getInstance().getTextureManager();
                centerId = manager.register("killstreak_apache_center", center);
                rightId = manager.register("killstreak_apache_right", right);
                var leftId = NativeRemoteHud.screenTexture("tablet_chopper_gunner_left_background");
                if (leftId == null)
                    throw new IllegalStateException("Missing left cockpit panel");
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
        if (center == null || right == null || mc.level == null)
            return;
        long tick = mc.level.getGameTime();
        if (lastUpdate != Long.MIN_VALUE && tick >= lastUpdate && tick - lastUpdate < 2)
            return;
        lastUpdate = tick;
        NativeImage image = center.getPixels(), r = right.getPixels();
        if (image == null || r == null)
            return;
        image.copyFrom(centerBase);
        r.copyFrom(rightBase);
        composite(image, centerReticle, 0, 0);

        // 使用原俯仰指针，将源 LUI 定位适配为实际传感器角度。
        int pointerY = Math.round(centerBase.getHeight() * .5F
                + Mth.clamp(TacticalClient.pitch(), -75, 75) / 75F * centerBase.getHeight() * .30F);
        composite(image, centerArrow, 30, pointerY - centerArrow.getHeight() / 2);
        Entity camera = mc.getCameraEntity();
        int[] contacts = TacticalClient.radar.contacts();
        Vec3 nearest = null;
        double distance = Double.MAX_VALUE;
        if (camera != null && !TacticalClient.radar.jammed())
            for (int i = 0; i + 3 < contacts.length; i += 4) {
                Vec3 point = new Vec3(contacts[i] / 10D, contacts[i + 1] / 10D, contacts[i + 2] / 10D);
                double d = point.distanceToSqr(camera.position());
                if (d < distance && TacticalVisibility.pointVisible(point)) {
                    distance = d;
                    nearest = point;
                }
            }
        if (nearest != null && camera != null) {
            Vec3 offset = nearest.subtract(camera.position()),
                    forward = Vec3.directionFromRotation(0, TacticalClient.yaw()),
                    side = Vec3.directionFromRotation(0, TacticalClient.yaw() + 90);
            float rx = (float) offset.dot(side) / 192F, ry = (float) -offset.dot(forward) / 192F,
                    length = (float) Math.sqrt(rx * rx + ry * ry);
            if (length > .85F) {
                rx *= .85F / length;
                ry *= .85F / length;
            }
            composite(r, rightSquare,
                    Math.round(rightBase.getWidth() * .5F + rx * rightBase.getWidth() * .32F
                            - rightSquare.getWidth() / 2F),
                    Math.round(rightBase.getHeight() * .42F + ry * rightBase.getHeight() * .32F
                            - rightSquare.getHeight() / 2F));
        }
        center.upload();
        right.upload();
    }

    /**
     * NativeImage uses ABGR integers; source pixels remain intact, composed only in runtime buffers.
     * NativeImage 的整数像素格式为 ABGR；保留源图像像素，仅在运行时缓冲中合成。
     */
    private static void composite(NativeImage target, NativeImage source, int x, int y) {
        for (int sy = 0; sy < source.getHeight(); sy++)
            for (int sx = 0; sx < source.getWidth(); sx++) {
                int tx = x + sx, ty = y + sy;
                if (tx < 0 || ty < 0 || tx >= target.getWidth() || ty >= target.getHeight())
                    continue;
                int s = source.getPixelRGBA(sx, sy), sa = s >>> 24;
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
        if (centerId != null)
            manager.release(centerId);
        else if (center != null)
            center.close();
        if (rightId != null)
            manager.release(rightId);
        else if (right != null)
            right.close();
        for (NativeImage image : new NativeImage[] { centerBase, centerReticle, centerArrow, rightBase, rightSquare })
            if (image != null)
                image.close();
        centerBase = centerReticle = centerArrow = rightBase = rightSquare = null;
        center = right = null;
        centerId = rightId = null;
        materials = Map.of();
        loaded = false;
        lastUpdate = Long.MIN_VALUE;
    }
}
