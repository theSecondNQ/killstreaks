package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.AircraftCrashLibrary;
import net.killstreakcod.universal.AircraftFlight;
import net.killstreakcod.universal.NativeArsenalLibrary;
import net.killstreakcod.universal.RemoteViewSubscriptions;
import net.killstreakcod.universal.StreakNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Snapshot-based engines and camera-aware delivery of positional one-shot sounds.
 * 根据快照管理引擎音，并按摄像机位置发送一次性空间音效。
 */
public final class SupportAudio {
    private record Sample(Vec3 at, long tick, Vec3 velocity) {
    }

    private static final Map<TacticalUnitEntity, Sample> PREVIOUS = new IdentityHashMap<>();

    private SupportAudio() {}

    public static void clear() {
        PREVIOUS.clear();
    }

    public static SoundEvent engine(TacticalUnitEntity.Kind kind) {
        if (kind == TacticalUnitEntity.Kind.UAV || kind == TacticalUnitEntity.Kind.COUNTER_UAV
                || kind == TacticalUnitEntity.Kind.ADVANCED_UAV || kind == TacticalUnitEntity.Kind.STEALTH
                || kind == TacticalUnitEntity.Kind.CRUISE)
            return null;
        SoundEvent nativeLoop = StreakSounds.engine(kind);
        if (nativeLoop != null)
            return nativeLoop;
        return switch (kind) {
            case PRECISION -> StreakSounds.AIRSTRIKE_APPROACH.get();
            case SAE -> null;
            case VTOL -> NativeAudio.sfx("vtol_engine", StreakSounds.JET.get());
            default -> null;
        };
    }

    public static double radius(TacticalUnitEntity.Kind kind) {
        return switch (kind) {
            case SENTRY -> 40;
            case WHEELSON -> 64;
            case BOMB_DRONE -> 96;
            default -> 512;
        };
    }

