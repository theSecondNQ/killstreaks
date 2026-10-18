package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.StreakReward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The document's research entries and costs, shared by server validation and the terminal UI.
 * 文档规定的研究条目与费用；服务端校验和终端界面共用同一份定义。
 */
public final class ResearchCatalog {
    public enum Category {
        RECON, SUPPORT, FIREPOWER, MODERNIZATION;

        public Component title() {
            return Component
                    .translatable("research.killstreakcod.category." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public record Ingredient(Item item, int count) {
    }

    public record Cost(int levels, List<Ingredient> materials) {
        public Cost {
            materials = List.copyOf(materials);
        }

        public boolean affordable(Player player) {
            return player.isCreative() || player.experienceLevel >= levels
                    && materials.stream().allMatch(need -> quantity(player, need.item) >= need.count);
        }

        boolean pay(ServerPlayer player) {
            if (!affordable(player))
                return false;
            if (player.isCreative())
                return true;
            // Check everything before changing any slot; only the server thread can spend materials.
            // 先完整核对再改变背包；材料扣除只发生在服务端线程，避免部分扣款。
            for (Ingredient need : materials) {
                int remaining = need.count;
                for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(need.item)) {
                        int used = Math.min(stack.getCount(), remaining);
                        stack.shrink(used);
                        remaining -= used;
                    }
                }
                if (remaining > 0)
                    player.getOffhandItem().shrink(remaining);
            }
            if (levels > 0)
                player.giveExperienceLevels(-levels);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return true;
        }
    }

    public record Node(int index, String id, StreakReward reward, Category category, int tier, Cost cost,
            boolean base) {
        public long bit() {
            return 1L << index;
        }

        public Component title() {
            return base ? reward.displayName() : Component.translatable("research.killstreakcod.node." + id + ".name");
        }

        public Component description() {
            return Component.translatable("research.killstreakcod.node." + id + ".description");
        }
    }

    public record Definition(StreakReward reward, Node research, Cost call, Cost repair, List<Node> upgrades) {
    }

    private record Upgrade(String id, int tier, Cost cost) {
    }

    private static final Map<String, Node> BY_ID = new LinkedHashMap<>();
    private static final Map<StreakReward, Definition> REWARDS = new EnumMap<>(StreakReward.class);
    private static final List<Cost> TERMINAL = List.of(
            cost(0, need(Items.COAL, 64), need(Items.COPPER_INGOT, 64), need(Items.IRON_INGOT, 32),
                    need(Items.GOLD_INGOT, 32), need(Items.REDSTONE, 16), need(Items.LAPIS_LAZULI, 16),
                    need(Items.DIAMOND, 8)),
            cost(0, need(Items.QUARTZ, 64), need(Items.BLAZE_ROD, 32), need(Items.ENDER_PEARL, 16),
                    need(Items.NETHERITE_SCRAP, 1)),
            cost(0, need(Items.DRAGON_BREATH, 16), need(Items.END_STONE, 16), need(Items.CHORUS_FRUIT, 16),
                    need(Items.SHULKER_SHELL, 8)),
            cost(0, need(Items.BEACON, 1), need(Items.ELYTRA, 1), need(Items.NETHERITE_INGOT, 1)));

