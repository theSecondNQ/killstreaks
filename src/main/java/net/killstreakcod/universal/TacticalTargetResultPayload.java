package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TacticalTargetResultPayload(boolean accepted) implements CustomPacketPayload {
    public static final Type<TacticalTargetResultPayload> TYPE = new Type<>(TacticalTargetResultPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "target_result"));
    public static final StreamCodec<FriendlyByteBuf, TacticalTargetResultPayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> b.writeBoolean(p.accepted), b -> new TacticalTargetResultPayload(b.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
