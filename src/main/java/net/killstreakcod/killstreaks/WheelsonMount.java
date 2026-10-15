package net.killstreakcod.killstreaks;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Measured original chassis tag_turret and turret tag_barrel/tag_flash, in source centimetres.
 * 原游戏底盘 tag_turret 及炮塔 tag_barrel、tag_flash 的实测位置，单位为源资源厘米。
 */
public final class WheelsonMount {
    private WheelsonMount() {}

    public static Vec3 muzzle(TacticalUnitEntity unit, float yaw, float pitch) {
        Vector3f barrel = new Vector3f(3.8277798F, .24638F, 43.65752F);
        Vector3f point = new Vector3f(53.759102F, .24638F, 43.65752F).sub(barrel).rotateY(pitch * Mth.DEG_TO_RAD)
                .add(barrel)
                .rotateZ(-Mth.wrapDegrees(yaw - unit.getYRot()) * Mth.DEG_TO_RAD).add(-18.98142F, 0, 73.2409F);
        Vec3 local = new Vec3(-point.y * .01, point.z * .01 + .006604224, -point.x * .01)
                .yRot((180 - unit.getYRot()) * Mth.DEG_TO_RAD);
        return unit.position().add(local);
    }
}
