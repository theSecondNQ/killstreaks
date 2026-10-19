package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.ByteBufCodecs;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record SaveKillstreakLoadoutPayload(String slot1, String slot2, String slot3, String faction)
        implements CustomPacketPayload {
    public SaveKillstreakLoadoutPayload(String a, String b, String c) {
        this(a, b, c, VoiceFaction.SPECGRU);
    }

    public static final Type<SaveKillstreakLoadoutPayload> TYPE = new Type<>(SaveKillstreakLoadoutPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "save_killstreak_loadout"));
    public static final StreamCodec<FriendlyByteBuf, SaveKillstreakLoadoutPayload> STREAM_CODEC = StreamCodec.composite(
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
