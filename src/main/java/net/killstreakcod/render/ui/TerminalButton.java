package net.killstreakcod.render.ui;

import net.killstreakcod.render.UiDraw;
import net.killstreakcod.render.UiLayout;
import net.killstreakcod.render.OriginalRewardUi;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 保留原版按钮的键盘操作、旁白和点击音；仅替换视觉表现。 */
public final class TerminalButton extends Button {
    private Component detail = Component.empty();
    private Component help;
    private String icon = "";
    private boolean selected;
    private boolean primary;
    private boolean leftAligned;
    private TerminalUi.Rect clip;
    private float hover;
    private long lastFrame;
    private String layoutId, renderedKey;

    public TerminalButton(int x, int y, int width, int height, Component label, Runnable action) {
        super(x, y, width, height, label, ignored -> action.run(), DEFAULT_NARRATION);
        setTooltip(Tooltip.create(label));
        layoutId = label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                ? translated.getKey()
                : "label." + Integer.toUnsignedString(label.getString().hashCode(), 16);
    }

    public TerminalButton layoutId(String id) {
        layoutId = id;
        return this;
    }

    private double[] localPointer(double x, double y) {
        UiLayout.Element element = renderedKey == null ? null : UiLayout.element(renderedKey);
        if (element == null)
            return new double[] { x, y };
        var box = element.displayed();
        return new double[] { getX() + (x - box.x()) * width / box.width(),
                getY() + (y - box.y()) * height / box.height() };
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (renderedKey != null && UiLayout.buttonCovered(renderedKey, mouseX, mouseY))
            return false;
        var pointer = localPointer(mouseX, mouseY);
        return super.isMouseOver(pointer[0], pointer[1]);
    }

    public TerminalButton selected(boolean value) {
        selected = value;
        return this;
    }

    public TerminalButton primary() {
        primary = true;
        return this;
    }

    public TerminalButton detail(Component value) {
        if (detail.equals(value))
            return this;
        detail = value;
        leftAligned = true;
        describe();
        return this;
    }

    public TerminalButton help(Component value) {
        help = value;
        describe();
        return this;
    }

    @Override
    public void setMessage(Component message) {
        if (message.equals(getMessage()))
            return;
        super.setMessage(message);
        describe();
    }

    private void describe() {
        Component description = help != null ? help
                : detail == null || detail.getString().isEmpty() ? getMessage()
                        : Component.empty().append(getMessage()).append("\n").append(detail);
        setTooltip(Tooltip.create(description));
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        if (!detail.getString().isEmpty())
            output.add(NarratedElementType.HINT, detail);
    }

    public TerminalButton icon(String value) {
        icon = value;
        leftAligned = true;
        return this;
    }

    public TerminalButton clip(TerminalUi.Rect area) {
        clip = area;
        return this;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        if (renderedKey != null && UiLayout.buttonCovered(renderedKey, mouseX, mouseY))
            return false;
        var pointer = localPointer(mouseX, mouseY);
        return (clip == null || clip.contains(pointer[0], pointer[1])) && super.clicked(pointer[0], pointer[1]);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderedKey = UiLayout.context() == null ? null : UiLayout.context() + "/button/" + layoutId;
        try (UiLayout.Scope element = UiLayout.element(graphics, layoutId,
                new UiLayout.Box(getX(), getY(), width, height), true)) {
            var pointer = localPointer(mouseX, mouseY);
            mouseX = (int) pointer[0];
            mouseY = (int) pointer[1];
            isHovered = super.isMouseOver(mouseX, mouseY);
            if (clip != null) {
                isHovered = isHovered && clip.contains(mouseX, mouseY);
                graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
            }
            var font = Minecraft.getInstance().font;
            long now = Util.getMillis();
            float step = Math.min(1, Math.max(0, now - lastFrame) / 120F);
            lastFrame = now;
            float target = active && isHovered ? 1 : 0;
            hover += Math.signum(target - hover) * Math.min(Math.abs(target - hover), step);
            // 只渐变鼠标悬停颜色；键盘焦点和选择立即反馈，不移动文字或点击区域。
            int border = isFocused() ? TerminalUi.TEXT
                    : selected ? TerminalUi.ACCENT : TerminalUi.mix(TerminalUi.BORDER, TerminalUi.MUTED, hover);
            int background = selected ? TerminalUi.RAISED
                    : TerminalUi.mix(TerminalUi.SURFACE, TerminalUi.RAISED, hover);
            UiDraw.fill(graphics, "TerminalButton.renderWidget.0", getX(), getY(), getX() + width, getY() + height,
                    border);
            UiDraw.fill(graphics, "TerminalButton.renderWidget.1", getX() + 1, getY() + 1, getX() + width - 1,
                    getY() + height - 1, background);
            if ((selected || primary) && active)
                UiDraw.fill(graphics, "TerminalButton.renderWidget.2", getX() + 1, getY() + height - 3,
                        getX() + width - 1, getY() + height - 1, TerminalUi.ACCENT);
            int color = !active ? TerminalUi.DISABLED : selected || primary ? TerminalUi.ACCENT : TerminalUi.TEXT;
            int textX = getX() + 8;
            if (!icon.isEmpty()) {
                int size = Math.min(30, height - 10);
                OriginalRewardUi.icon(graphics, icon, getX() + 8 + size / 2F, getY() + height / 2F, size, color, 1);
                textX += size + 8;
            }
            int textWidth = Math.max(0, getX() + width - textX - 8);
            boolean twoLines = !detail.getString().isEmpty() && height >= 30;
            int textY = getY() + (height - (twoLines ? 21 : font.lineHeight)) / 2;
            if (leftAligned) {
                TerminalUi.text(graphics, font, getMessage(), textX, textY, textWidth, color);
                if (twoLines)
                    TerminalUi.text(graphics, font, detail, textX, textY + 12, textWidth,
                            active ? TerminalUi.MUTED : TerminalUi.DISABLED);
            } else {
                String label = TerminalUi.ellipsis(font, getMessage(), width - 12);
                UiDraw.drawString(graphics, "TerminalButton.renderWidget.3", font, label,
                        getX() + (width - font.width(label)) / 2, textY, color, false);
            }
            if (clip != null)
                graphics.disableScissor();
        }
    }
}
