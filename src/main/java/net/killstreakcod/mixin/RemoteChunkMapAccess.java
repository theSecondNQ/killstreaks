package net.killstreakcod.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface RemoteChunkMapAccess {
    @Accessor("viewDistance")
    int killstreak$serverViewDistance();

    @Invoker("playerLoadedChunk")
    void killstreak$sendChunk(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet,
            LevelChunk chunk);
}
