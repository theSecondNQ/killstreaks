package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.KillstreakRewardItem;
import net.killstreakcod.killstreaks.StreakReward;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry-backed reward vouchers and one tab containing every item registered by this mod.
 * 奖励凭证通过注册表注册，另设一个创造模式标签页容纳本模组全部已注册物品。
 */
public final class KillstreakContent {
    public static final Map<String, DeferredItem<KillstreakRewardItem>> REWARDS = registerRewards();
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            KillstreakcodMod.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("killstreaks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.killstreakcod"))
                    .icon(() -> REWARDS.get("uav").get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .displayItems((parameters, output) -> {
                        Item terminal = KillstreakcodMod.CONTROLLER.get();
                        output.accept(terminal);
                        REWARDS.values().forEach(item -> output.accept(item.get()));
                        for (var entry : KillstreakcodMod.ITEMS.getEntries()) {
                            Item item = entry.get();
                            if (item != terminal && !(item instanceof KillstreakRewardItem))
                                output.accept(item);
                        }
                    }).build());

    private KillstreakContent() {}

    private static Map<String, DeferredItem<KillstreakRewardItem>> registerRewards() {
        Map<String, DeferredItem<KillstreakRewardItem>> result = new LinkedHashMap<>();
        for (StreakReward reward : StreakReward.SELECTABLE)
            register(result, reward.id());
        register(result, "mgb");
        return Collections.unmodifiableMap(result);
    }

    private static void register(Map<String, DeferredItem<KillstreakRewardItem>> result, String reward) {
        result.put(reward, KillstreakcodMod.ITEMS.register("reward_" + reward,
                () -> new KillstreakRewardItem(new Item.Properties().stacksTo(64), reward)));
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
