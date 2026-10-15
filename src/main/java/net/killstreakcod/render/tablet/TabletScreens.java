package net.killstreakcod.render.tablet;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Original hardware variants plus reward-specific screens on the same source shell and UVs.
 * 在相同源外壳和 UV 上组合原硬件变体及各奖励专属屏幕。
 */
public final class TabletScreens {
    private TabletScreens() {}

    public static String modelId(String reward) {
        return switch (reward) {
            case "counter_uav" -> "tablet_counter";
            case "advanced_uav" -> "tablet_advanced";
            case "bomb_drone" -> "tablet_bomb";
            case "sentry_gun" -> "tablet_sentry";
            case "mortar_strike" -> "tablet_mortar";
            case "wheelson_hs" -> "tablet_wheelson";
            default -> "scene_tablet";
        };
    }

    public static String firstPersonModelId(String reward) {
        return "scene_tablet_vm";
    }

    public static Map<String, ResourceLocation> materialTextures(String reward) {
        return SceneTabletScreen.materials(reward);
    }
}
