package net.killstreakcod.universal;

import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime research effects use the mission's saved upgrade snapshot, not a mutable client selection.
 * 任务效果使用生成时保存的升级快照，不读取可变的客户端选项。
 */
public final class ResearchEffects {
    public record Survey(Vec3 origin, boolean advanced, boolean xray) {
    }

    private record VirtualSurvey(UUID owner, ServerLevel level, Vec3 center, Kind kind, ResearchMask upgrades,
            long until) {
    }

    private record Frozen(ServerLevel level, Vec3 anchor, long until) {
    }

    private static final List<VirtualSurvey> SURVEYS = new ArrayList<>();
    private static final Map<ServerPlayer, Long> JAMMED = new WeakHashMap<>();
    private static final Map<LivingEntity, Frozen> FROZEN = new WeakHashMap<>();

    private ResearchEffects() {}

    public static StreakReward reward(Kind kind) {
        return switch (kind) {
            case UAV -> StreakReward.UAV;
            case COUNTER_UAV -> StreakReward.COUNTER_UAV;
            case ADVANCED_UAV -> StreakReward.ADVANCED_UAV;
            case BOMB_DRONE -> StreakReward.BOMB_DRONE;
            case CLUSTER, MINE -> StreakReward.CLUSTER_MINE;
            case SENTRY -> StreakReward.SENTRY_GUN;
            case OVERWATCH -> StreakReward.OVERWATCH_HELO;
            case PRECISION -> StreakReward.PRECISION_AIRSTRIKE;
            case SAE -> StreakReward.SAE;
            case VTOL -> StreakReward.VTOL_JET;
            case STEALTH -> StreakReward.STEALTH_BOMBER;
            case CRUISE -> StreakReward.CRUISE_MISSILE;
            case WHEELSON -> StreakReward.WHEELSON;
            case CHOPPER -> StreakReward.CHOPPER_GUNNER;
            case GUNSHIP -> StreakReward.GUNSHIP;
            default -> null;
        };
    }

    public static void configure(TacticalUnitEntity unit, ServerPlayer player) {
        unit.researchUpgrades = ResearchSystem.activeUpgrades(player);
        StreakReward reward = reward(unit.kind());
        unit.researchSource = reward == null ? "" : reward.id();
        if (unit.kind() == Kind.DELIVERY)
            unit.researchSource = unit.cargo.equals("emergency") ? "emergency_airdrop"
                    : unit.cargo.equals("juggernaut") ? "juggernaut" : "care_package";
        unit.lifetime += durationBonus(unit.kind(), unit.researchUpgrades);
    }

    private static int durationBonus(Kind kind, ResearchMask upgrades) {
        StreakReward reward = reward(kind);
        return reward != null && ResearchCatalog.has(upgrades, reward.id() + ".endurance")
                ? kind == Kind.GUNSHIP ? 1200 : 600
                : 0;
    }

    public static void inherit(TacticalUnitEntity parent, TacticalUnitEntity child) {
        select(child, parent.researchUpgrades);
        child.researchSource = parent.researchSource;
    }

    public static void withoutUpgrades(TacticalUnitEntity unit) {
        select(unit, ResearchMask.EMPTY);
        unit.researchSource = "";
    }

    public static void select(TacticalUnitEntity unit, ResearchMask upgrades) {
        if (unit.level() instanceof ServerLevel level && StreakServerConfig.upgradesDisabled(level.getServer()))
            upgrades = ResearchMask.EMPTY;
        ResearchMask previous = unit.researchUpgrades;
        unit.lifetime = Math.max(1,
                unit.lifetime + durationBonus(unit.kind(), upgrades) - durationBonus(unit.kind(), previous));
        unit.researchUpgrades = upgrades;
        ResearchCombat.refreshArmor(unit, previous);
    }

    public static boolean has(TacticalUnitEntity unit, String node) {
        return (!(unit.level() instanceof ServerLevel level) || !StreakServerConfig.upgradesDisabled(level.getServer()))
                && ResearchCatalog.has(unit.researchUpgrades, node);
    }

