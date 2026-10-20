package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.TacticalRuntime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Three separated impact points per volley, with a fixed oblique descent.
 * 每轮齐射包含三个分散的落点，炮弹以固定斜角下降。
 */
public final class MortarStrikeFlight {
    public static final int VOLLEYS = 6, SHELLS_PER_VOLLEY = 3, VOLLEY_INTERVAL = 24;
    public static final double SPREAD_RADIUS = 8, ENTRY_HEIGHT = 55, APPROACH_DISTANCE = 40, SPEED = 2.4;
    public static final float BLAST_RADIUS = 8, BLAST_DAMAGE = 64;

    private MortarStrikeFlight() {}

    public static Vec3 target(ServerPlayer player, Vec3 center, float heading, int volley, int shell, int shells,
            double spread) {
        double angle = Math.toRadians(heading + volley * 20) + shell * Math.PI * 2 / shells;
        return TacticalRuntime
                .ground(player.serverLevel(), center.add(Math.cos(angle) * spread, 0, Math.sin(angle) * spread))
                .add(0, .12, 0);
    }

    public static Vec3 start(ServerPlayer player, Vec3 target, float heading) {
        Vec3 point = target.subtract(Vec3.directionFromRotation(0, heading).scale(APPROACH_DISTANCE)).add(0,
                ENTRY_HEIGHT, 0);
        return new Vec3(point.x,
                Mth.clamp(point.y, player.level().getMinBuildHeight() + 1, player.level().getMaxBuildHeight() - 3),
                point.z);
    }

    /**
     * Compensate LivingEntity air drag, keeping the fixed ground point's straight descent.
     * 补偿 LivingEntity 的空气阻力，使炮弹沿直线降至固定地面落点。
     */
    public static Vec3 velocity(Vec3 from, Vec3 target) {
        Vec3 delta = target.subtract(from);
        double distance = delta.length();
        return distance < .00001 ? Vec3.ZERO : delta.scale(Math.min(SPEED, distance) / distance);
    }
}
