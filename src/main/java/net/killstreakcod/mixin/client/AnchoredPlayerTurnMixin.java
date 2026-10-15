package net.killstreakcod.mixin.client;

import net.killstreakcod.render.TacticalClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MouseHandler turns LocalPlayer even when another entity owns the camera.
 * 即使摄像机属于另一个实体，MouseHandler 仍会转动 LocalPlayer。
 */
@Mixin(value = Entity.class, remap = false)
public abstract class AnchoredPlayerTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void killstreakcod$keepBodyFacing(double yaw, double pitch, CallbackInfo callback) {
        if (TacticalClient.remote() && (Object) this == Minecraft.getInstance().player)
            callback.cancel();
    }
}
