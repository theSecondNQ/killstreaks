package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded visual event. Gameplay damage and ballistics remain server authoritative.
 * 受限的视觉事件，实际玩法伤害和弹道仍由服务端确定。
 */
public record NativeFxPayload(ResourceLocation dimension, String profile, double x, double y, double z, float size,
        long seed) implements CustomPacketPayload {
    public static final int MAX_PROFILE_LENGTH = 64;

    public static boolean validProfile(String profile) {
        return profile != null && !profile.isEmpty() && profile.length() <= MAX_PROFILE_LENGTH;
    }

    public NativeFxPayload {
        if (!validProfile(profile))
            throw new IllegalArgumentException("Invalid native effect profile");
    }

    public static final Type<NativeFxPayload> TYPE = new Type<>(NativeFxPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "native_fx"));
    public static final StreamCodec<FriendlyByteBuf, NativeFxPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeResourceLocation(p.dimension);
        b.writeUtf(p.profile, MAX_PROFILE_LENGTH);
        b.writeDouble(p.x);
        b.writeDouble(p.y);
        b.writeDouble(p.z);
        b.writeFloat(p.size);
        b.writeLong(p.seed);
    }, b -> new NativeFxPayload(b.readResourceLocation(), b.readUtf(MAX_PROFILE_LENGTH), b.readDouble(), b.readDouble(),
            b.readDouble(), b.readFloat(), b.readLong()));

    public Vec3 at() {
        return new Vec3(x, y, z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
