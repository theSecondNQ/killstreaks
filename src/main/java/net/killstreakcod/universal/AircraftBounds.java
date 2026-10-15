package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server/client hit regions derived from original complete mesh bounds.
 * 根据原完整网格边界生成服务端及客户端的可命中区域。
 */
public final class AircraftBounds {
    private static final Map<TacticalUnitEntity.Kind, double[]> BOUNDS = new EnumMap<>(TacticalUnitEntity.Kind.class);
    static {
        try (var stream = AircraftBounds.class.getResourceAsStream("/assets/killstreakcod/mw/aircraft-bounds.json")) {
            var root = JsonParser
                    .parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("bounds");
            for (var e : root.entrySet()) {
                var a = e.getValue().getAsJsonArray();
                double[] b = new double[6];
                for (int i = 0; i < 6; i++)
                    b[i] = a.get(i).getAsDouble();
                BOUNDS.put(TacticalUnitEntity.Kind.valueOf(e.getKey()), b);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Missing aircraft collision metadata", e);
        }
    }

    public static AABB box(TacticalUnitEntity unit) {
        double[] b = BOUNDS.get(unit.kind());
        if (b == null)
            return null;

        // Vec3.xRot 与 PoseStack 的 X 轴四元数采用相反的角度约定。
        float pitch = (float) Math.toRadians(unit.flightPitch()), yaw = (float) Math.toRadians(180 - unit.flightYaw());
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX,
                maxZ = maxX;
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 2; z++) {
                    Vec3 v = new Vec3(b[x * 3], b[1 + y * 3], b[2 + z * 3]).xRot(pitch).yRot(yaw).add(unit.position());
                    minX = Math.min(minX, v.x);
                    minY = Math.min(minY, v.y);
                    minZ = Math.min(minZ, v.z);
                    maxX = Math.max(maxX, v.x);
                    maxY = Math.max(maxY, v.y);
                    maxZ = Math.max(maxZ, v.z);
                }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(.12);
    }

    private AircraftBounds() {}
}
