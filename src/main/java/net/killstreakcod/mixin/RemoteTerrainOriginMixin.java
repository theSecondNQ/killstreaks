package net.killstreakcod.mixin;

import net.killstreakcod.render.TacticalClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla terrain only; RenderingMixinPlugin retains Sodium's own camera-centred renderer.
 * 仅修改原版地形渲染；RenderingMixinPlugin 会保留 Sodium 自带的摄像机中心渲染器。
 */
@Mixin(LevelRenderer.class)
public abstract class RemoteTerrainOriginMixin {
    @Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double killstreak$cameraX(LocalPlayer player) {
        return TacticalClient.remote() ? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().x
                : player.getX();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double killstreak$cameraY(LocalPlayer player) {
        return TacticalClient.remote() ? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y
                : player.getY();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double killstreak$cameraZ(LocalPlayer player) {
        return TacticalClient.remote() ? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().z
                : player.getZ();
    }
}
