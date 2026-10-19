package net.killstreakcod.universal;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;

/**
 * Immutable research selection with a fixed network size and room for 128 stable node indices.
 * 不可变的研究选择，固定同步长度，可保存 128 个稳定节点位序。
 */
public record ResearchMask(long low, long high) {
    public static final int CAPACITY = 128;
    public static final ResearchMask EMPTY = new ResearchMask(0, 0);
    public static final StreamCodec<FriendlyByteBuf, ResearchMask> STREAM_CODEC = StreamCodec
            .of((buffer, mask) -> {
                buffer.writeLong(mask.low);
                buffer.writeLong(mask.high);
            }, buffer -> new ResearchMask(buffer.readLong(), buffer.readLong()));

    public static ResearchMask first(int count) {
        if (count < 0 || count > CAPACITY)
            throw new IllegalArgumentException("Research node count outside capacity: " + count);
        long low = count >= 64 ? -1L : count == 0 ? 0 : -1L >>> (64 - count);
        long high = count <= 64 ? 0 : -1L >>> (CAPACITY - count);
        return new ResearchMask(low, high);
    }

    public static ResearchMask single(int index) {
        if (index < 0 || index >= CAPACITY)
            throw new IllegalArgumentException("Research node index outside capacity: " + index);
        return index < 64 ? new ResearchMask(1L << index, 0) : new ResearchMask(0, 1L << (index - 64));
    }

    public boolean contains(int index) {
        return index >= 0 && index < CAPACITY
                && (index < 64 ? (low & 1L << index) != 0 : (high & 1L << (index - 64)) != 0);
    }

    public ResearchMask union(ResearchMask other) {
        return new ResearchMask(low | other.low, high | other.high);
    }

    public ResearchMask intersect(ResearchMask other) {
        return new ResearchMask(low & other.low, high & other.high);
    }

    public ResearchMask subtract(ResearchMask other) {
        return new ResearchMask(low & ~other.low, high & ~other.high);
    }

    public boolean isEmpty() {
        return low == 0 && high == 0;
    }

    public int count() {
        return Long.bitCount(low) + Long.bitCount(high);
    }
}
