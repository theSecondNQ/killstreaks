package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record UseKillstreakPayload(int slot) implements CustomPacketPayload {
    public static final Type<UseKillstreakPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "use_killstreak"));
    public static final StreamCodec<ByteBuf, UseKillstreakPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UseKillstreakPayload::slot, UseKillstreakPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
