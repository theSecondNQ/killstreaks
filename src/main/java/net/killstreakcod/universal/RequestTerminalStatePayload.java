package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record RequestTerminalStatePayload(boolean openScreen) implements CustomPacketPayload {
    public static final Type<RequestTerminalStatePayload> TYPE = new Type<>(RequestTerminalStatePayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "request_terminal_state"));
    public static final StreamCodec<FriendlyByteBuf, RequestTerminalStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RequestTerminalStatePayload::openScreen, RequestTerminalStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
