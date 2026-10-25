package net.killstreakcod.render;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** 遥控期间统一绘制本模组和 GD656，入口接管后不派发外部 HUD 事件或绘制普通叠层。 */
public final class RemoteHud {
    private RemoteHud() {}

    public static boolean render(GuiGraphics graphics, float partial) {
        if (!TacticalClient.remote() && !AircraftCrashClient.active())
            return false;
        graphics.pose().pushPose();
        try {
            // 遥控入口不派发普通 GUI 事件，因此必须在绘制任何 HUD 前合成热成像，F1 下也照常执行。
            ThermalVision.compose(graphics);
            if (!Minecraft.getInstance().options.hideGui) {
                if (TacticalClient.remote() && StreakPresentation.remoteHudVisible())
                    TacticalHud.remote(graphics);
                TacticalHud.banner(graphics);
                StreakPresentation.renderGui(graphics);
                OptionalClientIntegrations.remoteOverlay(graphics, partial);
                UiLayout.flush(graphics);
                StreakPresentation.renderBlackout(graphics);
                RadioSubtitleOverlay.render(graphics);
            } else
                StreakPresentation.renderBlackout(graphics);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
        return true;
    }
}
