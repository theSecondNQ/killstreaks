package net.killstreakcod.render;

import net.killstreakcod.render.ui.TerminalButton;
import net.killstreakcod.render.ui.TerminalUi;
import net.killstreakcod.render.ui.TerminalUi.Rect;
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
    private static final int GREEN = TerminalUi.ACCENT, WHITE = TerminalUi.TEXT;
    private static final int MUTED = TerminalUi.MUTED, AMBER = TerminalUi.WARNING;
    private final Screen terminal;
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
    private boolean singleColumn, onlineTab, quietRequest, compact;
    private String input = "";
    private Component status = Component.translatable("screen.killstreakcod.whitelist_loading");
    private EditBox name;
    private Button addName, previousTeam, nextTeam, previousOnline, nextOnline, refresh;

    public KillstreakWhitelistScreen(Screen terminal) {
        super(Component.translatable("screen.killstreakcod.whitelist_title"));
        this.terminal = terminal;
    }

    @Override
    protected void init() {
        if (name != null)
            input = name.getValue();
        panelWidth = Math.min(800, width - 16);
        panelHeight = Math.min(500, height - 16);
        compact = panelHeight < 280;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        columnWidth = (panelWidth - 44) / 2;
        singleColumn = panelWidth < 580;
        leftX = panelX + 14;
        rightX = leftX + columnWidth + 16;
        listY = panelY + (compact ? 60 : 83);
        pageY = panelY + panelHeight - 79;
        if (singleColumn) {
            columnWidth = panelWidth - 28;
            rightX = leftX;
        }
        inputY = panelY + panelHeight - 55;
        statusY = panelY + panelHeight - 21;
        rows = Math.max(1, Math.min(WhitelistStatePayload.MAX_PAGE_SIZE, (pageY - listY - 4) / 28));
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
        name = addRenderableWidget(new EditBox(font, leftX + 5, inputY + 6, panelWidth - 128, 16,
                Component.translatable("screen.killstreakcod.whitelist_name")));
        name.setMaxLength(64);
        name.setBordered(false);
        name.setTextColor(WHITE);
        name.setHint(Component.translatable("screen.killstreakcod.whitelist_name"));
        name.setValue(input);
        name.setResponder(value -> {
            input = value;
            updateEnabled();
        });
        addName = action(leftX + panelWidth - 110, inputY, 82, 20, "screen.killstreakcod.whitelist_add",
                () -> change(WhitelistActionPayload.ADD, name.getValue().trim())).layoutId("add-name");
        previousTeam = action(leftX, pageY, 25, 18, "screen.killstreakcod.whitelist_previous", () -> {
            teammatePage = Math.max(0, teammatePage - 1);
            requestState();
        }).layoutId("previousTeam");
        nextTeam = action(leftX + columnWidth - 25, pageY, 25, 18, "screen.killstreakcod.whitelist_next", () -> {
            teammatePage++;
            requestState();
        }).layoutId("nextTeam");
        previousOnline = action(rightX, pageY, 25, 18, "screen.killstreakcod.whitelist_previous", () -> {
            onlinePage = Math.max(0, onlinePage - 1);
            requestState();
        }).layoutId("previousOnline");
        nextOnline = action(rightX + columnWidth - 25, pageY, 25, 18, "screen.killstreakcod.whitelist_next", () -> {
            onlinePage++;
            requestState();
        }).layoutId("nextOnline");
        if (singleColumn) {
            previousTeam.visible = nextTeam.visible = !onlineTab;
            previousOnline.visible = nextOnline.visible = onlineTab;
            int tabWidth = (columnWidth - 6) / 2;
            addRenderableWidget(new TerminalButton(leftX, panelY + (compact ? 34 : 55), tabWidth, 20, Component
                    .translatable("screen.killstreakcod.whitelist_team_tab", state == null ? 0 : state.teammateTotal()),
                    () -> {
                        onlineTab = false;
                        rebuildControls();
                    }).selected(!onlineTab));
            addRenderableWidget(new TerminalButton(leftX + tabWidth + 6, panelY + (compact ? 34 : 55), tabWidth, 20,
                    Component.translatable("screen.killstreakcod.whitelist_online_tab",
                            state == null ? 0 : state.onlineTotal()),
                    () -> {
                        onlineTab = true;
                        rebuildControls();
                    }).selected(onlineTab));
        }
        refresh = action(panelX + panelWidth - 94, panelY + 7, 80, 20, "screen.killstreakcod.whitelist_refresh",
                this::requestState);
        // 保留原终端实例，使关闭此页后仍能保存尚未提交的奖励和阵营选择。
        addRenderableWidget(new TerminalButton(panelX + panelWidth - 130, panelY + panelHeight - 27, 116, 20,
                Component.translatable("screen.killstreakcod.whitelist_back"), this::onClose));
        if (state != null) {
            for (int i = 0; (!singleColumn || !onlineTab) && i < Math.min(rows, state.teammates().size()); i++) {
                var member = state.teammates().get(i);
                action(leftX + columnWidth - 50, listY + i * 28 + 3, 50, 20, "screen.killstreakcod.whitelist_remove",
                        () -> change(WhitelistActionPayload.REMOVE, member.uuid().toString()))
                        .layoutId("team.remove." + i);
            }
            for (int i = 0; (!singleColumn || onlineTab) && i < Math.min(rows, state.online().size()); i++) {
                var member = state.online().get(i);
                action(rightX + columnWidth - 50, listY + i * 28 + 3, 50, 20, "screen.killstreakcod.whitelist_add",
                        () -> change(WhitelistActionPayload.ADD, member.uuid().toString())).layoutId("online.add." + i);
            }
        }
        updateEnabled();
        name.setCursorPosition(Math.min(cursor, input.length()));
        if (focused)
            setInitialFocus(name);
    }

    private TerminalButton action(int x, int y, int width, int height, String key, Runnable callback) {
        TerminalButton button = addRenderableWidget(
                new TerminalButton(x, y, width, height, Component.translatable(key), callback));
        actions.add(button);
        return button;
    }

    private void updateEnabled() {
        if (name == null || addName == null)
            return;
        boolean writable = state != null && state.writable(), available = !pending && cooldown == 0;
        for (Button action : actions)
            action.active = available && writable;
        // 名字输入不受后台刷新及按钮冷却影响，避免输入到一半丢失按键。
        name.setEditable(writable);
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
        if (minecraft.getConnection() == null)
            return;
        beginRequest();
        StreakPackets.sendToServer(new RequestWhitelistPayload(request, teammatePage, onlinePage, rows));
    }

    private void change(int action, String target) {
        if (pending || state == null || !state.writable() || target.isBlank())
            return;
        quietRequest = false;
        beginRequest();
        StreakPackets.sendToServer(new WhitelistActionPayload(request, action, target, teammatePage, onlinePage, rows));
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
        try (UiLayout.Scope layoutFrame = UiLayout.screen(this)) {
            renderBackground(graphics);
            Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);
            TerminalUi.window(graphics, panel);
            TerminalUi.text(graphics, font, title, leftX, panelY + 13, panelWidth - 124, WHITE);
            if (!compact)
                TerminalUi.text(graphics, font, Component.translatable("screen.killstreakcod.whitelist_help"), leftX,
                        panelY + 40, panelWidth - 28, MUTED);
            if (!singleColumn) {
                TerminalUi.text(graphics, font,
                        Component.translatable("screen.killstreakcod.whitelist_teammates",
                                state == null ? 0 : state.teammateTotal()),
                        leftX, panelY + (compact ? 41 : 62), columnWidth, GREEN);
                TerminalUi.text(graphics, font,
                        Component.translatable("screen.killstreakcod.whitelist_online",
                                state == null ? 0 : state.onlineTotal()),
                        rightX, panelY + (compact ? 41 : 62), columnWidth, GREEN);
            }
            if (!singleColumn || !onlineTab)
                TerminalUi.panel(graphics, new Rect(leftX - 1, listY - 2, columnWidth + 2, pageY - listY + 24),
                        TerminalUi.SURFACE);
            if (!singleColumn || onlineTab)
                TerminalUi.panel(graphics, new Rect(rightX - 1, listY - 2, columnWidth + 2, pageY - listY + 24),
                        TerminalUi.SURFACE);
            if (state != null) {
                if (!singleColumn || !onlineTab) {
                    drawMembers(graphics, state.teammates(), leftX, mouseX, mouseY);
                    drawEmpty(graphics, state.teammates(), leftX, "screen.killstreakcod.whitelist_no_teammates");
                    UiDraw.drawCenteredString(graphics, "KillstreakWhitelistScreen.render.0", font,
                            Component.translatable("screen.killstreakcod.whitelist_page", teammatePage + 1,
                                    state.teammatePages()),
                            leftX + columnWidth / 2, pageY + 5, MUTED);
                }
                if (!singleColumn || onlineTab) {
                    drawMembers(graphics, state.online(), rightX, mouseX, mouseY);
                    drawEmpty(graphics, state.online(), rightX, "screen.killstreakcod.whitelist_no_online");
                    UiDraw.drawCenteredString(graphics, "KillstreakWhitelistScreen.render.1", font, Component
                            .translatable("screen.killstreakcod.whitelist_page", onlinePage + 1, state.onlinePages()),
                            rightX + columnWidth / 2, pageY + 5, MUTED);
                }
            } else {
                TerminalUi.text(graphics, font, status, leftX + 8, listY + 10, columnWidth - 16, MUTED);
            }
            TerminalUi.panel(graphics, new Rect(leftX, inputY, panelWidth - 118, 24), TerminalUi.SURFACE);
            TerminalUi.text(graphics, font, status, leftX, statusY, panelWidth - 158,
                    pending || state != null && !state.writable() ? AMBER : MUTED);
            for (Renderable widget : renderables)
                widget.render(graphics, mouseX, mouseY, partialTick);
            if (state != null) {
                if (!singleColumn || !onlineTab)
                    tooltip(graphics, state.teammates(), leftX, mouseX, mouseY);
                if (!singleColumn || onlineTab)
                    tooltip(graphics, state.online(), rightX, mouseX, mouseY);
            }
            if (mouseY >= statusY - 6 && mouseX < panel.right() - 136)
                graphics.renderTooltip(font, status, mouseX, mouseY);

        } finally {
            UiLayout.flush(graphics);
        }
    }

    private void drawEmpty(GuiGraphics graphics, List<WhitelistStatePayload.Member> members, int x, String key) {
        if (members.isEmpty())
            TerminalUi.text(graphics, font, Component.translatable(key), x + 8, listY + 10, columnWidth - 16, MUTED);
    }

    private void drawMembers(GuiGraphics graphics, List<WhitelistStatePayload.Member> members, int x, int mouseX,
            int mouseY) {
        for (int i = 0; i < Math.min(rows, members.size()); i++) {
            var member = members.get(i);
            int y = listY + i * 28;
            Rect row = new Rect(x + 2, y, columnWidth - 4, 26);
            UiDraw.fill(graphics, "KillstreakWhitelistScreen.drawMembers.0", row.x(), row.y(), row.right(),
                    row.bottom(), row.contains(mouseX, mouseY) ? TerminalUi.RAISED : TerminalUi.SURFACE);
            UiDraw.fill(graphics, "KillstreakWhitelistScreen.drawMembers.1", x + 6, y + 5, x + 22, y + 21,
                    TerminalUi.BORDER);
            if (!member.name().isEmpty())
                UiDraw.drawCenteredString(graphics, "KillstreakWhitelistScreen.drawMembers.2", font,
                        member.name().substring(0, 1), x + 14, y + 9, WHITE);
            UiDraw.fill(graphics, "KillstreakWhitelistScreen.drawMembers.3", x + 20, y + 18, x + 24, y + 22,
                    member.online() ? GREEN : MUTED);
            TerminalUi.text(graphics, font, Component.literal(member.name()), x + 29, y + 9, columnWidth - 87, WHITE);
        }
    }

    private void tooltip(GuiGraphics graphics, List<WhitelistStatePayload.Member> members, int x, int mouseX,
            int mouseY) {
        if (mouseX < x || mouseX >= x + columnWidth - 50)
            return;
        int index = (mouseY - listY) / 28;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && name.isFocused() && addName.active) {
            addName.onPress();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (mouseY >= listY && mouseY < pageY && state != null && vertical != 0) {
            boolean online = singleColumn ? onlineTab : mouseX >= rightX;
            int x = online ? rightX : leftX;
            if (mouseX >= x && mouseX < x + columnWidth) {
                Button page = online ? vertical > 0 ? previousOnline : nextOnline
                        : vertical > 0 ? previousTeam : nextTeam;
                if (page.active)
                    page.onPress();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    @Override
    public void onClose() {
        var mc = Minecraft.getInstance();
        mc.setScreen(mc.getConnection() == null ? null : terminal);
        if (mc.getConnection() != null)
            StreakPackets.sendToServer(new RequestTerminalStatePayload(false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
