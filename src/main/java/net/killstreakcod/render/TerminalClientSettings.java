package net.killstreakcod.render;

import net.killstreakcod.universal.NativePresentationConfig;
import net.killstreakcod.universal.StreakServerConfig.Kind;
import net.killstreakcod.universal.StreakServerConfig.Setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 客户端设置仅修改本机的原生 TOML，不向服务器发送客户端显示与音量配置。 */
final class TerminalClientSettings {
    private record Binding(Setting setting, ModConfigSpec.ConfigValue<?> value) {
    }

    private static final List<Binding> BINDINGS = List.of(
            binding("originalRadio", Kind.BOOLEAN, 0, 1, 1, NativePresentationConfig.RADIO),
            binding("radioSubtitles", Kind.BOOLEAN, 0, 1, 1, NativePresentationConfig.SUBTITLES),
            binding("radioVolume", Kind.DECIMAL, 0, 1, .75, NativePresentationConfig.RADIO_VOLUME),
            binding("effectSpriteLimit", Kind.INTEGER, 32, 512, 256, NativePresentationConfig.FX_LIMIT),
            binding("gunshipViewDistance", Kind.INTEGER, 16, 32, 32, NativePresentationConfig.GUNSHIP_VIEW_DISTANCE),
            binding("rewardRailStyle", Kind.INTEGER, 0, 2, 1, NativePresentationConfig.REWARD_RAIL_STYLE),
            binding("rewardRailAutoHide", Kind.BOOLEAN, 0, 1, 1, NativePresentationConfig.REWARD_RAIL_AUTO_HIDE));

    private TerminalClientSettings() {}

    private static Binding binding(String name, Kind kind, double minimum, double maximum, double initial,
            ModConfigSpec.ConfigValue<?> value) {
        return new Binding(new Setting(name, name, "client", kind, minimum, maximum, initial), value);
    }

    static List<Setting> settings() {
        return BINDINGS.stream().map(Binding::setting).toList();
    }

    static Map<String, Double> snapshot() {
        var result = new LinkedHashMap<String, Double>();
        for (Binding binding : BINDINGS) {
            Object value = binding.value().get();
            result.put(binding.setting().id(),
                    value instanceof Boolean flag ? flag ? 1D : 0D : ((Number) value).doubleValue());
        }
        return result;
    }

    static boolean save(Map<String, Double> patch) {
        if (patch.keySet().stream()
                .anyMatch(key -> BINDINGS.stream().noneMatch(binding -> binding.setting().id().equals(key))))
            return false;
        for (Binding binding : BINDINGS) {
            Double value = patch.get(binding.setting().id());
            if (value != null && !binding.setting().accepts(value))
                return false;
        }
        Map<String, Double> previous = snapshot();
        try {
            for (Binding binding : BINDINGS) {
                Double value = patch.get(binding.setting().id());
                if (value != null) {
                    write(binding, value);
                }
            }
            NativePresentationConfig.SPEC.save();
            return true;
        }
        catch (RuntimeException failure) {
            for (Binding binding : BINDINGS)
                write(binding, previous.get(binding.setting().id()));
            try {
                NativePresentationConfig.SPEC.save();
            }
            catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            com.mojang.logging.LogUtils.getLogger().warn("Cannot save killstreak client settings", failure);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void write(Binding binding, double value) {
        Object next = switch (binding.setting().kind()) {
            case BOOLEAN -> value != 0;
            case INTEGER -> (int) value;
            case DECIMAL -> value;
        };
        ((ModConfigSpec.ConfigValue<Object>) binding.value()).set(next);
    }
}
