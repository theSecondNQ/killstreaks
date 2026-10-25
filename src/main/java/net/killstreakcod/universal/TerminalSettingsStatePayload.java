package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.Map;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务器确认的配置值与编辑权限；不向客户端开放服务器文件路径。 */
public record TerminalSettingsStatePayload(UUID request, boolean writable, String faction, String message,
        Map<String, Double> values) implements CustomPacketPayload {
    public static final Type<TerminalSettingsStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "terminal_settings_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSettingsStatePayload> STREAM_CODEC = StreamCodec
            .of((buffer, payload) -> {
                buffer.writeUUID(payload.request());
                buffer.writeBoolean(payload.writable());
                buffer.writeUtf(payload.faction(), 32);
                buffer.writeUtf(payload.message(), 64);
                TerminalSettingsActionPayload.writeValues(buffer, payload.values());
            }, buffer -> new TerminalSettingsStatePayload(buffer.readUUID(), buffer.readBoolean(), buffer.readUtf(32),
                    buffer.readUtf(64), TerminalSettingsActionPayload.readValues(buffer)));

    public TerminalSettingsStatePayload {
        if (request == null || faction == null || message == null || faction.length() > 32 || message.length() > 64
                || values.size() > TerminalSettingsActionPayload.MAX_VALUES)
            throw new IllegalArgumentException("Invalid terminal settings snapshot");
        values = Map.copyOf(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
