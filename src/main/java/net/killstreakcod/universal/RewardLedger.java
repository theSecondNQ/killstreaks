package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.StreakReward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Owns reward accounting and the existing player-tag save format for one server-thread operation.
 * 在一次服务端操作内统一管理奖励账本及既有玩家标签存储格式，不跨玩家、重生或世界缓存状态。
 */
final class RewardLedger {
    private static final String PREFIX = "killstreakcod.";
    private static final String KILLS = PREFIX + "kills=", SCORE = PREFIX + "score=", MODE = PREFIX + "mode=";
    private static final String LOADOUT = PREFIX + "loadout.", STOCK = PREFIX + "stock.", READY = PREFIX + "ready=";
    private static final String PROGRESS = PREFIX + "progress.", BONUS = PREFIX + "bonus=";
    private static final String MIGRATED = PREFIX + "ledger_v21", LIFE_EARNED = PREFIX + "life_earned=";
    private static final List<StreakReward> DEFAULT = List.of(StreakReward.UAV, StreakReward.CARE_PACKAGE,
            StreakReward.CRUISE_MISSILE);
    private static final int BONUS_CAPACITY = 16, MGB_THRESHOLD = 30;

    enum Overflow {
        VOUCHER, REJECT
    }

    enum Reset {
        PROGRESS, LIFE, ALL
    }

    record Slot(StreakReward reward, int count, int progress) {
    }

    record Snapshot(boolean scoreMode, int kills, int score, List<Slot> slots, int mgbCount, List<String> bonuses) {
    }

    record Earned(String id, int count, int slot, boolean bonus) {
    }

    record Grant(boolean accepted, Earned earned, int vouchers) {
        private static final Grant REJECTED = new Grant(false, null, 0);
    }

    private final ServerPlayer player;
    private final boolean scoreMode, repeatable, mgbEnabled, juggernautEnabled;
    private final Set<String> originalTags, tags;
    private final Map<String, List<String>> keyedTags = new HashMap<>();
    private final Map<String, String> values = new HashMap<>();
    private final List<String> bonuses = new ArrayList<>();
    private final StreakReward[] loadout = new StreakReward[3];
    private boolean dirty;

