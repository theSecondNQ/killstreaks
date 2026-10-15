package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class NativeFxDelivery {
    private static final class Budget {
        long tick;
        final Map<UUID, Integer> sent = new HashMap<>();
    }

    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();

    private NativeFxDelivery() {}

    public static String destruction(TacticalUnitEntity.Kind kind) {
        return switch (kind) {
            case UAV, COUNTER_UAV -> "uav";
            case ADVANCED_UAV -> "advanced_uav";
            case CHOPPER, OVERWATCH, DELIVERY -> "helicopter";
            case SENTRY -> "sentry";
            default -> "equipment";
        };
    }

    public static void emit(ServerLevel level, String profile, Vec3 at, float size) {
        if (!NativeFxPayload.validProfile(profile))
            return;
        if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z) || !Float.isFinite(size))
            return;
        Budget budget = BUDGETS.computeIfAbsent(level, l -> new Budget());
        long now = level.getGameTime();
        if (budget.tick != now) {
            budget.tick = now;
            budget.sent.clear();
        }
        var packet = new NativeFxPayload(level.dimension().location(), profile, at.x, at.y, at.z,
                Math.max(.2F, Math.min(24, size)), level.random.nextLong());
        for (var player : level.players()) {
            if (!player.isAlive() || RemoteViewSubscriptions.origin(player).distanceToSqr(at) > 512D * 512)
                continue;
            int used = budget.sent.getOrDefault(player.getUUID(), 0);
            if (used >= 12)
                continue;
            budget.sent.put(player.getUUID(), used + 1);
            StreakNetwork.send(player, packet);
        }
    }
}
