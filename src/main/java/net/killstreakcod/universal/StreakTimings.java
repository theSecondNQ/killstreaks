package net.killstreakcod.universal;

/**
 * Shared authoritative timing in game ticks; based on the imported 30 FPS actions.
 * 以游戏刻表示的共用服务端时序，依据导入的每秒 30 帧动作计算。
 */
public final class StreakTimings {
    private StreakTimings() {}

    public static final int REMOTE_CALL = 34, BOMB_DEPLOY = 40;

    public static boolean remoteReward(String reward) {
        return switch (reward) {
            case "gunship", "chopper_gunner", "cruise_missile", "bomb_drone", "wheelson_hs" -> true;
            default -> false;
        };
    }

    public static boolean scoutReward(String reward) {
        return switch (reward) {
            case "uav", "counter_uav", "advanced_uav" -> true;
            default -> false;
        };
    }

    /**
     * Non-remote calls which share the original single-hand smartphone gesture.
     * 共用原单手手机手势的非遥控类呼叫。
     */
    public static boolean phoneReward(String reward) {
        return scoutReward(reward) || reward.equals("overwatch_helo");
    }

    public static int remoteTabletOffset(String reward) {
        return reward.equals("bomb_drone") ? BOMB_DEPLOY : 0;
    }

    public static int call(String reward) {
        return switch (reward) {
            case "uav", "counter_uav", "advanced_uav", "overwatch_helo" -> 30;
            case "mgb" -> 65;
            case "bomb_drone" -> BOMB_DEPLOY + REMOTE_CALL;
            case "wheelson_hs" -> REMOTE_CALL;
            case "sentry_gun" -> 24;
            case "cluster_mine" -> 16;
            case "sae", "vtol_jet", "stealth_bomber" -> 23;
            case "cruise_missile", "chopper_gunner", "gunship" -> REMOTE_CALL;
            default -> 48;
        };
    }

    public static int entry(String reward) {
        return switch (reward) {
            case "gunship" -> SceneEntryLibrary.GUNSHIP_ENTRY;
            case "chopper_gunner" -> 100;
            case "cruise_missile" -> 60;
            case "bomb_drone" -> 12;
            default -> 26;
        };
    }

    public static int exit(String reward) {
        return remoteReward(reward) ? 20 : 16;
    }

    public static int confirmed(String reward) {
        return switch (reward) {
            case "sae", "vtol_jet", "stealth_bomber" -> 25;
            default -> 18;
        };
    }
}
