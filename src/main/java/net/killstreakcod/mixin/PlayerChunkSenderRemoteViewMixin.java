package net.killstreakcod.mixin;

import net.killstreakcod.universal.RemoteViewSubscriptions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderRemoteViewMixin {
    @Redirect(method = "sendNextChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos killstreak$cameraPriority(ServerPlayer player) {
        return RemoteViewSubscriptions.chunkPriority(player);
    }
}
