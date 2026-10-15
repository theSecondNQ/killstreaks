package net.killstreakcod.mixin;

import net.killstreakcod.render.RemoteTerrainRange;
import net.killstreakcod.render.TacticalClient;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A transient effective distance for terrain, projection and fog; user settings are never written.
 * 为地形、投影和雾效提供临时有效距离，不写入用户设置。
 */
@Mixin(Options.class)
public abstract class RemoteOptionsRenderDistanceMixin {
    @Shadow
    private int serverRenderDistance;

    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void killstreak$remoteDistance(CallbackInfoReturnable<Integer> result) {
        if (TacticalClient.remote())
            result.setReturnValue(RemoteTerrainRange.effective(serverRenderDistance));
    }
}