    static {
        define(StreakReward.UAV, Category.RECON, 0, cost(0, need(Items.REDSTONE, 5), need(Items.IRON_INGOT, 10)),
                cost(0, need(Items.REDSTONE, 3)), cost(0, need(Items.REDSTONE, 3), need(Items.IRON_INGOT, 1)),
                upgrade("xray", 2, cost(10, need(Items.SPECTRAL_ARROW, 15))),
                upgrade("ender", 3, cost(30, need(Items.ENDER_PEARL, 16), need(Items.ENDER_EYE, 8))), endurance(1));
        define(StreakReward.COUNTER_UAV, Category.RECON, 0,
                cost(0, need(Items.REDSTONE, 5), need(Items.IRON_INGOT, 10), need(Items.REDSTONE_TORCH, 3)),
                cost(0, need(Items.REDSTONE, 3)),
                cost(0, need(Items.REDSTONE, 3), need(Items.IRON_INGOT, 1), need(Items.REDSTONE_TORCH, 1)),
                upgrade("assault", 2, cost(10, need(Items.REDSTONE_TORCH, 8), need(Items.FERMENTED_SPIDER_EYE, 8))),
                upgrade("sonic", 3, cost(30, need(Items.SCULK_SHRIEKER, 5), need(Items.TURTLE_SCUTE, 5))),
                endurance(1));
        define(StreakReward.ADVANCED_UAV, Category.RECON, 2,
                cost(0, need(Items.REDSTONE, 10), need(Items.IRON_INGOT, 20), need(Items.DIAMOND, 10)),
                cost(0, need(Items.REDSTONE, 5)),
                cost(0, need(Items.REDSTONE, 5), need(Items.IRON_INGOT, 10), need(Items.DIAMOND, 1)),
                upgrade("nowhere", 3, cost(30, need(Items.SPECTRAL_ARROW, 64), need(Items.REDSTONE, 32))),
                upgrade("watcher", 4, cost(45, need(Items.END_CRYSTAL, 5), need(Items.NETHER_STAR, 1))),
                upgrade("ender", 2, cost(10, need(Items.ENDER_PEARL, 16), need(Items.ENDER_EYE, 8))), endurance(2));
        define(StreakReward.BOMB_DRONE, Category.SUPPORT, 0,
                cost(0, need(Items.REDSTONE, 10), need(Items.IRON_INGOT, 15), need(Items.GUNPOWDER, 5)),
                cost(0, need(Items.REDSTONE, 3), need(Items.IRON_INGOT, 5), need(Items.GUNPOWDER, 3)),
                cost(0, need(Items.REDSTONE, 3), need(Items.IRON_INGOT, 5), need(Items.GUNPOWDER, 3)), improvedCharge(),
                upgrade("poison", 3, cost(30, need(Items.SPIDER_EYE, 32), need(Items.GLOWSTONE_DUST, 16))),
                endurance(1));
        define(StreakReward.CLUSTER_MINE, Category.SUPPORT, 0,
                cost(0, need(Items.REDSTONE, 10), need(Items.IRON_INGOT, 15), need(Items.GUNPOWDER, 20)),
                cost(0, need(Items.REDSTONE, 3), need(Items.IRON_INGOT, 7), need(Items.GUNPOWDER, 4)),
                cost(0, need(Items.REDSTONE, 3), need(Items.IRON_INGOT, 7), need(Items.GUNPOWDER, 4)), improvedCharge(),
                upgrade("split", 3, cost(30, need(Items.GUNPOWDER, 16), need(Items.SLIME_BALL, 8))),
                upgrade("extra", 1, cost(5, need(Items.IRON_INGOT, 10), need(Items.GUNPOWDER, 10))));
        define(StreakReward.CARE_PACKAGE, Category.SUPPORT, 0,
                cost(0, need(Items.REDSTONE, 15), need(Items.IRON_INGOT, 30), need(Items.COAL, 5)),
                cost(0, need(Items.REDSTONE, 5), need(Items.COAL, 5)),
                cost(0, need(Items.REDSTONE, 5), need(Items.COAL, 5)), luck(), teleport(), fastHands());
        define(StreakReward.OVERWATCH_HELO, Category.SUPPORT, 1,
                cost(0, need(Items.REDSTONE_BLOCK, 16), need(Items.IRON_BLOCK, 16), need(Items.GUNPOWDER, 32)),
                cost(0, need(Items.REDSTONE_BLOCK, 5), need(Items.GUNPOWDER, 16)),
                cost(0, need(Items.REDSTONE_BLOCK, 5), need(Items.IRON_BLOCK, 5), need(Items.GUNPOWDER, 16)),
                upgrade("scout", 2, cost(10, need(Items.REDSTONE, 10), need(Items.IRON_INGOT, 10))),
                upgrade("supply", 3,
                        cost(30, need(Items.REDSTONE, 16), need(Items.IRON_INGOT, 30), need(Items.COAL, 10))),
                upgrade("endurance", 1, cost(5, need(Items.REDSTONE_BLOCK, 5), need(Items.REPEATER, 10))));
        define(StreakReward.EMERGENCY_AIRDROP, Category.SUPPORT, 2,
                cost(0, need(Items.REDSTONE, 45), need(Items.IRON_BLOCK, 10), need(Items.COAL, 15)),
                cost(0, need(Items.REDSTONE, 15), need(Items.COAL, 15)),
                cost(0, need(Items.REDSTONE, 15), need(Items.COAL, 15)), luck(), teleport(),
                upgrade("escort", 4, cost(45, need(Items.REDSTONE_BLOCK, 16), need(Items.IRON_BLOCK, 16),
                        need(Items.GUNPOWDER, 32))),
                fastHands());
        define(StreakReward.SENTRY_GUN, Category.SUPPORT, 1,
                cost(0, need(Items.REDSTONE, 25), need(Items.IRON_INGOT, 25), need(Items.GUNPOWDER, 16)),
                cost(0, need(Items.REDSTONE, 10), need(Items.GUNPOWDER, 16)),
                cost(0, need(Items.REDSTONE, 10), need(Items.IRON_INGOT, 10), need(Items.GUNPOWDER, 16)),
                upgrade("continuous", 2, cost(10, need(Items.GUNPOWDER, 16), need(Items.REPEATER, 8))),
                upgrade("heavy", 3, cost(30, need(Items.GUNPOWDER, 32), need(Items.GLOWSTONE_DUST, 16))), endurance(1));
    }

