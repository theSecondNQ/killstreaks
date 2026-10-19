package net.killstreakcod.mixin.compat;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.ShootKey", remap = false)
public abstract class TaczShootMixin {
    @Shadow
    private static boolean lastTimeShootSuccess;
    @Shadow
    @Final
    public static KeyMapping SHOOT_KEY;

    @Inject(method = "autoShoot", at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$remoteFire(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (OptionalClientIntegrations.controlsGun()) {
            lastTimeShootSuccess = false;
            while (SHOOT_KEY.consumeClick()) {
            }
            ci.cancel();
        }
    }

    @Inject(method = "semiShootController", at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$controller(boolean down, CallbackInfoReturnable<Boolean> ci) {
        if (OptionalClientIntegrations.controlsGun()) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "autoShootController", at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$autoController(CallbackInfoReturnable<Boolean> ci) {
        if (OptionalClientIntegrations.controlsGun())
            ci.setReturnValue(false);
    }
}
