package net.killstreakcod.render;

import net.killstreakcod.render.compat.OptionalClientIntegrations;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Airborne controls stay at the sides; captions avoid the configured GD656 feedback.
 * 空中操控信息放在两侧，字幕避开 GD656 已配置的反馈位置。
 */
public final class RemoteHudLayout {
    /**
     * Immutable screen area shared with optional HUD compatibility bridges.
     * 与可选 HUD 兼容桥接共用的不可变屏幕区域。
     */
    public record Box(int x, int y, int w, int h) {
        boolean overlaps(Box other) {
            return x < other.x + other.w + 4 && x + w + 4 > other.x && y < other.y + other.h + 4 && y + h + 4 > other.y;
        }
    }

    private RemoteHudLayout() {}

    public static boolean active() {
        return TacticalClient.remote()
                && (TacticalClient.state.mode().equals("chopper") || TacticalClient.state.mode().equals("gunship"));
    }

    static int footerHeight(GuiGraphics gui) {
        if (AircraftHudRenderer.available(TacticalClient.state.mode()))
            return 0;
        var font = Minecraft.getInstance().font;
        return font.split(net.minecraft.network.chat.Component.literal(SceneNativeHud.controls()),
                Math.max(80, gui.guiWidth() - 16)).size() * font.lineHeight + 8;
    }

    public static int captionY(GuiGraphics gui, int width, int height) {
        int left = (gui.guiWidth() - width) / 2, bottom = gui.guiHeight() - footerHeight(gui) - 7;
        List<Box> reserved = new ArrayList<>(OptionalClientIntegrations.gdHudReservations(gui));
        reserved.addAll(AircraftHudRenderer.reservations(gui));
        for (int y = bottom - height; y >= 25; y--) {
            Box candidate = new Box(left, y, width, height);
            if (reserved.stream().noneMatch(candidate::overlaps))
                return y;
        }
        return Mth.clamp(Math.round(gui.guiHeight() * .18F), 8, Math.max(8, bottom - height));
    }

    static NativeSceneUi.Rect avoidFeedback(GuiGraphics gui, NativeSceneUi.Rect original) {
        List<Box> reserved = OptionalClientIntegrations.gdHudReservations(gui);
        Box initial = new Box(Math.round(original.x()), Math.round(original.y()), Math.round(original.w()),
                Math.round(original.h()));
        if (reserved.stream().noneMatch(initial::overlaps))
            return original;
        for (int y = initial.y - 1; y >= 4; y--) {
            Box candidate = new Box(initial.x, y, initial.w, initial.h);
            if (reserved.stream().noneMatch(candidate::overlaps))
                return new NativeSceneUi.Rect(original.x(), y, original.w(), original.h());
        }
        return original;
    }
}
