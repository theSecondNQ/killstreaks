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

    /** Captions fit between the wider aircraft panels. 字幕宽度按飞机侧栏预留，避免长句跨进武器栏。 */
    public static int captionWidth(GuiGraphics gui) {
        int side = AircraftHudRenderer.available(TacticalClient.state.mode())
                ? Math.max(12, Math.round(536 * gui.guiHeight() / 1080F))
                : SceneNativeHud.panelWidth(gui.guiWidth()) + 16;
        return Math.max(80, Math.min(Math.round(gui.guiWidth() * .6F), gui.guiWidth() - side * 2));
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

    /**
     * Move whole instrument panels around occupied regions while keeping the aiming area clear.
     * 以完整面板避让已占用区域；同时考虑水平和垂直位置，避免把武器栏向上推入准心或缩放标尺。
     */
    static NativeSceneUi.Rect placePanel(GuiGraphics gui, NativeSceneUi.Rect preferred, List<Box> instruments) {
        int margin = Math.max(4, Math.round(gui.guiHeight() * .012F));
        int width = Math.max(1, Math.min(Math.round(preferred.w()), gui.guiWidth() - margin * 2));
        int height = Math.max(1, Math.min(Math.round(preferred.h()), gui.guiHeight() - margin * 2));
        int maxX = Math.max(margin, gui.guiWidth() - margin - width);
        int maxY = Math.max(margin, gui.guiHeight() - margin - height);
        int initialX = Mth.clamp(Math.round(preferred.x()), margin, maxX);
        int initialY = Mth.clamp(Math.round(preferred.y()), margin, maxY);
        int aimSize = Math.round(Math.min(gui.guiHeight() * .52F, gui.guiWidth() * .7F));
        Box aim = new Box((gui.guiWidth() - aimSize) / 2, (gui.guiHeight() - aimSize) / 2, aimSize, aimSize);
        List<Box> reserved = new ArrayList<>(OptionalClientIntegrations.gdHudReservations(gui));
        reserved.addAll(instruments);
        reserved.add(aim);
        Box initial = new Box(initialX, initialY, width, height);
        if (reserved.stream().noneMatch(initial::overlaps))
            return new NativeSceneUi.Rect(initial.x, initial.y, initial.w, initial.h);
        List<Integer> xs = new ArrayList<>(List.of(initialX, margin, maxX));
        List<Integer> ys = new ArrayList<>(List.of(initialY, margin, maxY));
        for (Box area : reserved) {
            xs.add(Mth.clamp(area.x - width - 6, margin, maxX));
            xs.add(Mth.clamp(area.x + area.w + 6, margin, maxX));
            ys.add(Mth.clamp(area.y - height - 6, margin, maxY));
            ys.add(Mth.clamp(area.y + area.h + 6, margin, maxY));
        }
        Box best = new Box(initialX, initialY, width, height);
        long leastOverlap = Long.MAX_VALUE, nearest = Long.MAX_VALUE;
        for (int x : xs)
            for (int y : ys) {
                Box candidate = new Box(x, y, width, height);
                if (candidate.overlaps(aim))
                    continue;
                long overlap = 0;
                for (Box area : reserved) {
                    long dx = Math.max(0, Math.min(x + width + 4, area.x + area.w + 4) - Math.max(x, area.x));
                    long dy = Math.max(0, Math.min(y + height + 4, area.y + area.h + 4) - Math.max(y, area.y));
                    overlap += dx * dy;
                }
                long distance = (long) (x - initialX) * (x - initialX) + (long) (y - initialY) * (y - initialY);
                if (overlap < leastOverlap || overlap == leastOverlap && distance < nearest) {
                    best = candidate;
                    leastOverlap = overlap;
                    nearest = distance;
                }
            }
        return new NativeSceneUi.Rect(best.x, best.y, best.w, best.h);
    }
}
