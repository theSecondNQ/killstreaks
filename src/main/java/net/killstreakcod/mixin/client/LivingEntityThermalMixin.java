package net.killstreakcod.mixin.client;

import net.killstreakcod.render.OriginalJuggernautVisibility;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge 1.21.1 uses these Mojmap selectors in development and production.
 * NeoForge 1.21.1 在开发和正式环境中都使用这些 Mojmap 方法选择器。
 */
@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class LivingEntityThermalMixin {
    /**
     * Hide only the replaced base skin pass; finally restoration precedes every armor/item layer.
     * 只隐藏被替换的基础皮肤绘制阶段，finally 中的状态恢复必须先于所有护甲与物品图层。
     */
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"), remap = false, require = 1)
    private void killstreakcod$juggernautBase(EntityModel<?> model, PoseStack pose, VertexConsumer vertices, int light,
            int overlay, int color, Operation<Void> original, @Local(argsOnly = true) LivingEntity entity) {
        OriginalJuggernautVisibility.renderBase(model, entity,
                () -> original.call(model, pose, vertices, light, overlay, color));
    }
}
