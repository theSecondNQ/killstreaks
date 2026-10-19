package net.killstreakcod.universal;

import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.audio.SupportSoundPayload;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Source-linked primary fire/impact sounds and bounded native sprite adapters.
 * 将主要开火及命中音效关联到源资源，并对原精灵适配施加数量等限制。
 */
public final class AircraftFireEffects {
    private static final Map<TacticalUnitEntity, Long> LAST_BRASS = new WeakHashMap<>();

    private AircraftFireEffects() {}

    public static boolean shot(Entity emitter, Vec3 muzzle, float directDamage) {
        if (!(emitter instanceof TacticalUnitEntity unit))
            return false;
        int weapon = unit.kind() == Kind.CHOPPER ? -1
                : unit.kind() == Kind.GUNSHIP ? (directDamage == 0 ? 1 : 0)
                        : unit.kind() == Kind.VTOL && ResearchEffects.has(unit, "vtol_jet.hercules") ? 0 : -2;
        return weapon != -2 && shot(unit, muzzle, weapon);
    }

    /**
     * Third-slot mechanics retain their independent 105mm model and magazine.
     * 第三武器槽的机械逻辑保留独立的 105 毫米模型与弹容。
     */
    public static boolean shot(TacticalUnitEntity unit, Vec3 muzzle, int weapon) {
        if (!(unit.level() instanceof ServerLevel level))
            return false;
        String role = weapon < 0 ? "chopper_shot"
                : weapon == 0 ? "gunship25_shot" : weapon == 1 ? "gunship40_shot" : "gunship105_shot";
        for (var listener : level.players()) {
            boolean operator = listener == unit.owner() && TacticalRuntime.controlled(unit);
            if (!operator && RemoteViewSubscriptions.origin(listener).distanceToSqr(muzzle) > 512D * 512)
                continue;
            var sound = NativeAudio.sfx(operator ? role : role + "_npc", StreakSounds.SHOT.get());

            // 中文：每名听众只播放一个主音效，不同时叠加操作者与外部录音层；遥控操作者在实际摄像机原点听到操作者录音。
            Vec3 at = operator ? RemoteViewSubscriptions.origin(listener) : muzzle;
            StreakNetwork.send(listener, new SupportSoundPayload(level.dimension().location(), sound.getLocation(),
                    at.x, at.y, at.z, operator ? .58F : .55F, 1, 512));
        }
        String profile = weapon < 0 ? "apache_muzzle"
                : weapon == 0 ? "gunship25_muzzle" : weapon == 1 ? "gunship40_muzzle" : null;
        if (profile != null)
            NativeFxDelivery.emit(level, "aircraft25_" + profile, muzzle, weapon == 1 ? 1.4F : .8F);
        if (weapon < 0) {
            long now = level.getGameTime();
            if (now - LAST_BRASS.getOrDefault(unit, now - 6) >= 6) {
                LAST_BRASS.put(unit, now);
                NativeFxDelivery.emit(level, "aircraft25_apache_brass", muzzle, .4F);
            }
        }
        return true;
    }

    /**
     * Source state alternates the helicopter's left/right racks; physical travel stays independent.
     * 按源状态交替使用直升机左右火箭巢，实体飞行过程单独计算。
     */
    public static Vec3 rocketMount(TacticalUnitEntity unit) {
        Vec3 forward = Vec3.directionFromRotation(unit.flightPitch(), unit.flightYaw());
        Vec3 right = Vec3.directionFromRotation(0, unit.flightYaw() + 90);
        double side = (unit.rockets & 1) == 0 ? -1 : 1;
        return unit.position().add(0, -.5, 0).add(forward.scale(1.65)).add(right.scale(side * 1.4));
    }

    public static void rocketLaunch(TacticalUnitEntity projectile) {
        if (!(projectile.level() instanceof ServerLevel level))
            return;
        if (projectile.cargo.equals("helo_rocket"))
            NativeFxDelivery.emit(level, "aircraft25_apache_launch", projectile.position(), .8F);
        else if (projectile.cargo.equals("105mm"))
            NativeFxDelivery.emit(level, "aircraft25_gunship105_trail", projectile.position(), .55F);
    }

    /**
     * Two update ticks per emitted native trail; retain the existing real projectile and tracer.
     * 每两刻生成一次原资源尾迹，保留现有真实弹体和曳光效果。
     */
    public static boolean projectileTrail(TacticalUnitEntity unit, Vec3 from, Vec3 to) {
        String role = unit.cargo.equals("helo_rocket") ? "apache_trail"
                : unit.cargo.equals("105mm") ? "gunship105_trail" : null;
        if (role == null || !(unit.level() instanceof ServerLevel level))
            return false;
        if (((level.getGameTime() + unit.getId()) & 1) == 0) {
            Vec3 velocity = to.subtract(from).normalize();
            Vec3 exhaust = to.subtract(velocity.scale(.65));
            NativeFxDelivery.emit(level, "aircraft25_" + role, exhaust, .7F);
        }
        return true;
    }

    public static boolean impact(TacticalUnitEntity unit, Vec3 at, float radius) {
        if (!(unit.level() instanceof ServerLevel level))
            return false;
        String role;
        if (unit.kind() == Kind.ROCKET && unit.cargo.equals("helo_rocket"))
            role = water(level, at) ? "apache_water" : "apache_impact";
        else if (unit.kind() == Kind.ROCKET && unit.cargo.equals("105mm"))
            role = water(level, at) ? "gunship105_water" : "gunship105_impact";
        else if ((unit.kind() == Kind.CHOPPER || unit.kind() == Kind.GUNSHIP
                || unit.kind() == Kind.VTOL && ResearchEffects.has(unit, "vtol_jet.hercules")) && radius <= 5) {
            String caliber = unit.kind() == Kind.GUNSHIP && radius > 3 ? "40" : "25";
            String surface = surface(level, at);
            if (caliber.equals("40") && surface.equals("glass"))
                surface = "gen";
            role = caliber + "_" + surface;
        } else
            return false;
        NativeFxDelivery.emit(level, "aircraft25_" + role, at, Math.min(12, radius));
        return true;
    }

    private static boolean water(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    /**
     * Source surface enum is unrecovered. Map only unambiguous Minecraft materials.
     * 原表面类型枚举尚未恢复，只映射含义明确的 Minecraft 材质。
     */
    private static String surface(ServerLevel level, Vec3 at) {
        if (water(level, at))
            return "water";
        BlockPos pos = BlockPos.containing(at);
        var state = level.getBlockState(pos);
        if (state.isAir())
            state = level.getBlockState(pos.below());
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS))
            return "wood";
        if (id.contains("glass"))
            return "glass";
        if (id.contains("iron") || id.contains("copper") || id.contains("gold") || id.contains("netherite")
                || id.contains("anvil"))
            return "metal";
        if (state.is(BlockTags.DIRT) || id.contains("sand") || id.contains("mud") || id.contains("gravel"))
            return "dirt";
        return "gen";
    }
}
