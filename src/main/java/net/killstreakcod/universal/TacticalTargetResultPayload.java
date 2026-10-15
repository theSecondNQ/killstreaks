package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TacticalTargetResultPayload(boolean accepted) implements CustomPacketPayload {
    public static final Type<TacticalTargetResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "target_result"));
    public static final StreamCodec<ByteBuf, TacticalTargetResultPayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> b.writeBoolean(p.accepted), b -> new TacticalTargetResultPayload(b.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
