package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 只传按住状态与手别；射速、蓄转、伤害和是否持枪均由服务端判断。 */
public record HandheldFirePayload(boolean firing, boolean offhand) implements CustomPacketPayload {
    public static final Type<HandheldFirePayload> TYPE = new Type<>(HandheldFirePayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "handheld_fire"));
    public static final StreamCodec<FriendlyByteBuf, HandheldFirePayload> STREAM_CODEC = StreamCodec
            .of((buffer, payload) -> {
                buffer.writeBoolean(payload.firing());
                buffer.writeBoolean(payload.offhand());
            }, buffer -> new HandheldFirePayload(buffer.readBoolean(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