    public static boolean noAircraft(ServerPlayer player, StreakReward reward) {
        if (reward == null)
            return false;
        return (reward == StreakReward.UAV || reward == StreakReward.ADVANCED_UAV)
                && ResearchSystem.upgraded(player, reward.id() + ".ender")
                || (reward == StreakReward.CARE_PACKAGE || reward == StreakReward.EMERGENCY_AIRDROP)
                        && ResearchSystem.upgraded(player, reward.id() + ".teleport");
    }

    public static boolean startSurvey(ServerPlayer player, StreakReward reward) {
        if (!noAircraft(player, reward))
            return false;
        ResearchMask upgrades = ResearchSystem.activeUpgrades(player);
        Kind kind = reward == StreakReward.ADVANCED_UAV ? Kind.ADVANCED_UAV : Kind.UAV;
        SURVEYS.add(new VirtualSurvey(player.getUUID(), player.serverLevel(), player.position(), kind, upgrades,
                player.serverLevel().getGameTime() + 600 + durationBonus(kind, upgrades)));
        started(player, player.position(), kind, upgrades);
        return true;
    }

    public static List<Survey> surveys(ServerPlayer receiver) {
        List<Survey> result = new ArrayList<>();
        for (VirtualSurvey survey : SURVEYS) {
            if (survey.level != receiver.level() || survey.until <= survey.level.getGameTime())
                continue;
            ServerPlayer owner = survey.level.getServer().getPlayerList().getPlayer(survey.owner);
            if (owner == null || owner.level() != survey.level || !FriendlyPlayers.allied(receiver, owner))
                continue;
            if (ResearchCombat.blackout(owner))
                continue;
            result.add(new Survey(survey.center.add(0, 160, 0), survey.kind == Kind.ADVANCED_UAV,
                    survey.kind == Kind.UAV && ResearchCatalog.has(survey.upgrades, "uav.xray")));
        }
        return result;
    }

    public static boolean radar(TacticalUnitEntity unit) {
        return unit.kind() == Kind.UAV || unit.kind() == Kind.ADVANCED_UAV
                || unit.kind() == Kind.OVERWATCH && has(unit, "overwatch_helo.scout");
    }

    public static void detected(List<LivingEntity> targets, boolean xray) {
        if (xray)
            for (LivingEntity target : targets)
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
    }

    public static void unitTick(TacticalUnitEntity unit) {
        if (StreakServerConfig.upgradesDisabled(((ServerLevel) unit.level()).getServer())) {
            disableUpgrades(unit);
            return;
        }
        ResearchCombat.tick(unit);
        ServerPlayer player = unit.owner();
        if (player == null || unit.researchUpgrades.isEmpty() || !unit.onStation() || unit.departing()
                || unit.crashing())
            return;
        if (unit.researchStarted < 0) {
            unit.researchStarted = unit.age;
            started(player, unit.center, unit.kind(), unit.researchUpgrades);
            if (unit.kind() == Kind.OVERWATCH && has(unit, "overwatch_helo.supply")) {
                TacticalUnitEntity crate = TacticalRuntime.spawnChild(unit, Kind.CRATE, unit.position().add(0, -2, 0),
                        unit.center, 6000, unit.heading, 0,
                        TacticalRuntime.randomPackage(player.serverLevel().getServer(), false).id(), Vec3.ZERO);
                withoutUpgrades(crate);
            }
        }
        if (unit.age % 20 != 0)
            return;
        ResearchCombat.station(unit);
        ongoing(player, unit.center, unit.kind(), unit.researchUpgrades);
        if (unit.kind() == Kind.COUNTER_UAV && has(unit, "counter_uav.sonic") && unit.researchSonicRounds < 3
                && unit.age - unit.researchStarted >= unit.researchSonicRounds * 200) {
            unit.researchSonicRounds++;
            for (LivingEntity target : enemies(player, unit.center, 192)) {
                freeze(target, 100);
            }
        }
    }

