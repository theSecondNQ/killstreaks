package net.killstreakcod.mixin.compat;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ's shared key context also covers melee, inspect, fire select, crawl and refit.
 * TaCZ 的共享按键上下文也覆盖近战、检视、射击模式切换、匍匐和改装操作。
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.InputExtraCheck", remap = false)
public abstract class TaczInputContextMixin {
    @Inject(method = "isInGame()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$context(CallbackInfoReturnable<Boolean> ci) {
        if (OptionalClientIntegrations.controlsGun())
            ci.setReturnValue(false);
    }
}
