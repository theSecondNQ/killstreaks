package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;

import java.io.IOException;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Independent sensor compositor: Iris owns its pipeline and GameRenderer owns its effect.
 * 传感器使用独立合成器，Iris 管理其渲染管线，GameRenderer 管理其后处理效果。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class ThermalVision {
    private static final int MAX_VERTICES = 262144; // 最多缓存三 MiB 的位置数据，容量按完整四边形截断。
    private static RenderTarget main, heat, scene;
    private static ShaderInstance maskShader, compositeShader;
    private static ClientLevel world;
    private static BufferBuilder storage;
    private static BufferBuilder vertices;
    private static int count, width, height;
    private static boolean capturing, ready;
    private static long nextAttempt = Long.MIN_VALUE;

    private ThermalVision() {}

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.player.isAlive() && TacticalClient.remote()
                && TacticalClient.state.thermal() && StreakPresentation.remoteHudVisible();
    }

    /**
     * Called by the existing remote state handler/tick; no vanilla shader is loaded or shut down.
     * 由现有遥控状态处理器或更新逻辑调用，不加载也不关闭原版着色器。
     */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || world != null && world != mc.level)
            invalidate();
    }

    /**
     * Layers submit posed vertices only while the sensor's independent mask is collecting.
     * 仅在传感器独立遮罩开始采集后，附加层才提交已变换的顶点。
     */
    static boolean isCapturing() {
        return capturing;
    }

    static void capture(float x, float y, float z) {
        if (!capturing || count >= MAX_VERTICES)
            return;
        if (vertices == null) {
            vertices = storage;
            vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        }
        vertices.vertex(x, y, z).endVertex();
        count++;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
            return;
        if (OptionalShaderCompat.shadowPass())
            return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            capturing = false;
            ready = false;
            vertices = null;
            count = 0;
            discardVertices();
            if (active() && ensure())
                capturing = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (!capturing)
                return;
            capturing = false;
            if (!active() || heat == null) {
                discardVertices();
                return;
            }
            try (ThermalRenderState ignored = new ThermalRenderState()) {

                // AFTER_LEVEL 在 Iris 最终合成之后、手部及 HUD 清除深度之前执行；
                // 复制真实场景深度，避免未被遮挡的视线区域让目标显示成穿墙轮廓。
                RenderSystem.disableScissor();
                RenderSystem.depthMask(true);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.colorMask(true, true, true, true);
                heat.clear(Minecraft.ON_OSX);
                heat.copyDepthFrom(main);
                heat.bindWrite(true);
                if (vertices != null && maskShader != null) {
                    var modelView = RenderSystem.getModelViewStack();
                    modelView.pushPose();
                    try {
                        modelView.last().pose().set(WorldRenderMatrices.view(event));
                        RenderSystem.applyModelViewMatrix();
                        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
                        RenderSystem.setShader(() -> maskShader);
                        RenderSystem.disableBlend();
                        RenderSystem.disableCull();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.depthMask(false);

                        // 原场景与遮罩的投影完全一致，通过微小偏移避免深度冲突闪烁。
                        RenderSystem.enablePolygonOffset();
                        RenderSystem.polygonOffset(-1, -1);
                        BufferBuilder.RenderedBuffer mesh = vertices.endOrDiscardIfEmpty();
                        vertices = null;
                        if (mesh != null)
                            BufferUploader.drawWithShader(mesh);
                    } finally {
                        modelView.popPose();
                        RenderSystem.applyModelViewMatrix();
                    }
                }
                ready = true;
            }
            catch (RuntimeException | LinkageError failure) {
                failed(failure);
            } finally {
                vertices = null;
                discardVertices();
            }
        }
    }

    private static void discardVertices() {
        if (storage != null) {
            // Unlike 1.21's separate storage, the 1.20 builder also owns the open batch.
            if (storage.building()) {
                var unfinished = storage.endOrDiscardIfEmpty();
                if (unfinished != null)
                    unfinished.release();
            }
            storage.discard();
        }
        vertices = null;
    }

    /**
     * Fallback for the ordinary GUI entry; the replacement remote HUD calls compose directly.
     * 普通 GUI 入口的备用回调；遥控 HUD 接管时直接调用合成方法。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void compose(RenderGuiEvent.Pre event) {
        compose(event.getGuiGraphics());
    }

    /** 合成世界颜色与可见实体遮罩；ready 在提交前清除，同一帧只能处理一次。 */
    public static void compose(GuiGraphics graphics) {
        if (!ready || scene == null || compositeShader == null || !active() || OptionalShaderCompat.shadowPass())
            return;
        ready = false;
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            graphics.flush();
            RenderSystem.disableScissor();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);

            // 读取最终显示的世界图像，而非 Iris 几何缓冲内部数据；GUI 不应用此过滤。
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST);
            main.bindWrite(true);
            RenderSystem.disableCull();
            compositeShader.setSampler("DiffuseSampler", scene.getColorTextureId());
            compositeShader.setSampler("HeatSampler", heat.getColorTextureId());
            RenderSystem.setShader(() -> compositeShader);
            BufferBuilder quad = Tesselator.getInstance().getBuilder();
            quad.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            quad.vertex(-1, -1, 0).uv(0, 0).endVertex();
            quad.vertex(1, -1, 0).uv(1, 0).endVertex();
            quad.vertex(1, 1, 0).uv(1, 1).endVertex();
            quad.vertex(-1, 1, 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(quad.end());
        }
        catch (RuntimeException | LinkageError failure) {
            failed(failure);
        }
    }

    private static boolean ensure() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        if (maskShader == null || compositeShader == null || target.width <= 0 || target.height <= 0)
            return false;
        if (scene != null && main == target && world == mc.level && width == target.width && height == target.height)
            return true;
        if (mc.level.getGameTime() < nextAttempt)
            return false;
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            if (scene != null && (main != target || world != mc.level))
                release();
            if (scene == null) {
                scene = new TextureTarget(target.width, target.height, false, Minecraft.ON_OSX);
                heat = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
                heat.setClearColor(0, 0, 0, 0);
                if (target.isStencilEnabled())
                    heat.enableStencil();
                storage = new BufferBuilder(65536);
                main = target;
                world = mc.level;
            } else {
                scene.resize(target.width, target.height, Minecraft.ON_OSX);
                heat.resize(target.width, target.height, Minecraft.ON_OSX);
            }
            width = target.width;
            height = target.height;
            nextAttempt = Long.MIN_VALUE;
            return true;
        }
        catch (RuntimeException | LinkageError failure) {
            failed(failure);
            return false;
        }
    }

    private static void failed(Throwable failure) {
        LogUtils.getLogger().warn(
                "Killstreaks: independent thermal compositor unavailable; leaving the normal scene intact", failure);
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            release();
        }
        Minecraft mc = Minecraft.getInstance();
        nextAttempt = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime() + 100;
    }

    /**
     * Reload, dimension, death, MGB teardown and sensor-off all close only our own resources.
     * 重载资源、切换维度、死亡、核弹中断及关闭传感器时，只释放本模组拥有的资源。
     */
    static void invalidate() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ThermalVision::invalidate);
            return;
        }
        if (scene != null || heat != null || storage != null)
            try (ThermalRenderState ignored = new ThermalRenderState()) {
                release();
            }
        nextAttempt = Long.MIN_VALUE;
    }

    private static void release() {
        capturing = false;
        ready = false;
        vertices = null;
        count = 0;
        if (scene != null)
            scene.destroyBuffers();
        if (heat != null)
            heat.destroyBuffers();
        scene = null;
        main = null;
        heat = null;
        world = null;
        width = height = 0;
        if (storage != null)
            storage.clear();
        storage = null;
    }

    // Forge 1.20.1 event wrappers use the simple class name; each shader subscriber needs a distinct name.
    @EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ThermalShaderEvents {
        private ThermalShaderEvents() {}

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "thermal_mask"),
                        DefaultVertexFormat.POSITION), shader -> maskShader = shader);
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "thermal_composite"),
                        DefaultVertexFormat.POSITION_TEX), shader -> compositeShader = shader);
            }
            catch (IOException | RuntimeException | LinkageError failure) {
                maskShader = null;
                compositeShader = null;
                LogUtils.getLogger().warn("Killstreaks: thermal sensor shaders could not load", failure);
            }
        }

    }
}
