package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.Comparator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 智能投射的自动锁定；沿用飞机实际炮口、阵营保护和方块视线，目标消失后自动换锁。 */
public final class PrecisionAutoAim {
    private static final double RADIUS = 32, HEIGHT = 24;
    private LivingEntity target;
    private int nextSearch;

    public Vec3 direction(TacticalUnitEntity plane, Vec3 muzzle, Vec3 normal) {
        ServerPlayer owner = plane.owner();
        if (owner == null)
            return normal;
        if (!valid(plane, owner, target, muzzle)) {
            boolean lostTarget = target != null;
            target = null;
            if (lostTarget || plane.age >= nextSearch) {
                nextSearch = plane.age + 6;
                target = plane.level()
                        .getEntitiesOfClass(LivingEntity.class,
                                new AABB(plane.center, plane.center).inflate(RADIUS, HEIGHT, RADIUS),
                                candidate -> valid(plane, owner, candidate, muzzle))
                        .stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(plane.center)))
                        .orElse(null);
            }
        }
        Vec3 point = target == null ? null : exposed(plane, target, muzzle);
        return point == null ? normal : point.subtract(muzzle).normalize();
    }

    private static boolean valid(TacticalUnitEntity plane, ServerPlayer owner, LivingEntity candidate, Vec3 muzzle) {
        if (candidate == null || candidate.level() != plane.level() || !TacticalRuntime.enemy(owner, candidate)
                || candidate.isInvulnerable()
                || candidate instanceof ServerPlayer player && player.getAbilities().invulnerable)
            return false;
        Vec3 offset = candidate.position().subtract(plane.center);
        return offset.horizontalDistanceSqr() <= RADIUS * RADIUS && Math.abs(offset.y) <= HEIGHT
                && exposed(plane, candidate, muzzle) != null;
    }

    private static Vec3 exposed(TacticalUnitEntity plane, LivingEntity candidate, Vec3 muzzle) {
        Vec3 torso = candidate.getBoundingBox().getCenter();
        if (TacticalRuntime.clear(plane.level(), muzzle, torso, plane))
            return torso;
        Vec3 head = candidate.getEyePosition();
        return TacticalRuntime.clear(plane.level(), muzzle, head, plane) ? head : null;
    }
}
