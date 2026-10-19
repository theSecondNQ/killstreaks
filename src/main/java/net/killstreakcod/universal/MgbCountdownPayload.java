package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Same-dimension authoritative MGB timeline, refreshed twice a second and on player lifecycle.
 * 在同维度同步服务端确认的核弹时间线，每秒刷新两次，并在玩家生命周期变化时刷新。
 */
public record MgbCountdownPayload(ResourceLocation dimension, UUID strike, long gameTime, int remaining,
        double x, double y, double z, String caller) implements CustomPacketPayload {
    public static final Type<MgbCountdownPayload> TYPE = new Type<>(MgbCountdownPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mgb_countdown"));
    public static final StreamCodec<FriendlyByteBuf, MgbCountdownPayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> {
                b.writeResourceLocation(p.dimension);
                b.writeUUID(p.strike);
                b.writeLong(p.gameTime);
                b.writeVarInt(p.remaining);
                b.writeDouble(p.x);
                b.writeDouble(p.y);
                b.writeDouble(p.z);
                b.writeUtf(p.caller, 64);
            }, b -> new MgbCountdownPayload(b.readResourceLocation(), b.readUUID(), b.readLong(), b.readVarInt(),
                    b.readDouble(), b.readDouble(), b.readDouble(), b.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
