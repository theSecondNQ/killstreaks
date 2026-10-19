package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Camera-aware aircraft cannon audio; delivered once per listener.
 * 根据摄像机位置分发飞机机炮音效，每名听众只接收一次。
 */
public record SupportSoundPayload(ResourceLocation dimension, ResourceLocation sound, double x, double y, double z,
        float volume, float pitch, float radius) implements CustomPacketPayload {
    public static final Type<SupportSoundPayload> TYPE = new Type<>(SupportSoundPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "support_sound"));
    public static final StreamCodec<FriendlyByteBuf, SupportSoundPayload> STREAM_CODEC = StreamCodec
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
