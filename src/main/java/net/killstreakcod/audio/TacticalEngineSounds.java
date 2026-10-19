package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.TacticalClient;
import net.killstreakcod.universal.AircraftCrashLibrary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/**
 * Complete server snapshots keep positional engine audio alive beyond entity/chunk tracking.
 * 完整的服务端快照使空间引擎音在实体或区块跟踪范围外仍能持续播放。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class TacticalEngineSounds {
    private static final Map<Integer, SourceState> SOURCES = new HashMap<>();
    private static ClientLevel previousLevel;
    private static int clock;

    private TacticalEngineSounds() {}

    /**
     * Called on the client main thread by the network handler. Each payload is a complete snapshot.
     * 由网络处理器在客户端主线程调用，每个消息都包含完整快照。
     */
    public static void accept(SupportAudioPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        acceptWorld(mc);
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(payload.dimension()))
            return;
        Set<Integer> present = new HashSet<>();
        TacticalUnitEntity.Kind[] kinds = TacticalUnitEntity.Kind.values();
        for (SupportAudioPayload.Source source : payload.sources()) {
            if (source.kind() < 0 || source.kind() >= kinds.length || !Double.isFinite(source.x())
                    || !Double.isFinite(source.y()) || !Double.isFinite(source.z()) || !Float.isFinite(source.aimYaw())
                    || !Float.isFinite(source.speed()))
                continue;
            if (AircraftCrashLibrary.stage(source.stage())) {
                SourceState dying = SOURCES.remove(source.entityId());
                if (dying != null)
                    dying.finish(mc);
                continue;
            }
            TacticalUnitEntity.Kind kind = kinds[source.kind()];
            SoundEvent event = SupportAudio.engine(kind);
            if (event == null)
                continue;
            present.add(source.entityId());
            SourceState state = SOURCES.get(source.entityId());
            if (state != null && state.kind != kind) {
                state.finish(mc);
                SOURCES.remove(source.entityId());
                state = null;
            }
            if (state == null) {
                state = new SourceState(source, kind);
                SOURCES.put(source.entityId(), state);
            } else
                state.update(source);
            state.lastSeen = clock;
            state.endingAt = -1;
        }
        for (SourceState state : SOURCES.values())
            if (!present.contains(state.id) && state.endingAt < 0)
                state.endingAt = clock;
    }

    private static void acceptWorld(Minecraft mc) {
        if (mc.level != previousLevel || mc.player == null) {
            for (SourceState state : SOURCES.values())
                state.finish(mc);
            SOURCES.clear();
            previousLevel = mc.level;
            clock = 0;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        acceptWorld(mc);
        if (mc.level == null || mc.player == null || mc.isPaused())
            return;
        clock++;

        // 巡航导弹循环音由 SceneEntryAudio 管理。这里只按服务端完整快照的相同规则启动受支持的空间引擎音。
        if (TacticalClient.remote() && mc.getCameraEntity() instanceof TacticalUnitEntity unit && unit.isAlive()
                && !unit.isRemoved()
                && (unit.kind() == TacticalUnitEntity.Kind.BOMB_DRONE || unit.kind() == TacticalUnitEntity.Kind.CRUISE)
                && SupportAudio.engine(unit.kind()) != null)
            SOURCES.computeIfAbsent(unit.getId(), id -> new SourceState(unit));
        SOURCES.values().removeIf(state -> {
            TacticalUnitEntity tracked = state.tracked();
            if (tracked != null && tracked.crashing()) {
                state.finish(mc);
                return true;
            }
            SoundEvent engine = SupportAudio.engine(state.kind);
            if (engine == null) {
                state.finish(mc);
                return true;
            }
            if (clock - state.lastSeen > 40 && state.endingAt < 0)
                state.endingAt = clock;
            if (state.endingAt >= 0 && clock - state.endingAt >= 20) {
                state.finish(mc);
                return true;
            }

            // 资源重载可能在声源仍存活时停止循环音；启动宽限结束后重新创建。
            if (state.loop != null && (state.loop.isStopped()
                    || (clock - state.loop.startedAt > 40 && !mc.getSoundManager().isActive(state.loop))))
                state.finish(mc);
            if (state.loop == null && state.endingAt < 0) {
                state.loop = new EngineLoop(state, engine);
                mc.getSoundManager().play(state.loop);
            }
            return false;
        });
    }

    private static final class SourceState {
        final int id;
        final TacticalUnitEntity.Kind kind;
        Vec3 from, to;
        float aimFrom, aimTo, speed;
        int sampledAt, lastSeen, endingAt = -1;
        EngineLoop loop;

        SourceState(SupportAudioPayload.Source source, TacticalUnitEntity.Kind kind) {
            id = source.entityId();
            this.kind = kind;
            from = to = new Vec3(source.x(), source.y(), source.z());
            aimFrom = aimTo = source.aimYaw();
            speed = source.speed();
            sampledAt = lastSeen = clock;
        }

        SourceState(TacticalUnitEntity unit) {
            id = unit.getId();
            kind = unit.kind();
            from = to = unit.position();
            aimFrom = aimTo = unit.aimYaw();
            speed = (float) unit.getDeltaMovement().length() * 20;
            sampledAt = lastSeen = clock;
        }

        void update(SupportAudioPayload.Source source) {
            from = position();
            aimFrom = aim();
            to = new Vec3(source.x(), source.y(), source.z());
            aimTo = source.aimYaw();
            speed = source.speed();
            sampledAt = clock;
        }

        float progress() {
            return Mth.clamp((clock - sampledAt) / 10F, 0, 1);
        }

        TacticalUnitEntity tracked() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null)
                return null;
            Entity entity = level.getEntity(id);
            return entity instanceof TacticalUnitEntity unit && unit.kind() == kind && unit.isAlive()
                    && !unit.isRemoved() ? unit : null;
        }

        Vec3 position() {
            TacticalUnitEntity unit = tracked();
            return unit != null ? unit.position() : from.lerp(to, progress());
        }

        float aim() {
            TacticalUnitEntity unit = tracked();
            return unit != null ? unit.aimYaw() : aimFrom + Mth.wrapDegrees(aimTo - aimFrom) * progress();
        }

        void finish(Minecraft mc) {
            if (loop != null) {
                loop.finish();
                mc.getSoundManager().stop(loop);
                loop = null;
            }
        }
    }

    private static final class EngineLoop extends AbstractTickableSoundInstance {
        private final SourceState state;
        final int startedAt;
        private float previousAim, mechanical;

        EngineLoop(SourceState state, SoundEvent sound) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.state = state;
            startedAt = clock;
            looping = true;
            delay = 0;
            pitch = 1;
            relative = false;
            attenuation = SoundInstance.Attenuation.NONE;
            mechanical = state.kind == TacticalUnitEntity.Kind.SENTRY ? 0
                    : state.kind == TacticalUnitEntity.Kind.BOMB_DRONE ? .65F : .55F;
            previousAim = state.aim();
            updateSound();
        }

        void finish() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean canPlaySound() {
            return !isStopped() && Minecraft.getInstance().level == previousLevel;
        }

        private void updateSound() {
            Minecraft mc = Minecraft.getInstance();
            Vec3 at = state.position();
            x = at.x;
            y = at.y;
            z = at.z;
            float aim = state.aim();
            if (state.kind == TacticalUnitEntity.Kind.SENTRY) {
                float motion = Math.abs(Mth.wrapDegrees(aim - previousAim));
                mechanical = Mth.lerp(.4F, mechanical, motion > .15F ? .24F : 0);
            } else if (state.kind == TacticalUnitEntity.Kind.WHEELSON) {
                float motion = Math.min(1, Math.max(0, state.speed) / 7);
                mechanical = Mth.lerp(.15F, mechanical, .22F + motion * .35F);
            }
            previousAim = aim;
            double radius = SupportAudio.radius(state.kind),
                    distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
            float gain = (float) Math.max(0, 1 - distance / radius);
            float ending = state.endingAt < 0 ? 1 : Mth.clamp(1 - (clock - state.endingAt) / 20F, 0, 1);

            // 原资源 JSON 的衰减距离为 24 至 128 格；通过手动增益将飞机可听范围扩到 512 格，而非全局满音量播放。
            volume = mechanical * gain * gain * ending;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != previousLevel || mc.player == null) {
                stop();
                return;
            }
            if (!mc.isPaused())
                updateSound();
        }
    }
}
