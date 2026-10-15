package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NativeVoiceFinishedPayload(UUID gate) implements CustomPacketPayload {
    public static final Type<NativeVoiceFinishedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "native_voice_finished"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NativeVoiceFinishedPayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> b.writeUUID(p.gate), b -> new NativeVoiceFinishedPayload(b.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
