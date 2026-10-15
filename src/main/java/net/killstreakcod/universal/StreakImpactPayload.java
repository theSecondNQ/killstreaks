package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * A short, distance attenuated camera impulse, sent only by the server.
 * 仅由服务端发送短暂且随距离衰减的摄像机震动冲量。
 */
public record StreakImpactPayload(float strength) implements CustomPacketPayload {
    public static final Type<StreakImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "impact"));
    public static final StreamCodec<ByteBuf, StreakImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeFloat(payload.strength),
            buffer -> new StreakImpactPayload(buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
