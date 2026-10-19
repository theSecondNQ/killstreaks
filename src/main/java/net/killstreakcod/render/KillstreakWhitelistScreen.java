package net.killstreakcod.render;

import net.killstreakcod.universal.RequestTerminalStatePayload;
import net.killstreakcod.universal.RequestWhitelistPayload;
import net.killstreakcod.universal.WhitelistActionPayload;
import net.killstreakcod.universal.WhitelistStatePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.killstreakcod.network.StreakPackets;

/**
 * A terminal page with real widgets, independent of GUI scale and server-owned friendship data.
 * 终端页面使用实际界面控件，布局适应 GUI 缩放，队友关系数据由服务端管理。
 */
public final class KillstreakWhitelistScreen extends Screen {
    private static final int GREEN = 0xFF9BBB62, WHITE = 0xFFE7ECE5, MUTED = 0xFF9BA9A0, AMBER = 0xFFE0B35D;
    private final KillstreakTerminalScreen terminal;
    private final List<Button> actions = new ArrayList<>();
    private WhitelistStatePayload state;
    private UUID request;
    private boolean pending;
    private int requestTicks;
    private int cooldown;
    private int panelX, panelY, panelWidth, panelHeight, columnWidth, leftX, rightX, listY, pageY, inputY, statusY,
            rows;
    private int teammatePage, onlinePage;
    private int refreshTicks;
    private boolean singleColumn, onlineTab, quietRequest;
    private String input = "";
    private Component status = Component.translatable("screen.killstreakcod.whitelist_loading");
    private EditBox name;
    private Button addName, previousTeam, nextTeam, previousOnline, nextOnline, refresh;

    public KillstreakWhitelistScreen(KillstreakTerminalScreen terminal) {
        super(Component.translatable("screen.killstreakcod.whitelist_title"));
        this.terminal = terminal;
    }

    @Override
    protected void init() {
        if (name != null)
            input = name.getValue();
        panelWidth = Math.min(720, width - 12);
        panelHeight = Math.min(450, height - 12);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        columnWidth = (panelWidth - 44) / 2;
        singleColumn = panelWidth < 540;
        leftX = panelX + 14;
        rightX = leftX + columnWidth + 16;
        listY = panelY + (singleColumn ? 84 : 75);
        pageY = panelY + panelHeight - 103;
        if (singleColumn) {
            columnWidth = panelWidth - 28;
            rightX = leftX;
        }
        inputY = panelY + panelHeight - 77;
        statusY = panelY + panelHeight - 52;
        rows = Math.max(1, Math.min(WhitelistStatePayload.MAX_PAGE_SIZE, (pageY - listY) / 24));
        rebuildControls();
        requestState();
    }

