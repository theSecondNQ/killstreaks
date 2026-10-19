package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

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

/**
 * One native global config. Gameplay always reads the hosting server's values.
 * 使用一份原生全局配置，玩法始终读取当前服务器的配置值。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class StreakServerConfig {
    public static final String FILE_NAME = "killstreakcod-server.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SCORESTREAKS, REPEATABLE_REWARDS;
    public static final ModConfigSpec.BooleanValue MGB_ENABLED, JUGGERNAUT_ENABLED;
    public static final ModConfigSpec.BooleanValue DISABLE_UPGRADES;
    private static final Map<MinecraftServer, Options> ACTIVE = new WeakHashMap<>();

    private record Options(boolean scorestreaks, boolean repeatableRewards, boolean mgbEnabled,
            boolean juggernautEnabled, boolean disableUpgrades) {
    }

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("连杀奖励获取设置，对本游戏实例的所有存档生效。联机时由服务端配置决定。",
                "Global killstreak earning rules for every world. Multiplayer uses the hosting server's file.")
                .translation("killstreakcod.configuration.earning").push("earning");
        SCORESTREAKS = builder
                .comment("是否按得分获取奖励。true：得分获取；false：击杀获取。", "切换模式时保留已获得奖励，清空普通奖励尚未完成的进度。",
                        "Use scorestreaks instead of killstreaks. Earned stock is retained when changing mode.",
                        "Default: false", "Allowed Values: true, false")
                .translation("killstreakcod.configuration.scorestreaks").define("scorestreaks", false);
        REPEATABLE_REWARDS = builder.comment("是否允许连杀奖励重复获取。", "true：每种奖励独立循环累计，死亡不清进度。",
                "false：每种奖励每条命只自然获得一次，死亡清进度、保留已获得奖励。",
                "Allow repeated rewards across deaths; false means once per reward per life with stock retained.",
                "Default: true", "Allowed Values: true, false")
                .translation("killstreakcod.configuration.repeatableRewards").define("repeatableRewards", true);
        builder.pop();
        builder.comment("特殊连杀奖励开关，由服务器统一控制。", "Server-authoritative availability of special killstreak rewards.")
                .translation("killstreakcod.configuration.rewards").push("rewards");
        MGB_ENABLED = builder
                .comment("是否启用核弹（MGB）。默认关闭。", "关闭时不累计核弹进度，也不能获取或启动核弹。",
                        "Enable MGB earning and activation. Default: false.")
                .translation("killstreakcod.configuration.mgbEnabled").define("mgbEnabled", false);
        JUGGERNAUT_ENABLED = builder
                .comment("是否启用无畏战甲。默认关闭。", "关闭时不能选择、获取或使用该奖励，空投随机奖励也会排除它。",
                        "Enable Juggernaut selection, acquisition and use. Default: false.")
                .translation("killstreakcod.configuration.juggernautEnabled").define("juggernautEnabled", false);
        builder.pop();
        builder.comment("连杀研究升级规则，由服务器统一控制。", "Server-authoritative killstreak research upgrade rules.")
                .translation("killstreakcod.configuration.research").push("research");
        DISABLE_UPGRADES = builder.comment("是否禁用所有连杀升级项目。默认关闭，即允许升级。", "true：禁止研究和装配升级，两种模式均不应用升级效果；已保存的研究和装配选择保留。",
                "禁用期间，四级终端和全部基础连杀研究即可开放连杀模式。",
                "Disable upgrade research, equipment and effects in both modes, retaining saved progress and loadouts.",
                "Default: false", "Allowed Values: true, false")
                .translation("killstreakcod.configuration.disableUpgrades").define("disableUpgrades", false);
        builder.pop();
        SPEC = builder.build();
    }

    private StreakServerConfig() {}

    private static Options configured() {
        return new Options(SCORESTREAKS.get(), REPEATABLE_REWARDS.get(), MGB_ENABLED.get(), JUGGERNAUT_ENABLED.get(),
                DISABLE_UPGRADES.get());
    }

    private static Options current(MinecraftServer server) {
        return ACTIVE.computeIfAbsent(server, key -> configured());
    }

    public static boolean scorestreaks(MinecraftServer server) {
        return current(server).scorestreaks;
    }

    public static boolean repeatable(MinecraftServer server) {
        return current(server).repeatableRewards;
    }

    public static boolean upgradesDisabled(MinecraftServer server) {
        return current(server).disableUpgrades;
    }

    /**
     * Use the hosting server's cached options for every reward acquisition and activation path.
     * 所有奖励获取及启动入口统一读取当前服务器缓存的配置。
     */
    public static boolean rewardEnabled(MinecraftServer server, String reward) {
        Options options = current(server);
        return switch (reward.toLowerCase(Locale.ROOT)) {
            case "mgb" -> options.mgbEnabled;
            case "juggernaut" -> options.juggernautEnabled;
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

    private static void apply(MinecraftServer server) {
        Options next = configured(), previous = ACTIVE.put(server, next);
        if (previous == null)
            return;
        if (previous.scorestreaks != next.scorestreaks)
            KillstreakService.modeConfigChanged(server);
        if (previous.repeatableRewards != next.repeatableRewards)
            KillstreakService.repeatConfigChanged(server);
        if (previous.mgbEnabled != next.mgbEnabled || previous.juggernautEnabled != next.juggernautEnabled)
            KillstreakService.rewardConfigChanged(server);
        if (previous.disableUpgrades != next.disableUpgrades)
            ResearchSystem.upgradesConfigChanged(server);
    }

    public static boolean setScorestreaks(MinecraftServer server, boolean value) {
        boolean previous = SCORESTREAKS.get();
        try {
            SCORESTREAKS.set(value);
            SPEC.save();
            apply(server);
            return true;
        }
        catch (RuntimeException failure) {
            SCORESTREAKS.set(previous);
            com.mojang.logging.LogUtils.getLogger().error("Cannot save global killstreak config {}", FILE_NAME,
                    failure);
            return false;
        }
    }

    /**
     * FML's file watcher may dispatch outside the server thread. Never change player state there.
     * FML 文件监视器可能在服务端线程之外触发回调，不能在那里修改玩家状态。
     */
    public static void reloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC)
            return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
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
