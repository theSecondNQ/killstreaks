package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.StreakNetwork;
import net.killstreakcod.universal.StreakPresentationPayload;
import net.killstreakcod.universal.TacticalRuntime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class SentryTurretItem extends Item {
    public SentryTurretItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos place = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(place).canBeReplaced()
                || !level.getBlockState(place.below()).isFaceSturdy(level, place.below(), Direction.UP))
            return InteractionResult.FAIL;
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && context.getPlayer() instanceof ServerPlayer owner) {
            if (TacticalRuntime.busy(owner))
                return InteractionResult.FAIL;
            if (!serverLevel.noCollision(new net.minecraft.world.phys.AABB(place).expandTowards(0, 0.8, 0)))
                return InteractionResult.FAIL;
            var saved = context.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            var at = net.minecraft.world.phys.Vec3.atBottomCenterOf(place);
            TacticalUnitEntity unit = TacticalRuntime.spawn(owner, TacticalUnitEntity.Kind.SENTRY, at, at,
                    saved.contains("Remaining") ? Math.max(1, Math.min(2400, saved.getInt("Remaining"))) : 2400);
            unit.heading = owner.getYRot();
            if (saved.contains("Health"))
                unit.setHealth(net.minecraft.util.Mth.clamp(saved.getFloat("Health"), 1, unit.getMaxHealth()));
            StreakNetwork.send(owner, new StreakPresentationPayload("sentry_gun", "confirmed", 18));
            Player player = context.getPlayer();
            if (!player.getAbilities().instabuild)
                context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
