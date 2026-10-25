package net.killstreakcod.render;

import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.TacticalVisibility;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * Collect posed geometry through Forge layers without intercepting other renderers.
 * 通过 Forge 附加层采集已完成姿态计算的网格，不拦截其他实体渲染器。
 */
public final class ThermalLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final VertexConsumer MASK = new MaskVertices();

    public ThermalLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limb, float amount,
            float partial, float age, float yaw, float pitch) {
        if (captures(entity)) {
            // 只采集轮廓，不向世界缓冲区提交白色材质，也不修改共享模型的可见状态。
            getParentModel().renderToBuffer(pose, MASK, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }

    static boolean captures(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return ThermalVision.isCapturing() && !OptionalShaderCompat.shadowPass() && mc.player != null
                && entity != mc.player && !entity.isInvisible() && !entity.isSpectator()
                && !(entity instanceof TacticalUnitEntity) && TacticalVisibility.visibleFromCamera(entity);
    }

    /** Only our own armor uses this sink. 仅本模组护甲层使用此轮廓缓冲区。 */
    static MultiBufferSource maskBuffers() {
        return type -> MASK;
    }

    private static final class MaskVertices implements VertexConsumer {
        private float x;
        private float y;
        private float z;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            return this;
        }

        @Override
        public void endVertex() {
            ThermalVision.capture(x, y, z);
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}
    }
}