    private ResearchCatalog() {}

    public static Node node(String id) {
        return BY_ID.get(id);
    }

    public static List<Node> nodes() {
        return List.copyOf(BY_ID.values());
    }

    public static Definition definition(StreakReward reward) {
        return REWARDS.get(reward);
    }

    public static List<Definition> definitions(Category category) {
        return BY_ID.values().stream().filter(node -> node.base && node.category == category)
                .sorted(Comparator.comparingInt(Node::tier).thenComparingInt(Node::index))
                .map(node -> REWARDS.get(node.reward)).toList();
    }

    public static long fullMask() {
        return (1L << BY_ID.size()) - 1;
    }

    public static boolean has(long unlocked, String id) {
        Node node = node(id);
        return node != null && (unlocked & node.bit()) != 0;
    }

    public static Cost terminalCost(int currentTier) {
        return currentTier >= 0 && currentTier < TERMINAL.size() ? TERMINAL.get(currentTier) : null;
    }

    /**
     * Save names instead of bit positions so future nodes cannot reassign existing research.
     * 存档使用稳定名称而非位序，避免后续插入节点时错误对应已有研究。
     */
    public static ListTag saveNodes(long mask) {
        ListTag result = new ListTag();
        for (Node node : BY_ID.values())
            if ((mask & node.bit()) != 0)
                result.add(StringTag.valueOf(node.id()));
        return result;
    }

    public static long loadNodes(ListTag nodes) {
        long result = 0;
        for (Tag tag : nodes) {
            Node node = node(tag.getAsString());
            if (node != null)
                result |= node.bit();
        }
        return result;
    }

    public static int quantity(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item))
                count += stack.getCount();
        }
        if (player.getOffhandItem().is(item))
            count += player.getOffhandItem().getCount();
        return count;
    }

    private static void define(StreakReward reward, Category category, int tier, Cost research, Cost call, Cost repair,
            Upgrade... upgrades) {
        Node base = add(reward.id(), reward, category, tier, research, true);
        List<Node> children = new ArrayList<>();
        for (Upgrade upgrade : upgrades)
            children.add(add(reward.id() + "." + upgrade.id, reward, category, upgrade.tier, upgrade.cost, false));
        List<Node> ordered = children.stream().sorted(Comparator.comparingInt(Node::tier).thenComparingInt(Node::index))
                .toList();
        REWARDS.put(reward, new Definition(reward, base, call, repair, ordered));
    }

    private static Node add(String id, StreakReward reward, Category category, int tier, Cost cost, boolean base) {
        if (BY_ID.size() >= 63)
            throw new IllegalStateException("Expand the research network mask before adding more nodes.");
        Node node = new Node(BY_ID.size(), id, reward, category, tier, cost, base);
        BY_ID.put(id, node);
        return node;
    }

    private static Ingredient need(Item item, int count) {
        return new Ingredient(item, count);
    }

    private static Cost cost(int levels, Ingredient... materials) {
        return new Cost(levels, List.of(materials));
    }

    private static Upgrade upgrade(String id, int tier, Cost cost) {
        return new Upgrade(id, tier, cost);
    }

    private static Upgrade endurance(int tier) {
        return upgrade("endurance", tier, cost(5, need(Items.REDSTONE, 16), need(Items.REPEATER, 2)));
    }

    private static Upgrade improvedCharge() {
        return upgrade("charge", 2, cost(10, need(Items.GUNPOWDER, 16), need(Items.GLOWSTONE_DUST, 8)));
    }

    private static Upgrade luck() {
        return upgrade("luck", 2, cost(10, need(Items.EMERALD, 16), need(Items.GOLD_INGOT, 8)));
    }

    private static Upgrade teleport() {
        return upgrade("teleport", 3, cost(30, need(Items.ENDER_PEARL, 16), need(Items.ENDER_EYE, 16)));
    }

    private static Upgrade fastHands() {
        return upgrade("fast", 1, cost(5, need(Items.SUGAR, 8)));
    }
}
