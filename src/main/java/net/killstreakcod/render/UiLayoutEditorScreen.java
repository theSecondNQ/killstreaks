package net.killstreakcod.render;

import net.killstreakcod.render.ui.TerminalButton;
import net.killstreakcod.render.ui.TerminalScroll;
import net.killstreakcod.render.ui.TerminalUi;
import net.killstreakcod.universal.TacticalViewPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** 真正的控件预览与拖动编辑；所有修改先写草稿，保存后原子替换本机 JSON。 */
public final class UiLayoutEditorScreen extends Screen {
    private final Screen parent;
    private final List<String> contexts = new ArrayList<>();
    private String context, selected;
    private List<UiLayout.Element> elements = List.of();
    private final EditBox[] values = new EditBox[5];

    private record Control(AbstractWidget widget, int offset) {
    }

    private final List<Control> controls = new ArrayList<>();
    private final TerminalScroll scroll = new TerminalScroll();
    private TerminalUi.Rect body;
    private TerminalButton top, save;
    private int panelX, panelWidth;
    private boolean syncing, dragging, hidden, finished;
    private double dragX, dragY;
    private UiLayout.Style dragStyle;
    private Component status = text("hint");

    public UiLayoutEditorScreen(Screen parent) {
        super(text("title"));
        this.parent = parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> ? null
                : parent;
        UiLayout.beginEdit();
        context = TacticalClient.remote() ? "hud." + TacticalClient.state.mode() : "hud.normal";
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.killstreakcod.layout." + key, args);
    }

    @Override
    protected void init() {
        controls.clear();
        contexts.clear();
        for (String mode : List.of("normal", "bomb_drone", "cruise", "wheelson", "chopper", "gunship", "earned", "nuke",
                "scope", "radio"))
            contexts.add("hud." + mode);
        if (!contexts.contains(context))
            context = "hud.normal";
        panelWidth = Math.min(196, Math.max(152, width / 3));
        panelX = width - panelWidth - 8;
        body = new TerminalUi.Rect(panelX + 6, 30, panelWidth - 12, Math.max(36, height - 74));
        scroll.layout(body, 240);
        int x = panelX + 8, w = panelWidth - 16;
        control(button(x, 30, 22, 20, Component.literal("‹"), () -> cycleContext(-1)), 0);
        control(button(x + w - 28, 30, 22, 20, Component.literal("›"), () -> cycleContext(1)), 0);
        control(button(x, 56, 22, 20, Component.literal("‹"), () -> cycleElement(-1)), 26);
        control(button(x + w - 28, 56, 22, 20, Component.literal("›"), () -> cycleElement(1)), 26);
        for (int i = 0; i < values.length; i++) {
            int index = i;
            var input = new EditBox(font, x + 52, 88 + i * 25, w - 63, 18, text("field." + i));
            input.setMaxLength(12);
            input.setResponder(value -> {
                if (!syncing)
                    applyValues(index);
            });
            values[i] = addRenderableWidget(input);
            control(input, 58 + i * 25);
        }
        top = button(x, 218, w, 20, text("top"), () -> {
            if (selected != null) {
                var s = UiLayout.style(selected);
                UiLayout.change(selected,
                        new UiLayout.Style(s.x(), s.y(), s.width(), s.height(), s.opacity(), !s.top()));
                updateFields();
            }
        });
        control(top, 188);
        control(button(x, 244, w / 2 - 6, 20, text("reset"), () -> {
            if (selected != null)
                UiLayout.change(selected, UiLayout.Style.DEFAULT);
            updateFields();
        }), 214);
        control(button(x + w / 2, 244, w / 2 - 6, 20, text("reset_page"), () -> {
            UiLayout.resetContext(context);
            updateFields();
        }), 214);
        button(x, height - 32, w / 2 - 3, 20, Component.translatable("gui.cancel"), this::onClose);
        save = button(x + w / 2 + 3, height - 32, w / 2 - 3, 20,
                Component.translatable("screen.killstreakcod.settings.save"), () -> {
                    if (UiLayout.save()) {
                        finished = true;
                        returnToParent();
                    } else
                        status = text("failed");
                }).primary();
        updateFields();
        positionControls();
    }

    private void control(AbstractWidget widget, int offset) {
        controls.add(new Control(widget, offset));
    }

