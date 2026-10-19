package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Input only: targets, damage, ammunition and movement are validated on the server.
 * 只提交输入意图；目标、伤害、弹药和移动均由服务端验证。
 */
public record TacticalControlPayload(float yaw, float pitch, float forward, float strafe, int flags, int sequence)
        implements CustomPacketPayload {
    public TacticalControlPayload(float yaw, float pitch, float forward, float strafe, int flags) {
        this(yaw, pitch, forward, strafe, flags, 0);
    }

    public static final int FIRE = 1, SECONDARY = 2, UP = 4, DOWN = 8, BOOST = 16, THERMAL = 32, CYCLE = 64, EXIT = 128,
            AUTO = 256, FLARES = 512, WEAPON_1 = 1024, WEAPON_2 = 2048, WEAPON_3 = 4096, CYCLE_BACK = 8192;
    public static final int WEAPON_4 = 16384, WEAPON_5 = 32768;
    public static final int HELD_MASK = FIRE | UP | DOWN | BOOST;
    public static final int ACTION_MASK = SECONDARY | THERMAL | CYCLE | EXIT | AUTO | FLARES | WEAPON_1 | WEAPON_2
            | WEAPON_3 | CYCLE_BACK | WEAPON_4 | WEAPON_5;
    public static final Type<TacticalControlPayload> TYPE = new Type<>(TacticalControlPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "control_v2"));
    public static final StreamCodec<FriendlyByteBuf, TacticalControlPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeFloat(p.yaw);
        b.writeFloat(p.pitch);
        b.writeFloat(p.forward);
        b.writeFloat(p.strafe);
        b.writeInt(p.flags);
        b.writeInt(p.sequence);
    }, b -> new TacticalControlPayload(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readInt(),
            b.readInt()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
