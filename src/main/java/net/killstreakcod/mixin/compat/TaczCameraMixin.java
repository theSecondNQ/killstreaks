package net.killstreakcod.mixin.compat;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczCameraMixin {
    @Inject(method = { "applyLevelCameraAnimation",
            "applyCameraRecoil" }, at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$camera(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (OptionalClientIntegrations.controlsGun())
            ci.cancel();
    }

    @Inject(method = { "applyScopeMagnification",
            "applyGunModelFovModifying" }, at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$fov(ViewportEvent.ComputeFov event, CallbackInfo ci) {
        if (OptionalClientIntegrations.controlsGun())
            ci.cancel();
    }

    @Inject(method = "onComputeMovementFov", at = @At("HEAD"), cancellable = true, remap = false)
    private static void killstreakcod$movementFov(ComputeFovModifierEvent event, CallbackInfo ci) {
        if (OptionalClientIntegrations.controlsGun())
            ci.cancel();
    }
}
