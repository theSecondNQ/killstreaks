package net.killstreakcod.mixin;

import net.killstreakcod.universal.compat.SuperbWarfareCompat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.atsuishio.superbwarfare.data.gun.GunData", remap = false)
public abstract class SuperbWarfareGunGuardMixin {
    @Inject(method = "canShoot(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreakcod$canShoot(Entity shooter, CallbackInfoReturnable<Boolean> callback) {
        if (SuperbWarfareCompat.blockWeapon(shooter))
            callback.setReturnValue(false);
    }

    @Inject(method = "shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreakcod$shoot(@Coerce Object parameters, CallbackInfo callback) {
        if (SuperbWarfareCompat.blockWeapon(SuperbWarfareCompat.shooter(parameters)))
            callback.cancel();
    }
}