    private void positionControls() {
        for (Control control : controls) {
            AbstractWidget widget = control.widget();
            widget.setY(body.y() + control.offset() - (int) scroll.offset());
            widget.visible = !hidden && widget.getY() >= body.y()
                    && widget.getY() + widget.getHeight() <= body.bottom();
            if (!widget.visible && getFocused() == widget)
                setFocused(null);
        }
    }

    private TerminalButton button(int x, int y, int w, int h, Component label, Runnable action) {
        return addRenderableWidget(new TerminalButton(x, y, w, h, label, action));
    }

    private void cycleContext(int direction) {
        int index = contexts.indexOf(context);
        context = contexts.get(Math.floorMod(index + direction, contexts.size()));
        selected = null;
        elements = List.of();
        updateFields();
    }

    private void cycleElement(int direction) {
        if (elements.isEmpty())
            return;
        int index = -1;
        for (int i = 0; i < elements.size(); i++)
            if (elements.get(i).key().equals(selected))
                index = i;
        selected = elements.get(Math.floorMod(index + direction, elements.size())).key();
        updateFields();
    }

    private void applyValues(int changed) {
        UiLayout.Element e = selected == null ? null : UiLayout.element(selected);
        if (e == null)
            return;
        try {
            float[] v = new float[5];
            for (int i = 0; i < v.length; i++)
                v[i] = Float.parseFloat(values[i].getValue());
            if (v[2] <= 0 || v[3] <= 0)
                throw new IllegalArgumentException("Invalid UI size");
            var original = e.original();
            var s = UiLayout.style(selected);
            UiLayout.change(selected,
                    new UiLayout.Style((v[0] - original.x() - original.width() / 2) / width,
                            (v[1] - original.y() - original.height() / 2) / height, v[2] / original.width(),
                            v[3] / original.height(), v[4] / 100, s.top()));
            for (EditBox value : values)
                value.setTextColor(TerminalUi.TEXT);
            save.active = true;
            status = text("hint");
        }
        catch (RuntimeException invalid) {
            values[changed].setTextColor(TerminalUi.WARNING);
            save.active = false;
            status = text("invalid");
        }
    }

    private void updateFields() {
        syncing = true;
        var e = selected == null ? null : UiLayout.element(selected);
        for (EditBox value : values) {
            if (value == null)
                continue;
            value.active = e != null;
            value.setTextColor(TerminalUi.TEXT);
        }
        if (e != null) {
            var s = UiLayout.style(selected);
            var box = UiLayout.resolve(e.original(), s, width, height);
            float[] numbers = { box.x() + box.width() / 2, box.y() + box.height() / 2, box.width(), box.height(),
                    s.opacity() * 100 };
            for (int i = 0; i < numbers.length; i++)
                values[i].setValue(String.format(java.util.Locale.ROOT, "%.1f", numbers[i]));
            top.selected(s.top());
        } else {
            for (EditBox value : values)
                if (value != null)
                    value.setValue("");
            if (top != null)
                top.selected(false);
        }
        if (top != null)
            top.active = e != null;
        if (save != null)
            save.active = true;
        syncing = false;
    }

