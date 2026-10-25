package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.AircraftFlight;

import net.minecraft.world.phys.Vec3;

/**
 * Shared delivery path for preflight collision checks and actual transport flight.
 * 预飞碰撞检查与实际运输飞行共用同一条空投航线。
 */
public final class DeliveryFlight {
    public static final int LIFETIME = 260, DROP_TICK = 65;

    private DeliveryFlight() {}

    public static Vec3 position(Vec3 center, float heading, int tick) {
        Vec3 d = Vec3.directionFromRotation(0, heading);
        if (tick < DROP_TICK)
            return AircraftFlight.hermite(center.add(d.scale(-300)).add(0, 48, 0), center.add(0, 30, 0),
                    d.scale(6).add(0, -.3, 0), d.scale(4), tick, DROP_TICK);
        // 飞抵投放点时仍保持前进速度，投下空投后直接沿机头方向离开。
        double elapsed = tick - DROP_TICK, climb = 24 * (1 - Math.exp(-elapsed * elapsed / 3600));
        return center.add(d.scale(elapsed * 4)).add(0, 30 + climb, 0);
    }
}
