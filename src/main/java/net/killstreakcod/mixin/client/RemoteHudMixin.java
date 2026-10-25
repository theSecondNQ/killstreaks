package net.killstreakcod.mixin.client;

import net.killstreakcod.render.RemoteHud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 HUD 事件派发前接管，避免仅取消 Pre 事件仍被 receiveCanceled 监听器绘制其他界面。 */
@Mixin(value = GuiLayerManager.class, remap = false)
public abstract class RemoteHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreaks$remoteHud(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (RemoteHud.render(graphics, tracker.getGameTimeDeltaPartialTick(false)))
            ci.cancel();
    }
}
