package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record BonusStatePayload(String queue, boolean mgb) implements CustomPacketPayload {
    public static final Type<BonusStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "bonus"));
    public static final StreamCodec<ByteBuf, BonusStatePayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        ByteBufCodecs.STRING_UTF8.encode(b, p.queue);
        b.writeBoolean(p.mgb);
    }, b -> new BonusStatePayload(ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
