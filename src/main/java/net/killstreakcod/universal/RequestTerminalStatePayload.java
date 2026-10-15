package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record RequestTerminalStatePayload(boolean openScreen) implements CustomPacketPayload {
    public static final Type<RequestTerminalStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "request_terminal_state"));
    public static final StreamCodec<ByteBuf, RequestTerminalStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RequestTerminalStatePayload::openScreen, RequestTerminalStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
