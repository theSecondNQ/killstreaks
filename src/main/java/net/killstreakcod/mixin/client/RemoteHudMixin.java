package net.killstreakcod.mixin.client;

import net.killstreakcod.render.RemoteHud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Forge HUD 事件派发前接管，同时支持开发环境名称和生产环境 SRG 方法名。 */
@Mixin(value = ForgeGui.class, remap = false)
public abstract class RemoteHudMixin {
    @Inject(method = { "render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            "m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void killstreaks$remoteHud(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (RemoteHud.render(graphics, partial))
            ci.cancel();
    }
}