    private RewardLedger(ServerPlayer player) {
        this.player = player;
        var server = player.serverLevel().getServer();
        scoreMode = StreakServerConfig.scorestreaks(server);
        repeatable = StreakServerConfig.repeatable(server);
        mgbEnabled = StreakServerConfig.rewardEnabled(server, "mgb");
        juggernautEnabled = StreakServerConfig.rewardEnabled(server, "juggernaut");
        originalTags = new HashSet<>();
        for (String tag : player.getTags()) {
            if (!tag.startsWith(PREFIX))
                continue;
            originalTags.add(tag);
            String key = key(tag);
            if (key != null) {
                keyedTags.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tag);
                values.putIfAbsent(key, tag.substring(key.length()));
            }
        }
        tags = new HashSet<>(originalTags);
        for (int slot = 0; slot < loadout.length; slot++) {
            String key = LOADOUT + (slot + 1);
            loadout[slot] = StreakReward.byId(values.get(key)).orElse(DEFAULT.get(slot));
            put(key, loadout[slot].id());
        }
        String queue = values.getOrDefault(BONUS, "");
        if (!queue.isEmpty())
            bonuses.addAll(List.of(queue.split(",")));
        migrate();
        save();
    }

    static RewardLedger open(ServerPlayer player) {
        return new RewardLedger(player);
    }

    static void copy(Player original, ServerPlayer replacement, boolean death) {
        for (String tag : original.getTags())
            if (tag.startsWith(PREFIX))
                replacement.addTag(tag);
        replacement.removeTag(PREFIX + "juggernaut");
        if (death)
            open(replacement).reset(Reset.LIFE);
    }

    Snapshot snapshot() {
        List<Slot> slots = new ArrayList<>(loadout.length);
        for (StreakReward reward : loadout) {
            boolean enabled = enabled(reward.id());
            int progress = !repeatable && tags.contains(LIFE_EARNED + reward.id())
                    ? threshold(reward)
                    : number(PROGRESS + reward.id() + "=");
            slots.add(new Slot(reward, enabled ? stock(reward.id()) : 0, enabled ? progress : 0));
        }
        return new Snapshot(scoreMode, number(KILLS), number(SCORE), List.copyOf(slots),
                mgbEnabled ? stock("mgb") : 0,
                bonuses.stream().filter(id -> StreakReward.byId(id).isPresent() && enabled(id)).toList());
    }

    List<Earned> awardKill(boolean support) {
        addNumber(SCORE, support ? 25 : 125);
        List<Earned> earned = new ArrayList<>();
        if (!support) {
            addNumber(KILLS, 1);
            earn("mgb", MGB_THRESHOLD, 1, 4, earned);
        }
        int amount = scoreMode ? (support ? 25 : 125) : support ? 0 : 1;
        if (amount > 0)
            for (int slot = 0; slot < loadout.length; slot++)
                earn(loadout[slot].id(), threshold(loadout[slot]), amount, slot, earned);
        save();
        return List.copyOf(earned);
    }

    void select(String first, String second, String third) {
        String[] ids = { first, second, third };
        for (int slot = 0; slot < loadout.length; slot++) {
            loadout[slot] = StreakReward.byId(ids[slot]).orElseThrow();
            put(LOADOUT + (slot + 1), loadout[slot].id());
        }
        save();
    }

    /**
     * Consume only after the caller has accepted the deployment and airspace checks.
     * 调用方通过部署及空域检查后再扣除库存；禁用的额外奖励仍留在原队列中。
     */
    boolean consume(int slot) {
        if (slot == 3) {
            for (int i = 0; i < bonuses.size(); i++) {
                String id = bonuses.get(i);
                if (StreakReward.byId(id).isPresent() && enabled(id)) {
                    bonuses.remove(i);
                    put(BONUS, String.join(",", bonuses));
                    save();
                    return true;
                }
            }
            return false;
        }
        if (slot < 0 || slot > 4)
            return false;
        String id = slot == 4 ? "mgb" : loadout[slot].id();
        if (!enabled(id) || stock(id) == 0)
            return false;
        stock(id, stock(id) - 1);
        save();
        return true;
    }

    Grant grant(String id, int amount, Overflow overflow) {
        if (amount <= 0 || !enabled(id))
            return Grant.REJECTED;
        int slot = id.equals("mgb") ? 4 : selectedSlot(id);
        boolean bonus = slot < 0;
        if (overflow == Overflow.REJECT && (bonus ? bonuses.size() >= BONUS_CAPACITY : stock(id) == Integer.MAX_VALUE))
            return Grant.REJECTED;
        int vouchers = 0;
        if (bonus) {
            StreakReward reward = StreakReward.byId(id).orElseThrow();
            for (int i = 0; i < amount; i++)
                if (!queueBonus(reward))
                    vouchers++;
        } else
            stock(id, saturatingAdd(stock(id), amount));
        save();
        return new Grant(true, new Earned(id, bonus ? amount : stock(id), bonus ? 3 : slot, bonus), vouchers);
    }

    /**
     * A full bonus queue leaves overflow handling to the item-delivery caller.
     * 队列满时返回 false，由物品发放入口补发奖励卡；退款也使用同一队列规则。
     */
    boolean addBonus(StreakReward reward) {
        boolean queued = queueBonus(reward);
        save();
        return queued;
    }

    void refundStock(String id) {
        stock(id, saturatingAdd(stock(id), 1));
        save();
    }

    void reset(Reset reset) {
        if (reset != Reset.PROGRESS)
            dirty |= tags.removeIf(tag -> tag.startsWith(LIFE_EARNED));
        if (reset != Reset.LIFE || !repeatable) {
            putNumber(KILLS, 0);
            putNumber(SCORE, 0);
            clearRemainders();
            putNumber(PROGRESS + "mgb=", 0);
        }
        if (reset == Reset.ALL) {
            for (StreakReward reward : StreakReward.SELECTABLE)
                stock(reward.id(), 0);
            stock("mgb", 0);
            bonuses.clear();
            put(BONUS, "");
        }
        save();
    }

    private void earn(String id, int threshold, int amount, int slot, List<Earned> earned) {
        if (!enabled(id) || !repeatable && tags.contains(LIFE_EARNED + id))
            return;
        String key = PROGRESS + id + "=";
        long progress = (long) number(key) + amount;
        int count = (int) (progress / threshold);
        putNumber(key, repeatable ? (int) (progress % threshold) : (int) Math.min(threshold, progress));
        if (count == 0)
            return;
        stock(id, saturatingAdd(stock(id), repeatable ? count : 1));
        dirty |= tags.add(LIFE_EARNED + id);
        earned.add(new Earned(id, stock(id), slot, false));
    }

    private boolean enabled(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "mgb" -> mgbEnabled;
            case "juggernaut" -> juggernautEnabled;
            default -> true;
        };
    }

    private int threshold(StreakReward reward) {
        return scoreMode ? reward.score() : reward.kills();
    }

    private int selectedSlot(String id) {
        for (int slot = 0; slot < loadout.length; slot++)
            if (loadout[slot].id().equals(id))
                return slot;
        return -1;
    }

    private void migrate() {
        if (repeatable) {
            for (StreakReward reward : StreakReward.SELECTABLE)
                if (number(PROGRESS + reward.id() + "=") >= threshold(reward))
                    putNumber(PROGRESS + reward.id() + "=", 0);
            if (number(PROGRESS + "mgb=") >= MGB_THRESHOLD)
                putNumber(PROGRESS + "mgb=", 0);
        }
        String mode = scoreMode ? "score" : "kills";
        if (tags.contains(MIGRATED)) {
            if (!mode.equals(values.get(MODE))) {
                clearRemainders();
                put(MODE, mode);
            }
            return;
        }
        for (StreakReward reward : StreakReward.SELECTABLE) {
            if (tags.contains(READY + reward.id()) && stock(reward.id()) == 0)
                stock(reward.id(), 1);
            putNumber(PROGRESS + reward.id() + "=", number(scoreMode ? SCORE : KILLS) % threshold(reward));
            dirty |= tags.remove(PREFIX + "used=" + reward.id());
        }
        if (tags.contains(PREFIX + "mgb_ready"))
            stock("mgb", Math.max(1, stock("mgb")));
        putNumber(PROGRESS + "mgb=", number(KILLS) % MGB_THRESHOLD);
        dirty |= tags.remove(PREFIX + "mgb_used");
        dirty |= tags.add(MIGRATED);
        put(MODE, mode);
    }

    private void clearRemainders() {
        for (StreakReward reward : StreakReward.SELECTABLE)
            putNumber(PROGRESS + reward.id() + "=", 0);
    }

    private boolean queueBonus(StreakReward reward) {
        if (bonuses.size() >= BONUS_CAPACITY)
            return false;
        bonuses.add(reward.id());
        put(BONUS, String.join(",", bonuses));
        return true;
    }

    private int stock(String id) {
        return number(STOCK + id + "=");
    }

    private void stock(String id, int count) {
        putNumber(STOCK + id + "=", count);
        String ready = id.equals("mgb") ? PREFIX + "mgb_ready" : READY + id;
        dirty |= count > 0 ? tags.add(ready) : tags.remove(ready);
    }

    private void addNumber(String key, int amount) {
        putNumber(key, saturatingAdd(number(key), amount));
    }

    private static int saturatingAdd(int value, int amount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) value + amount);
    }

    private int number(String key) {
        try {
            return Math.max(0, Integer.parseInt(values.getOrDefault(key, "0")));
        }
        catch (NumberFormatException failure) {
            return 0;
        }
    }

    private void putNumber(String key, int value) {
        put(key, Integer.toString(value));
    }

    private void put(String key, String value) {
        List<String> previous = keyedTags.getOrDefault(key, List.of());
        if (previous.size() == 1 && value.equals(values.get(key)))
            return;
        tags.removeAll(previous);
        String tag = key + value;
        tags.add(tag);
        keyedTags.put(key, List.of(tag));
        values.put(key, value);
        dirty = true;
    }

    /**
     * Index both numeric tags and legacy loadout tags once; loadout keys intentionally lack '='.
     * 一次索引数值标签及旧配装标签；旧配装键没有等号，保持原格式以兼容已有存档。
     */
    private static String key(String tag) {
        if (tag.startsWith(LOADOUT) && tag.length() > LOADOUT.length()) {
            char slot = tag.charAt(LOADOUT.length());
            if (slot >= '1' && slot <= '3')
                return tag.substring(0, LOADOUT.length() + 1);
        }
        int separator = tag.indexOf('=');
        return separator < 0 ? null : tag.substring(0, separator + 1);
    }

    private void save() {
        if (!dirty)
            return;
        for (String tag : originalTags)
            if (!tags.contains(tag))
                player.removeTag(tag);
        for (String tag : tags)
            if (!originalTags.contains(tag))
                player.addTag(tag);
        originalTags.clear();
        originalTags.addAll(tags);
        dirty = false;
    }
}
