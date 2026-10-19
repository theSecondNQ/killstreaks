package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * A destruction-only movie anchor; independent of the restored player body or entity tracking.
 * 仅用于击毁过场的锚点，不依赖已恢复的玩家本体或实体跟踪状态。
 */
public record AircraftCrashPayload(ResourceLocation dimension, int entityId, int variant, double x, double y, double z,
        float yaw, float pitch, int duration) implements CustomPacketPayload {
    public static final Type<AircraftCrashPayload> TYPE = new Type<>(AircraftCrashPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "aircraft_crash"));
    public static final StreamCodec<FriendlyByteBuf, AircraftCrashPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        net.killstreakcod.network.ByteBufCodecs.RESOURCE_LOCATION.encode(b, p.dimension);
        b.writeInt(p.entityId);
        b.writeInt(p.variant);
        b.writeDouble(p.x);
        b.writeDouble(p.y);
        b.writeDouble(p.z);
        b.writeFloat(p.yaw);
        b.writeFloat(p.pitch);
        b.writeInt(p.duration);
    }, b -> new AircraftCrashPayload(net.killstreakcod.network.ByteBufCodecs.RESOURCE_LOCATION.decode(b), b.readInt(), b.readInt(), b.readDouble(),
            b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
