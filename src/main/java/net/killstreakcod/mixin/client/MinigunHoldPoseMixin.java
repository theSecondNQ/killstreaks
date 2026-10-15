package net.killstreakcod.mixin.client;

import net.killstreakcod.render.NativeMinigunPresentation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class MinigunHoldPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void killstreakcod$hold(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch,
            CallbackInfo callback) {
        NativeMinigunPresentation.body((HumanoidModel<?>) (Object) this, entity, age);
    }
}
