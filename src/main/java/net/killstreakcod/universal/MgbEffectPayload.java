package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record MgbEffectPayload(ResourceLocation dimension, UUID strike, int age, double x, double y, double z)
        implements CustomPacketPayload {
    public static final Type<MgbEffectPayload> TYPE = new Type<>(MgbEffectPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "nuclear_effect"));
    public static final StreamCodec<FriendlyByteBuf, MgbEffectPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeResourceLocation(p.dimension);
                b.writeUUID(p.strike);
                b.writeVarInt(p.age);
                b.writeDouble(p.x);
                b.writeDouble(p.y);
                b.writeDouble(p.z);
            },
            b -> new MgbEffectPayload(b.readResourceLocation(), b.readUUID(), b.readVarInt(), b.readDouble(),
                    b.readDouble(), b.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
