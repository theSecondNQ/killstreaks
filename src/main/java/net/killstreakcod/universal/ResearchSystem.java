package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owns research, gameplay modes and calls that require both an earned charge and materials.
 * 在服务端统一管理研究与玩法模式；生存呼叫同时需要已获得的连杀次数和材料。
 */
public final class ResearchSystem {
    private ResearchSystem() {}

    private static HeadquartersResearchData data(ServerPlayer player) {
        return HeadquartersResearchData.get(player.serverLevel().getServer());
    }

    private static HeadquartersResearchData.Progress progress(ServerPlayer player) {
        var data = data(player);
        var progress = data.player(player);
        if (progress.streakMode && !player.isCreative() && !progress.legacyAccess && !progress.complete()) {
            progress.streakMode = false;
            data.setDirty();
        }
        return progress;
    }

    public static void login(ServerPlayer player) {
        progress(player);
    }

    public static boolean survival(ServerPlayer player) {
        return !progress(player).streakMode;
    }

    public static long activeUpgrades(ServerPlayer player) {
        var progress = progress(player);
        return progress.streakMode ? 0 : progress.unlocked;
    }

    public static boolean upgraded(ServerPlayer player, String node) {
        return ResearchCatalog.has(activeUpgrades(player), node);
    }

    static boolean earnable(ServerPlayer player, String id) {
        return !survival(player) || id.equals("mgb") || ResearchCatalog.has(progress(player).unlocked, id);
    }

    private static boolean researched(ServerPlayer player, StreakReward reward) {
        return ResearchCatalog.has(progress(player).unlocked, reward.id());
    }

    private static ResearchCatalog.Cost callCost(ServerPlayer player, StreakReward reward) {
        var definition = ResearchCatalog.definition(reward);
        return (progress(player).repairs & definition.research().bit()) != 0 ? definition.repair() : definition.call();
    }

    public static boolean call(ServerPlayer player, StreakReward reward) {
        String failure = callFailure(player, reward);
        if (!failure.isEmpty()) {
            player.displayClientMessage(message(failure), true);
            return false;
        }
        RewardLedger ledger = RewardLedger.open(player);
        if (ledger.count(reward) == 0) {
            player.displayClientMessage(message("not_earned"), true);
            return false;
        }
        if (!StreakAirspace.player(player, reward))
            return false;
        ResearchCatalog.Cost cost = callCost(player, reward);
        if (!cost.affordable(player)) {
            player.displayClientMessage(message("materials"), true);
            return false;
        }
        // Consume one real charge, then pay. A rejected payment restores that charge before returning.
        // 先消耗一次真实奖励再支付材料；支付未通过时，立即退还这次奖励。
        if (!ledger.consume(reward))
            return false;
        if (!cost.pay(player)) {
            ledger.refundStock(reward.id());
            player.displayClientMessage(message("materials"), true);
            return false;
        }
        var progress = progress(player);
        progress.repairs &= ~ResearchCatalog.definition(reward).research().bit();
        data(player).setDirty();
        if (player.containerMenu instanceof HeadquartersTerminalMenu)
            player.closeContainer();
        TacticalRuntime.deploy(player, reward, false);
        KillstreakService.sendTerminalState(player, false);
        return true;
    }

    private static String callFailure(ServerPlayer player, StreakReward reward) {
        if (!player.isAlive() || player.isSpectator() || TacticalRuntime.busy(player)
                || TacticalRuntime.mgbDetonating(player.serverLevel()))
            return "busy";
        if (ResearchEffects.jammed(player))
            return "jammed";
        if (!survival(player) && !player.isCreative())
            return "call_mode";
        if (ResearchCatalog.definition(reward) == null || !player.isCreative() && !researched(player, reward))
            return "locked";
        if (!StreakServerConfig.rewardEnabled(player.serverLevel().getServer(), reward.id()))
            return "disabled";
        return "";
    }

    public static void destroyed(TacticalUnitEntity unit) {
        ServerPlayer owner = unit.owner();
        StreakReward reward = ResearchEffects.reward(unit.kind());
        var definition = reward == null ? null : ResearchCatalog.definition(reward);
        if (owner == null || definition == null || unit.researchUpgrades == 0)
            return;
        var progress = progress(owner);
        progress.repairs |= definition.research().bit();
        data(owner).setDirty();
        KillstreakService.sendTerminalState(owner, false);
    }