    private void preview(GuiGraphics graphics, float partial) {
        if (context.equals("hud.earned")) {
            EarnedSplashRenderer
                    .render(graphics, "gunship",
                            Component
                                    .translatable("hud.killstreakcod.notice_ready",
                                            KillstreakKeyMappings.USE_SLOT_3.getTranslatedKeyMessage().getString())
                                    .getString(),
                            1600);
        } else if (context.equals("hud.nuke")) {
            NukeUiRenderer.render(graphics, 2800, 160, "PLAYER");
        } else if (context.equals("hud.normal")) {
            TacticalHud.normal(graphics);
        } else if (context.equals("hud.scope")) {
            NativePrecisionScopeHud.render(graphics);
        } else if (context.equals("hud.radio")) {
            net.killstreakcod.audio.NativeRadioSubtitles.preview(graphics);
        } else {
            TacticalViewPayload previous = TacticalClient.state;
            try {
                TacticalClient.state = new TacticalViewPayload(-9876, context.substring(4), 1200, 0, 100, false, 8, 3,
                        0, 45, 0, 0, 22, new int[3], new int[] { 30, 5, 1 }, 0);
                TacticalHud.remote(graphics);
            } finally {
                TacticalClient.state = previous;
            }
        }
        UiLayout.flush(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xa8071012);
        preview(graphics, partialTick);
        elements = UiLayout.elements(context);
        if (selected != null && elements.stream().noneMatch(e -> e.key().equals(selected))) {
            selected = null;
            updateFields();
        }
        if (selected == null && !elements.isEmpty()) {
            selected = elements.get(0).key();
            updateFields();
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2400);
        for (var e : elements) {
            var b = e.displayed();
            int color = e.key().equals(selected) ? 0xffbfd587 : b.contains(mouseX, mouseY) ? 0xffedf2ec : 0x5535474a;
            graphics.renderOutline(Math.round(b.x()), Math.round(b.y()), Math.max(2, Math.round(b.width())),
                    Math.max(2, Math.round(b.height())), color);
        }
        graphics.pose().popPose();
        if (hidden) {
            graphics.drawString(font, text("show_panel"), 8, 8, TerminalUi.TEXT, true);
            return;
        }
        // 窄 GUI 下以可滚动操作面板保留原始字号，拖动区域不缩放。
        positionControls();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2500);
        TerminalUi.panel(graphics, new TerminalUi.Rect(panelX, 8, panelWidth, height - 16), TerminalUi.BACKGROUND);
        TerminalUi.text(graphics, font, title, panelX + 8, 16, panelWidth - 16, TerminalUi.ACCENT);
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int offset = (int) scroll.offset();
        Component caption = text("context." + context.substring(4));
        TerminalUi.text(graphics, font, caption, panelX + 35, 36 - offset, panelWidth - 76, TerminalUi.TEXT);
        int selectedIndex = -1;
        for (int i = 0; i < elements.size(); i++)
            if (elements.get(i).key().equals(selected))
                selectedIndex = i;
        TerminalUi.text(graphics, font,
                Component.literal((selectedIndex + 1) + "/" + elements.size() + " · ")
                        .append(selectedIndex < 0 ? Component.empty() : UiLayout.label(elements.get(selectedIndex))),
                panelX + 35, 62 - offset, panelWidth - 76, TerminalUi.MUTED);
        for (int i = 0; i < 5; i++)
            if (values[i].visible)
                TerminalUi.text(graphics, font, text("field." + i), panelX + 8, 93 + i * 25 - offset, 48,
                        TerminalUi.TEXT);
        for (Control control : controls)
            control.widget().render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        scroll.render(graphics);
        for (var widget : renderables)
            if (controls.stream().noneMatch(c -> c.widget() == widget))
                widget.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
        TerminalUi.text(graphics, font, status, 8, height - 15, Math.max(1, panelX - 16), TerminalUi.TEXT);
        if (selected != null && UiLayout.element(selected) != null && mouseY > height - 26 && mouseX < panelX)
            graphics.renderTooltip(font, UiLayout.label(UiLayout.element(selected)), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!hidden && x >= panelX) {
            if (button == 0 && scroll.click(x, y))
                return true;
            return super.mouseClicked(x, y, button);
        }
        if (button == 0) {
            var hit = elements.stream().filter(e -> e.displayed().contains(x, y))
                    .min(Comparator.comparingDouble(e -> e.displayed().width() * e.displayed().height()));
            if (hit.isPresent()) {
                selected = hit.get().key();
                dragging = true;
                dragX = x;
                dragY = y;
                dragStyle = UiLayout.style(selected);
                setFocused(null);
                updateFields();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (scroll.drag(y)) {
            positionControls();
            return true;
        }
        if (dragging && selected != null) {
            UiLayout.change(selected,
                    new UiLayout.Style(dragStyle.x() + (float) ((x - dragX) / width),
                            dragStyle.y() + (float) ((y - dragY) / height), dragStyle.width(), dragStyle.height(),
                            dragStyle.opacity(), dragStyle.top()));
            updateFields();
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        scroll.release();
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        if (!hidden && body.contains(x, y)) {
            scroll.wheel(vertical);
            positionControls();
            return true;
        }
        return super.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_H && !(getFocused() instanceof EditBox)) {
            hidden = !hidden;
            return true;
        }
        if (!(getFocused() instanceof EditBox) && selected != null) {
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            if (dx != 0 || dy != 0) {
                var s = UiLayout.style(selected);
                float step = hasShiftDown() ? 10 : 1;
                UiLayout.change(selected, new UiLayout.Style(s.x() + dx * step / width, s.y() + dy * step / height,
                        s.width(), s.height(), s.opacity(), s.top()));
                updateFields();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        UiLayout.cancel();
        finished = true;
        returnToParent();
    }

    private void returnToParent() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (!finished)
            UiLayout.cancel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
