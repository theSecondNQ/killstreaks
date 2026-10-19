package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record UseKillstreakPayload(int slot) implements CustomPacketPayload {
    public static final Type<UseKillstreakPayload> TYPE = new Type<>(UseKillstreakPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "use_killstreak"));
    public static final StreamCodec<FriendlyByteBuf, UseKillstreakPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UseKillstreakPayload::slot, UseKillstreakPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