    public static void action(ServerPlayer player, ResearchActionPayload action) {
        if (!(player.containerMenu instanceof HeadquartersTerminalMenu menu) || menu.containerId != action.containerId()
                || !menu.stillValid(player) || !player.isAlive() || player.isSpectator()
                || !menu.acceptAction(player.serverLevel().getGameTime()))
            return;
        String result = switch (action.action()) {
            case ResearchActionPayload.REQUEST -> "";
            case ResearchActionPayload.RESEARCH -> research(player, action.target());
            case ResearchActionPayload.UPGRADE -> upgradeTerminal(player);
            case ResearchActionPayload.CALL -> {
                StreakReward reward = StreakReward.byId(action.target()).orElse(null);
                yield reward != null && call(player, reward) ? "called" : "call_failed";
            }
            case ResearchActionPayload.MODE -> changeMode(player, action.target());
            case ResearchActionPayload.UNLOCK_ALL -> unlockAll(player);
            default -> "invalid";
        };
        sendState(player, result);
        KillstreakService.sendTerminalState(player, false);
    }

    private static String research(ServerPlayer player, String id) {
        ResearchCatalog.Node node = ResearchCatalog.node(id);
        if (node == null)
            return "invalid";
        var progress = progress(player);
        if (ResearchCatalog.has(progress.unlocked, id))
            return "already";
        if (!player.isCreative() && (progress.tier < node.tier() || !node.base() && !researched(player, node.reward())))
            return "requirements";
        if (!node.cost().pay(player))
            return "materials";
        progress.unlocked |= node.bit();
        data(player).setDirty();
        return progress.complete() ? "complete" : "researched";
    }

    private static String upgradeTerminal(ServerPlayer player) {
        var progress = progress(player);
        ResearchCatalog.Cost cost = ResearchCatalog.terminalCost(progress.tier);
        if (cost == null)
            return "maximum";
        if (!cost.pay(player))
            return "materials";
        progress.tier++;
        data(player).setDirty();
        return progress.complete() ? "complete" : "upgraded";
    }

    private static String changeMode(ServerPlayer player, String mode) {
        if (TacticalRuntime.busy(player))
            return "busy";
        var progress = progress(player);
        if (mode.equals("survival")) {
            progress.streakMode = false;
            // Choosing survival retires the legacy exemption permanently; completed research can unlock it again.
            // 主动进入生存模式后永久收回旧存档豁免，之后通过完成研究重新开放连杀模式。
            progress.legacyAccess = false;
        } else if (mode.equals("killstreak") && (player.isCreative() || progress.complete() || progress.legacyAccess)) {
            progress.streakMode = true;
        } else {
            return "mode_locked";
        }
        data(player).setDirty();
        return "mode_changed";
    }

    private static String unlockAll(ServerPlayer player) {
        if (!player.isCreative())
            return "creative";
        var progress = progress(player);
        progress.tier = 4;
        progress.unlocked = ResearchCatalog.fullMask();
        data(player).setDirty();
        return "complete";
    }

    public static void sendState(ServerPlayer player, String message) {
        var progress = progress(player);
        RewardLedger ledger = RewardLedger.open(player);
        int[] stocks = StreakReward.SELECTABLE.stream().mapToInt(ledger::count).toArray();
        int container = player.containerMenu instanceof HeadquartersTerminalMenu ? player.containerMenu.containerId
                : -1;
        StreakNetwork.send(player,
                new ResearchStatePayload(container, progress.tier, progress.unlocked, progress.repairs,
                        progress.streakMode, player.isCreative() || progress.complete() || progress.legacyAccess,
                        player.isCreative(), TacticalRuntime.busy(player), message, stocks));
    }

    public static Component message(String code) {
        return Component.translatable("research.killstreakcod.message." + code);
    }

    public static void tick(MinecraftServer server) {
        ResearchEffects.tick(server);
        if (server.getTickCount() % 20 == 0)
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                if (survival(player) || player.containerMenu instanceof HeadquartersTerminalMenu)
                    KillstreakService.sendTerminalState(player, false);
    }
}
