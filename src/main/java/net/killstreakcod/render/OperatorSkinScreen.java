package net.killstreakcod.render;

import net.killstreakcod.universal.RequestTerminalStatePayload;

import java.io.IOException;
import java.util.EnumMap;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int GREEN = 0xFF9BBB62, MUTED = 0xFF9BA9A0, AMBER = 0xFFE0B35D;
    private final KillstreakTerminalScreen terminal;
    private final EnumMap<OperatorSkins.Role, OperatorSkins.Selection> choices = OperatorSkins.selections();
    private OperatorSkins.Role role = OperatorSkins.Role.CHOPPER_GUNNER;
    private int panelX, panelY, panelWidth, panelHeight, leftWidth, listX, listWidth, listY, rows, page;
    private Component status = Component.empty();
    private boolean error;

    public OperatorSkinScreen(KillstreakTerminalScreen terminal) {
        super(Component.translatable("screen.killstreakcod.skins_title"));
        this.terminal = terminal;
        refreshFiles();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(720, width - 12);
        panelHeight = Math.min(450, height - 12);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        leftWidth = Math.max(82, (panelWidth - 42) / 3);
        listX = panelX + 14 + leftWidth + 12;
        listWidth = panelX + panelWidth - 14 - listX;
        listY = panelY + 70;
        rows = Math.max(1, Math.min(12, (panelHeight - 176) / 22));
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

    private Button button(int x, int y, int w, int h, Component label, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, h).build());
    }

    private void rebuild() {
        clearWidgets();
        clearFocus();
        page = Math.max(0, Math.min(page, pages() - 1));
        int tabWidth = (panelWidth - 40) / 3;
        for (var value : OperatorSkins.Role.values())
            button(panelX + 14 + value.ordinal() * (tabWidth + 6), panelY + 39, tabWidth, 20,
                    Component.translatable("screen.killstreakcod.skin_role." + value.key()), () -> {
                        role = value;
                        showSelection();
                        rebuild();
                    }).active = value != role;
        var preview = addRenderableWidget(new PlayerSkinWidget(leftWidth, Math.max(40, panelHeight - 132),
                Minecraft.getInstance().getEntityModels(), () -> OperatorSkins.preview(choices.get(role))));
        preview.setPosition(panelX + 14, listY + 2);
        for (int i = 0; i < rows; i++) {
            int index = page * rows + i;
            if (index > OperatorSkins.files().size())
                break;
            String file = index == 0 ? "" : OperatorSkins.files().get(index - 1);
            Component full = file.isEmpty() ? Component.translatable("screen.killstreakcod.skin_player")
                    : Component.literal(file);
            boolean selected = choices.get(role).file().equals(file);
            Component label = Component
                    .literal((selected ? "✓ " : "") + font.plainSubstrByWidth(full.getString(), listWidth - 22))
                    .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            button(listX, listY + i * 22, listWidth, 20, label, () -> {
                var previous = choices.get(role);
                choices.put(role, new OperatorSkins.Selection(file, previous.slim()));
                rebuild();
            }).setTooltip(Tooltip.create(full));
        }
        int pagerY = panelY + panelHeight - 106;
        button(listX, pagerY, 26, 18, Component.literal("<"), () -> {
            page--;
            rebuild();
        }).active = page > 0;
        button(listX + listWidth - 26, pagerY, 26, 18, Component.literal(">"), () -> {
            page++;
            rebuild();
        }).active = page + 1 < pages();
        var selected = choices.get(role);
        button(listX, panelY + panelHeight - 82, listWidth, 20, Component.translatable(
                "screen.killstreakcod.skin_model",
                Component.translatable(selected.file().isEmpty() ? "screen.killstreakcod.skin_follow_player"
                        : selected.slim() ? "screen.killstreakcod.skin_slim" : "screen.killstreakcod.skin_wide")),
                () -> {
                    var previous = choices.get(role);
                    choices.put(role, new OperatorSkins.Selection(previous.file(), !previous.slim()));
                    rebuild();
                }).active = !selected.file().isEmpty();
        button(panelX + 14, panelY + panelHeight - 55, 132, 20,
                Component.translatable("screen.killstreakcod.skins_folder"),
                () -> Util.getPlatform().openPath(OperatorSkins.directory()));
        button(panelX + 154, panelY + panelHeight - 55, 92, 20,
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
                });
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF40514A);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF00A1012);
        graphics.drawString(font, title, panelX + 14, panelY + 12, GREEN, false);
        graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable("screen.killstreakcod.skins_help").getString(),
                        panelWidth - 28),
                panelX + 14, panelY + 26, MUTED, false);
        graphics.drawCenteredString(font,
                Component.translatable("screen.killstreakcod.whitelist_page", page + 1, pages()), listX + listWidth / 2,
                panelY + panelHeight - 101, MUTED);
        Component message = !error && OperatorSkins.unavailable(choices.get(role))
                ? Component.translatable("screen.killstreakcod.skin_unavailable")
                : status;
        graphics.drawString(font, font.plainSubstrByWidth(message.getString(), panelWidth - 190), panelX + 14,
                panelY + panelHeight - 22, error ? AMBER : GREEN, false);
        for (Renderable widget : renderables)
            widget.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= panelX + 14 && mouseX < panelX + panelWidth - 176 && mouseY >= panelY + panelHeight - 30
                && mouseY < panelY + panelHeight - 8)
            graphics.renderTooltip(font, message, mouseX, mouseY);
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