    private void rebuildControls() {
        boolean focused = name == null || name.isFocused();
        int cursor = name == null ? input.length() : name.getCursorPosition();
        if (name != null)
            input = name.getValue();
        clearWidgets();
        setFocused(null);
        actions.clear();
        name = addRenderableWidget(new EditBox(font, leftX, inputY, panelWidth - 118, 20,
                Component.translatable("screen.killstreakcod.whitelist_name")));
        name.setMaxLength(64);
        name.setHint(Component.translatable("screen.killstreakcod.whitelist_name"));
        name.setValue(input);
        name.setResponder(value -> {
            input = value;
            updateEnabled();
        });
        addName = action(leftX + panelWidth - 110, inputY, 82, 20, "screen.killstreakcod.whitelist_add",
                () -> change(WhitelistActionPayload.ADD, name.getValue().trim()));
        previousTeam = action(leftX, pageY, 25, 18, "screen.killstreakcod.whitelist_previous", () -> {
            teammatePage = Math.max(0, teammatePage - 1);
            requestState();
        });
        nextTeam = action(leftX + columnWidth - 25, pageY, 25, 18, "screen.killstreakcod.whitelist_next", () -> {
            teammatePage++;
            requestState();
        });
        previousOnline = action(rightX, pageY, 25, 18, "screen.killstreakcod.whitelist_previous", () -> {
            onlinePage = Math.max(0, onlinePage - 1);
            requestState();
        });
        nextOnline = action(rightX + columnWidth - 25, pageY, 25, 18, "screen.killstreakcod.whitelist_next", () -> {
            onlinePage++;
            requestState();
        });
        if (singleColumn) {
            previousTeam.visible = nextTeam.visible = !onlineTab;
            previousOnline.visible = nextOnline.visible = onlineTab;
            int tabWidth = (columnWidth - 6) / 2;
            addRenderableWidget(Button.builder(Component.translatable("screen.killstreakcod.whitelist_team_tab",
                    state == null ? 0 : state.teammateTotal()), b -> {
                        onlineTab = false;
                        rebuildControls();
                    }).bounds(leftX, panelY + 57, tabWidth, 20).build()).active = onlineTab;
            addRenderableWidget(Button.builder(Component.translatable("screen.killstreakcod.whitelist_online_tab",
                    state == null ? 0 : state.onlineTotal()), b -> {
                        onlineTab = true;
                        rebuildControls();
                    }).bounds(leftX + tabWidth + 6, panelY + 57, tabWidth, 20).build()).active = !onlineTab;
        }
        refresh = action(panelX + panelWidth - 108, panelY + 12, 94, 20, "screen.killstreakcod.whitelist_refresh",
                this::requestState);

        // 保留原终端实例，使关闭此页后仍能保存尚未提交的奖励和阵营选择。
        addRenderableWidget(
                Button.builder(Component.translatable("screen.killstreakcod.whitelist_back"), button -> onClose())
                        .bounds(panelX + panelWidth - 130, panelY + panelHeight - 27, 116, 20).build());
        if (state != null) {
            for (int i = 0; (!singleColumn || !onlineTab) && i < Math.min(rows, state.teammates().size()); i++) {
                var member = state.teammates().get(i);
                action(leftX + columnWidth - 50, listY + i * 24 + 2, 50, 20, "screen.killstreakcod.whitelist_remove",
                        () -> change(WhitelistActionPayload.REMOVE, member.uuid().toString()));
            }
            for (int i = 0; (!singleColumn || onlineTab) && i < Math.min(rows, state.online().size()); i++) {
                var member = state.online().get(i);
                action(rightX + columnWidth - 50, listY + i * 24 + 2, 50, 20, "screen.killstreakcod.whitelist_add",
                        () -> change(WhitelistActionPayload.ADD, member.uuid().toString()));
            }
        }
        updateEnabled();
        name.setCursorPosition(Math.min(cursor, input.length()));
        if (focused)
            setInitialFocus(name);
    }

    private Button action(int x, int y, int width, int height, String key, Runnable callback) {
        Button button = addRenderableWidget(Button.builder(Component.translatable(key), ignored -> callback.run())
                .bounds(x, y, width, height).build());
        actions.add(button);
        return button;
    }

    private void updateEnabled() {
        if (name == null || addName == null)
            return;
        boolean writable = state != null && state.writable(), available = !pending && cooldown == 0;
        for (Button action : actions)
            action.active = available && writable;
        name.setEditable(available && writable);
        addName.active = available && writable && !name.getValue().trim().isEmpty();
        previousTeam.active = available && state != null && teammatePage > 0;
        nextTeam.active = available && state != null && teammatePage + 1 < state.teammatePages();
        previousOnline.active = available && state != null && onlinePage > 0;
        nextOnline.active = available && state != null && onlinePage + 1 < state.onlinePages();
        refresh.active = available;
    }

    private void beginRequest() {
        request = UUID.randomUUID();
        pending = true;
        requestTicks = 0;
        refreshTicks = 0;
        if (!quietRequest)
            status = Component.translatable("screen.killstreakcod.whitelist_loading");
        updateEnabled();
    }

    private void requestState() {
        quietRequest = false;
        requestStatePacket();
    }

    private void requestStatePacket() {
        beginRequest();
        StreakPackets.sendToServer(new RequestWhitelistPayload(request, teammatePage, onlinePage, rows));
    }

    private void change(int action, String target) {
        if (pending || state == null || !state.writable() || target.isBlank())
            return;
        quietRequest = false;
        beginRequest();
        StreakPackets
                .sendToServer(new WhitelistActionPayload(request, action, target, teammatePage, onlinePage, rows));
    }

    public void receiveState(WhitelistStatePayload received) {
        if (!received.request().equals(request))
            return;
        boolean changed = state == null || !state.teammates().equals(received.teammates())
                || !state.online().equals(received.online()) || state.writable() != received.writable()
                || state.teammatePage() != received.teammatePage() || state.onlinePage() != received.onlinePage()
                || state.teammateTotal() != received.teammateTotal() || state.onlineTotal() != received.onlineTotal();
        state = received;
        teammatePage = received.teammatePage();
        onlinePage = received.onlinePage();
        pending = received.message().equals("screen.killstreakcod.whitelist_pending");
        cooldown = 4;
        if (!quietRequest || !received.message().isEmpty())
            status = received.message().isEmpty() ? Component.translatable("screen.killstreakcod.whitelist_saved_help")
                    : Component.translatable(received.message(), received.detail());
        if (changed)
            rebuildControls();
        else
            updateEnabled();
    }

