package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.universal.NativeFxDelivery;
import net.killstreakcod.universal.RemoteViewSubscriptions;
import net.killstreakcod.universal.StreakImpactPayload;
import net.killstreakcod.universal.StreakNetwork;
import net.killstreakcod.universal.TacticalRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * Budgeted cosmetic stages for SAE, independent of damage, collision and target selection.
 * SAE 的视觉阶段受特效预算限制，与伤害、碰撞和目标选择分别处理。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class SaeEffects {
    private static final int MAX_PACKETS = 48, MAX_PARTICLES = 160, MAX_PENDING = 1024;
    private static final double NORMAL_RANGE_SQR = 192D * 192, REMOTE_RANGE_SQR = 512D * 512;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, .48F, .055F), 1.25F);
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(.44F, .36F, .27F), 1.5F);
    private static final Map<ServerLevel, WorldEffects> WORLDS = new IdentityHashMap<>();

    private SaeEffects() {}

    /**
     * Called once when the mother casing opens. Does not create or move bomblets.
     * 母弹外壳打开时调用一次，不在这里创建或移动子弹药。
     */
    public static void release(ServerLevel level, Entity emitter, Vec3 at) {
        if (!valid(level, at))
            return;
        long seed = seed(emitter), now = level.getGameTime();
        WorldEffects state = state(level);
        queue(state, new Pulse(Phase.RELEASE_CORE, now, at, Vec3.ZERO, seed, 1));
        queue(state, new Pulse(Phase.RELEASE_RING, now + 2, at, Vec3.ZERO, seed, 2.4F));
        queue(state, new Pulse(Phase.RELEASE_SMOKE, now + 6, at, Vec3.ZERO, seed, 1));
    }

    /**
     * A staggered short fire tail, rather than an emitter or a continuous ribbon.
     * 生成错时出现的短火焰尾迹，而非发射器或连续带状尾迹。
     */
    public static void trail(ServerLevel level, Entity emitter, Vec3 from, Vec3 to, int age) {
        if (!valid(level, from) || !valid(level, to) || from.distanceToSqr(to) < .00001)
            return;
        long seed = seed(emitter);
        boolean parent = emitter instanceof TacticalUnitEntity unit && unit.cargo.equals("sae_parent");
        if (Math.floorMod(age + (int) (seed & 0x7fffffff), parent ? 2 : 3) != 0)
            return;
        Vec3 backwards = from.subtract(to).normalize();
        Vec3 at = from.lerp(to, .45).add(backwards.scale(parent ? .45 : .2));
        queue(state(level),
                new Pulse(Phase.TRAIL, level.getGameTime(), at, backwards.scale(.035), seed, parent ? 2 : 1));
    }

    /**
     * Cosmetic impact only; actual radius/damage come from the caller's SAE parameters.
     * 这里只处理命中的视觉效果，实际爆炸范围和伤害由调用方的 SAE 参数决定。
     */
    public static void impact(ServerLevel level, Entity emitter, Vec3 at, float radius) {
        if (!valid(level, at) || !Float.isFinite(radius))
            return;
        long seed = seed(emitter), now = level.getGameTime();
        WorldEffects state = state(level);

        // 三个目标点可能同时产生 57 次命中，视觉效果最多分摊到 0.2 秒内播放。
        long start = now + Math.floorMod(emitter == null ? (int) seed : emitter.getId(), 5);
        float size = Mth.clamp(radius, .5F, 12F);
        queue(state, new Pulse(Phase.IMPACT_FIRE, start, at, Vec3.ZERO, seed, size));
        queue(state, new Pulse(Phase.IMPACT_DUST, start + 2, at, Vec3.ZERO, seed, size));
        queue(state, new Pulse(Phase.IMPACT_SMOKE, start + 6, at, Vec3.ZERO, seed, size));

        // 每组相近的爆炸只播放一次原资源 SAE 音效，避免 57 段录音叠播。
        state.audio.removeIf(sound -> sound.expires <= now);
        if (state.audio.stream().noneMatch(sound -> sound.at.distanceToSqr(at) < 16D * 16)) {
            if (state.audio.size() >= 32)
                state.audio.remove(0);
            state.audio.add(new SoundStamp(at, now + 10));
            TacticalRuntime.sound(level, at, NativeAudio.sfx("sae21_impact", StreakSounds.SAE_BLAST.get()), 1.05F,
                    .96F + (float) sample(seed + 9) * .08F);
            for (ServerPlayer observer : level.players()) {
                double distance = RemoteViewSubscriptions.origin(observer).distanceTo(at);
                if (distance < 64)
                    StreakNetwork.send(observer,
                            new StreakImpactPayload((float) (Math.min(1.6, size * .3) * (1 - distance / 64))));
            }
        }
    }

    private static boolean valid(ServerLevel level, Vec3 at) {
        return level != null && at != null && Double.isFinite(at.x) && Double.isFinite(at.y) && Double.isFinite(at.z);
    }

    private static long seed(Entity entity) {
        return entity == null ? 0x56ee31L : (long) entity.getId() * 0x9e3779b97f4a7c15L;
    }

    private static WorldEffects state(ServerLevel level) {
        return WORLDS.computeIfAbsent(level, key -> new WorldEffects());
    }

    private static void queue(WorldEffects state, Pulse pulse) {
        if (state.pending.size() >= MAX_PENDING) {
            if (pulse.phase.priority >= 3)
                return;
            int replace = -1;
            for (int i = 0; i < state.pending.size(); i++)
                if (state.pending.get(i).phase.priority >= 3) {
                    replace = i;
                    break;
                }
            if (replace < 0)
                return;
            state.pending.remove(replace);
        }
        state.pending.add(pulse);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        for (var worlds = WORLDS.entrySet().iterator(); worlds.hasNext();) {
            var entry = worlds.next();
            ServerLevel level = entry.getKey();
            WorldEffects state = entry.getValue();
            long now = level.getGameTime();
            state.beginTick(now);
            state.audio.removeIf(sound -> sound.expires <= now);
            if (state.pending.isEmpty() && state.audio.isEmpty()) {
                worlds.remove();
                continue;
            }
            List<Pulse> due = new ArrayList<>();
            for (var iterator = state.pending.iterator(); iterator.hasNext();) {
                Pulse pulse = iterator.next();
                if (pulse.due > now)
                    continue;
                iterator.remove();
                if (now - pulse.due <= 20)
                    due.add(pulse);
            }

            // 特效预算不足时，优先保留核心火焰和闪光，再考虑装饰性尘土、烟雾及尾迹。
            due.sort(Comparator.comparingInt((Pulse pulse) -> pulse.phase.priority)
                    .thenComparingLong(pulse -> pulse.due));
            for (Pulse pulse : due)
                draw(new Audience(level, state, pulse.at), pulse);
            if (state.pending.isEmpty() && state.audio.isEmpty())
                worlds.remove();
        }
    }

    private static void draw(Audience audience, Pulse pulse) {
        Vec3 at = pulse.at;
        float radius = pulse.radius;
        switch (pulse.phase) {
            case RELEASE_CORE -> {
                audience.send(ParticleTypes.FLASH, at, 1, 0, 0, 0, 0);
                audience.send(ParticleTypes.EXPLOSION, at, 1, 0, 0, 0, 0);
                ring(audience, at, 1.05, 8, pulse.seed, .2, .045);
                audience.send(GOLD, at, 10, .45, .18, .45, .035);
                audience.send(ParticleTypes.POOF, at, 6, .25, .12, .25, .04);
            }
            case RELEASE_RING -> {
                ring(audience, at, 2.4, 12, pulse.seed, .26, .055);
                audience.send(ParticleTypes.ELECTRIC_SPARK, at, 4, .7, .2, .7, .1);
                audience.send(ParticleTypes.SMOKE, at, 6, .45, .15, .45, .015);
            }
            case RELEASE_SMOKE -> {
                audience.send(ParticleTypes.SMOKE, at.add(0, .2, 0), 8, .7, .25, .7, .02);
                audience.send(GOLD, at, 6, .8, .15, .8, .01);
            }
            case IMPACT_FIRE -> {
                NativeFxDelivery.emit(audience.level, "sae21_impact", at, Math.min(3, radius * .5F));
                Vec3 core = at.add(0, .22, 0);
                audience.send(ParticleTypes.EXPLOSION, core, 2, radius * .14, .2, radius * .14, 0);
                audience.send(ParticleTypes.FLAME, core, 6, radius * .18, .35, radius * .18, .075);
                if ((pulse.seed & 3) == 0)
                    audience.send(ParticleTypes.FLASH, core, 1, 0, 0, 0, 0);
                audience.send(GOLD, core, 5, radius * .17, .3, radius * .17, .045);
            }
            case IMPACT_DUST -> {
                Vec3 ground = at.add(0, .18, 0);
                audience.send(ParticleTypes.POOF, ground, 5, radius * .38, .18, radius * .38, .055);
                audience.send(DUST, ground, 6, radius * .45, .14, radius * .45, .035);
            }
            case IMPACT_SMOKE -> {
                audience.send(ParticleTypes.LARGE_SMOKE, at.add(0, .6, 0), 3, radius * .17, .25, radius * .17, .025);
                audience.send(ParticleTypes.SMOKE, at.add(0, 1, 0), 4, radius * .2, .4, radius * .2, .02);
            }
            case TRAIL -> {
                audience.send(ParticleTypes.FLAME, at, 0, pulse.velocity.x, pulse.velocity.y, pulse.velocity.z, 1);
                if (radius > 1 && ((pulse.seed + (long) audience.level.getGameTime()) & 3) == 0)
                    audience.send(ParticleTypes.SMOKE, at, 1, .04, .04, .04, .008);
            }
        }
    }

    private static void ring(Audience audience, Vec3 center, double radius, int pieces, long seed, double speed,
            double rise) {
        double offset = sample(seed) * Math.PI * 2;
        for (int i = 0; i < pieces; i++) {
            double angle = offset + i * Math.PI * 2 / pieces, x = Math.cos(angle), z = Math.sin(angle);

            // 粒子数量设为零表示按指定速度发射一个粒子，而非按高斯分布散布一群碎片。
            audience.send(ParticleTypes.FLAME, center.add(x * radius, 0, z * radius), 0, x * speed, rise, z * speed, 1);
        }
    }

    private static double sample(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level)
            WORLDS.remove(level);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        WORLDS.clear();
    }

    private enum Phase {
        RELEASE_CORE(0), IMPACT_FIRE(0), RELEASE_RING(1), IMPACT_DUST(2), RELEASE_SMOKE(3), IMPACT_SMOKE(3), TRAIL(4);

        final int priority;

        Phase(int priority) {
            this.priority = priority;
        }
    }

    private record Pulse(Phase phase, long due, Vec3 at, Vec3 velocity, long seed, float radius) {
    }

    private record SoundStamp(Vec3 at, long expires) {
    }

    private static final class Budget {
        int packets, particles;
    }

    private static final class WorldEffects {
        final List<Pulse> pending = new ArrayList<>();
        final List<SoundStamp> audio = new ArrayList<>();
        final Map<UUID, Budget> budgets = new HashMap<>();
        long tick = Long.MIN_VALUE;

        void beginTick(long now) {
            if (now != tick) {
                tick = now;
                budgets.clear();
            }
        }
    }

    private static final class Audience {
        final ServerLevel level;
        final WorldEffects state;
        final List<ServerPlayer> players = new ArrayList<>();

        Audience(ServerLevel level, WorldEffects state, Vec3 at) {
            this.level = level;
            this.state = state;
            for (ServerPlayer player : level.players()) {
                boolean remote = RemoteViewSubscriptions.active(player);
                Vec3 eye = remote ? RemoteViewSubscriptions.origin(player) : player.position();
                if (eye.distanceToSqr(at) < (remote ? REMOTE_RANGE_SQR : NORMAL_RANGE_SQR))
                    players.add(player);
            }
        }

        <T extends ParticleOptions> void send(T type, Vec3 at, int count, double dx, double dy, double dz,
                double speed) {
            int cost = Math.max(1, count);
            for (ServerPlayer player : players) {
                Budget budget = state.budgets.computeIfAbsent(player.getUUID(), key -> new Budget());
                if (budget.packets >= MAX_PACKETS || budget.particles + cost > MAX_PARTICLES)
                    continue;
                if (level.sendParticles(player, type, true, at.x, at.y, at.z, count, dx, dy, dz, speed)) {
                    budget.packets++;
                    budget.particles += cost;
                }
            }
        }
    }
}
