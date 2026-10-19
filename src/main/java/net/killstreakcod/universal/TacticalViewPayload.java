package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TacticalViewPayload(int entityId, String mode, int remaining, int weapon, int health, boolean thermal,
        int rockets, int flares, float yaw, float pitch, int cooldown, int rocketCooldown, float speed, int[] reloads,
        int[] ammo, int capabilities) implements CustomPacketPayload {
    public TacticalViewPayload(int id, String mode, int ticks, int weapon, int health, boolean thermal, int rockets,
            int flares, float yaw, float pitch, int cooldown, int rocketCooldown, float speed, int[] reloads,
            int[] ammo) {
        this(id, mode, ticks, weapon, health, thermal, rockets, flares, yaw, pitch, cooldown, rocketCooldown, speed,
                reloads, ammo, 0);
    }

    public TacticalViewPayload(int id, String mode, int ticks, int weapon, int health, boolean thermal, int rockets,
            int flares, float yaw, float pitch, int cooldown, int rocketCooldown, float speed, int[] reloads) {
        this(id, mode, ticks, weapon, health, thermal, rockets, flares, yaw, pitch, cooldown, rocketCooldown, speed,
                reloads, StreakWeaponProfiles.MAGAZINE);
    }

    public TacticalViewPayload(int id, String mode, int ticks, int weapon, int health, boolean thermal) {
        this(id, mode, ticks, weapon, health, thermal, 0, 0, 0, 0, 0, 0, 0, new int[3]);
    }

    public TacticalViewPayload(int id, String mode, int ticks, int weapon, int health, boolean thermal, int rockets,
            int flares) {
        this(id, mode, ticks, weapon, health, thermal, rockets, flares, 0, 0, 0, 0, 0, new int[3]);
    }

    public static final Type<TacticalViewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "view_v2"));
    public static final StreamCodec<ByteBuf, TacticalViewPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeInt(p.entityId);
        ByteBufCodecs.STRING_UTF8.encode(b, p.mode);
        b.writeInt(p.remaining);
        b.writeInt(p.weapon);
        b.writeInt(p.health);
        b.writeBoolean(p.thermal);
        b.writeInt(p.rockets);
        b.writeInt(p.flares);
        b.writeFloat(p.yaw);
        b.writeFloat(p.pitch);
        b.writeInt(p.cooldown);
        b.writeInt(p.rocketCooldown);
        b.writeFloat(p.speed);
        for (int i : p.reloads)
            b.writeInt(i);
        for (int i : p.ammo)
            b.writeInt(i);
        b.writeInt(p.capabilities);
    }, b -> new TacticalViewPayload(b.readInt(), ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readInt(),
            b.readInt(), b.readBoolean(), b.readInt(), b.readInt(), b.readFloat(), b.readFloat(), b.readInt(),
            b.readInt(), b.readFloat(), new int[] { b.readInt(), b.readInt(), b.readInt() },
            new int[] { b.readInt(), b.readInt(), b.readInt() }, b.readInt()));

    public TacticalViewPayload {
        if (reloads.length != 3 || ammo.length != 3 || weapon < 0 || weapon > 4)
            throw new IllegalArgumentException("Three gunship weapons required");
        reloads = reloads.clone();
        ammo = ammo.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
