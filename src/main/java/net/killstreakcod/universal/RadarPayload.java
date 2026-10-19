package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record RadarPayload(boolean active, boolean advanced, boolean jammed, int[] contacts)
        implements CustomPacketPayload {
    public static final Type<RadarPayload> TYPE = new Type<>(RadarPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "radar"));
    public static final StreamCodec<FriendlyByteBuf, RadarPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeBoolean(p.active);
                b.writeBoolean(p.advanced);
                b.writeBoolean(p.jammed);
                TacticalMapPayload.writeArray(b, p.contacts);
            },
            b -> new RadarPayload(b.readBoolean(), b.readBoolean(), b.readBoolean(),
                    TacticalMapPayload.readArray(b, 512)));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
