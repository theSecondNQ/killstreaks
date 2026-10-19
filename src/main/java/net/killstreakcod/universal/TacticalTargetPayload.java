package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TacticalTargetPayload(int[] points, float[] headings, boolean cancel) implements CustomPacketPayload {
    public TacticalTargetPayload(int[] points, float heading, boolean cancel) {
        this(points, uniform(points.length / 2, heading), cancel);
    }

    private static float[] uniform(int count, float heading) {
        float[] a = new float[count];
        java.util.Arrays.fill(a, heading);
        return a;
    }

    public TacticalTargetPayload {
        if (points.length > 6 || headings.length > 3)
            throw new IllegalArgumentException("Too many targets");
        points = points.clone();
        headings = headings.clone();
    }

    public static final Type<TacticalTargetPayload> TYPE = new Type<>(TacticalTargetPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "target"));
    public static final StreamCodec<FriendlyByteBuf, TacticalTargetPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                TacticalMapPayload.writeArray(b, p.points);
                b.writeInt(p.headings.length);
                for (float angle : p.headings)
                    b.writeFloat(angle);
                b.writeBoolean(p.cancel);
            },
            b -> {
                int[] points = TacticalMapPayload.readArray(b, 6);
                int n = b.readInt();
                if (n < 0 || n > 3)
                    throw new IllegalArgumentException("Invalid heading count");
                float[] angles = new float[n];
                for (int i = 0; i < n; i++)
                    angles[i] = b.readFloat();
                return new TacticalTargetPayload(points, angles, b.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
