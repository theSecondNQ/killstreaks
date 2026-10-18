package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Validate before spending a reward; aircraft also clear terrain along their actual route.
 * 在消耗奖励前验证空域，同时确保飞机实际航线上的地形空间畅通。
 */
public final class StreakAirspace {
    private StreakAirspace() {}

    public static boolean airborne(StreakReward reward) {
        return reward != null && switch (reward) {
            case CLUSTER_MINE, SENTRY_GUN, WHEELSON -> false;
            default -> true;
        };
    }

    public static boolean permitted(ServerPlayer player, StreakReward reward) {
        if (!player.serverLevel().dimension().equals(Level.NETHER) || !airborne(reward))
            return true;
        player.displayClientMessage(Component.translatable("message.killstreakcod.nether_aircraft"), true);
        return false;
    }

    public static boolean permitted(ServerLevel level, TacticalUnitEntity.Kind kind) {
        if (!level.dimension().equals(Level.NETHER))
            return true;
        return switch (kind) {
            case UAV, COUNTER_UAV, ADVANCED_UAV, BOMB_DRONE, CRUISE, GUNSHIP, CHOPPER, OVERWATCH, VTOL, PRECISION, SAE,
                    STEALTH, DELIVERY, BOMB, ROCKET ->
                false;
            default -> true;
        };
    }

    public static boolean player(ServerPlayer player, StreakReward reward) {
        return permitted(player, reward)
                && (ResearchEffects.noAircraft(player, reward) || AircraftAirspace.direct(player, reward));
    }

    public static boolean target(ServerPlayer player, StreakReward reward, Vec3 point, float heading) {
        return permitted(player, reward) && AircraftAirspace.target(player, reward, point, heading);
    }
}
