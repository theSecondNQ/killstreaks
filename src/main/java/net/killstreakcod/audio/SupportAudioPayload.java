package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * A complete same-dimension source snapshot, independent of entity tracking/render distance.
 * 同维度的完整声源快照，不依赖实体跟踪或渲染距离。
 */
public record SupportAudioPayload(ResourceLocation dimension, List<Source> sources) implements CustomPacketPayload {
    public record Source(int entityId, int kind, double x, double y, double z, float aimYaw, float speed,
            float flightYaw, float flightPitch, int clock, int stage, boolean departing, float vx, float vy, float vz) {
    }

    public static final Type<SupportAudioPayload> TYPE = new Type<>(SupportAudioPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "support_audio"));

    public SupportAudioPayload {
        sources = List.copyOf(sources);
        if (sources.size() > 128)
            throw new IllegalArgumentException("Too many audio sources");
    }

    public static final StreamCodec<FriendlyByteBuf, SupportAudioPayload> STREAM_CODEC = StreamCodec
            .of((buffer, value) -> {
                buffer.writeResourceLocation(value.dimension());
                buffer.writeVarInt(value.sources().size());
                for (Source s : value.sources()) {
                    buffer.writeVarInt(s.entityId());
                    buffer.writeVarInt(s.kind());
                    buffer.writeDouble(s.x());
                    buffer.writeDouble(s.y());
                    buffer.writeDouble(s.z());
                    buffer.writeFloat(s.aimYaw());
                    buffer.writeFloat(s.speed());
                    buffer.writeFloat(s.flightYaw());
                    buffer.writeFloat(s.flightPitch());
                    buffer.writeVarInt(s.clock());
                    buffer.writeVarInt(s.stage());
                    buffer.writeBoolean(s.departing());
                    buffer.writeFloat(s.vx());
                    buffer.writeFloat(s.vy());
                    buffer.writeFloat(s.vz());
                }
            }, buffer -> {
                ResourceLocation dimension = buffer.readResourceLocation();
                int count = buffer.readVarInt();
                if (count < 0 || count > 128)
                    throw new IllegalArgumentException("Invalid audio source count");
                List<Source> sources = new ArrayList<>(count);
                for (int i = 0; i < count; i++)
                    sources.add(new Source(buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
                return new SupportAudioPayload(dimension, sources);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
