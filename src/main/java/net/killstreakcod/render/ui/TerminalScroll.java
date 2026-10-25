package net.killstreakcod.render.ui;

import net.killstreakcod.render.UiDraw;
import net.minecraft.client.gui.GuiGraphics;

/** 每个滚动区域独立持有位置；支持滚轮、点击轨道和拖动滑块。 */
public final class TerminalScroll {
    private TerminalUi.Rect track = new TerminalUi.Rect(0, 0, 0, 0);
    private double offset;
    private double maximum;
    private int thumbHeight;
    private boolean dragging;
    private double grab;

    public void layout(TerminalUi.Rect area, int contentHeight) {
        track = new TerminalUi.Rect(area.right() - 5, area.y() + 3, 3, Math.max(1, area.height() - 6));
        maximum = Math.max(0, contentHeight - area.height());
        thumbHeight = Math.min(track.height(),
                Math.max(14, (int) ((double) area.height() / Math.max(1, contentHeight) * track.height())));
        set(offset);
    }

    public double offset() {
        return offset;
    }

    public void set(double value) {
        offset = Math.max(0, Math.min(maximum, value));
    }

    public void wheel(double amount) {
        set(offset - amount * 36);
    }

    private int thumbY() {
        return track.y() + (maximum == 0 ? 0 : (int) Math.round(offset / maximum * (track.height() - thumbHeight)));
    }

    public void render(GuiGraphics graphics) {
        if (maximum <= 0)
            return;
        UiDraw.fill(graphics, "TerminalScroll.render.0", track.x(), track.y(), track.right(), track.bottom(),
                TerminalUi.SURFACE);
        UiDraw.fill(graphics, "TerminalScroll.render.1", track.x(), thumbY(), track.right(), thumbY() + thumbHeight,
                dragging ? TerminalUi.ACCENT : TerminalUi.MUTED);
    }

    public boolean click(double mouseX, double mouseY) {
        if (maximum <= 0 || mouseX < track.x() - 3 || mouseX >= track.right() + 3 || mouseY < track.y()
                || mouseY >= track.bottom())
            return false;
        grab = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight ? mouseY - thumbY() : thumbHeight / 2D;
        dragging = true;
        drag(mouseY);
        return true;
    }

    public boolean drag(double mouseY) {
        if (!dragging)
            return false;
        int travel = track.height() - thumbHeight;
        if (travel > 0)
            set((mouseY - track.y() - grab) / travel * maximum);
        return true;
    }

    public void release() {
        dragging = false;
    }
}
