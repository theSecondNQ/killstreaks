package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** 全局 TOML、设置页元数据与运行时快照共用一个配置定义，所有玩法取值由服务端决定。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class StreakServerConfig {
    public static final String FILE_NAME = "killstreakcod-server.toml";

    public enum Kind {
        BOOLEAN, INTEGER, DECIMAL
    }

    public record Setting(String id, String label, String section, Kind kind, double minimum, double maximum,
            double initial) {
        public Component title() {
            return Component.translatable("killstreakcod.configuration." + label);
        }

        public boolean accepts(double value) {
            return Double.isFinite(value) && value >= minimum && value <= maximum
                    && (kind == Kind.DECIMAL || value == Math.rint(value));
        }
    }

    private record Binding(Setting setting, ModConfigSpec.ConfigValue<?> value) {
    }

    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, Map<String, Double>> ACTIVE = new WeakHashMap<>();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SCORESTREAKS, REPEATABLE_REWARDS, HOSTILE_KILLS_ONLY;
    public static final ModConfigSpec.BooleanValue MGB_ENABLED, JUGGERNAUT_ENABLED, DISABLE_UPGRADES;
    public static final ModConfigSpec.BooleanValue TERMINAL_ENABLED, AIRSPACE_CHECKS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("对本实例所有世界生效；联机使用服务端配置。", "Global rules; multiplayer uses the hosting server.")
                .translation("killstreakcod.configuration.earning").push("earning");
        SCORESTREAKS = toggle(builder, "earning", "scorestreaks", false, "按得分而非击杀获取奖励；切换时保留库存、清空未完成进度。");
        REPEATABLE_REWARDS = toggle(builder, "earning", "repeatableRewards", true,
                "开启后独立循环累计且死亡不清进度；关闭后每条命每种奖励只自然获取一次。");
        HOSTILE_KILLS_ONLY = toggle(builder, "earning", "hostileKillsOnly", true, "只计敌对生物、Boss 和敌方玩家；关闭后其他非友军生物也可计数。");
        builder.pop().translation("killstreakcod.configuration.rewards").push("rewards");
        MGB_ENABLED = toggle(builder, "rewards", "mgbEnabled", false, "是否允许获取与使用核弹，默认关闭。");
        JUGGERNAUT_ENABLED = toggle(builder, "rewards", "juggernautEnabled", false, "是否允许获取与使用无畏战甲，默认关闭。");
        builder.pop().translation("killstreakcod.configuration.research").push("research");
        TERMINAL_ENABLED = toggle(builder, "research", "terminalEnabled", true,
                "是否启用总部终端研究与预付材料系统。关闭后所有玩家直接使用连杀模式，且不应用任何升级；既有研究保留。");
        DISABLE_UPGRADES = toggle(builder, "research", "disableUpgrades", false, "禁用升级研究、装配及效果，保留已保存的研究与装配选择。");
        builder.pop().translation("killstreakcod.configuration.airspace").push("airspace");
        AIRSPACE_CHECKS = toggle(builder, "airspace", "airspaceChecks", true, "是否检查航线与机体上下三格空间并自动抬升；关闭不改变地狱禁飞规则。");
        var distance = builder.comment("炮艇遥控地面区块观察半径，单位为区块。", "Gunship ground watch radius in chunks.")
                .translation("killstreakcod.configuration.gunshipViewDistance")
                .defineInRange("gunshipViewDistance", 32, 16, 32);
        bind(new Setting("airspace.gunshipViewDistance", "gunshipViewDistance", "airspace", Kind.INTEGER, 16, 32, 32),
                distance);
        builder.pop().comment("武器基础伤害，1 点为半颗心；0 可关闭该武器的伤害。",
                "Base damage before armour and explosion falloff; one point is half a heart. Zero disables damage.")
                .translation("killstreakcod.configuration.damage").push("damage");
        for (StreakDamage.Channel channel : StreakDamage.Channel.values()) {
            var value = builder.comment("基础伤害；研究加成、爆炸衰减及自动攻击变体沿用原有比例。", "Base damage; existing modifiers still apply.")
                    .translation("killstreakcod.configuration.damage." + channel.id)
                    .defineInRange(channel.id, channel.base, 0, 1000000);
            bind(new Setting(channel.key(), "damage." + channel.id, "damage", Kind.DECIMAL, 0, 1000000, channel.base),
                    value);
        }
        builder.pop();
        SPEC = builder.build();
    }

    private StreakServerConfig() {}

    private static ModConfigSpec.BooleanValue toggle(ModConfigSpec.Builder builder, String section, String name,
            boolean initial, String description) {
        var value = builder.comment(description, "Default: " + initial)
                .translation("killstreakcod.configuration." + name).define(name, initial);
        bind(new Setting(section + "." + name, name, section, Kind.BOOLEAN, 0, 1, initial ? 1 : 0), value);
        return value;
    }

    private static void bind(Setting setting, ModConfigSpec.ConfigValue<?> value) {
        BINDINGS.put(setting.id(), new Binding(setting, value));
    }

    public static List<Setting> settings() {
        return BINDINGS.values().stream().map(Binding::setting).toList();
    }

    private static Map<String, Double> configured() {
        var values = new LinkedHashMap<String, Double>();
        for (var entry : BINDINGS.entrySet()) {
            Object value = entry.getValue().value().get();
            values.put(entry.getKey(), value instanceof Boolean flag ? flag ? 1D : 0D : ((Number) value).doubleValue());
        }
        return Map.copyOf(values);
    }

    public static Map<String, Double> snapshot(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, key -> configured());
    }

    public static double number(MinecraftServer server, String id) {
        return snapshot(server).get(id);
    }

    private static boolean flag(MinecraftServer server, String id) {
        return number(server, id) != 0;
    }

    public static boolean scorestreaks(MinecraftServer server) {
        return flag(server, "earning.scorestreaks");
    }

    public static boolean repeatable(MinecraftServer server) {
        return flag(server, "earning.repeatableRewards");
    }

    public static boolean hostileKillsOnly(MinecraftServer server) {
        return flag(server, "earning.hostileKillsOnly");
    }

    public static boolean terminalEnabled(MinecraftServer server) {
        return flag(server, "research.terminalEnabled");
    }

    public static boolean airspaceChecks(MinecraftServer server) {
        return flag(server, "airspace.airspaceChecks");
    }

    public static boolean upgradesDisabled(MinecraftServer server) {
        return !terminalEnabled(server) || flag(server, "research.disableUpgrades");
    }

    public static int gunshipViewDistance(MinecraftServer server) {
        return (int) number(server, "airspace.gunshipViewDistance");
    }

    public static boolean rewardEnabled(MinecraftServer server, String reward) {
        return switch (reward.toLowerCase(Locale.ROOT)) {
            case "mgb" -> flag(server, "rewards.mgbEnabled");
            case "juggernaut" -> flag(server, "rewards.juggernautEnabled");
            default -> true;
        };
    }

    public static boolean requireReward(ServerPlayer player, String reward) {
        if (rewardEnabled(player.serverLevel().getServer(), reward))
            return true;
        player.displayClientMessage(Component.translatable("message.killstreakcod.reward_disabled",
                Component.translatable("streak.killstreakcod." + reward)), true);
        return false;
    }

    /** 先完整校验，再统一保存；客户端只能提交已声明的键和范围内的值。 */
    public static boolean update(MinecraftServer server, Map<String, Double> changes) {
        if (changes.size() > BINDINGS.size())
            return false;
        for (var entry : changes.entrySet()) {
            Binding binding = BINDINGS.get(entry.getKey());
            if (binding == null || !binding.setting().accepts(entry.getValue()))
                return false;
        }
        var previous = configured();
        try {
            for (var entry : changes.entrySet())
                write(BINDINGS.get(entry.getKey()), entry.getValue());
            SPEC.save();
            apply(server);
            return true;
        }
        catch (RuntimeException failure) {
            for (var entry : changes.entrySet())
                write(BINDINGS.get(entry.getKey()), previous.get(entry.getKey()));
            try {
                SPEC.save();
            }
            catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            com.mojang.logging.LogUtils.getLogger().error("Cannot save killstreak server settings", failure);
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

    public static boolean setScorestreaks(MinecraftServer server, boolean value) {
        return update(server, Map.of("earning.scorestreaks", value ? 1D : 0D));
    }

    private static void apply(MinecraftServer server) {
        var next = configured();
        var previous = ACTIVE.put(server, next);
        if (previous == null)
            return;
        if (!previous.get("earning.scorestreaks").equals(next.get("earning.scorestreaks")))
            KillstreakService.modeConfigChanged(server);
        if (!previous.get("earning.repeatableRewards").equals(next.get("earning.repeatableRewards")))
            KillstreakService.repeatConfigChanged(server);
        if (!previous.get("rewards.mgbEnabled").equals(next.get("rewards.mgbEnabled"))
                || !previous.get("rewards.juggernautEnabled").equals(next.get("rewards.juggernautEnabled")))
            KillstreakService.rewardConfigChanged(server);
        if (!previous.get("research.disableUpgrades").equals(next.get("research.disableUpgrades"))
                || !previous.get("research.terminalEnabled").equals(next.get("research.terminalEnabled"))) {
            ResearchSystem.upgradesConfigChanged(server);
            if (!terminalEnabled(server))
                for (ServerPlayer player : server.getPlayerList().getPlayers())
                    if (player.containerMenu instanceof HeadquartersTerminalMenu)
                        player.closeContainer();
        }
    }

    /** 文件监视器可能不在服务端线程；只在服务端线程替换运行时快照。 */
    public static void reloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC)
            return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
            server.execute(() -> {
                if (ACTIVE.containsKey(server))
                    apply(server);
            });
    }

    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        ACTIVE.put(event.getServer(), configured());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.remove(event.getServer());
    }
}
