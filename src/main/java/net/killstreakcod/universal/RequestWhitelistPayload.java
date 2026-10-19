package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Page sizes are chosen from the actual screen height, never from an unbounded client list.
 * 分页大小依据实际屏幕高度决定，不接受客户端任意长度的名单。
 */
public record RequestWhitelistPayload(UUID request, int teammatePage, int onlinePage, int pageSize)
        implements CustomPacketPayload {
    public RequestWhitelistPayload {
        if (request == null || teammatePage < 0 || onlinePage < 0 || teammatePage > 1_000_000 || onlinePage > 1_000_000
                || pageSize < 1 || pageSize > WhitelistStatePayload.MAX_PAGE_SIZE)
            throw new IllegalArgumentException("Invalid teammate page request");
    }

    public static final Type<RequestWhitelistPayload> TYPE = new Type<>(RequestWhitelistPayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "request_whitelist"));
    public static final StreamCodec<FriendlyByteBuf, RequestWhitelistPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.request);
                b.writeVarInt(p.teammatePage);
                b.writeVarInt(p.onlinePage);
                b.writeVarInt(p.pageSize);
            },
            b -> new RequestWhitelistPayload(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
