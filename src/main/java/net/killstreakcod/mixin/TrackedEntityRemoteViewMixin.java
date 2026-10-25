package net.killstreakcod.mixin;

import net.killstreakcod.universal.RemoteViewSubscriptions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityRemoteViewMixin {
    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private ServerEntity serverEntity;

    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    /** 仅为本人的遥控实体提前建立原版配对，避免等待地形批次时摄像机一直留在玩家身上。 */
    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void killstreak$pairCamera(ServerPlayer player, CallbackInfo ci) {
        if (!RemoteViewSubscriptions.isCamera(player, entity))
            return;
        if (seenBy.add(player.connection))
            serverEntity.addPairing(player);
        ci.cancel();
    }

    /**
     * 调整算出的跟踪偏移，不独占 position() 调用，兼容其他模组的自由视角。
     * 未使用本模组遥控时，原样保留原版或其他模组计算的偏移。
     */
    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private Vec3 killstreak$cameraOffset(Vec3 offset, ServerPlayer player) {
        return RemoteViewSubscriptions.active(player)
                ? RemoteViewSubscriptions.origin(player).subtract(entity.position())
                : offset;
    }
}
