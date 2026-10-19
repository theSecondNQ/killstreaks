package net.killstreakcod.universal;

import net.killstreakcod.network.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.killstreakcod.network.StreakPackets;

/**
 * Mock players and players whose connection is closing have no negotiated payload channels.
 * 模拟玩家及连接正在关闭的玩家没有协商好的自定义消息通道。
 */
public final class StreakNetwork {
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        StreakPackets.sendToPlayer(player, payload);
    }

    private StreakNetwork() {}
}
