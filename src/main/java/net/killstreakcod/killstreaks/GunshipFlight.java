package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.AircraftFlight;
import net.killstreakcod.universal.StreakTimings;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A nose-forward ingress joins a player-centred orbit with a continuous tangent.
 * 机头朝前的入场航线以连续切线衔接到以玩家为中心的盘旋轨道。
 */
public final class GunshipFlight {
    public static final double RADIUS = 112, HEIGHT = 128, ANGULAR_SPEED = -.009;
    public static final float YAW_LIMIT = 90, PITCH_LIMIT = 75;
    private static final double INGRESS_SPEED = RADIUS * Math.abs(ANGULAR_SPEED);

    private GunshipFlight() {}

    public static Vec3 position(Vec3 player, float heading, int elapsed) {
        int intro = StreakTimings.entry("gunship");
        double angle = Math.toRadians(heading) + Math.PI;
        if (elapsed < intro) {
            Vec3 join = player.add(Math.cos(angle) * RADIUS, HEIGHT, Math.sin(angle) * RADIUS);
            Vec3 forward = Vec3.directionFromRotation(0, heading);
            return AircraftFlight.hermite(join.add(forward.scale(-320)), join, forward.scale(3.5),
                    forward.scale(INGRESS_SPEED), elapsed, intro);
        }
        angle += (elapsed - intro) * ANGULAR_SPEED;
        return player.add(Math.cos(angle) * RADIUS, HEIGHT, Math.sin(angle) * RADIUS);
    }

    public static float speed() {
        return (float) (INGRESS_SPEED * 20);
    }

    public static float mountYaw(float flightYaw) {
        return Mth.wrapDegrees(flightYaw - 90);
    }

    /**
     * A bounded mount may look to either side, but never turn fully around.
     * 受限武器座可以向左右观察，但不能完全转向后方。
     */
    public static float clampYaw(float requested, float flightYaw) {
        float center = mountYaw(flightYaw);
        return Mth.wrapDegrees(center + Mth.clamp(Mth.wrapDegrees(requested - center), -YAW_LIMIT, YAW_LIMIT));
    }

    public static float clampPitch(float requested) {
        return Mth.clamp(requested, -PITCH_LIMIT, PITCH_LIMIT);
    }
}
