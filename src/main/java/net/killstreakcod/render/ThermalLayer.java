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

/** 通过附加层采集已变换的生物轮廓，原实体材质继续由各自渲染器绘制。 */
public final class ThermalLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final VertexConsumer MASK = new MaskVertices();
    private static final MultiBufferSource MASK_BUFFERS = type -> MASK;

    public ThermalLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limb, float amount,
            float partial, float age, float yaw, float pitch) {
        if (captures(entity))
            OriginalJuggernautVisibility.renderBase(getParentModel(), entity,
                    () -> getParentModel().renderToBuffer(pose, MASK, light, OverlayTexture.NO_OVERLAY, -1));
    }

    static boolean captures(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return ThermalVision.isCapturing() && !OptionalShaderCompat.shadowPass() && mc.player != null
                && entity != mc.player && !entity.isInvisible() && !entity.isSpectator()
                && !(entity instanceof TacticalUnitEntity) && TacticalVisibility.visibleFromCamera(entity);
    }

    /** 仅本模组护甲层使用的轮廓接收器，不向世界缓冲区写入白色材质。 */
    static MultiBufferSource maskBuffers() {
        return MASK_BUFFERS;
    }

    private static final class MaskVertices implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            ThermalVision.capture(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
