package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Combat changes read an immutable mission selection; temporary effects own their cleanup here.
 * 战斗升级读取不可变任务快照；临时干扰、生命上限和化学云在此统一清理。
 */
public final class ResearchCombat {
    public static final int COORDINATION = 1, IRON_MAIDEN = 2, HELLFIRE = 4, UNLIMITED_ROCKETS = 8, DISABLED = 16;
    private static final ResourceLocation HALF_HEALTH = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "research_bomber_half_health");

    private record Blackout(Set<TacticalUnitEntity> units, Set<LivingEntity> enemies) {
    }

    private record Chemical(AreaEffectCloud cloud, ServerPlayer owner) {
    }

    private static final Map<TacticalUnitEntity, Blackout> BLACKOUTS = new HashMap<>();
    private static final Map<LivingEntity, Integer> WEAKENED = new IdentityHashMap<>();
    private static final List<Chemical> CLOUDS = new ArrayList<>();

    private ResearchCombat() {}

    public static float healthMultiplier(Kind kind, ResearchMask upgrades) {
        String node = switch (kind) {
            case VTOL -> "vtol_jet.endurance";
            case WHEELSON -> "wheelson_hs.armor";
            case CHOPPER -> "chopper_gunner.armor";
            case GUNSHIP -> "gunship.armor";
            default -> "";
        };
        return ResearchCatalog.has(upgrades, node) ? 2 : 1;
    }

    public static void refreshArmor(TacticalUnitEntity unit, ResearchMask previous) {
        float ratio = healthMultiplier(unit.kind(), unit.researchUpgrades) / healthMultiplier(unit.kind(), previous);
        if (ratio == 1)
            return;
        float health = unit.getHealth();
        var attribute = unit.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(attribute.getBaseValue() * ratio);
            unit.setHealth(Math.min(unit.getMaxHealth(), health * ratio));
        }
    }

    public static float incomingDamage(TacticalUnitEntity unit, float damage) {
        float reduction = switch (unit.kind()) {
            case VTOL -> ResearchEffects.has(unit, "vtol_jet.hercules") ? .5F
                    : ResearchEffects.has(unit, "vtol_jet.endurance") ? .25F : 0;
            case WHEELSON -> ResearchEffects.has(unit, "wheelson_hs.armor") ? .1F : 0;
            case CHOPPER -> ResearchEffects.has(unit, "chopper_gunner.charlie") ? .5F
                    : ResearchEffects.has(unit, "chopper_gunner.armor") ? .25F : 0;
            case GUNSHIP -> ResearchEffects.has(unit, "gunship.iron_maiden") ? .75F
                    : ResearchEffects.has(unit, "gunship.armor") ? .5F : 0;
            default -> 0;
        };
        return damage * (1 - reduction);
    }

    public static float damageScale(TacticalUnitEntity unit) {
        if (unit.kind() == Kind.PRECISION && ResearchEffects.has(unit, "precision_airstrike.overload"))
            return 1.5F;
        if ((unit.kind() == Kind.SAE || unit.cargo.startsWith("sae")) && ResearchEffects.has(unit, "sae.charge"))
            return 1.25F;
        return radiusScale(unit);
    }

    public static float radiusScale(TacticalUnitEntity unit) {
        return unit.kind() == Kind.CRUISE && ResearchEffects.has(unit, "cruise_missile.charge")
                || unit.cargo.equals("stealth") && ResearchEffects.has(unit, "stealth_bomber.overpressure") ? 1.5F : 1;
    }

    public static void ammunition(TacticalUnitEntity unit) {
        if (unit.kind() == Kind.CHOPPER && ResearchEffects.has(unit, "chopper_gunner.shadow"))
            unit.rockets = 16;
        if (unit.kind() == Kind.GUNSHIP)
            unit.rockets = ResearchEffects.has(unit, "gunship.iron_maiden") ? 32 : 0;
        if (unit.kind() == Kind.CRUISE)
            unit.rockets = ResearchEffects.has(unit, "cruise_missile.hellfire") ? 2 : 0;
    }

    public static int capabilities(TacticalUnitEntity unit) {
        int selected = switch (unit.kind()) {
            case GUNSHIP -> (ResearchEffects.has(unit, "gunship.coordination") ? COORDINATION : 0)
                    | (ResearchEffects.has(unit, "gunship.iron_maiden") ? IRON_MAIDEN : 0);
            case CRUISE -> ResearchEffects.has(unit, "cruise_missile.hellfire") ? HELLFIRE : 0;
            case CHOPPER -> ResearchEffects.has(unit, "chopper_gunner.charlie") ? UNLIMITED_ROCKETS : 0;
            default -> 0;
        };
        return selected | (disabled(unit) ? DISABLED : 0);
    }

    public static boolean disabled(TacticalUnitEntity unit) {
        return BLACKOUTS.entrySet().stream()
                .anyMatch(entry -> active(entry.getKey()) && entry.getValue().units.contains(unit));
    }

    public static boolean blackout(ServerPlayer player) {
        return BLACKOUTS.keySet().stream().anyMatch(source -> active(source) && source.level() == player.level()
                && source.owner() != null && !FriendlyPlayers.allied(player, source.owner()));
    }

    private static boolean active(TacticalUnitEntity source) {
        return source.isAlive() && !source.isRemoved() && !source.departing() && !source.crashing()
                && ResearchEffects.has(source, "stealth_bomber.dark");
    }

    public static void tick(TacticalUnitEntity unit) {
        if (unit.owner() == null || unit.age < unit.delay)
            return;
        if (unit.kind() == Kind.STEALTH && ResearchEffects.has(unit, "stealth_bomber.dark")
                && !BLACKOUTS.containsKey(unit))
            beginBlackout(unit);
        if (unit.kind() == Kind.BOMB && unit.cargo.equals("stealth")
                && ResearchEffects.has(unit, "stealth_bomber.homing"))
            guideBomb(unit);
        if (unit.kind() == Kind.WHEELSON && unit.autopilot && !TacticalRuntime.controlled(unit)
                && ResearchEffects.has(unit, "wheelson_hs.patrol")) {
            Vec3 movement = unit.getDeltaMovement();
            if (movement.horizontalDistanceSqr() > .0001) {
                unit.heading = (float) Math.toDegrees(Math.atan2(-movement.x, movement.z));
                unit.flightOrientation(unit.heading, 0);
            }
            if (unit.age % 20 == 0)
                patrol(unit);
        }
    }

    public static void station(TacticalUnitEntity unit) {
        String source = unit.kind() == Kind.CHOPPER ? "chopper_gunner" : "gunship";
        if ((unit.kind() == Kind.CHOPPER && ResearchEffects.has(unit, source + ".shadow")
                || unit.kind() == Kind.GUNSHIP && ResearchEffects.has(unit, source + ".iron_maiden"))
                && unit.age % 20 == 0)
            for (LivingEntity target : ResearchEffects.enemies(unit.owner(), unit.center, 192))
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        boolean chopper = unit.kind() == Kind.CHOPPER && ResearchEffects.has(unit, "chopper_gunner.charlie"),
                gunship = unit.kind() == Kind.GUNSHIP && ResearchEffects.has(unit, "gunship.iron_maiden");
        if ((chopper || gunship) && unit.researchSonicRounds < (chopper ? 5 : 7)
                && unit.age - unit.researchStarted >= unit.researchSonicRounds * (chopper ? 200 : 300)) {
            unit.researchSonicRounds++;
            for (LivingEntity target : ResearchEffects.enemies(unit.owner(), unit.center, 192))
                ResearchEffects.freeze(target, chopper ? 100 : 200);
        }
    }

    private static void patrol(TacticalUnitEntity unit) {
        LivingEntity target = TacticalRuntime.nearest(unit, 48, false);
        if (target != null) {
            unit.getNavigation().moveTo(target, 1);
            return;
        }
        if (unit.getNavigation().isDone()) {
            double angle = (unit.age / 80D + unit.getId()) * 1.7;
            Vec3 at = TacticalRuntime.ground((ServerLevel) unit.level(),
                    unit.center.add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14));
            unit.getNavigation().moveTo(at.x, at.y, at.z, .8);
        }
    }

    private static void guideBomb(TacticalUnitEntity unit) {
        if (unit.age % 4 != 0)
            return;
        Vec3 right = Vec3.directionFromRotation(0, unit.heading + 90);
        LivingEntity target = ResearchEffects.enemies(unit.owner(), unit.position(), 72).stream()
                .filter(enemy -> Math.abs(enemy.position().subtract(unit.position()).dot(right)) <= 24)
                .min(java.util.Comparator.comparingDouble(enemy -> enemy.distanceToSqr(unit))).orElse(null);
        if (target == null
                || !TacticalRuntime.clear(unit.level(), unit.getEyePosition(), target.getEyePosition(), unit))
            return;
        Vec3 direction = target.position().add(0, .5, 0).subtract(unit.position()).normalize();
        unit.center = target.position();
        unit.setDeltaMovement(unit.getDeltaMovement().lerp(direction.scale(1.8), .15));
    }

    private static void beginBlackout(TacticalUnitEntity source) {
        Set<TacticalUnitEntity> units = new HashSet<>();
        Set<LivingEntity> enemies = new HashSet<>();
        for (Entity entity : ((ServerLevel) source.level()).getAllEntities()) {
            if (entity instanceof TacticalUnitEntity unit && unit != source
                    && !StreakCombat.allied(source.owner(), unit))
                units.add(unit);
            if (entity instanceof LivingEntity target
                    && (TacticalRuntime.enemy(source.owner(), target) || entity instanceof TacticalUnitEntity unit
                            && unit != source && !StreakCombat.allied(source.owner(), unit))
                    && !(target instanceof ServerPlayer player && player.getAbilities().invulnerable)) {
                var attribute = target.getAttribute(Attributes.MAX_HEALTH);
                if (attribute == null)
                    continue;
                if (!WEAKENED.containsKey(target)) {
                    attribute.addTransientModifier(
                            new AttributeModifier(HALF_HEALTH, -.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    target.setHealth(Math.min(target.getHealth(), target.getMaxHealth()));
                }
                WEAKENED.merge(target, 1, Integer::sum);
                enemies.add(target);
            }
        }
        BLACKOUTS.put(source, new Blackout(units, enemies));
    }

    public static void chemical(TacticalUnitEntity emitter, Vec3 at) {
        if (!emitter.cargo.equals("sae_bomblet") || !ResearchEffects.has(emitter, "sae.chemical")
                || emitter.owner() == null)
            return;
        for (Chemical existing : CLOUDS)
            if (!existing.cloud.isRemoved() && existing.owner == emitter.owner()
                    && existing.cloud.position().distanceToSqr(at) < 16)
                return;
        // The cloud is visual; enemy-only effect application prevents friendly chemical damage.
        // 滞留云负责显示，效果另按敌我判定施加，避免毒气伤害队友。
        if (CLOUDS.size() >= 96)
            CLOUDS.removeFirst().cloud.discard();
        AreaEffectCloud cloud = new AreaEffectCloud(emitter.level(), at.x, at.y + .1, at.z);
        cloud.setOwner(emitter.owner());
        cloud.setRadius(5);
        cloud.setDuration(200);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(-.015F);
        cloud.setParticle(ParticleTypes.SNEEZE);
        emitter.level().addFreshEntity(cloud);
        CLOUDS.add(new Chemical(cloud, emitter.owner()));
    }

    public static void cleanup() {
        var entries = BLACKOUTS.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (active(entry.getKey()))
                continue;
            for (LivingEntity target : entry.getValue().enemies) {
                int count = WEAKENED.getOrDefault(target, 1) - 1;
                if (count == 0) {
                    var attribute = target.getAttribute(Attributes.MAX_HEALTH);
                    if (attribute != null)
                        attribute.removeModifier(HALF_HEALTH);
                    WEAKENED.remove(target);
                } else
                    WEAKENED.put(target, count);
            }
            entries.remove();
        }
        CLOUDS.removeIf(row -> {
            if (StreakServerConfig.upgradesDisabled(row.owner.serverLevel().getServer()))
                row.cloud.discard();
            return row.cloud.isRemoved() || row.owner.level() != row.cloud.level();
        });
        for (Chemical row : CLOUDS)
            if (row.cloud.tickCount % 10 == 0)
                for (Entity entity : StreakCombat.targets((ServerLevel) row.cloud.level(), row.owner, row.cloud,
                        new AABB(row.cloud.position(), row.cloud.position()).inflate(row.cloud.getRadius()), false)) {
                    if (!(entity instanceof LivingEntity target) || Math.abs(target.getY() - row.cloud.getY()) > 3
                            || target instanceof ServerPlayer player && player.getAbilities().invulnerable
                            || target.position().subtract(row.cloud.position())
                                    .horizontalDistanceSqr() > row.cloud.getRadius() * row.cloud.getRadius())
                        continue;
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 4), row.owner);
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 2), row.owner);
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 2), row.owner);
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60), row.owner);
                }
    }

    public static void clear() {
        for (LivingEntity target : WEAKENED.keySet()) {
            var attribute = target.getAttribute(Attributes.MAX_HEALTH);
            if (attribute != null)
                attribute.removeModifier(HALF_HEALTH);
        }
        for (Chemical row : CLOUDS)
            row.cloud.discard();
        WEAKENED.clear();
        BLACKOUTS.clear();
        CLOUDS.clear();
    }
}
