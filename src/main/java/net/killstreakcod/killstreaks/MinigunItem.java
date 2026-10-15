package net.killstreakcod.killstreaks;

import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.universal.NativeArsenalLibrary;
import net.killstreakcod.universal.NativeFxDelivery;
import net.killstreakcod.universal.TacticalRuntime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class MinigunItem extends Item {
    public static final int FIRE_INTERVAL_TICKS = 1;

    public MinigunItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {

        // 自定义渲染器负责枪管旋转和后坐力，使用拉弓姿势会让枪横向倾斜。
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (!(livingEntity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel))
            return;
        int ticksUsing = getUseDuration(stack, player) - remainingUseDuration;
        if (ticksUsing == 1)
            TacticalRuntime.sound(serverLevel, player.getEyePosition(),
                    NativeAudio.sfx("m134_spinup", StreakSounds.SPIN.get()), .8F, 1);
        if (ticksUsing < NativeArsenalLibrary.CHARGE_TICKS
                || (ticksUsing - NativeArsenalLibrary.CHARGE_TICKS) % FIRE_INTERVAL_TICKS != 0)
            return;
        if (ticksUsing % 4 == 0) {
            NativeFxDelivery.emit(serverLevel, "m13421_muzzle",
                    NativeArsenalLibrary.minigunSocket(player, "tag_flash", ticksUsing), .3F);
            NativeFxDelivery.emit(serverLevel, "m13421_brass",
                    NativeArsenalLibrary.minigunSocket(player, "tag_brass", ticksUsing), .3F);
        }
        float yawSpread = (player.getRandom().nextFloat() - 0.5F) * 1.8F;
        float pitchSpread = (player.getRandom().nextFloat() - 0.5F) * 1.2F;
        TacticalRuntime.hitscan(player, player, player.getEyePosition(), net.minecraft.world.phys.Vec3
                .directionFromRotation(player.getXRot() + pitchSpread, player.getYRot() + yawSpread), 128, 8, false);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player)
            TacticalRuntime.sound(server, player.getEyePosition(),
                    NativeAudio.sfx("m134_spindown", StreakSounds.SPIN.get()), .5F, 1);
    }
}
