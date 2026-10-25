package net.killstreakcod.universal;

import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StrikeRadioReports;
import net.killstreakcod.audio.SupportSoundPayload;
import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded server crash phases. Source action playback is separate from Minecraft collision.
 * 服务端坠毁阶段有明确边界，源动作播放与 Minecraft 碰撞分别处理。
 */
public final class AircraftCrash {
    public static final class State {
        int ticks, finishedAt = -1;
        Vec3 start, goal, drift = Vec3.ZERO;
        float yaw, pitch;
        boolean impact, canopy;

        State(Vec3 start, Vec3 goal, float yaw, float pitch) {
            this.start = start;
            this.goal = goal;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private AircraftCrash() {}

    public static boolean begin(TacticalUnitEntity unit, DamageSource source) {
        if (unit.crashing())
            return true;
        if (!(unit.level() instanceof ServerLevel level) || !unit.canBeDestroyed())
            return false;
        int variant = switch (unit.kind()) {
            case GUNSHIP -> AircraftCrashLibrary.GUNSHIP;
            case CHOPPER ->
                source.is(DamageTypeTags.IS_EXPLOSION) || source.getDirectEntity() instanceof TacticalUnitEntity shell
                        && shell.kind() == TacticalUnitEntity.Kind.ROCKET ? AircraftCrashLibrary.APACHE_MISSILE
                                : AircraftCrashLibrary.APACHE_BULLET;
            case VTOL -> AircraftCrashLibrary.VTOL;
            case OVERWATCH -> AircraftCrashLibrary.ESCORT;
            default -> -1;
        };
        if (variant < 0)
            return false;
        if (variant == AircraftCrashLibrary.VTOL)
            unit.cancelVtolSuccessor();
        Vec3 start = unit.position(), goal = variant == AircraftCrashLibrary.GUNSHIP ? start
                : variant == AircraftCrashLibrary.VTOL || variant == AircraftCrashLibrary.ESCORT ? groundGoal(unit)
                        : findGoal(unit, variant);
        unit.crash = new State(start, goal, unit.flightYaw(), unit.flightPitch());
        if (variant == AircraftCrashLibrary.GUNSHIP) {
            Vec3 drift = start.subtract(unit.getPosition(0));
            unit.crash.drift = drift.lengthSqr() > .000001 && drift.lengthSqr() < 16 ? drift
                    : Vec3.directionFromRotation(unit.flightPitch(), unit.flightYaw())
                            .scale(Math.min(1.5, GunshipFlight.speed() / 20D));
        }
        unit.beginCrash(variant);
        unit.setHealth(1);
        unit.setNoGravity(true);
        unit.noPhysics = true;
        unit.setDeltaMovement(Vec3.ZERO);
        StrikeRadioReports.destroyed(unit);
        if (unit.radioStrike == null)
            NativeAudio.voice(unit.owner(), NativeAudio.reward(unit), "destroyed");
        boolean controlled = TacticalRuntime.controlled(unit);
        ServerPlayer owner = unit.owner();
        if (variant == AircraftCrashLibrary.GUNSHIP && controlled)
            TacticalRuntime.releaseDestroyedControl(unit, false);
        if (controlled && owner != null && owner.isAlive() && owner.serverLevel() == level) {
            int duration = variant == AircraftCrashLibrary.GUNSHIP ? AircraftCrashLibrary.GUNSHIP_OUTRO
                    : AircraftCrashLibrary.pilotEnd(variant);
            StreakNetwork.send(owner, new AircraftCrashPayload(level.dimension().location(), unit.getId(), variant,
                    start.x, start.y, start.z, unit.flightYaw(), unit.flightPitch(), duration));
        }
        if (variant == AircraftCrashLibrary.GUNSHIP)
            return true;
        if (variant == AircraftCrashLibrary.VTOL)
            sound(unit, "vtol_crash", .85F);
        else if (variant == AircraftCrashLibrary.ESCORT) {
            fx(unit, "72e5231b177e04a7", unit.position(), 3);
            sound(unit, "apache_heavy", .85F);
        } else {
            fx(unit, "5dccca5f51ffb014", unit.position(), 3);
            fx(unit, "68f6c2ceba8cba08", unit.position(), 2);
            sound(unit, "apache_heavy", .65F);
        }
        return true;
    }

    public static void tick(TacticalUnitEntity unit) {
        if (!(unit.level() instanceof ServerLevel level) || unit.crash == null) {
            unit.discard();
            return;
        }
        State state = unit.crash;
        state.ticks++;
        unit.age++;
        unit.crashClock(state.ticks);
        TacticalRuntime.keepLoaded(unit, unit.position());
        int variant = AircraftCrashLibrary.variant(unit.stage());
        if (state.finishedAt >= 0) {
            if (variant == AircraftCrashLibrary.GUNSHIP && state.ticks == AircraftCrashLibrary.GUNSHIP_SHAKE) {
                sound(unit, "gunship_shake", .7F);
                for (ServerPlayer player : level.players())
                    if (RemoteViewSubscriptions.origin(player).distanceToSqr(unit.position()) < 512 * 512)
                        StreakNetwork.send(player, new StreakImpactPayload(.25F));
            }
            if (variant == AircraftCrashLibrary.GUNSHIP ? state.ticks >= AircraftCrashLibrary.GUNSHIP_DELETE
                    : state.ticks - state.finishedAt >= 7)
                unit.discard();
            return;
        }
        if (variant == AircraftCrashLibrary.GUNSHIP) {
            if (state.ticks < AircraftCrashLibrary.GUNSHIP_AIRBURST && !state.impact) {
                if (!state.impact)
                    move(unit, unit.position().add(state.drift).add(0, -Math.min(.8, state.ticks * .004), 0));
                unit.flightOrientation(state.yaw, state.pitch + Math.min(18, state.ticks * .1F));
            } else {
                fx(unit, "66417eeab994f07a", unit.position(), 12);
                explosionFallback(unit);
                sound(unit, "gunship_air", 1);
                sound(unit, "gunship_low", .65F);
                sound(unit, "gunship_whine", .65F);
                state.finishedAt = state.ticks;
                unit.stage(200);
            }
            return;
        }
        int end = variant == AircraftCrashLibrary.VTOL ? 100
                : variant == AircraftCrashLibrary.ESCORT ? 60 : Math.min(100, AircraftCrashLibrary.pilotEnd(variant));
        Vec3 delta = state.goal.subtract(unit.position());
        double speed = variant == AircraftCrashLibrary.VTOL ? .65 : 1.05;
        boolean support = variant == AircraftCrashLibrary.VTOL || variant == AircraftCrashLibrary.ESCORT;
        if (support) {
            Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
            if (horizontal.lengthSqr() > .2)
                horizontal = horizontal.normalize().scale(.2);
            // 支援机种的水平位移、下降速度和加速度均减半，仍以真正碰撞作为爆炸条件。
            move(unit, unit.position().add(horizontal).add(0, -Math.min(1.75, .09 + state.ticks * .0225), 0));
            // 坠入虚空只清理实体，不在空中制造落地爆炸。
            if (unit.getY() <= level.getMinBuildHeight() + 2 && !state.impact) {
                unit.discard();
                return;
            }
        } else
            move(unit, unit.position().add(delta.lengthSqr() < speed * speed ? delta : delta.normalize().scale(speed)));
        float spin = variant == AircraftCrashLibrary.VTOL ? 3.75F : 7.5F;
        if (support)
            spin *= .5F;
        unit.flightOrientation(state.yaw + state.ticks * spin, Math.min(10, state.ticks * .5F));
        if (variant == AircraftCrashLibrary.VTOL && !state.canopy
                && unit.position().distanceToSqr(state.goal) <= state.start.distanceToSqr(state.goal) * .25) {
            state.canopy = true;
            unit.crashCanopy(true);
            fx(unit, "1ca4755f343f089c", unit.position(), 5);
        }
        if ((variant == AircraftCrashLibrary.APACHE_MISSILE && state.ticks == 1)
                || (variant == AircraftCrashLibrary.APACHE_BULLET && state.ticks == 2)) {
            fx(unit, variant == AircraftCrashLibrary.APACHE_MISSILE ? "6bdb07079ccc97da" : "3ccb9814ee9a58c2",
                    AircraftCrashLibrary.pilotTag(unit, "tag_origin", state.ticks), .6F);
            if (variant == AircraftCrashLibrary.APACHE_BULLET) {
                fx(unit, "0900d8d4ae6924ad", AircraftCrashLibrary.pilotTag(unit, "j_shoulder_ri", state.ticks), .5F);
                fx(unit, "288763a94107b6c0", AircraftCrashLibrary.pilotTag(unit, "j_clavicle_ri", state.ticks), .5F);
            }
        }
        if (variant == AircraftCrashLibrary.APACHE_BULLET && state.ticks == 7)
            fx(unit, "288763a94107b6c0", AircraftCrashLibrary.pilotTag(unit, "j_elbow_ri", state.ticks), .4F);
        if (state.ticks % 10 == 0)
            fx(unit, variant == AircraftCrashLibrary.VTOL ? "276e6fc4d011c5d8"
                    : variant == AircraftCrashLibrary.ESCORT ? "72e5231b177e04a7" : "68f6c2ceba8cba08", unit.position(),
                    2);
        if (state.impact || !support && (delta.lengthSqr() < 2.54 * 2.54 || state.ticks >= end))
            finish(unit);
    }

    private static void move(TacticalUnitEntity unit, Vec3 at) {
        ServerLevel level = (ServerLevel) unit.level();
        State state = unit.crash;
        Vec3 bounded = new Vec3(at.x,
                net.minecraft.util.Mth.clamp(at.y, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 4), at.z);
        TacticalRuntime.keepLoaded(unit, bounded);
        BlockHitResult hit = level.clip(
                new ClipContext(unit.position(), bounded, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, unit));
        if (hit.getType() != HitResult.Type.MISS) {
            bounded = hit.getLocation().add(0, .5, 0);
            state.impact = true;
            if (unit.kind() == TacticalUnitEntity.Kind.GUNSHIP)
                fx(unit, "478e1c7edb43ed90", bounded, 12);
        }
        Vec3 velocity = bounded.subtract(unit.position());
        unit.flight(bounded);
        unit.setDeltaMovement(velocity);
    }

    private static Vec3 groundGoal(TacticalUnitEntity unit) {
        Vec3 ahead = unit.position().add(Vec3.directionFromRotation(0, unit.flightYaw()).scale(32));
        TacticalRuntime.keepLoaded(unit, ahead);
        var hit = unit.level().clip(new ClipContext(ahead, new Vec3(ahead.x, unit.level().getMinBuildHeight(), ahead.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, unit));
        return hit.getType() == HitResult.Type.MISS ? new Vec3(ahead.x, unit.level().getMinBuildHeight(), ahead.z)
                : hit.getLocation();
    }

    private static Vec3 findGoal(TacticalUnitEntity unit, int variant) {
        double radius = variant == AircraftCrashLibrary.APACHE_MISSILE || variant == AircraftCrashLibrary.APACHE_BULLET
                ? 31.75
                : 63.5;
        Vec3 forward = Vec3.directionFromRotation(0, unit.flightYaw()), right = new Vec3(-forward.z, 0, forward.x);
        for (Vec3 direction : new Vec3[] { forward, forward.scale(-1), right, right.scale(-1),
                forward.add(right).scale(.707), forward.subtract(right).scale(.707),
                right.subtract(forward).scale(.707), forward.scale(-1).subtract(right).scale(.707) }) {
            Vec3 goal = unit.position().add(direction.scale(radius)).add(0, -25.4, 0);
            boolean loaded = true;
            for (int i = 0; i <= 8; i++)
                if (!unit.level().hasChunkAt(BlockPos.containing(unit.position().lerp(goal, i / 8D)))) {
                    loaded = false;
                    break;
                }
            if (!loaded)
                continue;
            if (unit.level().clip(
                    new ClipContext(unit.position(), goal, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, unit))
                    .getType() == HitResult.Type.MISS)
                return goal;
        }
        return unit.position();
    }

    private static void finish(TacticalUnitEntity unit) {
        State state = unit.crash;
        if (state.finishedAt >= 0)
            return;
        state.finishedAt = state.ticks;
        int variant = AircraftCrashLibrary.variant(unit.stage());
        boolean support = variant == AircraftCrashLibrary.VTOL || variant == AircraftCrashLibrary.ESCORT;
        fx(unit, variant == AircraftCrashLibrary.VTOL ? "13ffee139ea96474"
                : variant == AircraftCrashLibrary.ESCORT ? "19e82a440e9fe95d" : "1ffaacab51081351", unit.position(),
                support ? 14 : 8);
        explosionFallback(unit);
        if (variant == AircraftCrashLibrary.ESCORT)
            sound(unit, "escort_explode", 1.5F);
        if (variant == AircraftCrashLibrary.VTOL)
            sound(unit, "gunship_air", 1.4F);
        if (support) {
            sound(unit, "gunship_low", .85F);
            for (ServerPlayer player : ((ServerLevel) unit.level()).players()) {
                double distance = RemoteViewSubscriptions.origin(player).distanceTo(unit.position());
                if (player.isAlive() && distance < 160)
                    StreakNetwork.send(player, new StreakImpactPayload((float) (1.65 * (1 - distance / 160))));
            }
        }
        if (variant != AircraftCrashLibrary.VTOL && variant != AircraftCrashLibrary.ESCORT) {
            sound(unit, state.impact ? "apache_ground" : "apache_air", 1);
            fx(unit, "68f6c2ceba8cba08", unit.position(), 6);
        }
        TacticalRuntime.releaseDestroyedControl(unit, true);
        unit.stage(variant + 100);
    }

    private static void fx(TacticalUnitEntity unit, String id, Vec3 at, float size) {
        NativeFxDelivery.emit((ServerLevel) unit.level(), "crash_" + id, at, size);
    }

    private static void explosionFallback(TacticalUnitEntity unit) {
        ServerLevel level = (ServerLevel) unit.level();
        for (ServerPlayer player : level.players())
            if (player.isAlive() && RemoteViewSubscriptions.origin(player).distanceToSqr(unit.position()) < 512 * 512)
                level.sendParticles(player, ParticleTypes.EXPLOSION_EMITTER, true, unit.getX(), unit.getY(),
                        unit.getZ(), 1, 0, 0, 0, 0);
    }

    private static void sound(TacticalUnitEntity unit, String role, float gain) {
        var event = AircraftCrashLibrary.sound(role, unit.getRandom().nextInt());
        if (event == null)
            return;
        for (var player : ((ServerLevel) unit.level()).players())
            if (player.isAlive() && RemoteViewSubscriptions.origin(player).distanceToSqr(unit.position()) < 512 * 512)
                StreakNetwork.send(player, new SupportSoundPayload(unit.level().dimension().location(),
                        event.getLocation(), unit.getX(), unit.getY(), unit.getZ(), gain, 1, 512));
    }

    public static void save(TacticalUnitEntity unit, CompoundTag n) {
        State s = unit.crash;
        if (s == null)
            return;
        CompoundTag c = new CompoundTag();
        c.putInt("Format", 1);
        c.putInt("Ticks", s.ticks);
        c.putInt("FinishedAt", s.finishedAt);
        c.putBoolean("Impact", s.impact);
        c.putBoolean("Canopy", s.canopy);
        c.putFloat("Yaw", s.yaw);
        c.putFloat("Pitch", s.pitch);
        for (int i = 0; i < 3; i++) {
            c.putDouble("Start" + i, i == 0 ? s.start.x : i == 1 ? s.start.y : s.start.z);
            c.putDouble("Goal" + i, i == 0 ? s.goal.x : i == 1 ? s.goal.y : s.goal.z);
            c.putDouble("Drift" + i, i == 0 ? s.drift.x : i == 1 ? s.drift.y : s.drift.z);
        }
        n.put("AircraftCrash", c);
    }

    public static void load(TacticalUnitEntity unit, CompoundTag n) {
        if (!unit.crashing())
            return;
        if (!n.contains("AircraftCrash")) {
            unit.discard();
            return;
        }
        CompoundTag c = n.getCompound("AircraftCrash");
        State s = new State(new Vec3(c.getDouble("Start0"), c.getDouble("Start1"), c.getDouble("Start2")),
                new Vec3(c.getDouble("Goal0"), c.getDouble("Goal1"), c.getDouble("Goal2")), c.getFloat("Yaw"),
                c.getFloat("Pitch"));
        if (c.getInt("Format") != 1 || !Double.isFinite(s.start.lengthSqr()) || !Double.isFinite(s.goal.lengthSqr())
                || !Float.isFinite(s.yaw) || !Float.isFinite(s.pitch)) {
            unit.discard();
            return;
        }
        s.ticks = c.getInt("Ticks");
        s.finishedAt = c.getInt("FinishedAt");
        int variant = AircraftCrashLibrary.variant(unit.stage());
        int maxTicks = variant == AircraftCrashLibrary.GUNSHIP ? AircraftCrashLibrary.GUNSHIP_DELETE
                : variant == AircraftCrashLibrary.VTOL || variant == AircraftCrashLibrary.ESCORT
                        ? Math.max(260, (unit.level().getMaxBuildHeight() - unit.level().getMinBuildHeight()) * 2 + 160)
                        : 260;
        if (s.ticks < 0 || s.ticks > maxTicks || s.finishedAt < -1 || s.finishedAt > s.ticks) {
            unit.discard();
            return;
        }
        s.drift = new Vec3(c.getDouble("Drift0"), c.getDouble("Drift1"), c.getDouble("Drift2"));
        if (!Double.isFinite(s.drift.lengthSqr()) || s.drift.lengthSqr() > 16) {
            unit.discard();
            return;
        }
        s.impact = c.getBoolean("Impact");
        s.canopy = c.getBoolean("Canopy");
        unit.crash = s;
        unit.crashCanopy(s.canopy);
        unit.crashClock(s.ticks);
        unit.setNoGravity(true);
        unit.noPhysics = true;
        unit.setHealth(1);
    }
}
