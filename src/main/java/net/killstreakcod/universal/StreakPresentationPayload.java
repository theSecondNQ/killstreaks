package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Authoritative start of a short call or camera transition animation.
 * 由服务端确认启动短时呼叫或摄像机过渡动画。
 */
public record StreakPresentationPayload(String reward, String stage, int duration) implements CustomPacketPayload {
    public static final Type<StreakPresentationPayload> TYPE = new Type<>(StreakPresentationPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "presentation"));
    public static final StreamCodec<FriendlyByteBuf, StreakPresentationPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(b, p.reward);
                ByteBufCodecs.STRING_UTF8.encode(b, p.stage);
                b.writeInt(p.duration);
            },
            b -> new StreakPresentationPayload(ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                    b.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
