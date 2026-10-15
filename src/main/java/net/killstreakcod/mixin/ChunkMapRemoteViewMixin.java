package net.killstreakcod.mixin;

import net.killstreakcod.universal.RemoteViewSubscriptions;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapRemoteViewMixin {
    @Redirect(method = "updateChunkTracking", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos killstreak$cameraChunk(ServerPlayer player) {
        return RemoteViewSubscriptions.chunkOrigin(player);
    }

    @Inject(method = "getPlayerViewDistance", at = @At("RETURN"), cancellable = true)
    private void killstreak$boundView(ServerPlayer player, CallbackInfoReturnable<Integer> result) {
        result.setReturnValue(RemoteViewSubscriptions.viewDistance(player, result.getReturnValue()));
    }
}
