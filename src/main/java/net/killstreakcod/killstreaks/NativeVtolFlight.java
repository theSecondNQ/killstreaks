package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.AircraftFlight;
import net.killstreakcod.universal.NativeCurveSet;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Independent rocket pass and patrol entry, sharing one authoritative preflight route.
 * 火箭弹攻击航段和巡航入场分别执行，共用同一条由服务端确认的预飞航线。
 */
public final class NativeVtolFlight {
    private static final NativeCurveSet SOURCE = NativeCurveSet.get("vtol_flyin");
    public static final int SOURCE_TICKS = SOURCE.last > 0 ? (int) Math.ceil(SOURCE.last / SOURCE.fps * 20) : 430;
    public static final int ATTACK_TICK = SOURCE.notifyTick("attack", 125),
            BOMB_TICK = SOURCE.notifyTick("attack_bomb", 145), STRIKE_END = BOMB_TICK + 6;
    private static final Vec3 END = SOURCE.root(SOURCE.last);
    private static final double SCALE = motionScale();
    public static final int RETURN_TICK = returnTick(), SECOND_INGRESS_TICKS = Math.max(60, SOURCE_TICKS - RETURN_TICK);
    public static final int FIRST_EXIT_TICKS = 140, FIRST_EXIT_END = STRIKE_END + FIRST_EXIT_TICKS, EMPTY_TICKS = 40;
    public static final int SECOND_ENTRY_START = FIRST_EXIT_END + EMPTY_TICKS,
            INGRESS_TICKS = SECOND_ENTRY_START + SECOND_INGRESS_TICKS;
    private static final double DEPARTURE_DISTANCE = 720;
    public static final double ROCKET_LINE_LENGTH = 64;
    private static final double PATROL_RADIUS = 10, PATROL_HEIGHT = 34, PATROL_SPEED = .02;

    private NativeVtolFlight() {}

    private static double motionScale() {
        double radius = 1;
        for (int i = 0; i <= SOURCE.last; i++)
            radius = Math.max(radius, SOURCE.root(i).distanceTo(END));
        return Math.min(.01, 280 / radius);
    }

    private static int returnTick() {
        int farthest = STRIKE_END + 60;
        double radius = 0;
        for (int t = STRIKE_END + 1; t < SOURCE_TICKS; t++) {
            Vec3 at = SOURCE.root(t * SOURCE.fps / 20).subtract(END);
            double d = at.x * at.x + at.y * at.y;
            if (d > radius) {
                radius = d;
                farthest = t;
            }
        }
        return Math.min(SOURCE_TICKS - 1, farthest);
    }

    private static Vec3 sourcePosition(Vec3 target, float heading, int tick) {
        if (SOURCE.last <= 0)
            return target.add(Vec3.directionFromRotation(0, heading).scale((tick - 145) * 2.4)).add(0, 34, 0);
        Vec3 source = SOURCE.root(Math.min(SOURCE.last, Math.max(0, tick) * SOURCE.fps / 20)).subtract(END)
                .scale(SCALE);
        Vec3 local = new Vec3(-source.y, source.z, -source.x).yRot((180 - heading) * Mth.DEG_TO_RAD);
        return target.add(local.x, Mth.clamp(34 + local.y, 30, 140), local.z);
    }

    /**
     * Actual first-frame entry is retained; after firing the hull flies straight away.
     * 保留原动作第一帧的入场姿态，开火后机体沿直线飞离。
     */
    public static Vec3 firstPosition(Vec3 target, float heading, int tick) {
        if (tick <= STRIKE_END)
            return sourcePosition(target, heading, tick);
        Vec3 start = sourcePosition(target, heading, STRIKE_END),
                velocity = start.subtract(sourcePosition(target, heading, STRIKE_END - 1));
        Vec3 end = returnAnchor(target, heading);
        return AircraftFlight.hermite(start, end, velocity, end.subtract(start).normalize().scale(8), tick - STRIKE_END,
                FIRST_EXIT_TICKS);
    }

