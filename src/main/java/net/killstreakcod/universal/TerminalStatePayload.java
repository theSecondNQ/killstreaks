package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record TerminalStatePayload(String slot1, String slot2, String slot3, boolean scoreMode,
        int kills, int score, int readyMask, boolean openScreen, int[] counts, int[] progress, boolean editable,
        int mgbCount, String faction, boolean juggernautEnabled) implements CustomPacketPayload {
    public TerminalStatePayload(String a, String b, String c, boolean mode, int kills, int score, int ready,
            boolean open) {
        this(a, b, c, mode, kills, score, ready, open, new int[] { ready & 1, (ready >> 1) & 1, (ready >> 2) & 1 },
                new int[3], true, 0, VoiceFaction.SPECGRU, false);
    }

    public static final Type<TerminalStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "terminal_state"));
    public static final StreamCodec<ByteBuf, TerminalStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(b, p.slot1);
                ByteBufCodecs.STRING_UTF8.encode(b, p.slot2);
                ByteBufCodecs.STRING_UTF8.encode(b, p.slot3);
                b.writeBoolean(p.scoreMode);
                b.writeInt(p.kills);
                b.writeInt(p.score);
                b.writeInt(p.readyMask);
                b.writeBoolean(p.openScreen);
                for (int i : p.counts)
                    b.writeInt(i);
                for (int i : p.progress)
                    b.writeInt(i);
                b.writeBoolean(p.editable);
                b.writeInt(p.mgbCount);
                ByteBufCodecs.STRING_UTF8.encode(b, p.faction);
                b.writeBoolean(p.juggernautEnabled);
            },
            b -> new TerminalStatePayload(ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                    ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean(), b.readInt(), b.readInt(), b.readInt(),
                    b.readBoolean(), new int[] { b.readInt(), b.readInt(), b.readInt() },
                    new int[] { b.readInt(), b.readInt(), b.readInt() }, b.readBoolean(), b.readInt(),
                    ByteBufCodecs.STRING_UTF8.decode(b), b.readBoolean()));

    public TerminalStatePayload {
        if (counts.length != 3 || progress.length != 3)
            throw new IllegalArgumentException("Three streak slots required");
        counts = counts.clone();
        progress = progress.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
