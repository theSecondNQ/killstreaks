package net.killstreakcod.mixin.client;

import net.killstreakcod.render.tablet.TabletTargeting;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ESC and targeting shortcuts are consumed before vanilla opens a screen or changes equipment.
 * 在原版打开界面或切换装备之前，先处理 ESC 和标定快捷键。
 */
@Mixin(value = KeyboardHandler.class, remap = false)
public abstract class TabletKeyboardMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void killstreakcod$tabletKeys(long window, int key, int scan, int action, int modifiers,
            CallbackInfo callback) {
        if (TabletTargeting.keyboard(window, key, scan, action, modifiers))
            callback.cancel();
    }
}
