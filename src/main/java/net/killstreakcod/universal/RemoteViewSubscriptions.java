package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.SupportAudio;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;
import net.killstreakcod.mixin.RemoteChunkMapAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A temporary network watch origin. The physical player and its ordinary simulation tickets remain.
 * 临时使用遥控位置作为网络观察原点，保留玩家本体和常规模拟区块票据。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class RemoteViewSubscriptions {
    private static final TicketType<UUID> TICKET = TicketType.create("killstreakcod_remote_view", UUID::compareTo);
    private static final Map<UUID, Lease> LEASES = new HashMap<>();

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
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(
                    ((RemoteChunkMapAccess) (Object) map).killstreak$serverViewDistance()));
            ((RemoteChunkMapAccess) (Object) map).killstreak$updateChunkTracking(player);
            map.move(player);
        }
    }

    private static boolean connected(ServerPlayer player) {
        return player.connection != null && player.connection.isAcceptingMessages();
    }

    public static boolean active(ServerPlayer player) {
        return valid(LEASES.get(player.getUUID()), player);
    }

    public static Vec3 origin(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        return valid(lease, player) ? lease.camera.getEyePosition() : player.position();
    }

    public static ChunkPos chunkOrigin(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        Vec3 center = origin(player);
        if (valid(lease, player) && lease.camera.kind() == Kind.PRECISION)
            center = center.lerp(lease.camera.center, .5);
        return new ChunkPos(BlockPos.containing(center));
    }

    /**
     * Alternate send priority between both ends so a large watch does not queue the target behind its middle.
     * 交替优先发送飞机端与目标端，避免目标区块被整个观察范围的中间区块排在队尾。
     */
    public static ChunkPos chunkPriority(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        if (valid(lease, player) && lease.camera.kind() == Kind.PRECISION) {
            Vec3 point = (lease.level.getGameTime() & 1) == 0 ? lease.camera.getEyePosition() : lease.camera.center;
            return new ChunkPos(BlockPos.containing(point));
        }
        return chunkOrigin(player);
    }

    public static int viewDistance(ServerPlayer player, int normal) {
        Lease lease = LEASES.get(player.getUUID());
        if (!valid(lease, player))
            return normal;
        if (lease.camera.kind() == Kind.PRECISION)
            return lease.precisionRadius;
        int limit = switch (lease.camera.kind()) {
            case GUNSHIP, CRUISE, PRECISION -> 16;
            default -> 8;
        };
        return Math.min(limit, ((RemoteChunkMapAccess) (Object) player.serverLevel().getChunkSource().chunkMap)
                .killstreak$serverViewDistance());
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
        int radius = access.killstreak$getPlayerViewDistance(player), ticketRadius = radius + 2;
        if (initial || !center.equals(lease.center) || lease.radius != ticketRadius) {
            if (initial || lease.radius != ticketRadius)
                player.connection.send(new ClientboundSetChunkCacheRadiusPacket(radius));
            cache.addRegionTicket(TICKET, center, ticketRadius, player.getUUID(), true);
            if (lease.center != null)
                cache.removeRegionTicket(TICKET, lease.center, lease.radius, player.getUUID(), true);
            lease.center = center;
            lease.radius = ticketRadius;
            // Vanilla diffs the positioned watch views, queues chunk batches and sends cache-center packets.
            // 中文：由原版比较定位后的观察视图差异、排队区块批次，并发送缓存中心消息。
            access.killstreak$updateChunkTracking(player);
        }
        // A stationary physical player would otherwise not refresh tracked entities as the camera moves.
        // 中文：否则玩家本体静止时，摄像机移动不会刷新所跟踪的实体。
        if (initial || lease.level.getGameTime() % 2 == 0)
            map.move(player);
    }

    private static void release(Lease lease) {
        if (lease.center != null)
            lease.level.getChunkSource().removeRegionTicket(TICKET, lease.center, lease.radius, lease.player.getUUID(),
                    true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        for (Lease lease : List.copyOf(LEASES.values()))
            if (valid(lease, lease.player))
                refresh(lease, false);
            else
                end(lease.player);
        if (event.getServer().getTickCount() % 10 == 0)
            SupportAudio.sendSnapshots(event.getServer());
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
        SupportAudio.clear();
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        LEASES.clear();
        SupportAudio.clear();
    }

    private static final class Lease {
        final ServerPlayer player;
        final TacticalUnitEntity camera;
        final ServerLevel level;
        final int precisionRadius;
        ChunkPos center;
        int radius;

        Lease(ServerPlayer player, TacticalUnitEntity camera, ServerLevel level) {
            this.player = player;
            this.camera = camera;
            this.level = level;
            Vec3 span = camera.center.subtract(camera.position());
            // A fixed bounded watch covers both the approaching aircraft and its designated ground point.
            // 固定且有上限的观察范围同时覆盖入场飞机和目标点，避免飞行中反复缩放缓存。
            precisionRadius = (int) Math.clamp(Math.ceil(Math.max(Math.abs(span.x), Math.abs(span.z)) / 32) + 4, 16,
                    32);
        }
    }
}
