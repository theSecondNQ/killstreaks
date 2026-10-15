package net.killstreakcod.universal;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Mock players and players whose connection is closing have no negotiated payload channels.
 * 模拟玩家及连接正在关闭的玩家没有协商好的自定义消息通道。
 */
public final class StreakNetwork {
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.isAcceptingMessages()
                && NetworkRegistry.hasChannel(player.connection, payload.type().id()))
            PacketDistributor.sendToPlayer(player, payload);
    }

    private StreakNetwork() {}
}
