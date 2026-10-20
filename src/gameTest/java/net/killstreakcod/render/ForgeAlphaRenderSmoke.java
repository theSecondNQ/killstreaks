package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.NativeFxPayload;
import net.killstreakcod.universal.TracerDelivery;
import net.killstreakcod.universal.TracerPayload;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Compare overlapping translucent FX against independently submitted back-to-front layers. */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class ForgeAlphaRenderSmoke {
    private static final List<String> RESULTS = new ArrayList<>();
    private static final List<String> BLEND_RESULTS = new ArrayList<>();
    private static final List<String> TRACER_RESULTS = new ArrayList<>();
    private static final List<String> MAIN_RESULTS = new ArrayList<>();
    private static Constructor<?> layerConstructor, spriteConstructor;
    private static ResourceLocation black, white;
    private static int frames, next;
    private static boolean complete, failed, pending;
    private static final int[] MAIN_EXPECTED = new int[3];

    private ForgeAlphaRenderSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.alphaSmoke") || complete
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
            return;
        var mc = Minecraft.getInstance();
        mc.player.setYRot(0);
        mc.player.yRotO = 0;
        mc.player.setXRot(-30);
        mc.player.xRotO = -30;
        if (++frames < 60)
            return;
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            sample(event);
        }
    }

    private static void sample(RenderLevelStageEvent event) throws Exception {
        var mc = Minecraft.getInstance();
        if (black == null)
            initialize();
        boolean real = next >= 24, moving = next % 24 / 8 == 1, offAxis = next % 24 / 8 == 2;
        double shift = (next % 8 - 3.5) * .002;
        Vector3f forward = new Matrix4f(WorldRenderMatrices.view(event)).invert()
                .transformDirection(new Vector3f(0, 0, -1)).normalize();
        Vec3 direction = new Vec3(forward), eye = event.getCamera().getPosition();
        Vec3 side = new Vec3(WorldRenderMatrices.billboardRotation(event).transform(new Vector3f(1, 0, 0)));
        NativeFxRenderer.accept(new NativeFxPayload(mc.level.dimension().location(), "smoke",
                eye.x, eye.y, eye.z, 1, 1));
        var sprites = sprites();
        sprites.clear();
        double age = 3 + event.getPartialTick();
        Object smoke = layer(real ? "70f0c478b689052c7796354d.png" : null, false);
        Object fire = layer(real ? "ec670dd77701bdb986597154.png" : null, true);
        Object back = sprite(smoke, eye.add(direction.scale(moving ? 6.2 : 6 + shift)),
                moving ? direction.scale((shift - .2) / age) : Vec3.ZERO, false, event);
        Object middle = sprite(fire, eye.add(direction.scale(6)).add(side.scale(offAxis ? .8 : 0)),
                Vec3.ZERO, true, event);
        Object front = sprite(smoke, eye.add(direction.scale(5.8)), Vec3.ZERO, false, event);
        if (next == 0) {
            for (boolean glow : new boolean[] { false, true }) {
                for (boolean translucent : new boolean[] { false, true }) {
                    try (ThermalRenderState ignored = new ThermalRenderState()) {
                        if (translucent)
                            new BlendMode(GL11.GL_ONE, GL11.GL_ZERO, GL14.GL_FUNC_ADD).apply();
                        else
                            new BlendMode().apply();
                        var single = sprite(layer(null, glow), eye.add(direction.scale(6)), Vec3.ZERO, glow, event);
                        try (var image = raster(event, List.of(single), false)) {
                            int color = image.getPixelRGBA(320, 180) & 255, expected = glow ? 192 : 32;
                            boolean passed = Math.abs(color - expected) <= 1;
                            failed |= !passed;
                            BLEND_RESULTS.add("{\"glow\":" + glow + ",\"previousTranslucent\":" + translucent
                                    + ",\"expected\":" + expected + ",\"pixel\":" + color
                                    + ",\"passed\":" + passed + "}");
                        }
                    }
                }
            }
            tracers(event, eye, direction);
        }
        List<Object> submitted = List.of(back, middle, front);
        List<Object> reference = shift > 0 ? submitted : List.of(middle, back, front);
        try (var actual = raster(event, submitted, false); var expected = raster(event, reference, true)) {
            int bad = 0, maximum = 0;
            long error = 0;
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    int a = actual.getPixelRGBA(x, y), b = expected.getPixelRGBA(x, y), difference = 0;
                    for (int channel = 0; channel < 3; channel++) {
                        int delta = Math.abs((a >>> (channel * 8) & 255) - (b >>> (channel * 8) & 255));
                        maximum = Math.max(maximum, delta);
                        error += delta;
                        difference = Math.max(difference, delta);
                    }
                    if (difference > 2)
                        bad++;
                }
            }
            boolean passed = bad == 0;
            failed |= !passed;
            RESULTS.add("{\"frame\":" + next + ",\"realTextures\":" + real + ",\"moving\":" + moving
                    + ",\"offAxis\":" + offAxis
                    + ",\"shift\":" + shift + ",\"badPixels\":" + bad + ",\"maxError\":" + maximum
                    + ",\"totalError\":" + error + ",\"passed\":" + passed + "}");
            if (next % 8 == 3 || next % 8 == 4) {
                actual.writeToFile(mc.gameDirectory.toPath().resolve("alpha-actual-" + next + ".png"));
                expected.writeToFile(mc.gameDirectory.toPath().resolve("alpha-expected-" + next + ".png"));
            }
        }
        sprites.clear();
        mainFrame(event, eye.add(direction.scale(6)));
        next++;
    }

    @SubscribeEvent
    public static void finalFrame(TickEvent.RenderTickEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.alphaSmoke") || complete || !pending
                || event.phase != TickEvent.Phase.END)
            return;
        pending = false;
        var mc = Minecraft.getInstance();
        try (ThermalRenderState ignored = new ThermalRenderState();
                var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int pixel = image.getPixelRGBA(330, 170), maximum = 0;
            for (int channel = 0; channel < 3; channel++) {
                maximum = Math.max(maximum, Math.abs((pixel >>> (channel * 8) & 255) - MAIN_EXPECTED[channel]));
            }
            boolean passed = maximum <= 2;
            failed |= !passed;
            MAIN_RESULTS.add("{\"frame\":" + (next - 1) + ",\"expected\":"
                    + java.util.Arrays.toString(MAIN_EXPECTED) + ",\"pixel\":" + (pixel & 0xffffff)
                    + ",\"maxError\":" + maximum + ",\"passed\":" + passed + "}");
            if (next == 4 || next == 5)
                image.writeToFile(mc.gameDirectory.toPath().resolve("alpha-main-" + (next - 1) + ".png"));
        }
        if (next < 48)
            return;
        String result = "{\"shaders\":" + OptionalShaderCompat.shaderPackActive() + ",\"passed\":" + !failed
                + ",\"blendCases\":[" + String.join(",", BLEND_RESULTS)
                + "],\"tracerCases\":[" + String.join(",", TRACER_RESULTS)
                + "],\"mainCases\":[" + String.join(",", MAIN_RESULTS)
                + "],\"cases\":[" + String.join(",", RESULTS) + "]}\n";
        Files.writeString(mc.gameDirectory.toPath().resolve("alpha-smoke.json"), result);
        complete = true;
        mc.getTextureManager().release(black);
        mc.getTextureManager().release(white);
        mc.stop();
        if (failed)
            throw new IllegalStateException("Translucent FX order or edge compositing failed: " + result);
    }

    private static void mainFrame(RenderLevelStageEvent event, Vec3 at) throws Exception {
        var mc = Minecraft.getInstance();
        boolean glow = next % 4 >= 2;
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int pixel = image.getPixelRGBA(330, 170);
                for (int channel = 0; channel < 3; channel++) {
                    int background = pixel >>> (channel * 8) & 255;
                    MAIN_EXPECTED[channel] = glow ? Math.min(255, background + 128)
                            : Math.round(background * (1 - 128F / 255));
                }
            }
            if (next % 2 == 0)
                new BlendMode().apply();
            else
                new BlendMode(GL11.GL_ONE, GL11.GL_ZERO, GL14.GL_FUNC_ADD).apply();
            var sprites = sprites();
            sprites.clear();
            sprites.add(sprite(layer(null, glow), at, Vec3.ZERO, glow, event));
            mc.getMainRenderTarget().bindWrite(true);
            NativeFxRenderer.render(event);
            sprites.clear();
        }
        pending = true;
    }

    private static Object layer(String asset, boolean glow) throws Exception {
        ResourceLocation texture = asset == null ? (glow ? white : black)
                : ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "textures/combatfx417/" + asset);
        return layerConstructor.newInstance(texture, glow, asset == null ? 1 : 8, asset == null ? 1 : 8,
                2F, 2F, "alpha_regression", 0, asset == null ? 32 : 512, asset == null ? 32 : 512,
                0xffffff, false);
    }

    private static Object sprite(Object layer, Vec3 origin, Vec3 velocity, boolean glow,
            RenderLevelStageEvent event) throws Exception {
        float progress = (3 + event.getPartialTick()) / 40;
        return spriteConstructor.newInstance(origin, velocity, layer, Minecraft.getInstance().level.getGameTime() - 3,
                40, 2.4F / (glow ? 1 + progress * .4F : .55F + progress), -progress * .3F,
                0xffffff, glow ? 1 / .9F : 2F, false);
    }

    private static NativeImage raster(RenderLevelStageEvent event, List<Object> layers, boolean serial)
            throws Exception {
        return raster(event, layers, serial, false);
    }

    private static NativeImage raster(RenderLevelStageEvent event, List<Object> layers, boolean serial, boolean tracer)
            throws Exception {
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            var target = new TextureTarget(640, 360, true, Minecraft.ON_OSX);
            try {
                target.setClearColor(.25F, .25F, .25F, 1);
                RenderSystem.depthMask(true);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                var sprites = sprites();
                sprites.clear();
                if (tracer) {
                    TracerEffects.render(event);
                } else if (serial) {
                    for (Object layer : layers) {
                        sprites.clear();
                        sprites.add(layer);
                        NativeFxRenderer.render(event);
                    }
                } else {
                    sprites.addAll(layers);
                    NativeFxRenderer.render(event);
                }
                return Screenshot.takeScreenshot(target);
            } finally {
                target.destroyBuffers();
            }
        }
    }

    private static void tracers(RenderLevelStageEvent event, Vec3 eye, Vec3 direction) throws Exception {
        var mc = Minecraft.getInstance();
        Vec3 from = eye.add(direction.scale(6));
        for (byte profile = 0; profile <= TracerDelivery.GUNSHIP25; profile++) {
            TracerEffects.clear();
            TracerEffects.accept(new TracerPayload(mc.level.dimension().location(), -1,
                    from.x, from.y, from.z, (float) direction.x * 6, (float) direction.y * 6,
                    (float) direction.z * 6, profile));
            new BlendMode().apply();
            try (var first = raster(event, List.of(), false, true)) {
                new BlendMode(GL11.GL_ONE, GL11.GL_ZERO, GL14.GL_FUNC_ADD).apply();
                try (var second = raster(event, List.of(), false, true)) {
                    int bad = 0, visible = 0, maximum = 0;
                    for (int y = 0; y < first.getHeight(); y++) {
                        for (int x = 0; x < first.getWidth(); x++) {
                            int a = first.getPixelRGBA(x, y), b = second.getPixelRGBA(x, y), delta = 0;
                            if ((a & 0xffffff) != 0x404040)
                                visible++;
                            for (int channel = 0; channel < 3; channel++) {
                                delta = Math.max(delta,
                                        Math.abs((a >>> (8 * channel) & 255) - (b >>> (8 * channel) & 255)));
                            }
                            maximum = Math.max(maximum, delta);
                            if (delta > 2)
                                bad++;
                        }
                    }
                    boolean passed = bad == 0 && visible > 0;
                    failed |= !passed;
                    TRACER_RESULTS.add("{\"profile\":" + profile + ",\"badPixels\":" + bad
                            + ",\"visiblePixels\":" + visible + ",\"maxError\":" + maximum
                            + ",\"passed\":" + passed + "}");
                }
            }
        }
        TracerEffects.clear();
    }

    private static void initialize() throws Exception {
        layerConstructor = Class.forName(NativeFxRenderer.class.getName() + "$Layer").getDeclaredConstructors()[0];
        spriteConstructor = Class.forName(NativeFxRenderer.class.getName() + "$Sprite").getDeclaredConstructors()[0];
        layerConstructor.setAccessible(true);
        spriteConstructor.setAccessible(true);
        black = texture("alpha_smoke", 0);
        white = texture("alpha_fire", 0xffffff);
    }

    private static ResourceLocation texture(String name, int color) {
        var image = new NativeImage(32, 32, false);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double radius = Math.hypot((x - 15.5) / 15, (y - 15.5) / 15);
                int alpha = (int) (128 * Math.max(0, Math.min(1, (1 - radius) * 4)));
                image.setPixelRGBA(x, y, alpha << 24 | color);
            }
        }
        return Minecraft.getInstance().getTextureManager().register(name, new DynamicTexture(image));
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> sprites() throws Exception {
        var field = NativeFxRenderer.class.getDeclaredField("SPRITES");
        field.setAccessible(true);
        return (Collection<Object>) field.get(null);
    }
}