    @Override
    public void tick() {
        super.tick();
        if (cooldown > 0 && --cooldown == 0)
            updateEnabled();
        if (pending && ++requestTicks >= 200) {
            pending = false;
            status = Component.translatable("screen.killstreakcod.whitelist_timeout");
            updateEnabled();
        }
        if (!pending && cooldown == 0 && ++refreshTicks >= 60) {
            quietRequest = true;
            requestStatePacket();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF40514A);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF00A1012);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), panelWidth - 130), leftX, panelY + 18,
                GREEN, false);
        graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable("screen.killstreakcod.whitelist_help").getString(),
                        panelWidth - 28),
                leftX, panelY + 39, MUTED, false);
        if (!singleColumn) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable("screen.killstreakcod.whitelist_teammates",
                            state == null ? 0 : state.teammateTotal()).getString(), columnWidth),
                    leftX, panelY + 60, GREEN, false);
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable("screen.killstreakcod.whitelist_online",
                            state == null ? 0 : state.onlineTotal()).getString(), columnWidth),
                    rightX, panelY + 60, GREEN, false);
        }
        if (state != null) {
            if (!singleColumn || !onlineTab) {
                drawMembers(graphics, state.teammates(), leftX, mouseX, mouseY);
                drawEmpty(graphics, state.teammates(), leftX, "screen.killstreakcod.whitelist_no_teammates");
                graphics.drawCenteredString(font, Component.translatable("screen.killstreakcod.whitelist_page",
                        teammatePage + 1, state.teammatePages()), leftX + columnWidth / 2, pageY + 5, MUTED);
            }
            if (!singleColumn || onlineTab) {
                drawMembers(graphics, state.online(), rightX, mouseX, mouseY);
                drawEmpty(graphics, state.online(), rightX, "screen.killstreakcod.whitelist_no_online");
                graphics.drawCenteredString(font, Component.translatable("screen.killstreakcod.whitelist_page",
                        onlinePage + 1, state.onlinePages()), rightX + columnWidth / 2, pageY + 5, MUTED);
            }
        }
        var lines = font.split(status, panelWidth - 28);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            graphics.drawString(font, lines.get(i), leftX, statusY + i * font.lineHeight,
                    pending || state != null && !state.writable() ? AMBER : GREEN, false);

        // 本屏幕统一控制背景与列表绘制顺序，最后只绘制控件，保留手动绘制的名字和列表行。
        for (Renderable widget : renderables)
            widget.render(graphics, mouseX, mouseY, partialTick);
        if (state != null) {
            if (!singleColumn || !onlineTab)
                tooltip(graphics, state.teammates(), leftX, mouseX, mouseY);
            if (!singleColumn || onlineTab)
                tooltip(graphics, state.online(), rightX, mouseX, mouseY);
        }
    }

    private void drawEmpty(GuiGraphics graphics, List<WhitelistStatePayload.Member> members, int x, String key) {
        if (members.isEmpty())
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(key).getString(), columnWidth - 8),
                    x + 4, listY + 8, MUTED, false);
    }

    private void drawMembers(GuiGraphics graphics, List<WhitelistStatePayload.Member> members, int x, int mouseX,
            int mouseY) {
        for (int i = 0; i < Math.min(rows, members.size()); i++) {
            var member = members.get(i);
            int y = listY + i * 24;
            graphics.fill(x, y, x + columnWidth, y + 23, 0xFF162220);
            graphics.fill(x + 4, y + 8, x + 8, y + 12, member.online() ? GREEN : MUTED);
            graphics.drawString(font, font.plainSubstrByWidth(member.name(), columnWidth - 66), x + 12, y + 7, WHITE,
                    false);
        }
    }

    private void tooltip(GuiGraphics graphics, List<WhitelistStatePayload.Member> members, int x, int mouseX,
            int mouseY) {
        if (mouseX < x || mouseX >= x + columnWidth - 50)
            return;
        int index = (mouseY - listY) / 24;
        if (mouseY < listY || index < 0 || index >= Math.min(rows, members.size()))
            return;
        var member = members.get(index);
        graphics.renderTooltip(font,
                List.of(Component.literal(member.name()),
                        Component.translatable(member.online() ? "screen.killstreakcod.whitelist_player_online"
                                : "screen.killstreakcod.whitelist_player_offline"),
                        Component.literal(member.uuid().toString())),
                java.util.Optional.empty(), mouseX, mouseY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(terminal);
        StreakPackets.sendToServer(new RequestTerminalStatePayload(false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
