package net.killstreakcod.mixin.compat;

import net.killstreakcod.render.compat.OptionalClientIntegrations;
import net.killstreakcod.render.StreakPresentation;
import net.killstreakcod.render.TacticalClient;

import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.FirstPersonRenderEvent", remap = false)
public abstract class TaczHandMixin {
    @Inject(method = "onRenderHand", at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$hideGun(RenderHandEvent event, CallbackInfo ci) {

        // 只取消这个渲染器；本模组自己的手部事件处理仍负责绘制平板或手机。
        if (OptionalClientIntegrations.controlsGun() && !(StreakPresentation.oneHandCall() && !TacticalClient.remote()
                && event.getHand() == InteractionHand.MAIN_HAND))
            ci.cancel();
    }
}
