package net.killstreakcod.network;

import java.util.function.BiConsumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;

/** Client handler installation does not change the common message order. */
public final class PayloadRegistrar {
    private final boolean clientHandlers;

    public PayloadRegistrar(boolean clientHandlers) {
        this.clientHandlers = clientHandlers;
    }

    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, NetworkContext> handler) {
        StreakPackets.register(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, NetworkContext> handler) {
        if (clientHandlers)
            StreakPackets.clientHandler(type, handler);
        else
            StreakPackets.register(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }
}
