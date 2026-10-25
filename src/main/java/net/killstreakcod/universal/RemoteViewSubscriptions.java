package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.SupportAudio;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.GunshipMounts;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;
import net.killstreakcod.mixin.RemoteChunkMapAccess;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.apache.commons.lang3.mutable.MutableObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;

/**
 * A temporary network watch origin. The physical player and its ordinary simulation tickets remain.
 * 临时使用遥控位置作为网络观察原点，保留玩家本体和常规模拟区块票据。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class RemoteViewSubscriptions {
    private static final TicketType<UUID> TICKET = TicketType.create("killstreakcod_remote_view", UUID::compareTo);
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static final Map<UUID, Restore> RESTORING = new HashMap<>();
    private static final int CHUNKS_PER_TICK = 16;

    private RemoteViewSubscriptions() {}

    public static void begin(ServerPlayer player, TacticalUnitEntity camera) {
        if (!player.isAlive() || player.isRemoved() || !connected(player) || camera == null || !camera.isAlive()
                || camera.level() != player.level())
            return;
        Lease previous = LEASES.get(player.getUUID());
        if (previous != null && previous.player == player && previous.camera == camera && valid(previous, player)) {
            refresh(previous, false);
            return;
        }
        if (previous != null)
            end(previous.player);
        RESTORING.remove(player.getUUID());
        Lease lease = new Lease(player, camera, player.serverLevel());
        LEASES.put(player.getUUID(), lease);
        refresh(lease, true);
    }

    public static void end(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        if (lease == null || lease.player != player)
            return;
        LEASES.remove(player.getUUID(), lease);
        release(lease);
        restoreBodyView(player);
    }

    /**
     * Never re-register a discarded pre-respawn body in ChunkMap's player watch.
     * 不得把重生前已被丢弃的本体重新登记到 ChunkMap 的玩家观察列表。
     */
    public static void restoreBodyView(ServerPlayer player) {
        if (connected(player) && !player.isRemoved()
                && player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            var map = player.serverLevel().getChunkSource().chunkMap;
            int radius = Math.max(2, ((RemoteChunkMapAccess) (Object) map).killstreak$serverViewDistance() - 1);
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(radius));
            ChunkPos center = player.chunkPosition();
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
            RESTORING.put(player.getUUID(), new Restore(player, player.serverLevel(), chunks(center, radius)));
            map.move(player);
        }
    }

    private static boolean connected(ServerPlayer player) {
        return net.killstreakcod.network.StreakPackets.connected(player);
    }

    public static boolean active(ServerPlayer player) {
        return valid(LEASES.get(player.getUUID()), player);
    }

    /** 操作者的摄像机实体必须先于远处地形送达，其他实体仍按正常区块和距离规则跟踪。 */
    public static boolean isCamera(ServerPlayer player, Entity entity) {
        Lease lease = LEASES.get(player.getUUID());
        return valid(lease, player) && lease.camera == entity;
    }

    public static Vec3 origin(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        return valid(lease, player) ? lease.camera.kind() == Kind.GUNSHIP ? GunshipMounts.camera(lease.camera, 1)
                : lease.camera.getEyePosition() : player.position();
    }

    public static ChunkPos chunkOrigin(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        Vec3 center = origin(player);
        return new ChunkPos(BlockPos.containing(center));
    }

    public static int viewDistance(ServerPlayer player, int normal) {
        Lease lease = LEASES.get(player.getUUID());
        if (!valid(lease, player))
            return normal;
        if (lease.camera.kind() == Kind.GUNSHIP)
            return StreakServerConfig.gunshipViewDistance(player.serverLevel().getServer());
        int limit = switch (lease.camera.kind()) {
            case GUNSHIP, CRUISE -> 16;
            default -> 8;
        };
        return Math.min(limit, ((RemoteChunkMapAccess) (Object) player.serverLevel().getChunkSource().chunkMap)
                .killstreak$serverViewDistance() - 1);
    }

    private static boolean valid(Lease lease, ServerPlayer player) {
        return lease != null && lease.player == player && lease.level == player.level() && player.isAlive()
                && !player.isRemoved() && connected(player)
                && player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && lease.camera.isAlive() && !lease.camera.isRemoved() && lease.camera.level() == lease.level;
    }

    private static void refresh(Lease lease, boolean initial) {
        ServerPlayer player = lease.player;
        var cache = lease.level.getChunkSource();
        var map = cache.chunkMap;
        var access = (RemoteChunkMapAccess) (Object) map;
        ChunkPos center = chunkOrigin(player);
        int radius = viewDistance(player, Math.max(2, access.killstreak$serverViewDistance() - 1)),
                ticketRadius = radius + 2;
        if (initial || !center.equals(lease.center) || lease.radius != ticketRadius) {
            if (initial || lease.radius != ticketRadius)
                player.connection.send(new ClientboundSetChunkCacheRadiusPacket(radius));
            cache.addRegionTicket(TICKET, center, ticketRadius, player.getUUID(), true);
            if (lease.center != null)
                cache.removeRegionTicket(TICKET, lease.center, lease.radius, player.getUUID(), true);
            lease.center = center;
            lease.radius = ticketRadius;
            // Changing the client cache radius discards its previous storage.
            // 客户端调整缓存半径后会重建存储，需要重新发送本次观察范围。
            if (initial || lease.watchRadius != radius)
                lease.sent.clear();
        }
        updateWatch(player);
        // A stationary physical player would otherwise not refresh tracked entities as the camera moves.
        // 中文：否则玩家本体静止时，摄像机移动不会刷新所跟踪的实体。
        if (initial || lease.level.getGameTime() % 2 == 0)
            map.move(player);
    }

    private static List<ChunkPos> chunks(ChunkPos center, int radius) {
        List<ChunkPos> result = new ArrayList<>();
        for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++)
                if (net.minecraft.server.level.ChunkMap.isChunkInRange(center.x + x, center.z + z, center.x, center.z,
                        radius))
                    result.add(new ChunkPos(center.x + x, center.z + z));
        result.sort(Comparator.comparingInt(chunk -> {
            int dx = chunk.x - center.x, dz = chunk.z - center.z;
            return dx * dx + dz * dz;
        }));
        return result;
    }

    /** Keep the ordinary body watch and simulation tickets intact on Forge 1.20.1. */
    public static void updateWatch(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        if (!valid(lease, player))
            return;
        var cache = lease.level.getChunkSource();
        var access = (RemoteChunkMapAccess) (Object) cache.chunkMap;
        ChunkPos center = chunkOrigin(player);
        int radius = viewDistance(player, Math.max(2, access.killstreak$serverViewDistance() - 1));
        if (!center.equals(lease.watchCenter) || radius != lease.watchRadius) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
            lease.watchCenter = center;
            lease.watchRadius = radius;
            lease.pending = chunks(center, radius);

            Set<Long> desired = new HashSet<>();
            for (ChunkPos chunk : lease.pending)
                desired.add(chunk.toLong());
            lease.sent.removeIf(key -> {
                if (desired.contains(key))
                    return false;
                player.untrackChunk(new ChunkPos(key));
                return true;
            });
        }
        int budget = CHUNKS_PER_TICK;
        for (ChunkPos chunk : lease.pending) {
            if (lease.sent.contains(chunk.toLong()))
                continue;
            var loaded = cache.getChunkNow(chunk.x, chunk.z);
            if (loaded == null)
                continue;
            access.killstreak$sendChunk(player, new MutableObject<ClientboundLevelChunkWithLightPacket>(), loaded);
            lease.sent.add(chunk.toLong());
            if (--budget == 0)
                break;
        }
    }

    public static List<ServerPlayer> watchers(ServerLevel level, ChunkPos chunk, boolean boundaryOnly,
            List<ServerPlayer> ordinary) {
        List<ServerPlayer> result = null;
        for (Lease lease : LEASES.values()) {
            if (lease.level != level || !valid(lease, lease.player) || !lease.sent.contains(chunk.toLong())
                    || ordinary.contains(lease.player))
                continue;
            if (boundaryOnly && Math.max(Math.abs(chunk.x - lease.watchCenter.x),
                    Math.abs(chunk.z - lease.watchCenter.z)) < lease.watchRadius - 1)
                continue;
            if (result == null)
                result = new ArrayList<>(ordinary);
            result.add(lease.player);
        }
        return result == null ? ordinary : result;
    }

    private static void restoreChunks() {
        var entries = RESTORING.values().iterator();
        while (entries.hasNext()) {
            Restore restore = entries.next();
            ServerPlayer player = restore.player;
            if (!connected(player) || player.isRemoved() || player.serverLevel() != restore.level) {
                entries.remove();
                continue;
            }
            var cache = restore.level.getChunkSource();
            var access = (RemoteChunkMapAccess) (Object) cache.chunkMap;
            int budget = CHUNKS_PER_TICK;
            while (restore.cursor < restore.chunks.size() && budget-- > 0) {
                ChunkPos chunk = restore.chunks.get(restore.cursor++);
                var loaded = cache.getChunkNow(chunk.x, chunk.z);
                if (loaded != null)
                    access.killstreak$sendChunk(player, new MutableObject<ClientboundLevelChunkWithLightPacket>(),
                            loaded);
            }
            if (restore.cursor >= restore.chunks.size())
                entries.remove();
        }
    }

    private static void release(Lease lease) {
        if (lease.center != null)
            lease.level.getChunkSource().removeRegionTicket(TICKET, lease.center, lease.radius, lease.player.getUUID(),
                    true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        restoreChunks();
        for (Lease lease : List.copyOf(LEASES.values()))
            if (valid(lease, lease.player))
                refresh(lease, false);
            else
                end(lease.player);
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount() % 10 == 0)
            SupportAudio.sendSnapshots(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            end(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            end(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        for (Lease lease : List.copyOf(LEASES.values()))
            end(lease.player);
        LEASES.clear();
        RESTORING.clear();
        SupportAudio.clear();
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        LEASES.clear();
        RESTORING.clear();
        SupportAudio.clear();
    }

    private static final class Restore {
        final ServerPlayer player;
        final ServerLevel level;
        final List<ChunkPos> chunks;
        int cursor;

        Restore(ServerPlayer player, ServerLevel level, List<ChunkPos> chunks) {
            this.player = player;
            this.level = level;
            this.chunks = chunks;
        }
    }

    private static final class Lease {
        final ServerPlayer player;
        final TacticalUnitEntity camera;
        final ServerLevel level;
        ChunkPos center, watchCenter;
        int radius, watchRadius;
        List<ChunkPos> pending = List.of();
        final Set<Long> sent = new HashSet<>();

        Lease(ServerPlayer player, TacticalUnitEntity camera, ServerLevel level) {
            this.player = player;
            this.camera = camera;
            this.level = level;
        }
    }
}
