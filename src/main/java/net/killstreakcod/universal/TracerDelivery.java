package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Delivery only: hitscan collision, impact, fire rate and damage remain with the caller.
 * 这里只分发视觉弹迹，射线碰撞、命中处理、射速和伤害仍由调用方负责。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class TracerDelivery {
    public static final int SMALL = 0, CANNON = 1, HEAVY = 2, PROJECTILE = 3, APACHE = 4, GUNSHIP25 = 5;
    private static final int MAX_SHOTS_PER_OBSERVER_TICK = 24;
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    private static net.minecraft.server.MinecraftServer server;
    private static int tick = Integer.MIN_VALUE;

    private TracerDelivery() {}

    public static void shot(ServerLevel level, Entity source, Vec3 from, Vec3 hit) {
        int profile = SMALL;
        if (source instanceof TacticalUnitEntity unit)
            profile = switch (unit.kind()) {
                case CHOPPER -> APACHE;
                case GUNSHIP -> GUNSHIP25;
                case VTOL -> ResearchEffects.has(unit, "vtol_jet.hercules") ? GUNSHIP25 : SMALL;
                case WHEELSON, PRECISION -> CANNON;
                default -> SMALL;
            };
        shot(level, source, from, hit, profile);
    }

    public static void shot(ServerLevel level, Entity source, Vec3 from, Vec3 hit, int profile) {
        if (level == null || !finite(from) || !finite(hit))
            return;
        Vec3 delta = hit.subtract(from);
        double length = delta.lengthSqr();
        if (length < .0025 || length > 1024D * 1024)
            return;
        if (server != level.getServer() || tick != level.getServer().getTickCount()) {
            server = level.getServer();
            tick = server.getTickCount();
            SENT.clear();
        }
        TracerPayload payload = new TracerPayload(level.dimension().location(), source == null ? -1 : source.getId(),
                from.x, from.y, from.z, (float) delta.x, (float) delta.y, (float) delta.z,
                Math.max(SMALL, Math.min(GUNSHIP25, profile)));
        for (ServerPlayer observer : level.players()) {
            boolean remote = RemoteViewSubscriptions.active(observer);
            Vec3 view = remote ? RemoteViewSubscriptions.origin(observer) : observer.position();
            double range = remote ? 512 : 192;
            if (distanceToSegmentSqr(view, from, hit) > range * range)
                continue;
            int sent = SENT.getOrDefault(observer.getUUID(), 0);
            if (sent >= MAX_SHOTS_PER_OBSERVER_TICK)
                continue;
            StreakNetwork.send(observer, payload);
            SENT.put(observer.getUUID(), sent + 1);
        }
    }

    /**
     * Called each real projectile tick; offset each emitter to one update every two ticks.
     * 每个真实弹体每刻调用，通过错开各发射器实现每两刻更新一次。
     */
    public static void projectile(ServerLevel level, Entity source, Vec3 previous, Vec3 current) {
        if (level == null || source == null || ((level.getGameTime() + source.getId()) & 1) != 0)
            return;
        shot(level, source, previous, current, PROJECTILE);
    }

    public static double distanceToSegmentSqr(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        double length = line.lengthSqr();
        if (length < .000001)
            return point.distanceToSqr(from);
        double progress = Math.max(0, Math.min(1, point.subtract(from).dot(line) / length));
        return point.distanceToSqr(from.add(line.scale(progress)));
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    @SubscribeEvent
    public static void cleanup(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (tick != net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount()) {
            SENT.clear();
            tick = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount();
            server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SENT.clear();
        server = null;
        tick = Integer.MIN_VALUE;
    }
}
