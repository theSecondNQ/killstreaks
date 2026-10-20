package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owns research, equipment, prepaid requisitions and calls that consume earned rewards.
 * 在服务端统一管理研究、升级装卸和预付储备；击杀达标消耗储备，呼叫消耗已获得的奖励。
 */
public final class ResearchSystem {
    private ResearchSystem() {}

    private static HeadquartersResearchData data(ServerPlayer player) {
        return HeadquartersResearchData.get(player.serverLevel().getServer());
    }

    private static HeadquartersResearchData.Progress progress(ServerPlayer player) {
        var data = data(player);
        var progress = data.player(player);
        if (progress.streakMode && !player.isCreative() && !canSwitch(player, progress)) {
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

    public static ResearchMask activeUpgrades(ServerPlayer player) {
        if (StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()))
            return ResearchMask.EMPTY;
        var progress = progress(player);
        return progress.unlocked.intersect(progress.equipped);
    }

    private static boolean complete(ServerPlayer player, HeadquartersResearchData.Progress progress) {
        ResearchMask required = StreakServerConfig.upgradesDisabled(player.serverLevel().getServer())
                ? ResearchCatalog.baseMask()
                : ResearchCatalog.fullMask();
        return progress.tier == 4 && progress.unlocked.intersect(required).equals(required);
    }

    private static boolean canSwitch(ServerPlayer player, HeadquartersResearchData.Progress progress) {
        return progress.canSwitch() || complete(player, progress);
    }

    public static boolean upgraded(ServerPlayer player, String node) {
        return ResearchCatalog.has(activeUpgrades(player), node);
    }

    static boolean earnable(ServerPlayer player, String id) {
        return !survival(player)
                || ResearchCatalog.has(progress(player).unlocked, id) && requisitionCount(player, id) > 0;
    }

    private static int requisitionCount(ServerPlayer player, String id) {
        return progress(player).requisitions.getOrDefault(id, 0);
    }

    /**
     * Called within the earning ledger operation; never opens another ledger or sends a stale snapshot.
     * 在获取奖励的账本操作内扣储备，不重新打开账本或发送尚未保存的库存快照。
     */
    static int consumeRequisitions(ServerPlayer player, String id, int requested) {
        if (requested <= 0)
            return 0;
        if (!survival(player))
            return requested;
        var progress = progress(player);
        int available = progress.requisitions.getOrDefault(id, 0);
        int consumed = Math.min(available, requested);
        if (consumed == 0)
            return 0;
        if (consumed == available)
            progress.requisitions.remove(id);
        else
            progress.requisitions.put(id, available - consumed);
        data(player).setDirty();
        return consumed;
    }

    static void clearRequisitions(ServerPlayer player) {
        var progress = progress(player);
        if (!progress.requisitions.isEmpty()) {
            progress.requisitions.clear();
            data(player).setDirty();
        }
    }

    private static boolean researched(ServerPlayer player, StreakReward reward) {
        return ResearchCatalog.has(progress(player).unlocked, reward.id());
    }

    private static ResearchCatalog.Cost requisitionCost(ServerPlayer player, ResearchCatalog.Definition definition) {
        return !StreakServerConfig.upgradesDisabled(player.serverLevel().getServer())
                && progress(player).repairs.contains(definition.research().index()) ? definition.repair()
                        : definition.call();
    }

    private static String prepare(ServerPlayer player, String id) {
        ResearchCatalog.Definition definition = ResearchCatalog.definition(id);
        if (!player.isAlive() || player.isSpectator())
            return "busy";
        if (definition == null || !player.isCreative() && !ResearchCatalog.has(progress(player).unlocked, id))
            return "locked";
        if (!survival(player) && !player.isCreative())
            return "prepare_mode";
        if (!StreakServerConfig.rewardEnabled(player.serverLevel().getServer(), id))
            return "disabled";
        int available = requisitionCount(player, id);
        if (available == Integer.MAX_VALUE)
            return "requisition_full";
        if (!requisitionCost(player, definition).pay(player))
            return "materials";
        var progress = progress(player);
        progress.requisitions.put(id, available + 1);
        progress.repairs = progress.repairs.subtract(definition.research().bit());
        data(player).setDirty();
        return "prepared";
    }

    public static boolean call(ServerPlayer player, StreakReward reward) {
        return call(player, reward.id());
    }

    public static boolean call(ServerPlayer player, String id) {
        ResearchCatalog.Definition definition = ResearchCatalog.definition(id);
        String failure = callFailure(player, id, definition);
        if (!failure.isEmpty()) {
            player.displayClientMessage(message(failure), true);
            return false;
        }
        RewardLedger ledger = RewardLedger.open(player);
        if (ledger.count(id) == 0) {
            player.displayClientMessage(message("not_earned"), true);
            return false;
        }
        StreakReward reward = definition.reward();
        if (reward != null && !StreakAirspace.player(player, reward))
            return false;
        // Materials were paid at the terminal; activation consumes only the earned, callable reward.
        // 材料已在终端预付；此时只消耗击杀达标后获得的可呼叫奖励。
        if (!ledger.consume(id))
            return false;
        if (player.containerMenu instanceof HeadquartersTerminalMenu)
            player.closeContainer();
        if (reward == null)
            TacticalRuntime.launchMgb(player);
        else
            TacticalRuntime.deploy(player, reward, false);
        KillstreakService.sendTerminalState(player, false);
        return true;
    }

    private static String callFailure(ServerPlayer player, String id, ResearchCatalog.Definition definition) {
        if (!player.isAlive() || player.isSpectator() || TacticalRuntime.busy(player)
                || TacticalRuntime.mgbDetonating(player.serverLevel())
                || id.equals("mgb") && TacticalRuntime.mgbActive(player.serverLevel()))
            return "busy";
        if (ResearchEffects.jammed(player))
            return "jammed";
        if (!survival(player) && !player.isCreative())
            return "call_mode";
        if (definition == null || !player.isCreative() && !ResearchCatalog.has(progress(player).unlocked, id))
            return "locked";
        if (!StreakServerConfig.rewardEnabled(player.serverLevel().getServer(), id))
            return "disabled";
        return "";
    }

    public static void destroyed(TacticalUnitEntity unit) {
        ServerPlayer owner = unit.owner();
        StreakReward reward = ResearchEffects.reward(unit.kind());
        var definition = reward == null ? null : ResearchCatalog.definition(reward);
        if (owner == null || definition == null || unit.researchUpgrades.isEmpty())
            return;
        var progress = progress(owner);
        progress.repairs = progress.repairs.union(definition.research().bit());
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
                yield call(player, action.target()) ? "called" : "call_failed";
            }
            case ResearchActionPayload.MODE -> changeMode(player, action.target());
            case ResearchActionPayload.UNLOCK_ALL -> unlockAll(player);
            case ResearchActionPayload.EQUIP -> equip(player, action.target(), true);
            case ResearchActionPayload.UNEQUIP -> equip(player, action.target(), false);
            case ResearchActionPayload.PREPARE -> prepare(player, action.target());
            default -> "invalid";
        };
        sendState(player, result);
        KillstreakService.sendTerminalState(player, false);
    }

    private static String research(ServerPlayer player, String id) {
        ResearchCatalog.Node node = ResearchCatalog.node(id);
        if (node == null)
            return "invalid";
        if (!node.base() && StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()))
            return "upgrades_disabled";
        var progress = progress(player);
        if (ResearchCatalog.has(progress.unlocked, id))
            return "already";
        if (!player.isCreative() && (progress.tier < node.tier() || !node.base() && !researched(player, node.reward())))
            return "requirements";
        if (!node.cost().pay(player))
            return "materials";
        progress.unlocked = progress.unlocked.union(node.bit());
        progress.equipped = progress.equipped.union(node.bit());
        progress.modeUnlocked |= progress.complete();
        data(player).setDirty();
        return complete(player, progress) ? "complete" : "researched";
    }

    private static String equip(ServerPlayer player, String id, boolean equipped) {
        ResearchCatalog.Node node = ResearchCatalog.node(id);
        if (node == null || node.base())
            return "invalid";
        if (StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()))
            return "upgrades_disabled";
        var progress = progress(player);
        if (!ResearchCatalog.has(progress.unlocked, id))
            return "upgrade_locked";
        // Equipment changes are free and affect future deployments; existing missions retain their snapshot.
        // 装卸免费且只影响后续部署；正在执行的任务继续使用生成时保存的升级快照。
        if (equipped)
            progress.equipped = progress.equipped.union(node.bit());
        else
            progress.equipped = progress.equipped.subtract(node.bit());
        data(player).setDirty();
        return equipped ? "equipped" : "unequipped";
    }

    private static String upgradeTerminal(ServerPlayer player) {
        var progress = progress(player);
        ResearchCatalog.Cost cost = ResearchCatalog.terminalCost(progress.tier);
        if (cost == null)
            return "maximum";
        if (!cost.pay(player))
            return "materials";
        progress.tier++;
        progress.modeUnlocked |= progress.complete();
        data(player).setDirty();
        return complete(player, progress) ? "complete" : "upgraded";
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
        } else if (mode.equals("killstreak") && (player.isCreative() || canSwitch(player, progress))) {
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
        // Unlocking the remainder must not reinstall upgrades the player deliberately removed.
        // 补齐剩余研究时，不重新装配玩家已经主动卸下的升级。
        ResearchMask available = StreakServerConfig.upgradesDisabled(player.serverLevel().getServer())
                ? ResearchCatalog.baseMask()
                : ResearchCatalog.fullMask();
        progress.equipped = progress.equipped.union(available.subtract(progress.unlocked));
        progress.unlocked = progress.unlocked.union(available);
        progress.modeUnlocked |= progress.complete();
        data(player).setDirty();
        return "complete";
    }

    public static void sendState(ServerPlayer player, String message) {
        var progress = progress(player);
        RewardLedger ledger = RewardLedger.open(player);
        int[] stocks = StreakReward.SELECTABLE.stream().mapToInt(ledger::count).toArray();
        int[] requisitions = StreakReward.SELECTABLE.stream()
                .mapToInt(reward -> progress.requisitions.getOrDefault(reward.id(), 0)).toArray();
        int container = player.containerMenu instanceof HeadquartersTerminalMenu ? player.containerMenu.containerId
                : -1;
        StreakNetwork.send(player,
                new ResearchStatePayload(container, progress.tier, progress.unlocked, progress.equipped,
                        progress.repairs, progress.streakMode, player.isCreative() || canSwitch(player, progress),
                        player.isCreative(), TacticalRuntime.busy(player), message, stocks, ledger.count("mgb"),
                        StreakServerConfig.upgradesDisabled(player.serverLevel().getServer()), requisitions,
                        progress.requisitions.getOrDefault("mgb", 0)));
    }

    public static void upgradesConfigChanged(MinecraftServer server) {
        ResearchEffects.upgradesConfigChanged(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendState(player, "");
            KillstreakService.sendTerminalState(player, false);
        }
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
