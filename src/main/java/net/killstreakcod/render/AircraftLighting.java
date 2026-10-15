package net.killstreakcod.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Missing far chunks use open-sky light, never emissive block light.
 * 远处区块缺失时使用露天天空光照，不使用自发光的方块光照。
 */
public final class AircraftLighting {
    public static int at(ClientLevel level, Vec3 position) {
        BlockPos at = BlockPos.containing(position);
        return level.hasChunkAt(at) ? LevelRenderer.getLightColor(level, at) : LightTexture.pack(0, 15);
    }

    private AircraftLighting() {}
}
