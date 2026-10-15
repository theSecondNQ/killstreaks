package net.killstreakcod.killstreaks;

import net.minecraft.world.phys.Vec3;

/**
 * Original A-10 front rotary muzzle, measured from native source mesh0 in centimetres.
 * 原游戏 A-10 前部旋转机炮的炮口位置，按原始 mesh0 测量，单位为厘米。
 */
public final class PrecisionCannonMount {
    public static final double SOURCE_X = 302.73, SOURCE_Y = 2.69, SOURCE_Z = -9.67;

    private PrecisionCannonMount() {}

    public static Vec3 direction(float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public static Vec3 muzzle(TacticalUnitEntity unit) {
        float yaw = unit.flightYaw(), pitch = unit.flightPitch();
        return unit.position().add(direction(yaw, pitch).scale(SOURCE_X * .01))
                .add(Vec3.directionFromRotation(0, yaw - 90).scale(SOURCE_Y * .01))
                .add(Vec3.directionFromRotation(pitch - 90, yaw).scale(SOURCE_Z * .01));
    }
}
