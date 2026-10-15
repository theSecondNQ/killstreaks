package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.KillstreakService;
import net.killstreakcod.universal.TacticalRuntime;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class RewardCardItem extends Item {
    public RewardCardItem(Properties p) {
        super(p);
    }

    public static ItemStack card(StreakReward reward) {
        ItemStack stack = KillstreakcodMod.CARE_PACKAGE_REWARD.get().getDefaultInstance();
        CompoundTag n = new CompoundTag();
        n.putString("Reward", reward.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
        stack.set(DataComponents.CUSTOM_NAME, reward.displayName());
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand h) {
        ItemStack stack = player.getItemInHand(h);
        if (player instanceof ServerPlayer sp) {
            String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("Reward");
            StreakReward reward = StreakReward.byId(id)
                    .orElseGet(() -> TacticalRuntime.randomPackage(sp.serverLevel().getServer()));
            if (!KillstreakService.grantBonus(sp, reward))
                return InteractionResultHolder.fail(stack);
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
