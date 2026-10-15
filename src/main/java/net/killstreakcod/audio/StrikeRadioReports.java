package net.killstreakcod.audio;

import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Attack completion is independent of the later visual flight-out/despawn.
 * 打击完成与之后的可视退场及实体移除分别处理。
 */
public final class StrikeRadioReports {
    private static final class Group {
        final ServerPlayer owner;
        final ServerLevel level;
        final String reward;
        final long created;
        final Set<TacticalUnitEntity> units = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<TacticalUnitEntity> attacksFinished = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<TacticalUnitEntity> departuresStarted = Collections.newSetFromMap(new IdentityHashMap<>());
        final boolean continuous;
        boolean hit, impact, destroyed, timedOut, leaving, leaveReported;
        long quietSince = -1, lastActivity;

        Group(ServerPlayer p, String reward) {
            owner = p;
            level = p.serverLevel();
            this.reward = reward;
            created = lastActivity = level.getGameTime();
            continuous = Set.of("gunship", "chopper_gunner", "wheelson_hs", "vtol_jet", "overwatch_helo")
                    .contains(reward);
        }
    }

    private static final Map<UUID, Group> GROUPS = new HashMap<>();

    private StrikeRadioReports() {}

    public static UUID begin(ServerPlayer player, String reward) {
        UUID id = UUID.randomUUID();
        GROUPS.put(id, new Group(player, reward));
        return id;
    }

    public static TacticalUnitEntity track(TacticalUnitEntity unit, UUID group) {
        if (unit == null || group == null)
            return unit;
        Group report = GROUPS.get(group);
        if (report != null) {
            unit.radioStrike = group;
            report.units.add(unit);
            report.quietSince = -1;
            report.lastActivity = report.level.getGameTime();
        }
        return unit;
    }

    public static void impact(TacticalUnitEntity unit, boolean hit) {
        Group group = GROUPS.get(unit.radioStrike);
        if (group != null) {
            group.impact = true;
            group.hit |= hit;
            group.lastActivity = group.level.getGameTime();
            group.quietSince = -1;
        }
    }

    public static void destroyed(TacticalUnitEntity unit) {
        Group group = GROUPS.get(unit.radioStrike);
        if (group != null && !group.destroyed) {
            group.destroyed = true;
            if (!gunshipEnded(group))
                NativeAudio.voice(group.owner, group.reward, "destroyed");
        }
    }

    private static boolean gunshipEnded(Group group) {
        return group.reward.equals("gunship") && group.leaveReported;
    }

    public static void timedOut(TacticalUnitEntity unit) {
        Group group = GROUPS.get(unit.radioStrike);
        if (group != null)
            group.timedOut = true;
    }

    private static boolean projectile(TacticalUnitEntity unit) {
        return unit.kind() == TacticalUnitEntity.Kind.BOMB || unit.kind() == TacticalUnitEntity.Kind.ROCKET;
    }

    public static void attackFinished(TacticalUnitEntity unit) {
        Group group = GROUPS.get(unit.radioStrike);
        if (group != null)
            group.attacksFinished.add(unit);
    }

    public static void departing(TacticalUnitEntity unit) {
        Group group = GROUPS.get(unit.radioStrike);
        if (group != null) {
            attackFinished(unit);
            group.departuresStarted.add(unit);
            group.leaving = true;
            reportDeparture(group);
        }
    }

    private static void reportDeparture(Group group) {
        if (!group.leaving || group.leaveReported || group.destroyed)
            return;
        if (group.units.stream()
                .anyMatch(unit -> !projectile(unit) && !unit.isRemoved() && !group.departuresStarted.contains(unit)))
            return;
        group.leaveReported = true;

        // 炸弹无人机成功引爆后，不应播放电池耗尽的语音。
        if (NativeAudio.voiceEvent(group.reward, "leave") != null || group.timedOut)
            NativeAudio.departure(group.owner, group.reward);
    }

    public static void removed(TacticalUnitEntity unit) {
        Group group = GROUPS.get(unit.radioStrike);
        if (group != null) {
            group.units.remove(unit);
            group.attacksFinished.remove(unit);
            group.departuresStarted.remove(unit);
        }
    }

    public static void endControl(TacticalUnitEntity unit) {
        departing(unit);
        removed(unit);
        unit.radioStrike = null;
    }

    public static void tick(MinecraftServer server) {
        for (var entry : List.copyOf(GROUPS.entrySet())) {
            Group group = entry.getValue();
            ServerPlayer player = group.owner;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive()
                    || player.serverLevel() != group.level) {
                GROUPS.remove(entry.getKey());
                continue;
            }
            group.units.removeIf(TacticalUnitEntity::isRemoved);
            group.attacksFinished.retainAll(group.units);
            reportDeparture(group);
            long now = group.level.getGameTime();
            boolean pending = group.units.stream()
                    .anyMatch(unit -> projectile(unit) || !group.continuous && !group.attacksFinished.contains(unit));
            if (!pending && now - group.lastActivity >= 12) {
                if (group.quietSince < 0)
                    group.quietSince = now;
                if (group.impact) {
                    if (!gunshipEnded(group))
                        NativeAudio.voice(player, group.reward, group.hit ? "hit" : "miss");
                    group.impact = group.hit = false;
                }
                if (group.units.isEmpty()) {
                    if (!group.leaveReported && !group.destroyed && group.timedOut)
                        NativeAudio.departure(player, group.reward);
                    GROUPS.remove(entry.getKey());
                }
            } else
                group.quietSince = -1;
            if (now - group.created > 7200)
                GROUPS.remove(entry.getKey());
        }
    }

    public static void clear() {
        GROUPS.clear();
    }
}
