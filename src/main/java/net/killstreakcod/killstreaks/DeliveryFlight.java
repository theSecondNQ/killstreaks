package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.AircraftFlight;

import net.minecraft.world.phys.Vec3;

/**
 * Shared delivery path for preflight collision checks and actual transport flight.
 * 预飞碰撞检查与实际运输飞行共用同一条空投航线。
 */
public final class DeliveryFlight {
    public static final int LIFETIME = 260;

    private DeliveryFlight() {}

    public static Vec3 position(Vec3 center, float heading, int tick) {
        Vec3 d = Vec3.directionFromRotation(0, heading);
        if (tick < 55)
            return AircraftFlight.hermite(center.add(d.scale(-300)).add(0, 48, 0), center.add(0, 30, 0),
                    d.scale(6).add(0, -.3, 0), Vec3.ZERO, tick, 55);
        if (tick < 135)
            return center.add(0, 30, 0);
        double elapsed = tick - 135;
        return center.add(d.scale(elapsed * elapsed * .04)).add(0, 30 + elapsed * elapsed * .006, 0);
    }
}