    public static void sendSnapshots(MinecraftServer server) {
        Set<TacticalUnitEntity> alive = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ServerLevel level : server.getAllLevels()) {
            List<TacticalUnitEntity> units = new ArrayList<>();
            Map<TacticalUnitEntity, Vec3> velocities = new IdentityHashMap<>();
            for (Entity entity : level.getAllEntities())
                if (entity instanceof TacticalUnitEntity unit && unit.isAlive() && !unit.isRemoved()
                        && unit.age >= unit.delay
                        && (engine(unit.kind()) != null || AircraftFlight.visible(unit.kind()))) {
                    units.add(unit);
                    alive.add(unit);
                    Vec3 velocity = velocity(unit, PREVIOUS.get(unit), level.getGameTime());
                    PREVIOUS.put(unit, new Sample(unit.position(), level.getGameTime(), velocity));
                    velocities.put(unit, velocity);
                }
            for (var player : level.players()) {
                Vec3 listener = RemoteViewSubscriptions.origin(player);
                var sources = units.stream()
                        .filter(unit -> player.isAlive()
                                && unit.position().distanceToSqr(listener) < Math.pow(radius(unit.kind()) + 64, 2))
                        .sorted(Comparator.comparingDouble(unit -> unit.position().distanceToSqr(listener))).limit(128)
                        .map(unit -> {
                            Vec3 v = velocities.get(unit);
                            return new SupportAudioPayload.Source(unit.getId(), unit.kind().ordinal(), unit.getX(),
                                    unit.getY(), unit.getZ(), unit.aimYaw(), (float) v.length() * 20, unit.flightYaw(),
                                    unit.flightPitch(), (int) unit.nativeClock(0), unit.stage(), unit.departing(),
                                    (float) v.x, (float) v.y, (float) v.z);
                        }).toList();
                StreakNetwork.send(player, new SupportAudioPayload(level.dimension().location(), sources));
            }
        }
        PREVIOUS.keySet().retainAll(alive);
    }

    private static Vec3 velocity(TacticalUnitEntity unit, Sample previous, long now) {
        return previous == null ? unit.getDeltaMovement()
                : now <= previous.tick ? previous.velocity
                        : unit.position().subtract(previous.at).scale(1D / (now - previous.tick));
    }

    /**
     * Complete, caller-only snapshot at entry so a distant scout need not wait up to 10 ticks.
     * 入场时立即向呼叫者发送完整快照，避免远处侦察机等待最多 10 刻才获得音频状态。
     */
    public static void sendSnapshot(ServerPlayer player) {
        if (!player.isAlive())
            return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 listener = RemoteViewSubscriptions.origin(player);
        List<TacticalUnitEntity> units = new ArrayList<>();
        Map<TacticalUnitEntity, Vec3> velocities = new IdentityHashMap<>();
        for (Entity entity : level.getAllEntities())
            if (entity instanceof TacticalUnitEntity unit && unit.isAlive() && !unit.isRemoved()
                    && unit.age >= unit.delay && (engine(unit.kind()) != null || AircraftFlight.visible(unit.kind()))) {
                Sample previous = PREVIOUS.get(unit);
                Vec3 v = velocity(unit, previous, now);
                units.add(unit);
                velocities.put(unit, v);

                // 为新生成的载具建立初始状态，不影响已有载具的常规采样。
                if (previous == null)
                    PREVIOUS.put(unit, new Sample(unit.position(), now, v));
            }
        var sources = units.stream()
                .filter(unit -> unit.position().distanceToSqr(listener) < Math.pow(radius(unit.kind()) + 64, 2))
                .sorted(Comparator.comparingDouble(unit -> unit.position().distanceToSqr(listener))).limit(128)
                .map(unit -> {
                    Vec3 v = velocities.get(unit);
                    return new SupportAudioPayload.Source(unit.getId(), unit.kind().ordinal(), unit.getX(), unit.getY(),
                            unit.getZ(), unit.aimYaw(), (float) v.length() * 20, unit.flightYaw(), unit.flightPitch(),
                            (int) unit.nativeClock(0), unit.stage(), unit.departing(), (float) v.x, (float) v.y,
                            (float) v.z);
                }).toList();
        StreakNetwork.send(player, new SupportAudioPayload(level.dimension().location(), sources));
    }

    public static boolean distantOneShot(net.minecraft.resources.ResourceLocation id) {
        return id.getNamespace().equals(KillstreakcodMod.MODID) && id.getPath().startsWith("native.aircraft25.")
                || NativeArsenalLibrary.positionalSound(id) || StreakSounds.mortarOneShot(id)
                || AircraftCrashLibrary.positionalSound(id)
                || id.equals(StreakSounds.AIRSTRIKE_FIRE.get().getLocation())
                || id.equals(StreakSounds.STEALTH_APPROACH.get().getLocation())
                || id.equals(StreakSounds.STEALTH_FLYBY.get().getLocation())
                || id.equals(StreakSounds.STEALTH_LEAVE.get().getLocation());
    }

    public static void broadcast(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        if (distantOneShot(sound.getLocation())) {
            float range = NativeArsenalLibrary.positionalSound(sound.getLocation())
                    ? (sound.getLocation().getPath().contains("m134") ? 96 : 512)
                    : StreakSounds.mortarOneShot(sound.getLocation()) ? 256
                            : sound == StreakSounds.AIRSTRIKE_FIRE.get() ? 640 : 512;
            for (var receiver : level.players())
                if (RemoteViewSubscriptions.origin(receiver).distanceToSqr(at) < range * range)
                    StreakNetwork.send(receiver, new SupportSoundPayload(level.dimension().location(),
                            sound.getLocation(), at.x, at.y, at.z, volume, pitch, range));
            return;
        }
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
        double vanillaRange = sound.getRange(volume), extraRange = 512;
        var packet = new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                at.x, at.y, at.z, volume, pitch, level.random.nextLong());
        for (var receiver : level.players()) {
            if (!RemoteViewSubscriptions.active(receiver))
                continue;

            // 原版已向这些接收者发送声音，重复发送会让枪声或爆炸声叠加两次。
            if (receiver.position().distanceToSqr(at) < vanillaRange * vanillaRange)
                continue;
            if (RemoteViewSubscriptions.origin(receiver).distanceToSqr(at) < extraRange * extraRange)
                receiver.connection.send(packet);
        }
    }
}