    private static Vec3 returnAnchor(Vec3 target, float heading) {
        Vec3 start = sourcePosition(target, heading, RETURN_TICK).subtract(target);
        double scale = DEPARTURE_DISTANCE / Math.max(1, start.horizontalDistance());
        return SOURCE.last <= 0
                ? target.add(Vec3.directionFromRotation(0, heading).scale(DEPARTURE_DISTANCE)).add(0, 69, 0)
                : target.add(start.x * scale, start.y, start.z * scale);
    }

    private static float patrolHeading(float heading) {
        Vec3 start = returnAnchor(Vec3.ZERO, heading);
        return (float) Math.toDegrees(Math.atan2(start.z, start.x)) + 90;
    }

    /**
     * Return from the same far point; the incoming tangent joins the orbit without a stopped/quantized root.
     * 从上次离场的远处位置返航，以入场切线平滑接入盘旋轨道，避免根节点停顿或离散跳动。
     */
    public static Vec3 secondPosition(Vec3 target, float heading, int tick) {
        float angle = patrolHeading(heading);
        if (tick >= SECOND_INGRESS_TICKS)
            return AircraftFlight.orbit(target, angle, tick - SECOND_INGRESS_TICKS, PATROL_RADIUS, PATROL_HEIGHT,
                    PATROL_SPEED);
        Vec3 start = returnAnchor(target, heading),
                end = AircraftFlight.orbit(target, angle, 0, PATROL_RADIUS, PATROL_HEIGHT, PATROL_SPEED);
        double radians = Math.toRadians(angle);
        Vec3 tangent = new Vec3(-Math.sin(radians), 0, Math.cos(radians)).scale(PATROL_RADIUS * PATROL_SPEED);
        return AircraftFlight.hermite(start, end, end.subtract(start).normalize().scale(6), tangent, Math.max(0, tick),
                SECOND_INGRESS_TICKS);
    }

    private static Vec3 secondDirection(float heading, int tick) {
        return secondPosition(Vec3.ZERO, heading, tick + 1)
                .subtract(secondPosition(Vec3.ZERO, heading, Math.max(0, tick - 1)));
    }

    public static float secondYaw(float heading, int tick) {
        Vec3 v = secondDirection(heading, tick);
        return (float) Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    public static float secondPitch(float heading, int tick, double elevation, int minimum, int maximum) {
        Vec3 before = secondPosition(Vec3.ZERO, heading, Math.max(0, tick - 1)),
                after = secondPosition(Vec3.ZERO, heading, tick + 1);
        double rise = Mth.clamp(elevation + after.y, minimum, maximum)
                - Mth.clamp(elevation + before.y, minimum, maximum);
        return (float) -Math.toDegrees(Math.atan2(rise, after.subtract(before).horizontalDistance()));
    }

    /**
     * Uniform impact points along the selected heading; each projectile then flies straight, without homing.
     * 沿选定方向均匀分配落点，每枚弹体随后直线飞行，不追踪目标。
     */
    public static Vec3 rocketTarget(Vec3 target, float heading, int tick) {
        int count = (STRIKE_END - ATTACK_TICK) / 3 + 1, index = Math.max(0, (tick - ATTACK_TICK) / 3);
        double p = count <= 1 ? .5 : Mth.clamp(index / (double) (count - 1), 0, 1);
        return target.add(Vec3.directionFromRotation(0, heading).scale((p - .5) * ROCKET_LINE_LENGTH));
    }

    public static int animationClock(boolean patrol, int tick) {
        return patrol ? RETURN_TICK + Math.max(0, tick) : tick <= STRIKE_END ? tick : tick - STRIKE_END;
    }

    /**
     * Used only for the whole mission's preflight, including the empty interval.
     * 仅用于整次任务的预飞检查，包含两次入场之间的空档。
     */
    public static Vec3 position(Vec3 target, float heading, int tick) {
        return tick <= FIRST_EXIT_END ? firstPosition(target, heading, tick)
                : secondPosition(target, heading, Math.max(0, tick - SECOND_ENTRY_START));
    }
}
