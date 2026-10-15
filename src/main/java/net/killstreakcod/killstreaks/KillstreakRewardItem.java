package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.KillstreakService;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A named voucher adds one ready reward; deployment still uses the normal killstreak keys.
 * 使用具名凭证获得一次已就绪奖励，实际部署仍通过常规连杀按键完成。
 */
public final class KillstreakRewardItem extends Item {
    private final String reward;

    public KillstreakRewardItem(Properties properties, String reward) {
        super(properties);
        this.reward = reward;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator())
            return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!KillstreakService.claimReward(serverPlayer, reward))
                return InteractionResultHolder.fail(stack);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.killstreakcod.reward_item").withStyle(ChatFormatting.GRAY));
    }
}
