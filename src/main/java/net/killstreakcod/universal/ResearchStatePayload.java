package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.StreakReward;

import java.util.Arrays;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded research state; the client renders it but cannot write it back as progress.
 * 有界研究状态；客户端仅用于显示，不能把显示状态写回为研究进度。
 */
public record ResearchStatePayload(int containerId, int tier, long unlocked, long repairs, boolean streakMode,
        boolean canSwitch, boolean creative, boolean busy, String message, int[] stocks)
        implements CustomPacketPayload {
    public ResearchStatePayload {
        if (stocks.length != StreakReward.SELECTABLE.size() || Arrays.stream(stocks).anyMatch(count -> count < 0))
            throw new IllegalArgumentException("Invalid research reward counts");
        stocks = stocks.clone();
    }

    public static final Type<ResearchStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "headquarters_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchStatePayload> STREAM_CODEC = StreamCodec
            .of((buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.tier);
                buffer.writeLong(payload.unlocked);
                buffer.writeLong(payload.repairs);
                buffer.writeBoolean(payload.streakMode);
                buffer.writeBoolean(payload.canSwitch);
                buffer.writeBoolean(payload.creative);
                buffer.writeBoolean(payload.busy);
                buffer.writeUtf(payload.message, 128);
                for (int count : payload.stocks)
                    buffer.writeVarInt(count);
            }, buffer -> new ResearchStatePayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(),
                    buffer.readLong(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readUtf(128), readStocks(buffer)));

    private static int[] readStocks(RegistryFriendlyByteBuf buffer) {
        // Fixed catalog size prevents a packet from requesting an unbounded allocation.
        // 按固定目录大小读取，避免数据包自行指定无限长度的库存数组。
        int[] counts = new int[StreakReward.SELECTABLE.size()];
        for (int i = 0; i < counts.length; i++)
            counts[i] = buffer.readVarInt();
        return counts;
    }

    public int count(StreakReward reward) {
        return stocks[reward.ordinal()];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
