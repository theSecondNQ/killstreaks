package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NativeVoicePayload(ResourceLocation dimension, int listenerId, String reward, String phase,
        java.util.UUID gate, int delay, String faction) implements CustomPacketPayload {
    public static final java.util.UUID NONE = new java.util.UUID(0, 0);

    public NativeVoicePayload(ResourceLocation dimension, int listenerId, String reward, String phase) {
        this(dimension, listenerId, reward, phase, NONE, 0, VoiceFaction.SPECGRU);
    }

    public static final Type<NativeVoicePayload> TYPE = new Type<>(NativeVoicePayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "native_voice"));
    public static final StreamCodec<FriendlyByteBuf, NativeVoicePayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> {
                b.writeResourceLocation(p.dimension);
                b.writeVarInt(p.listenerId);
                b.writeUtf(p.reward, 48);
                b.writeUtf(p.phase, 24);
                b.writeUUID(p.gate);
                b.writeVarInt(p.delay);
                b.writeUtf(p.faction, 16);
            }, b -> new NativeVoicePayload(b.readResourceLocation(), b.readVarInt(), b.readUtf(48), b.readUtf(24),
                    b.readUUID(), b.readVarInt(), b.readUtf(16)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
