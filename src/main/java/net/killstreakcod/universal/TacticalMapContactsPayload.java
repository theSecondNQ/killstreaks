package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Stride: x*10, eyeY*10, z*10, world yaw, relation (0 ally, 1 enemy).
 * 每条记录依次为 x×10、眼睛高度 eyeY×10、z×10、世界偏航角及关系，关系 0 表示友军、1 表示敌人。
 */
public record TacticalMapContactsPayload(String reward, int[] contacts) implements CustomPacketPayload {
    public TacticalMapContactsPayload {
        if (contacts.length > 1280 || contacts.length % 5 != 0)
            throw new IllegalArgumentException("Invalid map contacts");
        contacts = contacts.clone();
    }

    public static final Type<TacticalMapContactsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "target_contacts"));
    public static final StreamCodec<ByteBuf, TacticalMapContactsPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(b, p.reward);
                TacticalMapPayload.writeArray(b, p.contacts);
            },
            b -> new TacticalMapContactsPayload(ByteBufCodecs.STRING_UTF8.decode(b),
                    TacticalMapPayload.readArray(b, 1280)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
