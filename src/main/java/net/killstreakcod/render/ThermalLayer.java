package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.TacticalVisibility;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replace the actual model material, rather than drawing an additive coplanar layer.
 * 直接替换实际模型材质，不另画一层共面叠加图层。
 */
public final class ThermalLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public ThermalLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    // 保留现有 AddLayers 注册，替换绘制阶段同时覆盖护甲。
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limb, float amount,
            float partial, float age, float yaw, float pitch) {}

    public static MultiBufferSource buffers(LivingEntity entity, MultiBufferSource original) {
        Minecraft mc = Minecraft.getInstance();
        if (!TacticalClient.remote() || !TacticalClient.state.thermal() || !StreakPresentation.remoteHudVisible()
                || OptionalShaderCompat.shadowPass() || mc.player == null || entity == mc.player
                || entity instanceof TacticalUnitEntity || !TacticalVisibility.visibleFromCamera(entity))
            return original;

        // 只包装这一次原渲染调用，使护甲也显示热成像且不重复进入渲染器；字体、拴绳和阴影保留原顶点布局。
        return type -> type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS
                ? new WhiteVertices(original.getBuffer(HeatType.WHITE))
                : original.getBuffer(type);
    }

    private record WhiteVertices(VertexConsumer output) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            output.vertex(x, y, z);
            ThermalVision.capture((float) x, (float) y, (float) z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            output.color(255, 255, 255, 255);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            output.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            output.overlayCoords(OverlayTexture.NO_OVERLAY);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            output.uv2(15728880);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            output.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            output.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            output.defaultColor(255, 255, 255, 255);
        }

        @Override
        public void unsetDefaultColor() {
            output.unsetDefaultColor();
        }
    }

    /**
     * Normal depth test and depth writes: hot targets still disappear behind terrain.
     * 保留正常深度检测和深度写入，热目标在地形后方仍会被遮挡。
     */
    private static final class HeatType extends RenderType {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                "textures/entity/thermal_white.png");

        // 原版透明度着色器使白色不受定向光照或远距离雾影响。
        private static final RenderType WHITE = create("killstreakcod_white_heat", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_ALPHA_SHADER)
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setOverlayState(OVERLAY).createCompositeState(false));

        private HeatType() {
            super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false, () -> {
            }, () -> {
            });
        }
    }
}
