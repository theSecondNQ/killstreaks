package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NativeVoiceFinishedPayload(UUID gate) implements CustomPacketPayload {
    public static final Type<NativeVoiceFinishedPayload> TYPE = new Type<>(NativeVoiceFinishedPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "native_voice_finished"));
    public static final StreamCodec<FriendlyByteBuf, NativeVoiceFinishedPayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> b.writeUUID(p.gate), b -> new NativeVoiceFinishedPayload(b.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
