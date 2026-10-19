package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.HeadquartersTerminalBlock;
import net.killstreakcod.killstreaks.KillstreakRewardItem;
import net.killstreakcod.killstreaks.StreakReward;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Registry-backed reward vouchers and one tab containing every item registered by this mod.
 * 奖励凭证通过注册表注册，另设一个创造模式标签页容纳本模组全部已注册物品。
 */
public final class KillstreakContent {
    public static final Map<String, RegistryObject<KillstreakRewardItem>> REWARDS = registerRewards();
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, KillstreakcodMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
            KillstreakcodMod.MODID);
    public static final RegistryObject<HeadquartersTerminalBlock> HEADQUARTERS_TERMINAL = BLOCKS.register(
            "headquarters_terminal", () -> new HeadquartersTerminalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3, 6).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<BlockItem> HEADQUARTERS_TERMINAL_ITEM = KillstreakcodMod.ITEMS
            .register("headquarters_terminal", () -> new BlockItem(HEADQUARTERS_TERMINAL.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<HeadquartersTerminalMenu>> HEADQUARTERS_TERMINAL_MENU = MENUS
            .register("headquarters_terminal",
                    () -> new MenuType<>(HeadquartersTerminalMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            KillstreakcodMod.MODID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("killstreaks",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.killstreakcod"))
                    .icon(() -> REWARDS.get("uav").get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES).displayItems((parameters, output) -> {
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

    private static Map<String, RegistryObject<KillstreakRewardItem>> registerRewards() {
        Map<String, RegistryObject<KillstreakRewardItem>> result = new LinkedHashMap<>();
        for (StreakReward reward : StreakReward.SELECTABLE)
            register(result, reward.id());
        register(result, "mgb");
        return Collections.unmodifiableMap(result);
    }

    private static void register(Map<String, RegistryObject<KillstreakRewardItem>> result, String reward) {
        result.put(reward, KillstreakcodMod.ITEMS.register("reward_" + reward,
                () -> new KillstreakRewardItem(new Item.Properties().stacksTo(64), reward)));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }
}
