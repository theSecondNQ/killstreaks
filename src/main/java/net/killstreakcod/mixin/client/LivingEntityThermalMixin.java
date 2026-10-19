package net.killstreakcod.mixin.client;

import net.killstreakcod.render.OriginalJuggernautVisibility;
import net.killstreakcod.render.ThermalLayer;

import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forge remaps the vanilla selectors through the generated production refmap.
 * Forge 通过生成的 refmap 将原版注入点映射到正式环境。
 */
@Mixin(value = LivingEntityRenderer.class)
public abstract class LivingEntityThermalMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true, index = 5, require = 1)
    private MultiBufferSource killstreakcod$heatBuffer(MultiBufferSource buffers, LivingEntity entity, float yaw,
            float partial, PoseStack pose, MultiBufferSource original, int light) {
        return ThermalLayer.buffers(entity, buffers);
    }

    /**
     * Hide only the replaced base skin pass; finally restoration precedes every armor/item layer.
     * 只隐藏被替换的基础皮肤绘制阶段，finally 中的状态恢复必须先于所有护甲与物品图层。
     */
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), require = 1)
    private void killstreakcod$juggernautBase(EntityModel<?> model, PoseStack pose, VertexConsumer vertices, int light,
            int overlay, float red, float green, float blue, float alpha, LivingEntity entity, float yaw,
            float partial, PoseStack renderPose, MultiBufferSource buffers, int packedLight) {
        OriginalJuggernautVisibility.renderBase(model, entity,
                () -> model.renderToBuffer(pose, vertices, light, overlay, red, green, blue, alpha));
    }
}
