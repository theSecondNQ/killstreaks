package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.compat.SuperbWarfareCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * Entity damage and multipart hit routing; normal hurt APIs retain armour and boss phases.
 * 处理实体伤害及多部件命中路由，通过正常受伤 API 保留护甲和 Boss 阶段机制。
 */
public final class StreakCombat {
    private static final ResourceKey<DamageType> ROUND = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "streak_round"));
    private static final ResourceKey<DamageType> EXPLOSION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "streak_explosion"));
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "bosses"));

    private record Exposure(Entity target, float damage) {
    }

    private StreakCombat() {}

    static Entity root(Entity entity) {
        for (int i = 0; i < 8 && entity instanceof PartEntity<?> part; i++) {
            Entity parent = part.getParent();
            if (parent == null || parent == entity)
                break;
            entity = parent;
        }
        return entity;
    }

    static boolean boss(LivingEntity entity) {
        return entity.getType().is(BOSSES);
    }

    static boolean allied(ServerPlayer owner, Entity entity) {
        Entity target = root(entity);
        if (FriendlyPlayers.allied(owner, target))
            return true;
        if (target instanceof TacticalUnitEntity unit && (FriendlyPlayers.allied(owner, unit.ownerId)
                || unit.owner() != null && FriendlyPlayers.allied(owner, unit.owner())))
            return true;
        if (SuperbWarfareCompat.vehicle(target))
            for (Entity rider : target.getIndirectPassengers())
                if (FriendlyPlayers.allied(owner, rider))
                    return true;
        return false;
    }

    static boolean target(ServerPlayer owner, Entity emitter, Entity entity, boolean allowOwner) {
        Entity target = root(entity);
        if (entity instanceof PartEntity<?> && !entity.isPickable())
            return false;
        if (target == emitter || !target.isAlive() || target.isSpectator() || !(target instanceof LivingEntity
                || target instanceof EndCrystal || SuperbWarfareCompat.vehicle(target)))
            return false;
        if (target instanceof TacticalUnitEntity unit && owner.getUUID().equals(unit.ownerId))
            return false;
        if (SuperbWarfareCompat.destroyed(target))
            return false;
        if (target == owner)
            return allowOwner;
        return !allied(owner, target);
    }

    public static List<Entity> targets(ServerLevel level, ServerPlayer owner, Entity emitter, AABB area,
            boolean allowOwner) {
        return level.getEntities(emitter, area,
                entity -> target(owner, emitter, entity, allowOwner) && (!entity.isMultipartEntity()
                        || entity.isPickable() || entity.getParts() == null || entity.getParts().length == 0));
    }

    public static Optional<Vec3> intercept(Entity entity, Vec3 from, Vec3 to, double padding) {
        return SuperbWarfareCompat.vehicle(entity) ? SuperbWarfareCompat.intercept(entity, from, to)
                : entity.getBoundingBox().inflate(padding).clip(from, to);
    }

    static DamageSource source(ServerLevel level, Entity emitter, ServerPlayer owner, boolean explosive, Vec3 at) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(explosive ? EXPLOSION : ROUND), emitter, owner, at);
    }

    static boolean hostile(ServerPlayer owner, Entity entity) {
        Entity target = root(entity);
        return target instanceof LivingEntity living ? TacticalRuntime.enemy(owner, living)
                : target != owner && !allied(owner, target)
                        && (target instanceof EndCrystal || SuperbWarfareCompat.vehicle(target));
    }

    static boolean hurt(ServerPlayer owner, Entity emitter, Entity entity, float damage, boolean explosive, Vec3 at) {
        if (!explosive && emitter instanceof TacticalUnitEntity unit)
            damage *= ResearchCombat.damageScale(unit);
        if (!Float.isFinite(damage) || damage <= 0 || entity.isRemoved())
            return false;
        Entity target = root(entity);
        if (target instanceof LivingEntity living)
            living.invulnerableTime = 0;
        boolean applied = entity.hurt(source((ServerLevel) entity.level(), emitter, owner, explosive, at), damage);
        if (applied && SuperbWarfareCompat.destroyed(target))
            KillstreakService.queueVehicleKill(owner, target, emitter instanceof TacticalUnitEntity);
        return applied;
    }

    static boolean blast(ServerLevel level, ServerPlayer owner, TacticalUnitEntity emitter, Vec3 at, float radius,
            float damage) {
        if (!Float.isFinite(radius) || !Float.isFinite(damage) || radius <= 0 || damage <= 0)
            return false;
        Map<Entity, List<Exposure>> groups = new IdentityHashMap<>();
        for (Entity entity : targets(level, owner, emitter, new AABB(at, at).inflate(radius), true)) {
            AABB box = entity.getBoundingBox();
            Vec3 closest = new Vec3(net.minecraft.util.Mth.clamp(at.x, box.minX, box.maxX), net.minecraft.util.Mth.clamp(at.y, box.minY, box.maxY),
                    net.minecraft.util.Mth.clamp(at.z, box.minZ, box.maxZ));
            double distance = closest.distanceTo(at);
            if (distance >= radius)
                continue;
            int exposed = 0;
            for (int i = 1; i <= 5; i++)
                if (TacticalRuntime.clear(level, at, new Vec3((box.minX + box.maxX) * .5,
                        box.minY + box.getYsize() * i / 6, (box.minZ + box.maxZ) * .5), emitter))
                    exposed++;
            if (exposed == 0)
                continue;
            float amount = (float) (damage * (1 - distance / radius) * exposed / 5);
            Entity target = root(entity);
            groups.computeIfAbsent(target, key -> new ArrayList<>()).add(new Exposure(entity, amount));
        }
        boolean confirmed = false;
        for (var entry : groups.entrySet()) {
            Entity target = entry.getKey();
            boolean hostile = hostile(owner, target);
            entry.getValue().sort(Comparator.comparingDouble((Exposure e) -> e.damage).reversed());
            boolean applied = false;
            // Some multipart bosses have decorative/immune shield parts. Try
            // another exposed part only after rejection; apply damage once per boss.
            // 中文：部分多部件 Boss 含装饰性或免疫护盾部件；仅在伤害被拒绝后尝试另一个暴露部件，每个 Boss 只结算一次伤
            // 害。
            for (Exposure exposure : entry.getValue())
                if (hurt(owner, emitter, exposure.target, exposure.damage, true, at)) {
                    applied = true;
                    break;
                }
            confirmed |= applied && hostile;
            if (applied && target instanceof LivingEntity living)
                ResearchEffects.afterDamage(emitter, living);
            if (applied && !SuperbWarfareCompat.vehicle(target) && !(target instanceof EndCrystal))
                target.setDeltaMovement(
                        target.getDeltaMovement().add(target.position().subtract(at).normalize().scale(.6)));
        }
        return confirmed;
    }
}
