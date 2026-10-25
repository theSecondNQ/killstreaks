package net.killstreakcod.render.ui;

import net.killstreakcod.render.UiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 终端各页面共用的颜色、间距和绘制规则；文字始终使用原始 GUI 字号。 */
public final class TerminalUi {
    public static final int BACKGROUND = 0xff0b1114;
    public static final int SURFACE = 0xff111c20;
    public static final int RAISED = 0xff1b2a2d;
    public static final int BORDER = 0xff35474a;
    public static final int ACCENT = 0xffbfd587;
    public static final int TEXT = 0xffedf2ec;
    public static final int MUTED = 0xffa8b9b7;
    public static final int DISABLED = 0xff73847f;
    public static final int WARNING = 0xffedbd7d;

    private TerminalUi() {}

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < right() && mouseY < bottom();
        }
    }

    public static Rect window(int screenWidth, int screenHeight, int maxWidth, int maxHeight) {
        int width = Math.max(1, Math.min(maxWidth, screenWidth - 16));
        int height = Math.max(1, Math.min(maxHeight, screenHeight - 16));
        return new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public static void panel(GuiGraphics graphics, Rect box, int color) {
        UiDraw.fill(graphics, "TerminalUi.panel.0", box.x(), box.y(), box.right(), box.bottom(), BORDER);
        UiDraw.fill(graphics, "TerminalUi.panel.1", box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, color);
    }

    public static void window(GuiGraphics graphics, Rect box) {
        UiDraw.fill(graphics, "TerminalUi.window.0", box.x() + 3, box.y() + 4, box.right() + 3, box.bottom() + 4,
                0x55000000);
        panel(graphics, box, BACKGROUND);
        UiDraw.fill(graphics, "TerminalUi.window.1", box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 32, SURFACE);
        UiDraw.fill(graphics, "TerminalUi.window.2", box.x() + 1, box.y() + 1, box.x() + 4, box.y() + 32, ACCENT);
        UiDraw.hLine(graphics, "TerminalUi.window.3", box.x() + 1, box.right() - 2, box.y() + 32, BORDER);
        UiDraw.hLine(graphics, "TerminalUi.window.4", box.x() + 1, box.right() - 2, box.bottom() - 34, BORDER);
    }

    public static String ellipsis(Font font, Component value, int width) {
        String text = value.getString();
        if (width <= 0)
            return "";
        if (font.width(text) <= width)
            return text;
        String suffix = "…";
        if (font.width(suffix) > width)
            return "";
        return font.plainSubstrByWidth(text, width - font.width(suffix)) + suffix;
    }

    public static void text(GuiGraphics graphics, Font font, Component value, int x, int y, int width, int color) {
        UiDraw.drawString(graphics, "TerminalUi.text.0", font, ellipsis(font, value, width), x, y, color, false);
    }

    public static int wrapped(GuiGraphics graphics, Font font, Component value, int x, int y, int width, int color) {
        for (var line : font.split(value, Math.max(1, width))) {
            UiDraw.drawString(graphics, "TerminalUi.wrapped.0", font, line, x, y, color, false);
            y += font.lineHeight + 3;
        }
        return y;
    }

    public static void progress(GuiGraphics graphics, Rect box, double progress, int color) {
        UiDraw.fill(graphics, "TerminalUi.progress.0", box.x(), box.y(), box.right(), box.bottom(), BORDER);
        int filled = (int) Math.round(box.width() * Math.max(0, Math.min(1, progress)));
        if (filled > 0)
            UiDraw.fill(graphics, "TerminalUi.progress.1", box.x(), box.y(), box.x() + filled, box.bottom(), color);
    }

    static int mix(int from, int to, float amount) {
        int red = (int) (((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int green = (int) (((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int blue = (int) ((from & 255) * (1 - amount) + (to & 255) * amount);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }
}
