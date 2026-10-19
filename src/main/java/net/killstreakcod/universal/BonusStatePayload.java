package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record BonusStatePayload(String queue, boolean mgb) implements CustomPacketPayload {
    public static final Type<BonusStatePayload> TYPE = new Type<>(BonusStatePayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "bonus"));
    public static final StreamCodec<FriendlyByteBuf, BonusStatePayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        ByteBufCodecs.STRING_UTF8.encode(b, p.queue);
        b.writeBoolean(p.mgb);
    }, b -> new BonusStatePayload(ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
