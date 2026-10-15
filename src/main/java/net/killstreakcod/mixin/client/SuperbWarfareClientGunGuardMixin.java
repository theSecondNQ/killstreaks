package net.killstreakcod.mixin.client;

import net.killstreakcod.render.compat.OptionalClientIntegrations;
import net.killstreakcod.universal.compat.SuperbWarfareCompat;

import net.minecraft.client.Minecraft;
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
public abstract class SuperbWarfareClientGunGuardMixin {
    private static boolean killstreakcod$blocked(Entity shooter) {
        return shooter == Minecraft.getInstance().player && OptionalClientIntegrations.controlsGun();
    }

    @Inject(method = "canShoot(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreakcod$canShoot(Entity shooter, CallbackInfoReturnable<Boolean> callback) {
        if (killstreakcod$blocked(shooter))
            callback.setReturnValue(false);
    }

    @Inject(method = "shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreakcod$shoot(@Coerce Object parameters, CallbackInfo callback) {
        if (killstreakcod$blocked(SuperbWarfareCompat.shooter(parameters)))
            callback.cancel();
    }
}
