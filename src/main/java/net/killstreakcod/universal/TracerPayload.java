package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One visual shot, with a double-precision origin and compact relative endpoint.
 * 描述一次视觉射击，使用双精度起点和紧凑的相对终点。
 */
public record TracerPayload(ResourceLocation dimension, int sourceId, double x, double y, double z, float dx, float dy,
        float dz, int profile) implements CustomPacketPayload {
    public static final Type<TracerPayload> TYPE = new Type<>(TracerPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "tracer"));
    public static final StreamCodec<FriendlyByteBuf, TracerPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.dimension.toString());
                buffer.writeInt(payload.sourceId);
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
                buffer.writeFloat(payload.dx);
                buffer.writeFloat(payload.dy);
                buffer.writeFloat(payload.dz);
                buffer.writeByte(payload.profile);
            },
            buffer -> new TracerPayload(ResourceLocation.parse(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                    buffer.readInt(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readUnsignedByte()));

    public Vec3 from() {
        return new Vec3(x, y, z);
    }

    public Vec3 to() {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
