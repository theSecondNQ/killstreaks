package net.killstreakcod.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface RemoteChunkMapAccess {
    @Invoker("updateChunkTracking")
    void killstreak$updateChunkTracking(ServerPlayer player);

    @Invoker("getPlayerViewDistance")
    int killstreak$getPlayerViewDistance(ServerPlayer player);

    @Accessor("serverViewDistance")
    int killstreak$serverViewDistance();
}
