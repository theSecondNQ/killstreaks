package net.killstreakcod.mixin;

import net.killstreakcod.universal.RemoteViewSubscriptions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityRemoteViewMixin {
    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 killstreak$cameraPosition(ServerPlayer player) {
        return RemoteViewSubscriptions.origin(player);
    }
}
