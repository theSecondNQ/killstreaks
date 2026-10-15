package net.killstreakcod.render.tablet;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.NativeSceneUi;
import net.killstreakcod.render.StreakPresentation;
import net.killstreakcod.render.TacticalClient;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.StreakTimings;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Original activation layers composited into a small CPU texture; no shader framebuffer switch.
 * 在 CPU 上把原激活图层合成为小尺寸纹理，不切换光影帧缓冲。
 */
public final class SceneTabletScreen {
    public static final int WIDTH = 768, HEIGHT = 524;
    private static DynamicTexture surface;
    private static ResourceLocation location;
    private static String lastReward = "", lastPhase = "";
    private static int lastFrame = -1;
    private static NativeImage content;
    private static boolean lastShaderPack;
    private static final Map<String, NativeImage> icons = new HashMap<>();
    private static String heldMap = "";
    private static long lastRevision = -1;

    private SceneTabletScreen() {}

    public static Map<String, ResourceLocation> materials(String reward) {
        if (surface == null) {
            content = new NativeImage(WIDTH, HEIGHT, false);
            content.fillRect(0, 0, WIDTH, HEIGHT, 0xff101411);
            surface = new DynamicTexture(new NativeImage(WIDTH, HEIGHT, false));
            surface.setFilter(true, false);
            location = Minecraft.getInstance().getTextureManager().register("mwii_native_tablet", surface);
        }
        boolean shaderPack = OptionalShaderCompat.shaderPackActive(), displayChanged = shaderPack != lastShaderPack;
        lastShaderPack = shaderPack;
        boolean targeting = TabletTargeting.active() && TabletTargeting.reward().equals(reward);
        float ticks = targeting ? TabletTargeting.elapsed()
                : StreakPresentation.currentReward().equals(reward) ? StreakPresentation.elapsed() : 0;
        String stage = targeting ? "targeting" : StreakPresentation.currentStage();
        if (stage.equals("confirmed") && heldMap.equals(reward)) {
            if (displayChanged)
                upload();
            return Map.of("m/offhand_vm_tablet_image_ui", location);
        }
        if (targeting) {
            int frame = (int) (ticks / 20 * 22);
            long revision = TabletTargeting.revision();
            if (!reward.equals(lastReward) || !lastPhase.equals("targeting") || lastFrame != frame
                    || lastRevision != revision) {
                lastReward = reward;
                lastPhase = "targeting";
                lastFrame = frame;
                lastRevision = revision;
                TabletTargeting.paint(content);
                upload();
            } else if (displayChanged)
                upload();
            return Map.of("m/offhand_vm_tablet_image_ui", location);
        }
        float screenTicks = stage.equals("call") ? Math.max(0, ticks - StreakTimings.remoteTabletOffset(reward))
                : ticks;
        String phase = reward.equals("mgb") ? "TacticalNuke"
                : stage.equals("exit") ? "Inactive"
                        : stage.equals("confirmed") ? "Activate"
                                : stage.equals("call") && screenTicks >= 3 ? "Activate2" : "Idle";
        float sequenceTicks = phase.equals("Activate2") ? Math.max(0, screenTicks - 3) : screenTicks;
        if (TacticalClient.radar.jammed())
            phase = "EMPStart";
        int frame = (int) (sequenceTicks / 20 * 22);
        if (!reward.equals(lastReward) || !phase.equals(lastPhase) || frame != lastFrame) {
            lastReward = reward;
            lastPhase = phase;
            lastFrame = frame;
            paint(reward, phase, sequenceTicks, frame, stage);
        } else if (displayChanged)
            upload();
        return Map.of("m/offhand_vm_tablet_image_ui", location);
    }

    public static void begin(String reward, String phase) {
        lastReward = lastPhase = "";
        lastFrame = -1;
        lastRevision = -1;
        if (!phase.equals("confirmed") || !reward.equals(heldMap))
            heldMap = "";
    }

