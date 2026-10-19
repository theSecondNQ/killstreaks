package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record StreakEarnedPayload(String reward, int count, int slot, boolean packageReward, String faction)
        implements CustomPacketPayload {
    public StreakEarnedPayload(String reward, int count, int slot, boolean packageReward) {
        this(reward, count, slot, packageReward, VoiceFaction.SPECGRU);
    }

    public static final Type<StreakEarnedPayload> TYPE = new Type<>(StreakEarnedPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "earned"));
    public static final StreamCodec<FriendlyByteBuf, StreakEarnedPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(b, p.reward);
                b.writeInt(p.count);
                b.writeInt(p.slot);
                b.writeBoolean(p.packageReward);
                ByteBufCodecs.STRING_UTF8.encode(b, p.faction);
            },
            b -> new StreakEarnedPayload(ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readInt(), b.readBoolean(),
                    ByteBufCodecs.STRING_UTF8.decode(b)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
