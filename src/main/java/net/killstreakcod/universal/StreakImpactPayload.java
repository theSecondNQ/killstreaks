package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * A short, distance attenuated camera impulse, sent only by the server.
 * 仅由服务端发送短暂且随距离衰减的摄像机震动冲量。
 */
public record StreakImpactPayload(float strength) implements CustomPacketPayload {
    public static final Type<StreakImpactPayload> TYPE = new Type<>(StreakImpactPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "impact"));
    public static final StreamCodec<FriendlyByteBuf, StreakImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeFloat(payload.strength),
            buffer -> new StreakImpactPayload(buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
