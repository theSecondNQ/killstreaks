package net.killstreakcod.render;

import net.killstreakcod.render.ui.TerminalButton;
import net.killstreakcod.render.ui.TerminalUi;
import net.killstreakcod.render.ui.TerminalUi.Rect;
import net.killstreakcod.universal.RequestTerminalStatePayload;

import java.io.IOException;
import java.util.EnumMap;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Local selection page; retaining the terminal also preserves its unsaved loadout/faction.
 * 本地皮肤选择页面保留终端实例，同时保留尚未提交的连杀配置和阵营选择。
 */
public final class OperatorSkinScreen extends Screen {
    private static final int GREEN = TerminalUi.ACCENT, MUTED = TerminalUi.MUTED, AMBER = TerminalUi.WARNING;
    private final Screen terminal;
    private final EnumMap<OperatorSkins.Role, OperatorSkins.Selection> choices = OperatorSkins.selections();
    private OperatorSkins.Role role = OperatorSkins.Role.CHOPPER_GUNNER;
    private int panelX, panelY, panelWidth, panelHeight, leftWidth, listX, listWidth, listY, rows, page;
    private Component status = Component.empty();
    private boolean error, compact;
    private PlayerSkinWidget preview;
    private Rect previewArea;

    public OperatorSkinScreen(Screen terminal) {
        super(Component.translatable("screen.killstreakcod.skins_title"));
        this.terminal = terminal;
        refreshFiles();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(800, width - 16);
        panelHeight = Math.min(500, height - 16);
        compact = panelHeight < 300;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        leftWidth = Math.max(82, (panelWidth - 42) / 3);
        listX = panelX + 14 + leftWidth + 12;
        listWidth = panelX + panelWidth - 14 - listX;
        listY = panelY + (compact ? 65 : 82);
        previewArea = new Rect(panelX + 14, listY, leftWidth, panelHeight - (listY - panelY) - 72);
        rows = Math.max(1, Math.min(12, (panelY + panelHeight - 96 - listY) / 26));
        showSelection();
        rebuild();
    }

    private int pages() {
        return Math.max(1, (OperatorSkins.files().size() + 1 + rows - 1) / rows);
    }

    private void showSelection() {
        int selected = OperatorSkins.files().indexOf(choices.get(role).file()) + 1;
        page = Math.max(0, selected) / Math.max(1, rows);
    }

    private TerminalButton button(int x, int y, int w, int h, Component label, Runnable action) {
        return addRenderableWidget(new TerminalButton(x, y, w, h, label, action));
    }

    private void rebuild() {
        clearWidgets();
        clearFocus();
        page = Math.max(0, Math.min(page, pages() - 1));
        int tabWidth = (panelWidth - 40) / 3;
        for (var value : OperatorSkins.Role.values())
            button(panelX + 14 + value.ordinal() * (tabWidth + 6), panelY + (compact ? 39 : 55), tabWidth, 20,
                    Component.translatable("screen.killstreakcod.skin_role." + value.key()), () -> {
                        role = value;
                        showSelection();
                        rebuild();
                    }).selected(value == role);
        preview = addRenderableWidget(new PlayerSkinWidget(leftWidth - 8, Math.max(24, previewArea.height() - 8),
                Minecraft.getInstance().getEntityModels(), () -> OperatorSkins.preview(choices.get(role))));
        preview.setPosition(panelX + 18, listY + 4);
        for (int i = 0; i < rows; i++) {
            int index = page * rows + i;
            if (index > OperatorSkins.files().size())
                break;
            String file = index == 0 ? "" : OperatorSkins.files().get(index - 1);
            Component full = file.isEmpty() ? Component.translatable("screen.killstreakcod.skin_player")
                    : Component.literal(file);
            boolean selected = choices.get(role).file().equals(file);
            button(listX, listY + i * 26, listWidth, 24, full, () -> {
                var previous = choices.get(role);
                choices.put(role, new OperatorSkins.Selection(file, previous.slim()));
                rebuild();
            }).selected(selected).setTooltip(Tooltip.create(full));
        }
        int pagerY = panelY + panelHeight - 90;
        button(listX, pagerY, 26, 18, Component.literal("<"), () -> {
            page--;
            rebuild();
        }).active = page > 0;
        button(listX + listWidth - 26, pagerY, 26, 18, Component.literal(">"), () -> {
            page++;
            rebuild();
        }).active = page + 1 < pages();
        var selected = choices.get(role);
        button(listX, panelY + panelHeight - 62, listWidth, 22, Component.translatable(
                "screen.killstreakcod.skin_model",
                Component.translatable(selected.file().isEmpty() ? "screen.killstreakcod.skin_follow_player"
                        : selected.slim() ? "screen.killstreakcod.skin_slim" : "screen.killstreakcod.skin_wide")),
                () -> {
                    var previous = choices.get(role);
                    choices.put(role, new OperatorSkins.Selection(previous.file(), !previous.slim()));
                    rebuild();
                }).active = !selected.file().isEmpty();
        button(panelX + 14, panelY + panelHeight - 62, leftWidth, 22,
                Component.translatable("screen.killstreakcod.skins_folder"),
                () -> Util.getPlatform().openPath(OperatorSkins.directory()));
        button(panelX + panelWidth - 94, panelY + 7, 80, 20,
                Component.translatable("screen.killstreakcod.whitelist_refresh"), () -> {
                    refreshFiles();
                    showSelection();
                    rebuild();
                });
        button(panelX + panelWidth - 164, panelY + panelHeight - 28, 70, 20, Component.translatable("gui.cancel"),
                this::onClose);
        button(panelX + panelWidth - 86, panelY + panelHeight - 28, 72, 20,
                Component.translatable("screen.killstreakcod.save"), () -> {
                    try {
                        OperatorSkins.save(choices);
                        onClose();
                    }
                    catch (IOException | RuntimeException failure) {
                        error = true;
                        status = Component.translatable("screen.killstreakcod.skins_save_failed");
                        com.mojang.logging.LogUtils.getLogger().warn("Cannot save operator skin selection", failure);
                    }
                }).primary();
    }

