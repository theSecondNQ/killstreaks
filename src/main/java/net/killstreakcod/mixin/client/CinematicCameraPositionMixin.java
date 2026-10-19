package net.killstreakcod.mixin.client;

import net.killstreakcod.render.EntryScenes;
import net.killstreakcod.render.TacticalClient;
import net.killstreakcod.render.WheelsonView;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apply source cinematic sockets after vanilla interpolation and detached-camera placement.
 * 在原版插值及分离摄像机定位完成后，再应用原资源过场挂点。
 */
@Mixin(value = Camera.class)
public abstract class CinematicCameraPositionMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("RETURN"), require = 1)
    private void killstreakcod$cinematicPosition(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
            float partial, CallbackInfo ci) {
        if (TacticalClient.remote() && TacticalClient.state.mode().equals("wheelson"))
            setPosition(WheelsonView.position(entity, TacticalClient.yaw(), TacticalClient.pitch(), partial));
        Vec3 position = EntryScenes.cameraPosition((Camera) (Object) this);
        if (position != null)
            setPosition(position);
    }
}
