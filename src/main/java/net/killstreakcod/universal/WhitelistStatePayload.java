package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.killstreakcod.network.StreamCodec;
import net.killstreakcod.network.CustomPacketPayload;
import net.killstreakcod.network.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * An authoritative page contains at most 12 teammates and 12 available online players.
 * 每页数据由服务端确认，最多包含 12 名队友和 12 名可选在线玩家。
 */
public record WhitelistStatePayload(UUID request, int teammatePage, int teammatePages, int teammateTotal,
        int onlinePage, int onlinePages, int onlineTotal, int pageSize, boolean writable, String message, String detail,
        List<Member> teammates, List<Member> online) implements CustomPacketPayload {
    public static final int MAX_PAGE_SIZE = 12;

    public record Member(UUID uuid, String name, boolean online) {
        public Member {
            if (uuid == null || name == null || name.length() > 64)
                throw new IllegalArgumentException("Invalid teammate entry");
        }
    }

    public WhitelistStatePayload {
        if (request == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE || teammatePages < 1 || onlinePages < 1
                || teammatePage < 0 || onlinePage < 0 || teammatePage >= teammatePages || onlinePage >= onlinePages
                || teammateTotal < 0 || onlineTotal < 0 || message == null || message.length() > 64 || detail == null
                || detail.length() > 64 || teammates.size() > pageSize || online.size() > pageSize)
            throw new IllegalArgumentException("Invalid teammate state");
        teammates = List.copyOf(teammates);
        online = List.copyOf(online);
    }

    public static final Type<WhitelistStatePayload> TYPE = new Type<>(WhitelistStatePayload.class,
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "whitelist_state"));
    public static final StreamCodec<FriendlyByteBuf, WhitelistStatePayload> STREAM_CODEC = StreamCodec
            .of((b, p) -> {
                b.writeUUID(p.request);
                b.writeVarInt(p.teammatePage);
                b.writeVarInt(p.teammatePages);
                b.writeVarInt(p.teammateTotal);
                b.writeVarInt(p.onlinePage);
                b.writeVarInt(p.onlinePages);
                b.writeVarInt(p.onlineTotal);
                b.writeVarInt(p.pageSize);
                b.writeBoolean(p.writable);
                b.writeUtf(p.message, 64);
                b.writeUtf(p.detail, 64);
                write(b, p.teammates);
                write(b, p.online);
            }, b -> new WhitelistStatePayload(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readUtf(64),
                    b.readUtf(64), read(b), read(b)));

    private static void write(FriendlyByteBuf b, List<Member> members) {
        b.writeVarInt(members.size());
        for (Member member : members) {
            b.writeUUID(member.uuid);
            b.writeUtf(member.name, 64);
            b.writeBoolean(member.online);
        }
    }

    private static List<Member> read(FriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > MAX_PAGE_SIZE)
            throw new IllegalArgumentException("Too many teammate entries");
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            members.add(new Member(b.readUUID(), b.readUtf(64), b.readBoolean()));
        return members;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
