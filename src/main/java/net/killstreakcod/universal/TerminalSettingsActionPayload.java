package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 空修改请求快照；修改包只包含用户更改的键，避免覆盖另一管理员的其他改动。 */
public record TerminalSettingsActionPayload(UUID request, String faction, Map<String, Double> changes)
        implements CustomPacketPayload {
    static final int MAX_VALUES = 96;
    public static final Type<TerminalSettingsActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "terminal_settings_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSettingsActionPayload> STREAM_CODEC = StreamCodec
            .of((buffer, payload) -> {
                buffer.writeUUID(payload.request());
                buffer.writeUtf(payload.faction(), 32);
                writeValues(buffer, payload.changes());
            }, buffer -> new TerminalSettingsActionPayload(buffer.readUUID(), buffer.readUtf(32), readValues(buffer)));

    public TerminalSettingsActionPayload {
        if (request == null || faction == null || faction.length() > 32 || changes.size() > MAX_VALUES
                || changes.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().length() > 64
                        || entry.getValue() == null || !Double.isFinite(entry.getValue())))
            throw new IllegalArgumentException("Invalid terminal settings patch");
        changes = Map.copyOf(changes);
    }

    static void writeValues(RegistryFriendlyByteBuf buffer, Map<String, Double> values) {
        buffer.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buffer.writeUtf(key, 64);
            buffer.writeDouble(value);
        });
    }

    static Map<String, Double> readValues(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_VALUES)
            throw new IllegalArgumentException("Too many settings entries");
        var values = new LinkedHashMap<String, Double>();
        for (int i = 0; i < size; i++) {
            String key = buffer.readUtf(64);
            double value = buffer.readDouble();
            if (!Double.isFinite(value) || values.putIfAbsent(key, value) != null)
                throw new IllegalArgumentException("Invalid settings value");
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
