package net.killstreakcod.universal.compat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/** 只读取 GD656 服务端累计分数，以增量结算连杀；不相信客户端报分，也不改动 GD656 数据。 */
public final class Gd656Scores {
    private record Sample(double total, double remainder) {
    }

    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static boolean resolved;
    private static Object manager;
    private static Method score;

    private Gd656Scores() {}

    public static boolean available() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> type = Class.forName("org.mods.gd656killicon.server.data.PlayerDataManager");
                manager = type.getMethod("get").invoke(null);
                score = type.getMethod("getScore", UUID.class);
            }
            catch (ClassNotFoundException absent) {
                manager = null;
            }
            catch (ReflectiveOperationException | LinkageError incompatible) {
                manager = null;
                com.mojang.logging.LogUtils.getLogger().warn("Killstreaks: GD656 score adapter unavailable",
                        incompatible);
            }
        }
        return manager != null;
    }

    public static void sample(ServerPlayer player) {
        poll(player, false);
    }

    public static int poll(ServerPlayer player, boolean enabled) {
        if (!available())
            return 0;
        try {
            double total = ((Number) score.invoke(manager, player.getUUID())).doubleValue();
            if (!Double.isFinite(total))
                return 0;
            Sample before = SAMPLES.get(player.getUUID());
            double gain = before == null || !enabled ? 0 : Math.max(0, total - before.total) + before.remainder;
            int whole = (int) Math.min(Integer.MAX_VALUE, Math.floor(gain));
            SAMPLES.put(player.getUUID(), new Sample(total, gain - whole));
            return whole;
        }
        catch (ReflectiveOperationException | RuntimeException failure) {
            manager = null;
            com.mojang.logging.LogUtils.getLogger().warn("Killstreaks: GD656 score adapter disabled", failure);
            return 0;
        }
    }

    public static void forget(UUID player) {
        SAMPLES.remove(player);
    }

    public static void clear() {
        SAMPLES.clear();
    }
}
