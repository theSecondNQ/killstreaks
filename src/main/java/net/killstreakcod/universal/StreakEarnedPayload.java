package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record StreakEarnedPayload(String reward, int count, int slot, boolean packageReward, String faction)
        implements CustomPacketPayload {
    public StreakEarnedPayload(String reward, int count, int slot, boolean packageReward) {
        this(reward, count, slot, packageReward, VoiceFaction.SPECGRU);
    }

    public static final Type<StreakEarnedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "earned"));
    public static final StreamCodec<ByteBuf, StreakEarnedPayload> STREAM_CODEC = StreamCodec.of(
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