    public static void holdMap(String reward) {
        heldMap = reward;
    }

    public static NativeImage icon(String reward) {
        return icons.computeIfAbsent(reward, k -> {
            try (var input = Minecraft.getInstance().getResourceManager().open(ResourceLocation
                    .fromNamespaceAndPath(KillstreakcodMod.MODID, "textures/gui/streaks/" + k + ".png"))) {
                return NativeImage.read(input);
            }
            catch (Exception failure) {
                return null;
            }
        });
    }

    private static void paint(String reward, String phase, float ticks, int frame, String stage) {
        NativeImage target = content;
        if (target == null)
            return;
        target.fillRect(0, 0, WIDTH, HEIGHT, 0xff101411);
        float time = ticks * 50;
        TabletCanvas canvas = new TabletCanvas(target);
        for (var layer : NativeSceneUi.layers("StreakActivationTablet", phase, time, WIDTH, HEIGHT,
                reward.equals("mgb"))) {
            NativeImage image = NativeSceneUi.pixels(layer.image());
            if (image == null)
                continue;
            canvas.image(image, layer.rect(), layer.alpha(), layer.rotation(), layer.scale(),
                    layer.sprite() ? Math.floorMod(frame, 20) : -1);
        }
        NativeImage icon = icon(reward);
        var iconLayer = NativeSceneUi.state("StreakActivationTablet", "StreakImage", phase, time, WIDTH, HEIGHT);
        if (icon != null && iconLayer != null)
            canvas.image(icon, iconLayer.rect(), iconLayer.alpha(), iconLayer.rotation(), iconLayer.scale(), -1);
        NativeSceneUi.tabletText(canvas, "Title",
                net.minecraft.network.chat.Component.translatable("streak.killstreakcod." + reward), phase, time, WIDTH,
                HEIGHT);
        NativeSceneUi.tabletText(canvas, "ButtonLabel",
                net.minecraft.network.chat.Component.translatable(
                        "screen.killstreakcod.tablet." + (reward.equals("mgb") ? "launch_nuke" : "activate")),
                phase, time, WIDTH, HEIGHT);
        if (reward.equals("mgb")) {
            NativeSceneUi.tabletText(canvas, "KillstreakDescription",
                    net.minecraft.network.chat.Component.translatable("screen.killstreakcod.tablet.nuke_description"),
                    phase, time, WIDTH, HEIGHT);
            String[] codes = { "1", "3", "2", "6", "7" };
            for (int i = 0; i < codes.length; i++)
                NativeSceneUi.tabletText(canvas, "NumpadText" + (i + 1),
                        net.minecraft.network.chat.Component.literal(codes[i]), phase, time, WIDTH, HEIGHT);
        }
        if (stage.equals("exit")) {
            float p = Mth.clamp(ticks / Math.max(1, StreakTimings.exit(reward)), 0, 1),
                    brightness = 1 - p * p * (3 - 2 * p);
            for (int y = 0; y < HEIGHT; y++)
                for (int x = 0; x < WIDTH; x++) {
                    int color = target.getPixelRGBA(x, y), out = 0xff000000;
                    for (int shift = 0; shift <= 16; shift += 8)
                        out |= Math.round(((color >>> shift) & 255) * brightness) << shift;
                    target.setPixelRGBA(x, y, out);
                }
        }
        upload();
    }

    private static void upload() {
        TabletDisplayMaterial.prepare(content, surface.getPixels(), lastShaderPack);
        surface.upload();
    }

    public static void reset() {
        if (location != null)
            Minecraft.getInstance().getTextureManager().release(location);
        else if (surface != null)
            surface.close();
        if (content != null)
            content.close();
        content = null;
        surface = null;
        location = null;
        icons.values().forEach(NativeImage::close);
        icons.clear();
        lastReward = lastPhase = heldMap = "";
        lastFrame = -1;
        lastRevision = -1;
        lastShaderPack = false;
    }
}
