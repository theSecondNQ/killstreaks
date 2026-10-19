package net.killstreakcod.mixin;

import net.killstreakcod.universal.RemoteViewSubscriptions;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapRemoteViewMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void killstreak$remoteWatchers(ChunkPos chunk, boolean boundaryOnly,
            CallbackInfoReturnable<List<ServerPlayer>> result) {
        result.setReturnValue(RemoteViewSubscriptions.watchers(level, chunk, boundaryOnly, result.getReturnValue()));
    }
}