    static List<LivingEntity> enemies(ServerPlayer owner, Vec3 center, double range) {
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(range),
                target -> TacticalRuntime.enemy(owner, target)
                        && !(target instanceof ServerPlayer player && player.getAbilities().invulnerable)
                        && target.position().subtract(center).horizontalDistanceSqr() <= range * range);
    }

    static void freeze(LivingEntity target, int ticks) {
        ServerLevel level = (ServerLevel) target.level();
        FROZEN.put(target, new Frozen(level, target.position(), level.getGameTime() + ticks));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 255, false, false));
    }

    private static void started(ServerPlayer owner, Vec3 center, Kind kind, ResearchMask upgrades) {
        if (kind == Kind.ADVANCED_UAV && ResearchCatalog.has(upgrades, "advanced_uav.watcher"))
            for (LivingEntity target : enemies(owner, center, 160))
                target.setHealth(target.getHealth() * .5F);
        if (kind == Kind.COUNTER_UAV && ResearchCatalog.has(upgrades, "counter_uav.assault"))
            for (LivingEntity target : enemies(owner, center, 192)) {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                if (target instanceof ServerPlayer player)
                    JAMMED.put(player, (long) owner.serverLevel().getServer().getTickCount() + 60);
            }
    }

    private static void ongoing(ServerPlayer owner, Vec3 center, Kind kind, ResearchMask upgrades) {
        if (kind == Kind.ADVANCED_UAV && ResearchCatalog.has(upgrades, "advanced_uav.nowhere"))
            for (LivingEntity target : enemies(owner, center, 160))
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
    }

    public static boolean jammed(ServerPlayer player) {
        return JAMMED.getOrDefault(player, 0L) > player.serverLevel().getServer().getTickCount();
    }

    public static boolean allowActivation(ServerPlayer player) {
        if (!jammed(player))
            return true;
        player.displayClientMessage(ResearchSystem.message("jammed"), true);
        return false;
    }

    public static float blastScale(TacticalUnitEntity unit) {
        return unit.kind() == Kind.BOMB_DRONE && has(unit, "bomb_drone.charge")
                || unit.kind() == Kind.MINE && has(unit, "cluster_mine.charge") ? 1.25F : 1;
    }

    static void afterDamage(TacticalUnitEntity emitter, LivingEntity target) {
        if (emitter.kind() == Kind.BOMB_DRONE && has(emitter, "bomb_drone.poison") && emitter.owner() != null
                && TacticalRuntime.enemy(emitter.owner(), target))
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 2, false, true));
    }

    public static void splitMine(TacticalUnitEntity mine) {
        if (!has(mine, "cluster_mine.split") || mine.researchMineGeneration > 0)
            return;
        for (int direction : new int[] { -1, 1 }) {
            Vec3 offset = Vec3.directionFromRotation(0, mine.heading + 90).scale(direction * 1.4);
            Vec3 at = mine.position().add(offset).add(0, .1, 0);
            TacticalUnitEntity child = TacticalRuntime.spawnChild(mine, Kind.MINE, at, at, 1200, mine.heading, 0, "",
                    offset.scale(.1).add(0, .12, 0));
            child.researchMineGeneration = 1;
        }
    }

    public static StreakReward packageReward(TacticalUnitEntity delivery) {
        return TacticalRuntime.randomPackage(((ServerLevel) delivery.level()).getServer(),
                has(delivery, delivery.researchSource + ".luck"));
    }

    public static boolean fastCrate(TacticalUnitEntity crate) {
        return has(crate, crate.researchSource + ".fast");
    }

    public static void escort(TacticalUnitEntity delivery) {
        if (!delivery.isRemoved() && delivery.cargo.equals("emergency") && has(delivery, "emergency_airdrop.escort"))
            spawnEscort(delivery.owner(), delivery.center);
    }

    private static void spawnEscort(ServerPlayer player, Vec3 center) {
        if (player == null)
            return;
        TacticalUnitEntity escort = TacticalRuntime.spawn(player, Kind.OVERWATCH, center.add(-55, 30, 0), center, 1200);
        withoutUpgrades(escort);
    }

    public static boolean instantPackage(ServerPlayer player, StreakReward reward) {
        if (!noAircraft(player, reward))
            return false;
        NativeAudio.call(player, reward.id());
        boolean lucky = ResearchSystem.upgraded(player, reward.id() + ".luck");
        for (int i = 0; i < (reward == StreakReward.EMERGENCY_AIRDROP ? 3 : 1); i++)
            KillstreakService.grantBonus(player,
                    TacticalRuntime.randomPackage(player.serverLevel().getServer(), lucky));
        if (reward == StreakReward.EMERGENCY_AIRDROP && ResearchSystem.upgraded(player, "emergency_airdrop.escort"))
            spawnEscort(player, player.position());
        return true;
    }

    public static void tick(MinecraftServer server) {
        ResearchCombat.cleanup();
        var virtuals = SURVEYS.iterator();
        while (virtuals.hasNext()) {
            VirtualSurvey survey = virtuals.next();
            ServerPlayer player = server.getPlayerList().getPlayer(survey.owner);
            if (player == null || !player.isAlive() || player.level() != survey.level
                    || survey.until <= survey.level.getGameTime()) {
                virtuals.remove();
                if (player != null && survey.until <= survey.level.getGameTime())
                    NativeAudio.ended(player, survey.kind == Kind.ADVANCED_UAV ? "advanced_uav" : "uav");
            } else if (server.getTickCount() % 20 == 0 && !StreakServerConfig.upgradesDisabled(server)) {
                ongoing(player, survey.center, survey.kind, survey.upgrades);
            }
        }
        var frozen = FROZEN.entrySet().iterator();
        while (frozen.hasNext()) {
            var entry = frozen.next();
            LivingEntity target = entry.getKey();
            Frozen state = entry.getValue();
            if (!target.isAlive() || target.level() != state.level || state.until <= state.level.getGameTime()) {
                frozen.remove();
                continue;
            }
            if (target.position().distanceToSqr(state.anchor) > .0001) {
                if (target instanceof ServerPlayer player)
                    player.connection.teleport(state.anchor.x, state.anchor.y, state.anchor.z, player.getYRot(),
                            player.getXRot());
                else
                    target.setPos(state.anchor);
            }
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0;
            if (target instanceof Mob mob)
                mob.getNavigation().stop();
        }
        JAMMED.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getValue() <= server.getTickCount());
    }

    public static void clear() {
        ResearchCombat.clear();
        SURVEYS.clear();
        JAMMED.clear();
        FROZEN.clear();
    }

    private static void disableUpgrades(TacticalUnitEntity unit) {
        if (!unit.researchUpgrades.isEmpty()) {
            select(unit, ResearchMask.EMPTY);
            unit.rockets = Math.min(unit.rockets, unit.kind() == Kind.CHOPPER ? 8 : 0);
            if (unit.kind() == Kind.WHEELSON && unit.autopilot && !TacticalRuntime.controlled(unit)) {
                unit.getNavigation().stop();
                unit.setNoAi(true);
            }
        }

    }

    public static void upgradesConfigChanged(MinecraftServer server) {
        if (!StreakServerConfig.upgradesDisabled(server))
            return;
        for (ServerLevel level : server.getAllLevels())
            for (var entity : level.getAllEntities())
                if (entity instanceof TacticalUnitEntity unit)
                    disableUpgrades(unit);
        SURVEYS.replaceAll(survey -> new VirtualSurvey(survey.owner, survey.level, survey.center, survey.kind,
                ResearchMask.EMPTY, survey.until - durationBonus(survey.kind, survey.upgrades)));
        JAMMED.clear();
        for (LivingEntity target : FROZEN.keySet()) {
            var effect = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (effect != null && effect.getAmplifier() == 255)
                target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        FROZEN.clear();
        ResearchCombat.cleanup();
    }
}
