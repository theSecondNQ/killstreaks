package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.NativeArsenalLibrary;
import net.killstreakcod.universal.StreakAirspace;
import net.killstreakcod.universal.StreakNetwork;
import net.killstreakcod.universal.StreakPresentationPayload;
import net.killstreakcod.universal.StreakServerConfig;
import net.killstreakcod.universal.TacticalRuntime;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TacticalEquipmentItem extends Item {
    private final String operation;

    public TacticalEquipmentItem(Properties p, String op) {
        super(p.stacksTo(1));
        operation = op;
    }

    public static ItemStack stack(String op, String reward) {
        ItemStack stack = (op.equals("designator") ? KillstreakcodMod.DESIGNATOR
                : op.equals("drop_marker") ? KillstreakcodMod.DROP_MARKER : KillstreakcodMod.CLUSTER_MINE).get()
                .getDefaultInstance();
        CompoundTag n = new CompoundTag();
        n.putString("Reward", reward);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
        stack.set(DataComponents.CUSTOM_NAME, StreakReward.byId(reward).orElse(StreakReward.UAV).displayName());
        return stack;
    }

    private boolean activate(ServerPlayer player, ItemStack stack, Vec3 at) {
        if (TacticalRuntime.busy(player))
            return false;
        StreakReward reward = StreakReward
                .byId(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("Reward"))
                .orElse(operation.equals("designator") ? StreakReward.PRECISION_AIRSTRIKE
                        : operation.equals("drop_marker") ? StreakReward.CARE_PACKAGE : StreakReward.CLUSTER_MINE);
        if (!StreakServerConfig.requireReward(player, reward.id()))
            return false;
        if (operation.equals("designator")) {
            if (!TacticalRuntime.designate(player, reward, at))
                return false;
            StreakNetwork.send(player, new StreakPresentationPayload(reward.id(), "designator_fire", 0));
        } else if (operation.equals("drop_marker")) {
            if (!StreakAirspace.player(player, reward))
                return false;
            TacticalRuntime.throwMarker(player, reward);
        } else {
            Vec3 direction = player.getLookAngle(), origin = player.getEyePosition().add(direction.scale(.7));
            StreakNetwork.send(player,
                    new StreakPresentationPayload("cluster_mine", "confirmed", NativeArsenalLibrary.THROW_DURATION));
            TacticalUnitEntity unit = TacticalRuntime.spawnConfigured(player, TacticalUnitEntity.Kind.CLUSTER, origin,
                    player.position(), 1200, player.getYRot(), NativeArsenalLibrary.THROW_RELEASE, "",
                    direction.scale(.85).add(0, .15, 0));
            unit.stage(-21);
        }
        if (!player.getAbilities().instabuild)
            stack.shrink(1);
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return operation.equals("designator") ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!operation.equals("designator") || !(user instanceof ServerPlayer p)
                || getUseDuration(stack, user) - remaining < 6)
            return;
        Vec3 from = p.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(p.getLookAngle().scale(160)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        if (hit.getType() != HitResult.Type.MISS)
            activate(p, stack, hit.getLocation());
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getPlayer() == null)
            return InteractionResult.PASS;
        if (operation.equals("designator")) {
            c.getPlayer().startUsingItem(c.getHand());
            return InteractionResult.CONSUME;
        }
        if (c.getPlayer() instanceof ServerPlayer p)
            activate(p, c.getItemInHand(),
                    c.getClickLocation().add(Vec3.atLowerCornerOf(c.getClickedFace().getNormal()).scale(.3)));
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand h) {
        ItemStack stack = player.getItemInHand(h);
        if (operation.equals("designator")) {
            player.startUsingItem(h);
            return InteractionResultHolder.consume(stack);
        }
        if (player instanceof ServerPlayer sp) {
            Vec3 from = player.getEyePosition(), end = from.add(player.getLookAngle().scale(18));
            BlockHitResult hit = level
                    .clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            activate(sp, stack,
                    hit.getType() == HitResult.Type.MISS ? player.position().add(player.getLookAngle().scale(3))
                            : hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.3)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
