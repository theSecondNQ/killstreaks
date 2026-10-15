package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * The server derives the owner from the connection; no owner UUID is accepted.
 * 服务端从连接中确定所有者，不接受客户端提供的所有者 UUID。
 */
public record WhitelistActionPayload(UUID request, int action, String target, int teammatePage, int onlinePage,
        int pageSize) implements CustomPacketPayload {
    public static final int ADD = 0, REMOVE = 1;

    public WhitelistActionPayload {
        new RequestWhitelistPayload(request, teammatePage, onlinePage, pageSize);
        if (action != ADD && action != REMOVE || target == null || target.length() > 64)
            throw new IllegalArgumentException("Invalid teammate action");
    }

    public static final Type<WhitelistActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "whitelist_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WhitelistActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.request);
                b.writeVarInt(p.action);
                b.writeUtf(p.target, 64);
                b.writeVarInt(p.teammatePage);
                b.writeVarInt(p.onlinePage);
                b.writeVarInt(p.pageSize);
            },
            b -> new WhitelistActionPayload(b.readUUID(), b.readVarInt(), b.readUtf(64), b.readVarInt(), b.readVarInt(),
                    b.readVarInt()));

    RequestWhitelistPayload page() {
        return new RequestWhitelistPayload(request, teammatePage, onlinePage, pageSize);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
