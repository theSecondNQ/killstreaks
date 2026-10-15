package net.killstreakcod.mixin.compat;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerReload", remap = false)
public abstract class TaczReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreakcod$remoteReload(CallbackInfo ci) {
        if (OptionalClientIntegrations.controlsGun())
            ci.cancel();
    }
}
