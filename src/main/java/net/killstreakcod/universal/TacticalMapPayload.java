package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TacticalMapPayload(String reward, int originX, int originZ, int width, int[] colors, int[] heights)
        implements CustomPacketPayload {
    public static final int WIDTH = 133, RADIUS = 66;

    public TacticalMapPayload(String id, int x, int z, int[] colors) {
        this(id, x, z, (int) Math.sqrt(colors.length), colors, new int[colors.length]);
    }

    public TacticalMapPayload {
        if (width < 2 || width > WIDTH || colors.length != width * width || heights.length != colors.length)
            throw new IllegalArgumentException("Invalid terrain image");
        colors = colors.clone();
        heights = heights.clone();
    }

    public static final Type<TacticalMapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "target_map"));
    public static final StreamCodec<ByteBuf, TacticalMapPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(b, p.reward);
                b.writeInt(p.originX);
                b.writeInt(p.originZ);
                b.writeInt(p.width);
                writeArray(b, p.colors);
                writeArray(b, p.heights);
            },
            b -> new TacticalMapPayload(ByteBufCodecs.STRING_UTF8.decode(b), b.readInt(), b.readInt(), b.readInt(),
                    readArray(b, WIDTH * WIDTH), readArray(b, WIDTH * WIDTH)));

    static void writeArray(ByteBuf b, int[] a) {
        b.writeInt(a.length);
        for (int i : a)
            b.writeInt(i);
    }

    static int[] readArray(ByteBuf b, int limit) {
        int n = b.readInt();
        if (n < 0 || n > limit)
            throw new IllegalArgumentException("Invalid tactical array");
        int[] a = new int[n];
        for (int i = 0; i < n; i++)
            a[i] = b.readInt();
        return a;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
