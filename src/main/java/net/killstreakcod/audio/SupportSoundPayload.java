package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Camera-aware aircraft cannon audio; delivered once per listener.
 * 根据摄像机位置分发飞机机炮音效，每名听众只接收一次。
 */
public record SupportSoundPayload(ResourceLocation dimension, ResourceLocation sound, double x, double y, double z,
        float volume, float pitch, float radius) implements CustomPacketPayload {
    public static final Type<SupportSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "support_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SupportSoundPayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> {
                b.writeResourceLocation(p.dimension);
                b.writeResourceLocation(p.sound);
                b.writeDouble(p.x);
                b.writeDouble(p.y);
                b.writeDouble(p.z);
                b.writeFloat(p.volume);
                b.writeFloat(p.pitch);
                b.writeFloat(p.radius);
            }, b -> new SupportSoundPayload(b.readResourceLocation(), b.readResourceLocation(), b.readDouble(),
                    b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
