package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.RequestTerminalStatePayload;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.killstreakcod.network.StreakPackets;

public class KillstreakControllerItem extends Item {
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(net.killstreakcod.render.KillstreakClientEntityRenderers.itemExtension("tablet_uav", "killstreak_controller"));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.NONE;
    }

    public KillstreakControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide)
            StreakPackets.sendToServer(new RequestTerminalStatePayload(true));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
