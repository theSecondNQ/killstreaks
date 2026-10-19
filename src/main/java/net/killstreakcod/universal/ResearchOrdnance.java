package net.killstreakcod.universal;

import net.killstreakcod.audio.StrikeRadioReports;
import net.killstreakcod.killstreaks.MortarStrikeFlight;
import net.killstreakcod.killstreaks.NativeCruiseFlight;
import net.killstreakcod.killstreaks.PrecisionFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Strike composition, penetrating shells and auxiliary missiles share one mission snapshot.
 * 额外打击、钻地炮弹和附加导弹共用同一份任务升级快照。
 */
public final class ResearchOrdnance {
    private ResearchOrdnance() {}

    public static void precision(ServerPlayer player, Vec3 center, float heading, UUID report, ResearchMask upgrades,
            boolean controlAllowed) {
        if (StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()))
            upgrades = ResearchMask.EMPTY;
        boolean smart = controlAllowed && ResearchCatalog.has(upgrades, "precision_airstrike.smart");
        int entry = smart ? StreakTimings.REMOTE_CALL : 0;
        int rounds = ResearchCatalog.has(upgrades, "precision_airstrike.crossfire") ? 2 : 1;
        for (int round = 0; round < rounds; round++)
            for (int plane = 0; plane < 2; plane++) {
                int delay = entry + plane * PrecisionFlight.PLANE_INTERVAL
                        + round * (PrecisionFlight.LIFETIME + PrecisionFlight.PLANE_INTERVAL + 20);
                float direction = Mth.wrapDegrees(heading + round * 180);
                TacticalUnitEntity unit = TacticalRuntime.spawnConfigured(player, Kind.PRECISION, center.add(0, 36, 0),
                        center, PrecisionFlight.LIFETIME, direction, delay, "", Vec3.ZERO);
                ResearchEffects.select(unit, upgrades);
                StrikeRadioReports.track(unit, report);
                if (smart && round == 0 && plane == 0 && !unit.isRemoved()) {
                    unit.remotePrecision(true);
                    TacticalRuntime.startPrecisionControl(player, unit);
                }
            }
    }

    public static void mortar(ServerPlayer player, Vec3 center, float heading, UUID report, ResearchMask upgrades) {
        if (StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()))
            upgrades = ResearchMask.EMPTY;
        int shells = ResearchCatalog.has(upgrades, "mortar_strike.extra") ? 5 : MortarStrikeFlight.SHELLS_PER_VOLLEY;
        int groups = ResearchCatalog.has(upgrades, "mortar_strike.second_sun") ? 2 : 1;
        double spread = shells == 5 ? 5 : MortarStrikeFlight.SPREAD_RADIUS;
        int groupTicks = (MortarStrikeFlight.VOLLEYS - 1) * MortarStrikeFlight.VOLLEY_INTERVAL + 20;
        for (int group = 0; group < groups; group++)
            for (int volley = 0; volley < MortarStrikeFlight.VOLLEYS; volley++)
                for (int shell = 0; shell < shells; shell++) {
                    Vec3 target = MortarStrikeFlight.target(player, center, heading, volley, shell, shells, spread);
                    if (ResearchCatalog.has(upgrades, "mortar_strike.bunker"))
                        target = bunker(player.serverLevel(), target);
                    Vec3 from = MortarStrikeFlight.start(player, target, heading);
                    TacticalUnitEntity unit = TacticalRuntime.spawnConfigured(player, Kind.BOMB, from, target, 220,
                            heading, group * groupTicks + volley * MortarStrikeFlight.VOLLEY_INTERVAL, "mortar",
                            MortarStrikeFlight.velocity(from, target));
                    ResearchEffects.select(unit, upgrades);
                    unit.noPhysics = penetrates(unit);
                    StrikeRadioReports.track(unit, report);
                }
    }

    private static Vec3 bunker(ServerLevel level, Vec3 surface) {
        BlockPos top = BlockPos.containing(surface);
        for (int depth = 1; depth <= 10; depth++) {
            BlockPos at = top.below(depth);
            if (at.getY() > level.getMinBuildHeight() && level.isEmptyBlock(at) && level.isEmptyBlock(at.above()))
                return new Vec3(surface.x, at.getY() + .2, surface.z);
        }
        return surface;
    }

    public static boolean penetrates(TacticalUnitEntity unit) {
        return unit.cargo.equals("mortar") && ResearchEffects.has(unit, "mortar_strike.bunker")
                && unit.center.y < TacticalRuntime.ground((ServerLevel) unit.level(), unit.center).y - 1;
    }

    public static void squadron(ServerPlayer player, Kind kind, List<Vec3> points, float[] headings, UUID report,
            ResearchMask upgrades) {
        if (StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()))
            upgrades = ResearchMask.EMPTY;
        int extra = ResearchCatalog.has(upgrades, kind == Kind.SAE ? "sae.squadron" : "stealth_bomber.special_ops") ? 2
                : 0;
        for (int plane = 0; plane < points.size() + extra; plane++) {
            int target = plane % points.size();
            Vec3 center = TacticalRuntime.ground(player.serverLevel(), points.get(target));
            TacticalUnitEntity unit = TacticalRuntime.spawnConfigured(player, kind, center.add(0, 40, 0), center, 110,
                    Mth.wrapDegrees(headings[target]), plane * 20, "", Vec3.ZERO);
            ResearchEffects.select(unit, upgrades);
            StrikeRadioReports.track(unit, report);
        }
    }

    public static boolean coordinate(TacticalUnitEntity gunship, Vec3 point, float heading) {
        ServerPlayer player = gunship.owner();
        if (player == null || gunship.kind() != Kind.GUNSHIP || !ResearchEffects.has(gunship, "gunship.coordination")
                || !StreakAirspace.target(player, StreakReward.PRECISION_AIRSTRIKE, point, heading))
            return false;
        UUID report = StrikeRadioReports.begin(player, "precision_airstrike");
        precision(player, TacticalRuntime.ground(player.serverLevel(), point), heading, report, ResearchMask.EMPTY,
                false);
        return true;
    }

    public static void rocket(TacticalUnitEntity unit, float yaw, float pitch) {
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 from = unit.kind() == Kind.CHOPPER ? AircraftFireEffects.rocketMount(unit) : unit.getEyePosition();
        TacticalUnitEntity rocket = TacticalRuntime.spawnChild(unit, Kind.ROCKET, from.add(direction.scale(.6)),
                unit.center, 100, yaw, 0, "helo_rocket", direction.scale(4.5));
        unit.noteShot();
        AircraftFireEffects.rocketLaunch(rocket);
    }

    public static void departed(TacticalUnitEntity gunship) {
        if (gunship.kind() != Kind.GUNSHIP || !ResearchEffects.has(gunship, "gunship.iron_maiden")
                || gunship.owner() == null)
            return;
        List<LivingEntity> targets = ResearchEffects.enemies(gunship.owner(), gunship.center, 192).stream()
                .filter(target -> surface(target, (ServerLevel) gunship.level()))
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(gunship.center))).toList();
        for (int i = 0; i < 3; i++) {
            Vec3 target = targets.isEmpty() ? gunship.center : targets.get(i % targets.size()).position();
            TacticalUnitEntity missile = TacticalRuntime.spawnConfigured(gunship.owner(), Kind.CRUISE,
                    gunship.position().add(Vec3.directionFromRotation(0, gunship.heading + 90).scale((i - 1) * 4)),
                    target, 300, gunship.flightYaw(), 0, "research_autonomous", Vec3.ZERO, gunship.flightLift);
            ResearchEffects.withoutUpgrades(missile);
            missile.cruiseClock(NativeCruiseFlight.DURATION);
        }
    }

    private static boolean surface(LivingEntity target, ServerLevel level) {
        return target.getY() >= TacticalRuntime.ground(level, target.position()).y - 3;
    }

    public static boolean autonomousCruise(TacticalUnitEntity unit) {
        if (unit.kind() != Kind.CRUISE || !unit.cargo.equals("research_autonomous"))
            return false;
        ServerLevel level = (ServerLevel) unit.level();
        LivingEntity enemy = unit.owner() == null ? null
                : ResearchEffects.enemies(unit.owner(), unit.center, 128).stream()
                        .filter(target -> surface(target, level)
                                && TacticalRuntime.clear(level, unit.getEyePosition(), target.getEyePosition(), unit))
                        .min(Comparator.comparingDouble(target -> target.position().distanceToSqr(unit.center)))
                        .orElse(null);
        if (enemy != null)
            unit.center = enemy.position();
        Vec3 target = TacticalRuntime.ground(level, unit.center).add(0, .4, 0);
        Vec3 direction = target.subtract(unit.position()).normalize();
        Vec3 next = unit.position().add(direction.scale(Math.min(4, unit.position().distanceTo(target))));
        var hit = level
                .clip(new ClipContext(unit.position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, unit));
        if (hit.getType() != HitResult.Type.MISS || next.distanceToSqr(target) < 1) {
            TacticalRuntime.blast(unit,
                    hit.getType() == HitResult.Type.MISS ? target : TacticalRuntime.impactPosition(hit), 22, 180);
            unit.discard();
            return true;
        }
        unit.flight(next);
        unit.flightOrientation((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
                (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, unit.getX(), unit.getY(), unit.getZ(), 2,
                .1, .1, .1, .02);
        return true;
    }
}
