package net.killstreakcod.mixin;

import net.killstreakcod.universal.RemoteViewSubscriptions;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Native particle calls remain camera-relative even when the operator body is many chunks away.
 * 即使操作者本体远在多个区块之外，原资源粒子调用仍以摄像机位置为参照。
 */
@Mixin(ServerLevel.class)
public abstract class RemoteParticleDeliveryMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z", at = @At("HEAD"), cancellable = true)
    private void killstreak$remoteParticles(ServerPlayer player, boolean force, double x, double y, double z,
            Packet<?> packet, CallbackInfoReturnable<Boolean> result) {
        if (!RemoteViewSubscriptions.active(player))
            return;
        if (player.level() != (Object) this
                || RemoteViewSubscriptions.origin(player).distanceToSqr(new Vec3(x, y, z)) > 512 * 512) {
            result.setReturnValue(false);
            return;
        }
        if (packet instanceof ClientboundLevelParticlesPacket particles && !particles.isOverrideLimiter())
            packet = new ClientboundLevelParticlesPacket(particles.getParticle(), true, particles.getX(),
                    particles.getY(), particles.getZ(), particles.getXDist(), particles.getYDist(),
                    particles.getZDist(), particles.getMaxSpeed(), particles.getCount());
        player.connection.send(packet);
        result.setReturnValue(true);
    }
}
