package net.killstreakcod.render;

import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.NativeCruiseFlight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Camera-specific terrain allowance. The server separately bounds the horizontal watch radius.
 * 按摄像机类型设置地形可视范围，服务端另行限制水平观察半径。
 */
public final class RemoteTerrainRange {
    private static ClientLevel cruiseWorld;
    private static int cruiseEntity = -1, cruiseRange;

    private RemoteTerrainRange() {}

    public static void reset() {
        cruiseWorld = null;
        cruiseEntity = -1;
        cruiseRange = 0;
    }

    public static int effective(int serverDistance) {
        String mode = TacticalClient.state.mode();
        boolean cruise = mode.equals("cruise"), aerial = mode.equals("gunship") || cruise;
        int horizontal = Math.min(aerial ? 16 : 8, serverDistance > 0 ? serverDistance : 8);
        if (mode.equals("gunship"))
            horizontal = Math.min(net.killstreakcod.universal.NativePresentationConfig.GUNSHIP_VIEW_DISTANCE.get(),
                    serverDistance > 0 ? serverDistance : 32);
        if (!aerial)
            return horizontal;
        var mc = Minecraft.getInstance();
        if (mc.level == null)
            return horizontal;
        if (cruise && cruiseWorld == mc.level && cruiseEntity == TacticalClient.state.entityId() && cruiseRange > 0)
            return cruiseRange;
        var unit = mc.level.getEntity(TacticalClient.state.entityId());
        double eye = unit != null ? unit.getEyePosition().y
                : mc.player != null
                        ? mc.player.getY() + (cruise ? NativeCruiseFlight.LAUNCH_HEIGHT + 64 : GunshipFlight.HEIGHT) + 2
                        : mc.level.getMaxBuildHeight();

        // 一次性预留摄像机及抬升高度余量，本次飞行中不缩小地形网格范围。
        double vertical = Math.max(0, (eye - mc.level.getMinBuildHeight() + (cruise ? 16 : 0)) / 16);
        int range = Mth.clamp(Mth.ceil(Math.hypot(horizontal, vertical)) + 1, horizontal,
                cruise ? 48 : mode.equals("gunship") ? 64 : 32);
        if (cruise) {
            cruiseWorld = mc.level;
            cruiseEntity = TacticalClient.state.entityId();
            cruiseRange = range;
        }
        return range;
    }
}
