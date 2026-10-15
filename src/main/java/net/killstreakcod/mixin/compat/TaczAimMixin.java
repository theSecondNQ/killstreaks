package net.killstreakcod.mixin.compat;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerAim", remap = false)
public abstract class TaczAimMixin {
    @ModifyVariable(method = "aim(Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private boolean killstreakcod$remoteAim(boolean aiming) {
        return aiming && !OptionalClientIntegrations.controlsGun();
    }
}
