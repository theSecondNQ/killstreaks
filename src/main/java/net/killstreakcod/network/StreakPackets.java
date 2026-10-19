package net.killstreakcod.network;

import net.killstreakcod.KillstreakcodMod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class StreakPackets {
    private static final String PROTOCOL = "forge-1.20.1/" + KillstreakcodMod.NETWORK_VERSION;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<ResourceLocation, BiConsumer<?, NetworkContext>> CLIENT_HANDLERS = new HashMap<>();
    private static int nextId;

    private StreakPackets() {}

    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
            StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, NetworkContext> handler, NetworkDirection direction) {
        CHANNEL.messageBuilder(type.messageClass(), nextId++, direction)
                .encoder((payload, buffer) -> codec.encode(buffer, payload)).decoder(codec::decode)
                .consumerNetworkThread((payload, suppliedContext) -> {
                    var context = suppliedContext.get();
                    BiConsumer<T, NetworkContext> target = handler;
                    if (direction == NetworkDirection.PLAY_TO_CLIENT) {
                        @SuppressWarnings("unchecked")
                        var clientHandler = (BiConsumer<T, NetworkContext>) CLIENT_HANDLERS.get(type.id());
                        target = clientHandler;
                    }
                    if (target != null)
                        target.accept(payload, new NetworkContext(context));
                    context.setPacketHandled(true);
                }).add();
    }

    static <T extends CustomPacketPayload> void clientHandler(CustomPacketPayload.Type<T> type,
            BiConsumer<T, NetworkContext> handler) {
        CLIENT_HANDLERS.put(type.id(), handler);
    }

    public static boolean connected(ServerPlayer player) {
        return !(player instanceof net.minecraftforge.common.util.FakePlayer)
                && player.connection != null && player.connection.isAcceptingMessages()
                && player.connection.connection.channel() != null
                && CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (connected(player))
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        CHANNEL.sendToServer(payload);
    }
}