    private void refreshFiles() {
        try {
            OperatorSkins.refresh();
            error = false;
            status = Component.translatable("screen.killstreakcod.skins_count", OperatorSkins.files().size(),
                    OperatorSkins.rejected());
        }
        catch (IOException | RuntimeException failure) {
            error = true;
            status = Component.translatable("screen.killstreakcod.skins_read_failed");
            com.mojang.logging.LogUtils.getLogger().warn("Cannot refresh operator skin files", failure);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (UiLayout.Scope layoutFrame = UiLayout.screen(this)) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);
            TerminalUi.window(graphics, panel);
            TerminalUi.text(graphics, font, title, panelX + 14, panelY + 13, panelWidth - 124, TerminalUi.TEXT);
            if (!compact)
                TerminalUi.text(graphics, font, Component.translatable("screen.killstreakcod.skins_help"), panelX + 14,
                        panelY + 40, panelWidth - 28, MUTED);
            TerminalUi.panel(graphics, previewArea, TerminalUi.SURFACE);
            for (int y = previewArea.y() + 16; y < previewArea.bottom() - 1; y += 20)
                UiDraw.hLine(graphics, "OperatorSkinScreen.render.0", previewArea.x() + 1, previewArea.right() - 2, y,
                        TerminalUi.RAISED);
            int center = previewArea.x() + previewArea.width() / 2;
            UiDraw.vLine(graphics, "OperatorSkinScreen.render.1", center, previewArea.y() + 1, previewArea.bottom() - 2,
                    TerminalUi.RAISED);
            TerminalUi.panel(graphics, new Rect(listX - 3, listY - 3, listWidth + 6, rows * 26 + 4),
                    TerminalUi.SURFACE);
            UiDraw.drawCenteredString(graphics, "OperatorSkinScreen.render.2", font,
                    Component.translatable("screen.killstreakcod.whitelist_page", page + 1, pages()),
                    listX + listWidth / 2, panel.bottom() - 85, MUTED);
            Component message = !error && OperatorSkins.unavailable(choices.get(role))
                    ? Component.translatable("screen.killstreakcod.skin_unavailable")
                    : status;
            TerminalUi.text(graphics, font, message, panelX + 14, panel.bottom() - 22, panelWidth - 190,
                    error ? AMBER : MUTED);
            for (Renderable widget : renderables) {
                if (widget == preview)
                    graphics.enableScissor(previewArea.x() + 1, previewArea.y() + 1, previewArea.right() - 1,
                            previewArea.bottom() - 1);
                widget.render(graphics, mouseX, mouseY, partialTick);
                if (widget == preview)
                    graphics.disableScissor();
            }
            if (mouseX >= panelX + 14 && mouseX < panel.right() - 176 && mouseY >= panel.bottom() - 30)
                graphics.renderTooltip(font, message, mouseX, mouseY);

        } finally {
            UiLayout.flush(graphics);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < panelY + panelHeight - 68
                && vertical != 0) {
            int next = Math.max(0, Math.min(pages() - 1, page + (vertical < 0 ? 1 : -1)));
            if (next != page) {
                page = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            mc.setScreen(null);
            return;
        }
        mc.setScreen(terminal);
        PacketDistributor.sendToServer(new RequestTerminalStatePayload(false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
