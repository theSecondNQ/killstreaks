package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.StreakReward;

import java.util.Arrays;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded research state; the client renders it but cannot write it back as progress.
 * 有界研究状态；客户端仅用于显示，不能把显示状态写回为研究进度。
 */
public record ResearchStatePayload(int containerId, int tier, ResearchMask unlocked, ResearchMask equipped,
        ResearchMask repairs, boolean streakMode, boolean canSwitch, boolean creative, boolean busy, String message,
        int[] stocks, int mgbCount, boolean upgradesDisabled, int[] requisitions, int mgbRequisitions)
        implements CustomPacketPayload {
    public ResearchStatePayload {
        if (stocks.length != StreakReward.SELECTABLE.size() || mgbCount < 0
                || Arrays.stream(stocks).anyMatch(count -> count < 0)
                || requisitions.length != StreakReward.SELECTABLE.size() || mgbRequisitions < 0
                || Arrays.stream(requisitions).anyMatch(count -> count < 0))
            throw new IllegalArgumentException("Invalid research reward counts");
        stocks = stocks.clone();
        requisitions = requisitions.clone();
    }

    public static final Type<ResearchStatePayload> TYPE = new Type<>(ResearchStatePayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "headquarters_state_v5"));
    public static final StreamCodec<FriendlyByteBuf, ResearchStatePayload> STREAM_CODEC = StreamCodec
            .of((buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.tier);
                ResearchMask.STREAM_CODEC.encode(buffer, payload.unlocked);
                ResearchMask.STREAM_CODEC.encode(buffer, payload.equipped);
                ResearchMask.STREAM_CODEC.encode(buffer, payload.repairs);
                buffer.writeBoolean(payload.streakMode);
                buffer.writeBoolean(payload.canSwitch);
                buffer.writeBoolean(payload.creative);
                buffer.writeBoolean(payload.busy);
                buffer.writeUtf(payload.message, 128);
                for (int count : payload.stocks)
                    buffer.writeVarInt(count);
                buffer.writeVarInt(payload.mgbCount);
                buffer.writeBoolean(payload.upgradesDisabled);
                for (int count : payload.requisitions)
                    buffer.writeVarInt(count);
                buffer.writeVarInt(payload.mgbRequisitions);
            }, buffer -> new ResearchStatePayload(buffer.readVarInt(), buffer.readVarInt(),
                    ResearchMask.STREAM_CODEC.decode(buffer), ResearchMask.STREAM_CODEC.decode(buffer),
                    ResearchMask.STREAM_CODEC.decode(buffer), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(128), readCounts(buffer),
                    buffer.readVarInt(), buffer.readBoolean(), readCounts(buffer), buffer.readVarInt()));

    private static int[] readCounts(FriendlyByteBuf buffer) {
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

    public int count(String id) {
        return id.equals("mgb") ? mgbCount : StreakReward.byId(id).map(this::count).orElse(0);
    }

    public int requisitionCount(String id) {
        return id.equals("mgb") ? mgbRequisitions
                : StreakReward.byId(id).map(reward -> requisitions[reward.ordinal()]).orElse(0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
