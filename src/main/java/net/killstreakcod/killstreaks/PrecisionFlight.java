package net.killstreakcod.killstreaks;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 15-degree approach, tangent-aligned 64-block cannon sweep, then level departure.
 * 以 15 度俯角接近，沿切线方向进行 64 格机炮扫射，然后改平退场。
 */
public final class PrecisionFlight {
    public static final int PLANE_INTERVAL = 80, LIFETIME = 370, ATTACK_START = 60, ATTACK_END = 108;
    public static final double STRIKE_LENGTH = 64;
    private static final double BASE_SPEED = 3, SPEED = 4.5, PULL_OUT = 22, TAN = Math.tan(Math.toRadians(15));

    // 延长入场航段，在提高实际速度时保留打击中点；若 START 不变，最后的机炮扫射会低于 30 格最低高度。
    private static final double START = -500 - (SPEED - BASE_SPEED) * (ATTACK_START + ATTACK_END) / 2D;
    private static final double SWEEP_SPEED = STRIKE_LENGTH / (ATTACK_END - ATTACK_START),
            POWER = SPEED / (SPEED - SWEEP_SPEED);
    private static final double INITIAL_GAP = -STRIKE_LENGTH / 2 - (START + ATTACK_START * SPEED),
            INITIAL_HEIGHT = INITIAL_GAP * TAN;

    private PrecisionFlight() {}

    public static Vec3 position(Vec3 target, float heading, int tick) {
        return position(target, heading, tick, 0);
    }

    /**
     * Increased altitude also extends the approach, preserving the ground sweep and barrel tangent.
     * 提高高度时同时延长接近航段，保持地面扫射范围与炮管切线方向。
     */
    public static Vec3 position(Vec3 target, float heading, int tick, int lift) {
        double distance = START + tick * SPEED - lift / TAN, height;
        if (tick < ATTACK_START)
            height = INITIAL_HEIGHT + lift + (ATTACK_START - tick) * SPEED * TAN + muzzleLift(15);
        else if (tick <= ATTACK_END)
            height = sweepHeight(tick, lift) + muzzleLift(pitch(tick, lift));
        else {
            double t = Math.min(PULL_OUT, tick - ATTACK_END), slope = Math.tan(Math.toRadians(pitch(ATTACK_END, lift)));
            height = sweepHeight(ATTACK_END, lift) + muzzleLift(pitch(ATTACK_END, lift))
                    - SPEED * slope * (t - t * t / (2 * PULL_OUT));
        }
        return target.add(Vec3.directionFromRotation(0, heading).scale(distance)).add(0, Math.max(30, height), 0);
    }

    public static double strikeOffset(int tick) {
        return -STRIKE_LENGTH / 2 + SWEEP_SPEED * Mth.clamp(tick - ATTACK_START, 0, ATTACK_END - ATTACK_START);
    }

    private static double sweepGap(int tick, int lift) {
        return strikeOffset(tick) - (START + tick * SPEED) + lift / TAN;
    }

    private static double sweepHeight(int tick, int lift) {
        return (INITIAL_HEIGHT + lift) * Math.pow(sweepGap(tick, lift) / (INITIAL_GAP + lift / TAN), POWER);
    }

    private static double muzzleLift(double pitch) {
        return -PrecisionCannonMount.SOURCE_Z * .01 / Math.cos(Math.toRadians(pitch));
    }

    public static float pitch(int tick, int lift) {
        if (tick < ATTACK_START)
            return 15;
        if (tick <= ATTACK_END)
            return (float) Math.toDegrees(Math.atan(sweepHeight(tick, lift) / sweepGap(tick, lift)));
        return pitch(ATTACK_END, lift) * (1 - Mth.clamp((float) ((tick - ATTACK_END) / PULL_OUT), 0, 1));
    }
}
