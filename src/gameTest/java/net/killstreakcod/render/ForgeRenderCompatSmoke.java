package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.network.StreakPackets;
import net.killstreakcod.universal.NativeFxPayload;
import net.killstreakcod.universal.TracerPayload;

import java.nio.file.Files;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Isolated, opt-in world render regression. Never included in the production JAR. */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class ForgeRenderCompatSmoke {
    private static boolean opening, complete, sent, stopped;
    private static int frames;
    private static long sentAt;
    private static final long STARTED = System.nanoTime();

    private ForgeRenderCompatSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("killstreakcod.renderCompatSmoke") || event.phase != TickEvent.Phase.END || complete)
            return;
        var mc = Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (System.nanoTime() - STARTED > 180_000_000_000L)
            throw new IllegalStateException("Render compatibility test timed out at " + mc.screen);
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
                && mc.getOverlay() == null)
            mc.setScreen(new TitleScreen());
        if (opening || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null)
            return;
        opening = true;
        String name = Boolean.getBoolean("killstreakcod.cruiseSmoke") ? "render-cruise-" + STARTED : "render-compat";
        if (Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves/" + name + "/level.dat"))) {
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), name);
        } else {
            var rules = new GameRules();
            var settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.renderCompatSmoke") || complete
                || Boolean.getBoolean("killstreakcod.cruiseSmoke")
                || Boolean.getBoolean("killstreakcod.billboardSmoke")
                || Boolean.getBoolean("killstreakcod.alphaSmoke")
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ++frames < 8)
            return;
        boolean shaders = OptionalShaderCompat.shaderPackActive();
        var mc = Minecraft.getInstance();
        if (!sent) {
            sent = true;
            sentAt = mc.level.getGameTime();
            var direction = new net.minecraft.world.phys.Vec3(event.getCamera().getLookVector());
            var at = event.getCamera().getPosition().add(direction.scale(6));
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var dimension = player.level().dimension().location();
                StreakPackets.sendToPlayer(player, new NativeFxPayload(dimension, "cruise", at.x, at.y, at.z, 2, 1));
                StreakPackets.sendToPlayer(player, new TracerPayload(dimension, -1, at.x, at.y, at.z,
                        (float) direction.x * 20, (float) direction.y * 20, (float) direction.z * 20, 0));
            });
            return;
        }
        if (mc.level.getGameTime() - sentAt < 3)
            return;
        int sprites = ((java.util.Collection<?>) field(NativeFxRenderer.class, "SPRITES")).size();
        int shots = ((java.util.Collection<?>) field(TracerEffects.class, "SHOTS")).size();
        if ((sprites == 0 || shots == 0) && mc.level.getGameTime() - sentAt < 30)
            return;
        var direction = new net.minecraft.world.phys.Vec3(event.getCamera().getLookVector()).scale(6);
        var eventClip = new org.joml.Vector4f((float) direction.x, (float) direction.y, (float) direction.z, 1)
                .mul(event.getPoseStack().last().pose()).mul(event.getProjectionMatrix());
        var systemClip = new org.joml.Vector4f((float) direction.x, (float) direction.y, (float) direction.z, 1)
                .mul(RenderSystem.getModelViewMatrix()).mul(event.getProjectionMatrix());
        var correctedClip = new org.joml.Vector4f((float) direction.x, (float) direction.y, (float) direction.z, 1)
                .mul(WorldRenderMatrices.view(event)).mul(event.getProjectionMatrix());
        var origin = event.getCamera().getPosition().add(direction);
        boolean visible = event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(origin, origin).inflate(2));
        int screenEffectPixels = screenEffectPixels(event);
        var field = ThermalVision.class.getDeclaredField("maskShader");
        field.setAccessible(true);
        ShaderInstance shader = (ShaderInstance) field.get(null);
        if (shader == null)
            throw new IllegalStateException("Thermal mask shader was not loaded");
        int pixel;
        var matrix = RenderSystem.getModelViewStack();
        matrix.pushPose();
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            TextureTarget target = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
            try {
                target.setClearColor(0, 0, 0, 1);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.disableDepthTest();
                RenderSystem.disableBlend();
                RenderSystem.disableCull();
                RenderSystem.disableScissor();
                RenderSystem.colorMask(true, true, true, true);
                matrix.setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.setShader(() -> shader);
                BufferBuilder vertices = new BufferBuilder(256);
                vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                vertices.vertex(-1, -1, 0).endVertex();
                vertices.vertex(1, -1, 0).endVertex();
                vertices.vertex(1, 1, 0).endVertex();
                vertices.vertex(-1, 1, 0).endVertex();
                BufferUploader.drawWithShader(vertices.end());
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    var rgba = stack.malloc(4);
                    GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
                    pixel = (rgba.get(0) & 255) << 16 | (rgba.get(1) & 255) << 8 | rgba.get(2) & 255;
                }
            } finally {
                target.destroyBuffers();
            }
        } finally {
            matrix.popPose();
            RenderSystem.applyModelViewMatrix();
        }
        int effectPixels = effectPixels(event, false);
        // The received tracer has a shorter lifetime than the explosion's fade-in. Refresh its
        // visual event before rasterizing, after independently confirming network receipt above.
        var from = event.getCamera().getPosition()
                .add(new net.minecraft.world.phys.Vec3(event.getCamera().getLookVector()).scale(2));
        var forward = new net.minecraft.world.phys.Vec3(event.getCamera().getLookVector()).scale(20)
                .add(new net.minecraft.world.phys.Vec3(event.getCamera().getLeftVector()).scale(6));
        TracerEffects.clear();
        TracerEffects.accept(new TracerPayload(mc.level.dimension().location(), -1, from.x, from.y, from.z,
                (float) forward.x, (float) forward.y, (float) forward.z,
                net.killstreakcod.universal.TracerDelivery.CANNON));
        int tracerPixels = effectPixels(event, true);
        int texturedPixel = texturedPixel();
        float shakeDegrees = cinematicShakeDegrees();
        String result = "{\"shaders\":" + shaders + ",\"thermalMaskPixel\":" + pixel
                + ",\"shadowPass\":" + OptionalShaderCompat.shadowPass() + ",\"sprites\":" + sprites
                + ",\"shots\":" + shots + ",\"effectPixels\":" + effectPixels
                + ",\"tracerPixels\":" + tracerPixels + ",\"texturedPixel\":" + texturedPixel
                + ",\"entryScene\":" + EntryScenes.active() + ",\"eventClip\":\"" + eventClip
                + "\",\"systemClip\":\"" + systemClip + "\",\"correctedClip\":\"" + correctedClip
                + "\",\"frustumVisible\":" + visible + ",\"screenEffectPixels\":" + screenEffectPixels
                + ",\"cinematicShakeDegrees\":" + shakeDegrees + "}\n";
        Files.writeString(mc.gameDirectory.toPath().resolve("render-compat.json"), result);
        if (pixel != 0xffffff || sprites == 0 || shots == 0 || effectPixels == 0 || tracerPixels == 0
                || screenEffectPixels == 0 || !Float.isFinite(shakeDegrees) || shakeDegrees > 5)
            throw new IllegalStateException("Custom effects are missing: " + result);
        complete = true;
    }

    @SubscribeEvent
    public static void finalFrame(TickEvent.RenderTickEvent event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.renderCompatSmoke") || Boolean.getBoolean("killstreakcod.cruiseSmoke")
                || Boolean.getBoolean("killstreakcod.billboardSmoke")
                || Boolean.getBoolean("killstreakcod.alphaSmoke")
                || !complete || stopped || event.phase != TickEvent.Phase.END)
            return;
        var mc = Minecraft.getInstance();
        try (var image = screenshot()) {
            image.writeToFile(mc.gameDirectory.toPath().resolve("render-compat.png"));
        }
        stopped = true;
        mc.stop();
    }

    private static float cinematicShakeDegrees() throws Exception {
        var strength = TacticalClient.class.getDeclaredField("impactStrength");
        strength.setAccessible(true);
        float previous = strength.getFloat(null);
        try {
            TacticalClient.impact(1.1F);
            var pose = new com.mojang.blaze3d.vertex.PoseStack();
            TacticalClient.cinematicShake(pose);
            var direction = new org.joml.Vector3f(0, 0, -1);
            pose.last().pose().transformDirection(direction);
            return (float) Math.toDegrees(direction.angle(new org.joml.Vector3f(0, 0, -1)));
        } finally {
            strength.setFloat(null, previous);
        }
    }

    private static Object field(Class<?> owner, String name) throws Exception {
        var value = owner.getDeclaredField(name);
        value.setAccessible(true);
        return value.get(null);
    }

    private static com.mojang.blaze3d.platform.NativeImage screenshot() {
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            return net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        }
    }

    private static int screenEffectPixels(RenderLevelStageEvent event) {
        try (var before = screenshot()) {
            NativeFxRenderer.render(event);
            try (var after = screenshot()) {
                int changed = 0;
                for (int y = 0; y < before.getHeight(); y++) {
                    for (int x = 0; x < before.getWidth(); x++) {
                        if ((before.getPixelRGBA(x, y) & 0xffffff) != (after.getPixelRGBA(x, y) & 0xffffff))
                            changed++;
                    }
                }
                return changed;
            }
        }
    }

    private static int texturedPixel() throws Exception {
        var matrix = RenderSystem.getModelViewStack();
        matrix.pushPose();
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            var image = new com.mojang.blaze3d.platform.NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, -1);
            try (var texture = new net.minecraft.client.renderer.texture.DynamicTexture(image)) {
                TextureTarget target = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
                try {
                    target.setClearColor(0, 0, 0, 1);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableBlend();
                    RenderSystem.disableCull();
                    RenderSystem.disableScissor();
                    RenderSystem.colorMask(true, true, true, true);
                    matrix.setIdentity();
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
                    var shader = (ShaderInstance) field(NativeFxRenderer.class, "shader");
                    RenderSystem.setShader(() -> shader);
                    shader.setSampler("DiffuseSampler", texture.getId());
                    BufferBuilder vertices = new BufferBuilder(256);
                    vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    vertices.vertex(-1, -1, 0).uv(0, 0).color(255, 255, 255, 255).endVertex();
                    vertices.vertex(1, -1, 0).uv(1, 0).color(255, 255, 255, 255).endVertex();
                    vertices.vertex(1, 1, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
                    vertices.vertex(-1, 1, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
                    BufferUploader.drawWithShader(vertices.end());
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
                    try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                        var rgba = stack.malloc(4);
                        GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
                        return (rgba.get(0) & 255) << 16 | (rgba.get(1) & 255) << 8 | rgba.get(2) & 255;
                    }
                } finally {
                    target.destroyBuffers();
                }
            }
        } finally {
            matrix.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static int effectPixels(RenderLevelStageEvent event, boolean tracer) {
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            TextureTarget target = new TextureTarget(64, 64, true, Minecraft.ON_OSX);
            try {
                target.setClearColor(0, 0, 0, 1);
                RenderSystem.depthMask(true);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                if (tracer)
                    TracerEffects.render(event);
                else
                    NativeFxRenderer.render(event);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    var rgba = stack.malloc(64 * 64 * 4);
                    GL11.glReadPixels(0, 0, 64, 64, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
                    int colored = 0;
                    for (int i = 0; i < rgba.limit(); i += 4) {
                        if ((rgba.get(i) & 255) + (rgba.get(i + 1) & 255) + (rgba.get(i + 2) & 255) > 3)
                            colored++;
                    }
                    return colored;
                }
            } finally {
                target.destroyBuffers();
            }
        }
    }
}
