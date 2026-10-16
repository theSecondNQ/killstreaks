package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;
import net.killstreakcod.killstreaks.KillstreakRewardItem;
import net.killstreakcod.killstreaks.RewardCardItem;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.SupportHelicopterEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.compat.SuperbWarfareCompat;

import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects commands, lifecycle events and deployment to the reward ledger.
 * 对接命令、玩家生命周期及连杀部署；存储、迁移和获取规则统一交给奖励账本。
 */
public final class KillstreakService {
    public static final String WHEELSON_TAG = "killstreakcod.wheelson_unit";
    private static final String PREFIX = "killstreakcod.";
    private static final Map<LivingEntity, KillCredit> PENDING_KILLS = new IdentityHashMap<>();
    private static final Map<LivingEntity, Boolean> COUNTED_KILLS = new WeakHashMap<>();
    private static final Map<Entity, KillCredit> PENDING_VEHICLES = new IdentityHashMap<>();
    private static final Map<Entity, Boolean> COUNTED_VEHICLES = new WeakHashMap<>();

    private record KillCredit(UUID attacker, boolean support) {
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGESTIONS = (c, b) -> SharedSuggestionProvider
            .suggest(StreakReward.SELECTABLE.stream().map(StreakReward::id)
                    .filter(id -> StreakServerConfig.rewardEnabled(c.getSource().getServer(), id)), b);
    private static final SuggestionProvider<CommandSourceStack> GRANT_SUGGESTIONS = (c,
            b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream
                    .concat(StreakReward.SELECTABLE.stream().map(StreakReward::id), java.util.stream.Stream.of("mgb"))
                    .filter(id -> StreakServerConfig.rewardEnabled(c.getSource().getServer(), id)), b);

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("killstreak").executes(c -> {
            sendStatus(c.getSource().getPlayerOrException());
            return Command.SINGLE_SUCCESS;
        }).then(Commands.literal("list").executes(c -> {
            listRewards(c.getSource().getPlayerOrException());
            return 1;
        })).then(Commands.literal("loadout").then(Commands.argument("first", StringArgumentType.word())
                .suggests(SUGGESTIONS)
                .then(Commands.argument("second", StringArgumentType.word()).suggests(SUGGESTIONS).then(Commands
                        .argument("third", StringArgumentType.word()).suggests(SUGGESTIONS)
                        .executes(c -> setLoadout(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "first"), StringArgumentType.getString(c, "second"),
                                StringArgumentType.getString(c, "third")))))))
                .then(Commands.literal("mode").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("kills")
                                .executes(c -> setMode(c.getSource().getPlayerOrException(), "kills")))
                        .then(Commands.literal("score")
                                .executes(c -> setMode(c.getSource().getPlayerOrException(), "score"))))
                .then(Commands.literal("reset").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    RewardLedger.open(player).reset(RewardLedger.Reset.PROGRESS);
                    sendTerminalState(player, false);
                    player.sendSystemMessage(Component.translatable("message.killstreakcod.reset"));
                    return 1;
                }))
                .then(Commands.literal("resetall")
                        .executes(c -> resetAll(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(source -> source.hasPermission(2))
                                .executes(c -> resetAll(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
                .then(Commands.literal("give").requires(source -> source.hasPermission(2)).then(Commands
                        .argument("reward", StringArgumentType.word()).suggests(GRANT_SUGGESTIONS)
                        .executes(c -> grant(c.getSource(), List.of(c.getSource().getPlayerOrException()),
                                StringArgumentType.getString(c, "reward"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(c -> grant(c.getSource(), List.of(c.getSource().getPlayerOrException()),
                                        StringArgumentType.getString(c, "reward"),
                                        IntegerArgumentType.getInteger(c, "count")))))
                        .then(Commands.argument("targets", EntityArgument.players()).then(Commands
                                .argument("reward", StringArgumentType.word()).suggests(GRANT_SUGGESTIONS)
                                .executes(c -> grant(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                        StringArgumentType.getString(c, "reward"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> grant(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                StringArgumentType.getString(c, "reward"),
                                                IntegerArgumentType.getInteger(c, "count"))))))));
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p))
            return;
        if (!p.getTags().contains(PREFIX + "controller_given")) {
            p.addTag(PREFIX + "controller_given");
            giveItem(p, KillstreakcodMod.CONTROLLER.get().getDefaultInstance());
            p.sendSystemMessage(Component.translatable("message.killstreakcod.welcome"));
        }
        sendTerminalState(p, false);
        TacticalRuntime.syncMgb(p);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer dead) {
            TacticalRuntime.abortSession(dead);
            dead.removeTag(PREFIX + "juggernaut");
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer p))
            return;
        boolean support = event.getSource().getDirectEntity() instanceof TacticalUnitEntity
                || p.getUseItem().is(KillstreakcodMod.M134_MINIGUN.get());
        queueKill(p, victim, support);
    }

    /**
     * TaCZ posts its kill event after hurt(), which also posts LivingDeathEvent. Merge both.
     * TaCZ 在 hurt() 后发送击杀事件，而 hurt() 也会触发 LivingDeathEvent，需要合并两种来源避免重复计数。
     */
    public static void queueKill(ServerPlayer player, LivingEntity victim, boolean support) {
        if (victim == player || victim.level().isClientSide || !eligible(player, victim))
            return;
        if (COUNTED_KILLS.containsKey(victim))
            return;
        PENDING_KILLS.putIfAbsent(victim, new KillCredit(player.getUUID(), support));
    }

    /**
     * Queue confirmed vehicle destruction for server-side kill accounting.
     * 将已确认的载具击毁加入服务端击杀结算队列。
     */
    public static void queueVehicleKill(ServerPlayer player, Entity vehicle, boolean support) {
        if (player == null || vehicle.level().isClientSide || !SuperbWarfareCompat.destroyed(vehicle)
                || StreakCombat.allied(player, vehicle) || COUNTED_VEHICLES.containsKey(vehicle))
            return;
        PENDING_VEHICLES.putIfAbsent(vehicle, new KillCredit(player.getUUID(), support));
    }

    private static void flushKills(net.minecraft.server.MinecraftServer server) {
        COUNTED_KILLS.keySet().removeIf(LivingEntity::isAlive);
        // 空闲时复用空映射，避免每个服务端游戏刻都复制两个空击杀队列。
        Map<LivingEntity, KillCredit> pending = PENDING_KILLS.isEmpty() ? Map.of()
                : new IdentityHashMap<>(PENDING_KILLS);
        PENDING_KILLS.clear();
        for (var entry : pending.entrySet()) {
            LivingEntity victim = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().attacker());

            // 死亡被取消时不能发放击杀奖励，例如其他模组使目标复活的情况。
            if (player == null || !victim.isDeadOrDying() || !eligible(player, victim))
                continue;
            COUNTED_KILLS.put(victim, true);
            awardKill(player, entry.getValue().support());
        }
        COUNTED_VEHICLES.keySet().removeIf(vehicle -> !SuperbWarfareCompat.destroyed(vehicle));
        Map<Entity, KillCredit> vehicles = PENDING_VEHICLES.isEmpty() ? Map.of()
                : new IdentityHashMap<>(PENDING_VEHICLES);
        PENDING_VEHICLES.clear();
        for (var entry : vehicles.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().attacker());
            if (player == null || !SuperbWarfareCompat.destroyed(entry.getKey()))
                continue;
            COUNTED_VEHICLES.put(entry.getKey(), true);
            awardKill(player, entry.getValue().support());
        }
    }

    private static void awardKill(ServerPlayer player, boolean support) {
        RewardLedger ledger = RewardLedger.open(player);
        for (RewardLedger.Earned earned : ledger.awardKill(support))
            announce(player, earned);
        sendTerminalState(player, false, ledger.snapshot());
    }

    @SubscribeEvent
    public void clone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer p))
            return;
        if (event.getOriginal() instanceof ServerPlayer original)
            TacticalRuntime.abortSession(original);
        RewardLedger.copy(event.getOriginal(), p, event.isWasDeath());
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TacticalRuntime.resetAfterRespawn(p);
            sendTerminalState(p, false);
            TacticalRuntime.syncMgb(p);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p)
            TacticalRuntime.abortSession(p);
    }

    @SubscribeEvent
    public void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TacticalRuntime.abortSession(p);
            TacticalRuntime.syncMgb(p);
        }
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        TacticalRuntime.tick(event.getServer());
        flushKills(event.getServer());
    }

    @SubscribeEvent
    public void stopping(ServerStoppingEvent event) {
        TacticalRuntime.stop(event.getServer());
        PENDING_KILLS.clear();
        COUNTED_KILLS.clear();
        PENDING_VEHICLES.clear();
        COUNTED_VEHICLES.clear();
    }

    private static boolean eligible(ServerPlayer player, LivingEntity livingEntity) {
        return (livingEntity instanceof ServerPlayer && !livingEntity.isSpectator() || livingEntity instanceof Enemy
                || StreakCombat.boss(livingEntity)) && !(livingEntity instanceof TacticalUnitEntity)
                && !(livingEntity instanceof SupportHelicopterEntity) && !StreakCombat.allied(player, livingEntity);
    }

    private static void announce(ServerPlayer player, RewardLedger.Earned earned) {
        StreakNetwork.send(player, new StreakEarnedPayload(earned.id(), earned.count(), earned.slot(), earned.bonus(),
                VoiceFaction.get(player)));

        // 接收客户端的奖励提示负责就绪语音，让排队奖励的声音和图像在同一个客户端游戏刻开始。
    }

    private static int setLoadout(ServerPlayer player, String a, String b, String c) {
        return setLoadout(player, a, b, c, true);
    }

    private static int setLoadout(ServerPlayer player, String a, String b, String c, boolean sync) {
        if (TacticalRuntime.busy(player)) {
            player.sendSystemMessage(Component.translatable("message.killstreakcod.loadout_locked"));
            return 0;
        }
        String[] ids = { a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT), c.toLowerCase(Locale.ROOT) };
        Set<Integer> tiers = new HashSet<>();
        for (String id : ids) {
            var r = StreakReward.byId(id);
            if (r.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.killstreakcod.unknown_reward", id));
                return 0;
            }
            if (!StreakServerConfig.requireReward(player, r.get().id()))
                return 0;
            if (!tiers.add(r.get().kills())) {
                player.sendSystemMessage(Component.translatable("message.killstreakcod.same_tier"));
                return 0;
            }
        }
        RewardLedger ledger = RewardLedger.open(player);
        ledger.select(ids[0], ids[1], ids[2]);
        player.sendSystemMessage(Component.translatable("message.killstreakcod.loadout_set",
                StreakReward.byId(ids[0]).orElseThrow().displayName(),
                StreakReward.byId(ids[1]).orElseThrow().displayName(),
                StreakReward.byId(ids[2]).orElseThrow().displayName()));
        if (sync)
            sendTerminalState(player, false, ledger.snapshot());
        return 1;
    }

    private static int setMode(ServerPlayer player, String mode) {
        if (StreakServerConfig.setScorestreaks(player.serverLevel().getServer(), mode.equals("score")))
            return 1;
        player.sendSystemMessage(Component.translatable("message.killstreakcod.config_io"));
        return 0;
    }

    public static void modeConfigChanged(MinecraftServer server) {
        if (server == null)
            return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTerminalState(player, false);
            player.sendSystemMessage(Component.translatable("message.killstreakcod.mode_set",
                    Component.translatable(StreakServerConfig.scorestreaks(server) ? "mode.killstreakcod.score"
                            : "mode.killstreakcod.kills")));
        }
    }

    public static void repeatConfigChanged(MinecraftServer server) {
        if (server == null)
            return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTerminalState(player, false);
            player.sendSystemMessage(Component
                    .translatable(StreakServerConfig.repeatable(server) ? "message.killstreakcod.repeat_enabled"
                            : "message.killstreakcod.repeat_disabled"));
        }
    }

    public static void rewardConfigChanged(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            sendTerminalState(player, false);
    }

    public static void saveTerminalSelection(ServerPlayer player, SaveKillstreakLoadoutPayload in) {
        if (!VoiceFaction.valid(in.faction())) {
            sendTerminalState(player, false);
            return;
        }
        if (setLoadout(player, in.slot1(), in.slot2(), in.slot3(), false) == 1) {
            VoiceFaction.set(player, in.faction());
            player.sendSystemMessage(Component.translatable("message.killstreakcod.loadout_saved"));
        }
        sendTerminalState(player, false);
    }

    public static void sendTerminalState(ServerPlayer player, boolean open) {
        sendTerminalState(player, open, RewardLedger.open(player).snapshot());
    }

    private static void sendTerminalState(ServerPlayer player, boolean open, RewardLedger.Snapshot state) {
        String[] ids = new String[3];
        int[] counts = new int[3], progress = new int[3];
        int mask = 0;
        for (int i = 0; i < state.slots().size(); i++) {
            RewardLedger.Slot slot = state.slots().get(i);
            ids[i] = slot.reward().id();
            counts[i] = slot.count();
            progress[i] = slot.progress();
            if (counts[i] > 0)
                mask |= 1 << i;
        }
        StreakNetwork.send(player,
                new TerminalStatePayload(ids[0], ids[1], ids[2], state.scoreMode(), state.kills(), state.score(), mask,
                        open, counts, progress, !TacticalRuntime.busy(player), state.mgbCount(),
                        VoiceFaction.get(player),
                        StreakServerConfig.rewardEnabled(player.serverLevel().getServer(), "juggernaut")));
        StreakNetwork.send(player, new BonusStatePayload(String.join(",", state.bonuses()), state.mgbCount() > 0));
    }

    public static void useSlot(ServerPlayer player, int slot) {
        if (!player.isAlive() || player.isSpectator() || TacticalRuntime.mgbDetonating(player.serverLevel())
                || TacticalRuntime.busy(player))
            return;
        RewardLedger ledger = RewardLedger.open(player);
        RewardLedger.Snapshot state = ledger.snapshot();
        if (slot == 4) {
            if (!StreakServerConfig.requireReward(player, "mgb"))
                return;
            if (!TacticalRuntime.mgbActive(player.serverLevel()) && ledger.consume(slot)) {
                TacticalRuntime.launchMgb(player);
                sendTerminalState(player, false);
            }
            return;
        }
        if (slot == 3) {
            if (state.bonuses().isEmpty())
                return;
            StreakReward reward = StreakReward.byId(state.bonuses().getFirst()).orElseThrow();
            if (StreakAirspace.player(player, reward) && ledger.consume(slot)) {
                TacticalRuntime.deploy(player, reward, true);
                sendTerminalState(player, false);
            }
            return;
        }
        if (slot < 0 || slot > 2)
            return;
        RewardLedger.Slot selected = state.slots().get(slot);
        StreakReward reward = selected.reward();
        if (!StreakServerConfig.requireReward(player, reward.id()))
            return;
        if (selected.count() == 0) {
            player.displayClientMessage(Component.translatable("message.killstreakcod.not_ready", reward.displayName()),
                    true);
            return;
        }
        if (StreakAirspace.player(player, reward) && ledger.consume(slot)) {
            TacticalRuntime.deploy(player, reward, false);
            sendTerminalState(player, false);
        }
    }

    public static void refund(ServerPlayer player, StreakReward reward, boolean bonus) {
        if (bonus)
            addBonus(player, reward, false);
        else {
            RewardLedger.open(player).refundStock(reward.id());
            sendTerminalState(player, false);
        }
    }

    public static void refundMgb(ServerPlayer player) {
        RewardLedger.open(player).refundStock("mgb");
        sendTerminalState(player, false);
    }

    public static boolean grantBonus(ServerPlayer player, StreakReward reward) {
        if (!StreakServerConfig.requireReward(player, reward.id()))
            return false;
        addBonus(player, reward, true);
        return true;
    }

    /**
     * Server-authoritative item redemption; a full queue does not consume the voucher.
     * 物品兑换由服务端确认，奖励队列满时不消耗凭证。
     */
    public static boolean claimReward(ServerPlayer player, String id) {
        if (!player.isAlive() || player.isSpectator())
            return false;
        StreakReward reward = StreakReward.byId(id).orElse(null);
        if (reward == null && !id.equals("mgb"))
            return false;
        if (!StreakServerConfig.requireReward(player, id))
            return false;
        RewardLedger ledger = RewardLedger.open(player);
        RewardLedger.Grant grant = ledger.grant(id, 1, RewardLedger.Overflow.REJECT);
        if (!grant.accepted()) {
            player.displayClientMessage(Component.translatable("message.killstreakcod.reward_storage_full"), true);
            return false;
        }
        announce(player, grant.earned());
        sendTerminalState(player, false, ledger.snapshot());
        return true;
    }

    private static void addBonus(ServerPlayer player, StreakReward reward, boolean notify) {
        RewardLedger ledger = RewardLedger.open(player);
        if (!ledger.addBonus(reward))
            giveItem(player, RewardCardItem.card(reward));
        if (notify)
            announce(player, new RewardLedger.Earned(reward.id(), 1, 3, true));
        sendTerminalState(player, false, ledger.snapshot());
    }

    private static int grant(CommandSourceStack source, Collection<ServerPlayer> players, String name, int amount) {
        String id = name.toLowerCase(Locale.ROOT);
        StreakReward reward = StreakReward.byId(id).orElse(null);
        if (reward == null && !id.equals("mgb")) {
            source.sendFailure(Component.translatable("message.killstreakcod.unknown_reward", name));
            return 0;
        }
        if (!StreakServerConfig.rewardEnabled(source.getServer(), id)) {
            source.sendFailure(Component.translatable("message.killstreakcod.reward_disabled",
                    Component.translatable("streak.killstreakcod." + id)));
            return 0;
        }
        for (ServerPlayer player : players) {
            RewardLedger ledger = RewardLedger.open(player);
            RewardLedger.Grant grant = ledger.grant(id, amount, RewardLedger.Overflow.VOUCHER);
            if (!grant.accepted())
                continue;
            for (int i = 0; i < grant.vouchers(); i++)
                giveItem(player, RewardCardItem.card(reward));
            announce(player, grant.earned());
            sendTerminalState(player, false, ledger.snapshot());
        }
        source.sendSuccess(() -> Component.translatable("message.killstreakcod.given", amount,
                Component.translatable("streak.killstreakcod." + id), players.size()), true);
        return players.size();
    }

    private static int resetAll(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            TacticalRuntime.abortSession(player);
            PENDING_KILLS.entrySet().removeIf(entry -> entry.getValue().attacker().equals(player.getUUID()));
            PENDING_VEHICLES.entrySet().removeIf(entry -> entry.getValue().attacker().equals(player.getUUID()));
            RewardLedger.open(player).reset(RewardLedger.Reset.ALL);
            player.stopUsingItem();
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item.getItem() instanceof KillstreakRewardItem
                        || item.is(KillstreakcodMod.CARE_PACKAGE_REWARD.get())
                        || item.is(KillstreakcodMod.SENTRY_GUN.get())
                        || item.is(KillstreakcodMod.WHEELSON_CONTROLLER.get())
                        || item.is(KillstreakcodMod.DESIGNATOR.get()) || item.is(KillstreakcodMod.DROP_MARKER.get())
                        || item.is(KillstreakcodMod.CLUSTER_MINE.get()))
                    inventory.setItem(slot, ItemStack.EMPTY);
            }
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            StreakNetwork.send(player, new StreakPresentationPayload("", "reset_rewards", 0));
            sendTerminalState(player, false);
        }
        source.sendSuccess(() -> Component.translatable("message.killstreakcod.reset_all", players.size()), true);
        return players.size();
    }

    public static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    public static void sendStatus(ServerPlayer player) {
        RewardLedger.Snapshot state = RewardLedger.open(player).snapshot();
        player.sendSystemMessage(Component.translatable("message.killstreakcod.status", state.kills(), state.score(),
                Component.translatable(state.scoreMode() ? "mode.killstreakcod.score" : "mode.killstreakcod.kills")));
        for (int i = 0; i < state.slots().size(); i++) {
            RewardLedger.Slot slot = state.slots().get(i);
            StreakReward reward = slot.reward();
            player.sendSystemMessage(
                    Component.translatable("message.killstreakcod.slot_status", i + 1, reward.displayName(),
                            slot.count(), slot.progress(), state.scoreMode() ? reward.score() : reward.kills()));
        }
        player.sendSystemMessage(Component.translatable("message.killstreakcod.help"));
    }

    private static void listRewards(ServerPlayer player) {
        for (var reward : StreakReward.SELECTABLE)
            if (StreakServerConfig.rewardEnabled(player.serverLevel().getServer(), reward.id()))
                player.sendSystemMessage(Component.translatable("message.killstreakcod.reward_entry", reward.id(),
                        reward.displayName(), reward.kills(), reward.score()));
    }

}
