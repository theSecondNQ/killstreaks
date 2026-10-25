package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.GunshipMounts;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
        Matrix4f banked = gunship(unit) ? GunshipMounts.bodyTransform(unit, 1) : null;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX,
                maxZ = maxX;
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 2; z++) {
                    Vec3 local = new Vec3(b[x * 3], b[1 + y * 3], b[2 + z * 3]);
                    Vec3 v = (banked == null ? local.xRot(pitch).yRot(yaw) : transform(local, banked))
                            .add(unit.position());
                    minX = Math.min(minX, v.x);
                    minY = Math.min(minY, v.y);
                    minZ = Math.min(minZ, v.z);
                    maxX = Math.max(maxX, v.x);
                    maxY = Math.max(maxY, v.y);
                    maxZ = Math.max(maxZ, v.z);
                }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(.12);
    }

    /** 以源机体边界做旋转后的线段命中，避免只检测实体中心或膨胀的世界轴对齐盒。 */
    public static Optional<Vec3> intercept(TacticalUnitEntity unit, Vec3 from, Vec3 to, double padding) {
        double[] b = BOUNDS.get(unit.kind());
        if (b == null)
            return Optional.empty();
        float pitch = (float) Math.toRadians(unit.flightPitch());
        float yaw = (float) Math.toRadians(180 - unit.flightYaw());
        Matrix4f banked = gunship(unit) ? GunshipMounts.bodyTransform(unit, 1) : null;
        Matrix4f inverse = banked == null ? null : new Matrix4f(banked).invert();
        Vec3 localFrom = inverse == null ? from.subtract(unit.position()).yRot(-yaw).xRot(-pitch)
                : transform(from.subtract(unit.position()), inverse);
        Vec3 localTo = inverse == null ? to.subtract(unit.position()).yRot(-yaw).xRot(-pitch)
                : transform(to.subtract(unit.position()), inverse);
        AABB hull = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]).inflate(padding);
        return (hull.contains(localFrom) ? Optional.of(localFrom) : hull.clip(localFrom, localTo))
                .map(point -> (banked == null ? point.xRot(pitch).yRot(yaw) : transform(point, banked))
                        .add(unit.position()));
    }

    /** 机腹后段的左右发射口，与机体使用相同的俯仰和航向变换。 */
    public static Vec3 flareSocket(TacticalUnitEntity unit, int side) {
        double[] b = BOUNDS.get(unit.kind());
        if (b == null)
            return unit.position();
        Vec3 local = new Vec3((b[0] + b[3]) * .5 + side * .85, b[1] - .25, b[5] - (b[5] - b[2]) * .22);
        if (gunship(unit))
            return transform(local, GunshipMounts.bodyTransform(unit, 1)).add(unit.position());
        return local.xRot((float) Math.toRadians(unit.flightPitch()))
                .yRot((float) Math.toRadians(180 - unit.flightYaw())).add(unit.position());
    }

    private static boolean gunship(TacticalUnitEntity unit) {
        return unit.kind() == TacticalUnitEntity.Kind.GUNSHIP && !unit.crashing();
    }

    private static Vec3 transform(Vec3 point, Matrix4f matrix) {
        Vector3f result = matrix.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(result.x, result.y, result.z);
    }

    private AircraftBounds() {}
}
