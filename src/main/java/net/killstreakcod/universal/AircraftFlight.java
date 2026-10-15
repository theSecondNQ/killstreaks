package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.NativeVtolFlight;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft flight adapters: continuous approach, service orbit and nose-forward departure.
 * 将飞行逻辑适配到 Minecraft，包含连续接近、任务盘旋及机头朝前的退场。
 */
public final class AircraftFlight {
    public static final int APPROACH_TICKS = 90, DEPARTURE_TICKS = 160;
    public static final int PASS_OFFSET = 92, PASS_LIFETIME = 400;
    public static final double SAE_SPEED = 6;

    /**
     * Share spawn, preflight and actual passes; release the SAE cluster over its selected point.
     * 生成位置、预飞检查与实际航段共用计算，在选定点上方释放 SAE 集束弹。
     */
    public static Vec3 passPosition(Kind kind, Vec3 center, float heading, int tick) {
        boolean sae = kind == Kind.SAE;
        double distance = (tick - PASS_OFFSET - (sae ? 30 : 42)) * (sae ? SAE_SPEED : 2.4);
        return center.add(Vec3.directionFromRotation(0, heading).scale(distance)).add(0, sae ? 36 : 52, 0);
    }

    private AircraftFlight() {}

    public static boolean aircraft(Kind kind) {
        return switch (kind) {
            case UAV, COUNTER_UAV, ADVANCED_UAV, PRECISION, SAE, VTOL, OVERWATCH, STEALTH, CHOPPER, GUNSHIP, DELIVERY ->
                true;
            default -> false;
        };
    }

    public static boolean visible(Kind kind) {
        return aircraft(kind) && kind != Kind.COUNTER_UAV;
    }

    public static boolean persistent(Kind kind) {
        return switch (kind) {
            case UAV, COUNTER_UAV, ADVANCED_UAV, VTOL, OVERWATCH, CHOPPER, GUNSHIP -> true;
            default -> false;
        };
    }

    public static int approachTicks(Kind kind) {
        return switch (kind) {
            case UAV, ADVANCED_UAV, OVERWATCH -> APPROACH_TICKS;
            case VTOL -> NativeVtolFlight.INGRESS_TICKS;
            default -> 0;
        };
    }

    public static Vec3 orbit(Vec3 center, float heading, int elapsed, double radius, double height, double speed) {
        double angle = Math.toRadians(heading) + Math.max(0, elapsed) * speed;
        return center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    public static Vec3 approach(Vec3 center, float heading, int tick, double radius, double height, double orbitSpeed) {
        Vec3 forward = Vec3.directionFromRotation(0, heading);
        Vec3 end = orbit(center, heading, 0, radius, height, orbitSpeed);
        Vec3 start = end.add(forward.scale(-300)).add(0, 18, 0);
        return hermite(start, end, forward.scale(4.2).add(0, -.2, 0), forward.scale(radius * orbitSpeed), tick,
                APPROACH_TICKS);
    }

    /**
     * Velocities are in blocks/tick; endpoint tangents join without a heading jump.
     * 速度单位为格每刻，端点通过切线连续衔接，避免朝向跳变。
     */
    public static Vec3 hermite(Vec3 start, Vec3 end, Vec3 velocityStart, Vec3 velocityEnd, int tick, int duration) {
        double p = Mth.clamp(tick / (double) duration, 0, 1), p2 = p * p, p3 = p2 * p;
        return start.scale(2 * p3 - 3 * p2 + 1).add(velocityStart.scale((p3 - 2 * p2 + p) * duration))
                .add(end.scale(-2 * p3 + 3 * p2)).add(velocityEnd.scale((p3 - p2) * duration));
    }

    public static Vec3 departure(Vec3 start, float yaw, float pitch, Kind kind, int tick) {
        double p = Mth.clamp(tick / (double) DEPARTURE_TICKS, 0, 1);
        boolean rotor = kind == Kind.CHOPPER || kind == Kind.OVERWATCH || kind == Kind.VTOL;
        double first = rotor ? .65 : 1.3, last = rotor ? 6.1 : 7;

        // 衔接处保留当前机头方向，随后随速度提升缓缓爬升。
        Vec3 initial = Vec3.directionFromRotation(pitch, yaw).scale(first);
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        return start.add(initial.scale(tick * (1 - p * .5)))
                .add(forward.scale(last * tick * p * .5)).add(0, 38 * p * p, 0);
    }
}
