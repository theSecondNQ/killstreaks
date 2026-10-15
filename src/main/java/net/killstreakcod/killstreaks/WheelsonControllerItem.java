package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.KillstreakService;
import net.killstreakcod.universal.TacticalRuntime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WheelsonControllerItem extends Item {
    public WheelsonControllerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack controller = player.getItemInHand(hand);
        if (level.isClientSide)
            return InteractionResultHolder.sidedSuccess(controller, true);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResultHolder.fail(controller);

        Vec3 spawn = player.position().add(player.getLookAngle().normalize().scale(2.0D));
        if (TacticalRuntime.busy(serverPlayer))
            return InteractionResultHolder.fail(controller);
        spawn = player.position().add(player.getLookAngle().multiply(2.5D, 0.0D, 2.5D));
        if (!serverLevel
                .noCollision(new net.minecraft.world.phys.AABB(spawn.add(-.85, 0, -.85), spawn.add(.85, 1.05, .85))))
            return InteractionResultHolder.fail(controller);
        TacticalUnitEntity wheelson = TacticalRuntime.spawn(serverPlayer, TacticalUnitEntity.Kind.WHEELSON, spawn,
                player.position(), 2400);
        if (wheelson == null)
            return InteractionResultHolder.fail(controller);
        wheelson.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), 0.0F);
        wheelson.setCustomName(Component.translatable("entity.killstreakcod.wheelson"));
        wheelson.setCustomNameVisible(true);
        wheelson.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2 * 60 * 20, 2));
        wheelson.addTag(KillstreakService.WHEELSON_TAG);
        TacticalRuntime.linkControl(serverPlayer, wheelson);
        if (!serverPlayer.getAbilities().instabuild)
            controller.shrink(1);
        return InteractionResultHolder.sidedSuccess(controller, false);
    }
}
