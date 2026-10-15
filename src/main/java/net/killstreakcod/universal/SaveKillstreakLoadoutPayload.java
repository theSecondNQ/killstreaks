package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record SaveKillstreakLoadoutPayload(String slot1, String slot2, String slot3, String faction)
        implements CustomPacketPayload {
    public SaveKillstreakLoadoutPayload(String a, String b, String c) {
        this(a, b, c, VoiceFaction.SPECGRU);
    }

    public static final Type<SaveKillstreakLoadoutPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "save_killstreak_loadout"));
    public static final StreamCodec<ByteBuf, SaveKillstreakLoadoutPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SaveKillstreakLoadoutPayload::slot1,
            ByteBufCodecs.STRING_UTF8, SaveKillstreakLoadoutPayload::slot2,
            ByteBufCodecs.STRING_UTF8, SaveKillstreakLoadoutPayload::slot3,
            ByteBufCodecs.STRING_UTF8, SaveKillstreakLoadoutPayload::faction,
            SaveKillstreakLoadoutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
