package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Actions identify the open server menu, never a player UUID or a client-supplied price.
 * 操作只携带已打开的菜单编号，不接受客户端提供玩家 UUID、价格或研究结果。
 */
public record ResearchActionPayload(int containerId, int action, String target) implements CustomPacketPayload {
    public static final int REQUEST = 0, RESEARCH = 1, UPGRADE = 2, CALL = 3, MODE = 4, UNLOCK_ALL = 5;
    public static final Type<ResearchActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "headquarters_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchActionPayload> STREAM_CODEC = StreamCodec
            .of((buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.action);
                buffer.writeUtf(payload.target, 64);
            }, buffer -> new ResearchActionPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
