package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.NativeFxPayload;
import net.killstreakcod.universal.TracerDelivery;
import net.killstreakcod.universal.TracerPayload;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual final-frame orientation and terrain-depth regression, including Forge viewport camera overrides. */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class ForgeBillboardRenderSmoke {
    private record Sample(float yaw, float pitch, float roll, int distance, boolean viewport) {}
    private static final int[] EXPECTED = { 65280, 255, 65535, 16711680 };
    private static final int[][] POINTS = { { 300, 160 }, { 340, 160 }, { 300, 200 }, { 340, 200 } };
    private static final List<Sample> CASES = new ArrayList<>();
    private static final List<String> RESULTS = new ArrayList<>();
    private static int frames, next;
    private static boolean rotated, complete, failed, pending;
    private static ResourceLocation texture;
    private static Constructor<?> layerConstructor, spriteConstructor;
    private static int[] clear, scene, tracerClear, tracerScene;

    static {
        for (boolean viewport : new boolean[] { false, true }) {
            for (float pitch : new float[] { 0, -30, 30, -60, 60 }) {
                for (float yaw : new float[] { 0, 45, 90, 135, 180, 225, 270, 315 })
                    CASES.add(new Sample(yaw, pitch, 0, 6, viewport));
            }
            for (int distance : new int[] { 24, 96, 192 }) {
                for (float yaw : new float[] { 0, 90, 180, 270 })
                    CASES.add(new Sample(yaw, -30, 0, distance, viewport));
            }
        }
        for (float yaw : new float[] { 0, 90, 180, 270 }) {
            CASES.add(new Sample(yaw, -35, 60, 24, true));
            CASES.add(new Sample(yaw, -35, -60, 24, true));
        }
    }

    private ForgeBillboardRenderSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void viewport(ViewportEvent.ComputeCameraAngles event) {
        if (!Boolean.getBoolean("killstreakcod.billboardSmoke") || complete)
            return;
        Sample sample = CASES.get(next);
        if (sample.viewport) {
            event.setYaw(sample.yaw);
            event.setPitch(sample.pitch);
            event.setRoll(sample.roll);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void prepare(RenderLevelStageEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.billboardSmoke") || complete
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ++frames < 60)
            return;
        var mc = Minecraft.getInstance();
        Sample sample = CASES.get(next);
        if (!rotated) {
            mc.player.setYRot(sample.viewport ? 0 : sample.yaw);
            mc.player.yRotO = mc.player.getYRot();
            mc.player.setXRot(sample.viewport ? 0 : sample.pitch);
            mc.player.xRotO = mc.player.getXRot();
            rotated = true;
            return;
        }
        if (texture == null)
            initialize();
        Vector3f direction = new Matrix4f(WorldRenderMatrices.view(event)).invert()
                .transformDirection(new Vector3f(0, 0, -1)).normalize();
        Vec3 at = event.getCamera().getPosition().add(new Vec3(direction).scale(sample.distance));
        NativeFxRenderer.accept(new NativeFxPayload(mc.level.dimension().location(), "smoke", at.x, at.y, at.z, 1, 1));
        var sprites = sprites();
        sprites.clear();
        float progress = (3 + event.getPartialTick()) / 40;
        Object layer = layerConstructor.newInstance(texture, false, 1, 1, 2F, 2F, "orientation_test", 0, 32, 32,
                0xffffff, false);
        // Constant angular size, opacity and animation frame isolate camera-facing behavior.
        sprites.add(spriteConstructor.newInstance(at, Vec3.ZERO, layer, mc.level.getGameTime() - 3, 40,
                sample.distance * .4F / (.55F + progress), -progress * .3F, 0xffffff, 2F, false));
        pending = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void depth(RenderLevelStageEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.billboardSmoke") || !pending || complete
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
            return;
        // Run after the normal FX subscriber has painted the actual main framebuffer.
        clear = raster(event, false);
        scene = raster(event, true);
        var direction = new Matrix4f(WorldRenderMatrices.view(event)).invert()
                .transformDirection(new Vector3f(0, 0, -1)).normalize();
        Vec3 from = event.getCamera().getPosition().add(new Vec3(direction).scale(6));
        TracerEffects.accept(new TracerPayload(Minecraft.getInstance().level.dimension().location(), -1,
                from.x, from.y, from.z, direction.x * 6, direction.y * 6, direction.z * 6, TracerDelivery.CANNON));
        tracerClear = raster(event, false, true);
        tracerScene = raster(event, true, true);
    }

    @SubscribeEvent
    public static void finalFrame(TickEvent.RenderTickEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.billboardSmoke") || !pending || complete
                || event.phase != TickEvent.Phase.END)
            return;
        pending = false;
        var mc = Minecraft.getInstance();
        Sample sample = CASES.get(next);
        int[] pixels;
        try (ThermalRenderState ignored = new ThermalRenderState();
                var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels = marker(image);
            image.writeToFile(mc.gameDirectory.toPath().resolve("billboard-main-" + next + ".png"));
        }
        boolean oriented = Arrays.equals(Arrays.copyOf(clear, 4), EXPECTED) && clear[4] > 1000;
        boolean visible = sample.pitch > 0 || Arrays.equals(pixels, EXPECTED) && scene[4] >= clear[4] * .95;
        boolean occluded = sample.pitch < 30 || scene[4] <= clear[4] * .05;
        boolean tracerVisible = tracerClear[4] > 0 && (sample.pitch > 0 || tracerScene[4] >= tracerClear[4] * .95);
        boolean tracerOccluded = sample.pitch < 30 || tracerScene[4] <= tracerClear[4] * .05;
        failed |= !oriented || !visible || !occluded || !tracerVisible || !tracerOccluded;
        RESULTS.add("{\"yaw\":" + sample.yaw + ",\"pitch\":" + sample.pitch + ",\"roll\":" + sample.roll
                + ",\"distance\":" + sample.distance + ",\"viewport\":" + sample.viewport
                + ",\"pixels\":" + Arrays.toString(pixels) + ",\"clearPixels\":" + clear[4]
                + ",\"scenePixels\":" + scene[4] + ",\"oriented\":" + oriented
                + ",\"visible\":" + visible + ",\"occluded\":" + occluded
                + ",\"tracerPixels\":" + tracerClear[4] + ",\"sceneTracerPixels\":" + tracerScene[4]
                + ",\"tracerVisible\":" + tracerVisible + ",\"tracerOccluded\":" + tracerOccluded + "}");
        sprites().clear();
        TracerEffects.clear();
        if (++next < CASES.size()) {
            rotated = false;
            return;
        }
        String result = "{\"shaders\":" + OptionalShaderCompat.shaderPackActive() + ",\"cases\":["
                + String.join(",", RESULTS) + "]}\n";
        Files.writeString(mc.gameDirectory.toPath().resolve("billboard-smoke.json"), result);
        if (failed)
            throw new IllegalStateException("FX final screen orientation or terrain depth failed: " + result);
        complete = true;
        mc.getTextureManager().release(texture);
        mc.stop();
    }

    private static void initialize() throws Exception {
        var mc = Minecraft.getInstance();
        var image = new NativeImage(32, 32, false);
        int[] colors = { 0xff0000ff, 0xff00ff00, 0xffff0000, 0xff00ffff };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++)
                image.setPixelRGBA(x, y, colors[(y / 16) * 2 + x / 16]);
        }
        texture = mc.getTextureManager().register("billboard_orientation", new DynamicTexture(image));
        layerConstructor = Class.forName(NativeFxRenderer.class.getName() + "$Layer").getDeclaredConstructors()[0];
        spriteConstructor = Class.forName(NativeFxRenderer.class.getName() + "$Sprite").getDeclaredConstructors()[0];
        layerConstructor.setAccessible(true);
        spriteConstructor.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> sprites() throws Exception {
        var field = NativeFxRenderer.class.getDeclaredField("SPRITES");
        field.setAccessible(true);
        return (Collection<Object>) field.get(null);
    }

    private static int[] marker(NativeImage image) {
        int[] result = new int[4];
        for (int i = 0; i < POINTS.length; i++)
            result[i] = image.getPixelRGBA(POINTS[i][0], POINTS[i][1]) & 0xffffff;
        return result;
    }

    private static int[] raster(RenderLevelStageEvent event, boolean worldDepth) throws Exception {
        return raster(event, worldDepth, false);
    }

    private static int[] raster(RenderLevelStageEvent event, boolean worldDepth, boolean tracer) throws Exception {
        var mc = Minecraft.getInstance();
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            var target = new TextureTarget(640, 360, true, Minecraft.ON_OSX);
            try {
                target.setClearColor(0, 0, 0, 1);
                RenderSystem.depthMask(true);
                target.clear(Minecraft.ON_OSX);
                if (worldDepth)
                    target.copyDepthFrom(mc.getMainRenderTarget());
                target.bindWrite(true);
                if (tracer)
                    TracerEffects.render(event);
                else
                    NativeFxRenderer.render(event);
                try (var image = Screenshot.takeScreenshot(target)) {
                    int[] values = Arrays.copyOf(marker(image), 5);
                    for (int y = 0; y < 360; y++) {
                        for (int x = 0; x < 640; x++) {
                            if ((image.getPixelRGBA(x, y) & 0xffffff) != 0)
                                values[4]++;
                        }
                    }
                    return values;
                }
            } finally {
                target.destroyBuffers();
            }
        }
    }
}
