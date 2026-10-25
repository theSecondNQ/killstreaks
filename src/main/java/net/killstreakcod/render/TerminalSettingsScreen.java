package net.killstreakcod.render;

import net.killstreakcod.audio.VoiceFaction;
import net.killstreakcod.render.ui.TerminalButton;
import net.killstreakcod.render.ui.TerminalScroll;
import net.killstreakcod.render.ui.TerminalUi;
import net.killstreakcod.render.ui.TerminalUi.Rect;
import net.killstreakcod.universal.RequestTerminalStatePayload;
import net.killstreakcod.universal.StreakServerConfig;
import net.killstreakcod.universal.StreakServerConfig.Kind;
import net.killstreakcod.universal.StreakServerConfig.Setting;
import net.killstreakcod.universal.TerminalSettingsActionPayload;
import net.killstreakcod.universal.TerminalSettingsStatePayload;
import net.killstreakcod.universal.TerminalStatePayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 设置页保留未提交的输入；客户端偏好、玩家阵营与服务器规则分别保存。 */
public final class TerminalSettingsScreen extends Screen {
    enum Page {
        GENERAL, SERVER, CLIENT, KEYS
    }

    private record Row(int top, int height, Component title, Component help, AbstractWidget field,
            TerminalButton reset) {
    }

    private final KillstreakTerminalScreen parent;
    private final TerminalScroll scroll = new TerminalScroll();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, String> serverDraft = new LinkedHashMap<>();
    private final Map<String, String> clientDraft = new LinkedHashMap<>();
    private Map<String, Double> serverValues = Map.of();
    private Map<String, Double> clientValues;
    private String faction, savedFaction;
    private Page page = Page.GENERAL;
    private Rect panel, body;
    private TerminalButton save, refresh, back;
    private KeyMapping editingKey;
    private UUID request;
    private boolean pendingWrite, loaded, writable, warning;
    private int requestTicks, contentHeight;
    private Component status = text("loading");

    public TerminalSettingsScreen(KillstreakTerminalScreen parent) {
        super(text("title"));
        this.parent = parent;
        faction = savedFaction = parent.voiceFaction();
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("screen.killstreakcod.settings." + key, arguments);
    }

    String layoutPage() {
        return page.name().toLowerCase(java.util.Locale.ROOT);
    }

    void layoutPage(String name) {
        Page next = Page.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        if (next != page) {
            page = next;
            scroll.set(0);
            rebuild();
        }
    }

    @Override
    protected void init() {
        if (clientValues == null)
            clientValues = TerminalClientSettings.snapshot();
        rebuild();
        if (!loaded && request == null)
            request(false);
    }

    private void rebuild() {
        clearWidgets();
        rows.clear();
        contentHeight = 0;
        panel = TerminalUi.window(width, height, 820, 520);
        int inner = panel.width() - 24;
        int tabWidth = (inner - 18) / 4;
        for (Page tab : Page.values()) {
            button(panel.x() + 12 + tab.ordinal() * (tabWidth + 6), panel.y() + 41, tabWidth, 23,
                    text("tab." + tab.name().toLowerCase(java.util.Locale.ROOT)), () -> switchPage(tab))
                    .selected(page == tab).active = !pendingWrite;
        }
        body = new Rect(panel.x() + 12, panel.y() + 88, inner, Math.max(1, panel.height() - 132));
        switch (page) {
            case GENERAL -> general();
            case SERVER -> configuration(StreakServerConfig.settings(), serverDraft);
            case CLIENT -> configuration(TerminalClientSettings.settings(), clientDraft);
            case KEYS -> keys();
        }
        scroll.layout(body, contentHeight);
        positionRows();
        back = button(panel.right() - 140, panel.bottom() - 27, 60, 20, Component.translatable("gui.back"),
                this::onClose);
        save = button(panel.right() - 74, panel.bottom() - 27, 62, 20, text("save"), this::save).primary();
        refresh = button(panel.right() - 92, panel.y() + 7, 80, 20, text("refresh"), () -> request(false));
        refresh.setTooltip(Tooltip.create(text("refresh_help")));
        refreshControls();
    }

    private TerminalButton button(int x, int y, int width, int height, Component label, Runnable action) {
        return addRenderableWidget(new TerminalButton(x, y, width, height, label, action));
    }

    private void heading(Component title) {
        rows.add(new Row(contentHeight, 23, title, Component.empty(), null, null));
        contentHeight += 23;
    }

    private void general() {
        heading(Component.translatable("screen.killstreakcod.faction_header"));
        for (String id : List.of(VoiceFaction.KORTAC, VoiceFaction.SPECGRU)) {
            var choice = button(0, 0, body.width() - 12, 31, Component.translatable("faction.killstreakcod." + id),
                    () -> {
                        faction = id;
                        status = text("unsaved");
                        warning = false;
                        rebuild();
                    }).selected(faction.equals(id)).clip(body);
            choice.help(Component.translatable("screen.killstreakcod.faction_help"));
            choice.active = !pendingWrite;
            rows.add(new Row(contentHeight, 37, Component.empty(), Component.empty(), choice, null));
            contentHeight += 37;
        }
        heading(text("appearance_team"));
        navigation("skins", "skins_help", () -> minecraft.setScreen(new OperatorSkinScreen(this)));
        navigation("teammates", "teammates_help", () -> minecraft.setScreen(new KillstreakWhitelistScreen(this)));
        navigation("layout", "layout_help", () -> minecraft.setScreen(new UiLayoutEditorScreen(this)));
    }

    private void navigation(String title, String help, Runnable action) {
        var field = button(0, 0, body.width() - 12, 38, text(title), action).detail(text(help)).clip(body);
        field.active = !pendingWrite;
        rows.add(new Row(contentHeight, 44, Component.empty(), Component.empty(), field, null));
        contentHeight += 44;
    }

    private void configuration(List<Setting> settings, Map<String, String> draft) {
        String section = "";
        for (Setting setting : settings) {
            if (!section.equals(setting.section())) {
                section = setting.section();
                heading(Component.translatable("killstreakcod.configuration." + section));
            }
            Component help = help(setting);
            AbstractWidget field;
            if (setting.kind() == Kind.BOOLEAN || setting.id().equals("rewardRailStyle")) {
                var toggle = button(0, 0, 84, 22, enabled(setting, draft), () -> {
                    double value = value(setting, draft);
                    draft.put(setting.id(),
                            setting.id().equals("rewardRailStyle") ? Integer.toString(((int) value + 1) % 3)
                                    : value == 0 ? "1" : "0");
                    status = text("unsaved");
                    warning = false;
                    refreshControls();
                }).clip(body).layoutId("setting." + setting.id());
                toggle.help(Component.empty().append(setting.title()).append("\n").append(help));
                field = toggle;
            } else {
                var input = new EditBox(font, 0, 0, 84, 18, setting.title());
                input.setMaxLength(18);
                input.setBordered(false);
                input.setTextColor(TerminalUi.TEXT);
                input.setValue(draft.getOrDefault(setting.id(), number(baseline(setting))));
                input.setTooltip(Tooltip.create(Component.empty().append(setting.title()).append("\n").append(help)));
                input.setResponder(value -> {
                    draft.put(setting.id(), value);
                    status = text("unsaved");
                    warning = false;
                    refreshControls();
                });
                field = addRenderableWidget(input);
            }
            rows.add(new Row(contentHeight, 42, setting.title(), help, field, null));
            contentHeight += 42;
        }
    }

    private Component help(Setting setting) {
        Component description = Component.translatable("killstreakcod.configuration." + setting.label() + ".tooltip");
        if (setting.kind() == Kind.BOOLEAN)
            return description;
        return Component.empty().append(description).append("\n")
                .append(text("range", number(setting.minimum()), number(setting.maximum()), number(setting.initial())));
    }

    private void keys() {
        heading(text("weapons"));
        for (KeyMapping mapping : KillstreakKeyMappings.all()) {
            var field = button(0, 0, 90, 22, mapping.getTranslatedKeyMessage(), () -> {
                editingKey = mapping;
                status = text("press_key");
                refreshControls();
            }).clip(body).layoutId("key." + mapping.getName());
            var reset = button(0, 0, 42, 22, text("reset"), () -> bind(mapping, mapping.getDefaultKey())).clip(body)
                    .layoutId("reset." + mapping.getName());
            field.help(Component.empty().append(Component.translatable(mapping.getName())).append("\n")
                    .append(text("key_help")));
            rows.add(new Row(contentHeight, 34, Component.translatable(mapping.getName()), text("key_help"), field,
                    reset));
            contentHeight += 34;
        }
        heading(text("vanilla"));
        navigation("vanilla_controls", "vanilla_help",
                () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
    }

    private void switchPage(Page next) {
        if (pendingWrite || next == page)
            return;
        editingKey = null;
        page = next;
        scroll.set(0);
        rebuild();
    }

    private void positionRows() {
        for (Row row : rows) {
            if (row.field() == null)
                continue;
            int y = body.y() + row.top() - (int) scroll.offset();
            boolean full = y >= body.y() && y + row.field().getHeight() + 3 <= body.bottom();
            row.field().visible = full;
            boolean whole = row.title().getString().isEmpty();
            int fieldWidth = page == Page.KEYS && row.reset() != null ? 90 : 84;
            row.field().setX(whole ? body.x() : body.right() - fieldWidth - (row.reset() == null ? 16 : 64));
            row.field().setY(y + 3);
            if (row.reset() != null) {
                row.reset().visible = full;
                row.reset().setX(body.right() - 58);
                row.reset().setY(y + 3);
            }
        }
    }

    private double baseline(Setting setting) {
        return (page == Page.SERVER ? serverValues : clientValues).getOrDefault(setting.id(), setting.initial());
    }

    private double value(Setting setting, Map<String, String> draft) {
        try {
            return Double.parseDouble(draft.getOrDefault(setting.id(), number(baseline(setting))));
        }
        catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }

    private Component enabled(Setting setting, Map<String, String> draft) {
        if (setting.id().equals("rewardRailStyle"))
            return Component.translatable("screen.killstreakcod.rail.mode." + (int) value(setting, draft));
        return Component.translatable(value(setting, draft) == 0 ? "options.off" : "options.on");
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private boolean dirty(Page target) {
        if (target == Page.GENERAL)
            return !faction.equals(savedFaction);
        if (target == Page.KEYS)
            return false;
        var draft = target == Page.SERVER ? serverDraft : clientDraft;
        var values = target == Page.SERVER ? serverValues : clientValues;
        for (var entry : draft.entrySet()) {
            try {
                if (!Double.valueOf(entry.getValue()).equals(values.get(entry.getKey())))
                    return true;
            }
            catch (NumberFormatException invalid) {
                return true;
            }
        }
        return false;
    }

    private void refreshControls() {
        if (save == null)
            return;
        save.setMessage(text(pendingWrite ? "saving" : "save"));
        save.active = !pendingWrite && (request == null || page == Page.CLIENT) && dirty(page)
                && (page != Page.SERVER || loaded && writable);
        refresh.active = request == null;
        back.active = !pendingWrite;
        if (page == Page.SERVER || page == Page.CLIENT) {
            List<Setting> settings = page == Page.SERVER ? StreakServerConfig.settings()
                    : TerminalClientSettings.settings();
            Map<String, String> draft = page == Page.SERVER ? serverDraft : clientDraft;
            int index = 0;
            for (Row row : rows) {
                if (row.field() == null)
                    continue;
                Setting setting = settings.get(index++);
                row.field().active = !pendingWrite && (page != Page.SERVER || loaded && writable);
                if (row.field() instanceof TerminalButton toggle) {
                    toggle.setMessage(enabled(setting, draft));
                    toggle.selected(value(setting, draft) != 0);
                } else if (row.field() instanceof EditBox input) {
                    input.setTextColor(setting.accepts(value(setting, draft)) ? TerminalUi.TEXT : TerminalUi.WARNING);
                }
            }
        } else if (page == Page.KEYS) {
            int index = 0;
            for (Row row : rows) {
                if (row.reset() == null)
                    continue;
                KeyMapping mapping = KillstreakKeyMappings.all().get(index++);
                row.field().setMessage(editingKey == mapping ? text("press_key") : mapping.getTranslatedKeyMessage());
                ((TerminalButton) row.field()).selected(editingKey == mapping);
                row.reset().active = !mapping.isDefault();
            }
        }
    }

    private void request(boolean write) {
        if (request != null || minecraft.getConnection() == null)
            return;
        Map<String, Double> changes = new LinkedHashMap<>();
        if (write && page == Page.SERVER) {
            for (Setting setting : StreakServerConfig.settings()) {
                double value = value(setting, serverDraft);
                if (!setting.accepts(value)) {
                    status = text("invalid", setting.title());
                    warning = true;
                    return;
                }
                if (value != baseline(setting))
                    changes.put(setting.id(), value);
            }
        }
        request = UUID.randomUUID();
        pendingWrite = write;
        requestTicks = 0;
        status = text(write ? "saving" : "loading");
        warning = false;
        PacketDistributor.sendToServer(
                new TerminalSettingsActionPayload(request, write && page == Page.GENERAL ? faction : "", changes));
        rebuild();
    }

    private void save() {
        if (!save.active)
            return;
        if (page != Page.CLIENT) {
            request(true);
            return;
        }
        Map<String, Double> patch = new LinkedHashMap<>();
        for (Setting setting : TerminalClientSettings.settings()) {
            double value = value(setting, clientDraft);
            if (!setting.accepts(value)) {
                status = text("invalid", setting.title());
                warning = true;
                return;
            }
            if (value != baseline(setting))
                patch.put(setting.id(), value);
        }
        warning = !TerminalClientSettings.save(patch);
        status = text(warning ? "failed" : "saved");
        if (!warning) {
            clientValues = TerminalClientSettings.snapshot();
            clientDraft.clear();
        }
        refreshControls();
    }

    public void receiveState(TerminalSettingsStatePayload state) {
        if (!state.request().equals(request))
            return;
        boolean wrote = pendingWrite;
        boolean changedFaction = !faction.equals(savedFaction);
        loaded = true;
        writable = state.writable();
        serverValues = state.values();
        savedFaction = VoiceFaction.normalize(state.faction());
        parent.settingsFaction(savedFaction);
        if (!wrote || state.message().equals("saved")) {
            if (wrote && page == Page.GENERAL || !changedFaction)
                faction = savedFaction;
            if (wrote && page == Page.SERVER)
                serverDraft.clear();
        }
        warning = !state.message().isEmpty() && !state.message().equals("saved");
        status = text(warning ? "response." + state.message() : wrote ? "saved" : writable ? "ready" : "readonly");
        request = null;
        pendingWrite = false;
        rebuild();
    }

    public void receiveTerminalState(TerminalStatePayload state) {
        parent.receiveState(state);
    }

    private void bind(KeyMapping mapping, InputConstants.Key key) {
        // 此页录入单键；组合键可在原版键位页设置，重新录入时清除旧修饰键。
        mapping.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE, key);
        minecraft.options.setKey(mapping, key);
        KeyMapping.resetMapping();
        minecraft.options.save();
        editingKey = null;
        status = text("key_saved");
        warning = false;
        refreshControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (UiLayout.Scope layoutFrame = UiLayout.screen(this)) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            TerminalUi.window(graphics, panel);
            TerminalUi.text(graphics, font, title, panel.x() + 12, panel.y() + 13, panel.width() - 118,
                    TerminalUi.TEXT);
            Component description = text("help." + page.name().toLowerCase(java.util.Locale.ROOT));
            TerminalUi.text(graphics, font, description, body.x(), panel.y() + 73, body.width() - 10, TerminalUi.MUTED);
            graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
            for (Row row : rows) {
                int y = body.y() + row.top() - (int) scroll.offset();
                if (y + row.height() < body.y() || y >= body.bottom() || row.title().getString().isEmpty())
                    continue;
                if (row.field() == null) {
                    TerminalUi.text(graphics, font, row.title(), body.x(), y + 7, body.width() - 12, TerminalUi.ACCENT);
                    continue;
                }
                int available = row.field().getX() - body.x() - 12;
                TerminalUi.text(graphics, font, row.title(), body.x(), y + 5, available, TerminalUi.TEXT);
                if (page != Page.KEYS)
                    TerminalUi.text(graphics, font, Component.literal(row.help().getString().split("\n", 2)[0]),
                            body.x(), y + 19, available, TerminalUi.MUTED);
                if (row.field() instanceof EditBox input && input.visible)
                    TerminalUi.panel(graphics, new Rect(input.getX() - 4, input.getY() - 3, input.getWidth() + 8, 24),
                            input.isFocused() ? TerminalUi.RAISED : TerminalUi.SURFACE);
            }
            graphics.disableScissor();
            positionRows();
            for (Renderable widget : renderables)
                widget.render(graphics, mouseX, mouseY, partialTick);
            scroll.render(graphics);
            Component footer = dirty(page) && !pendingWrite && !warning ? text("unsaved") : status;
            TerminalUi.text(graphics, font, footer, panel.x() + 12, panel.bottom() - 21, panel.width() - 166,
                    warning ? TerminalUi.WARNING : TerminalUi.MUTED);
            if (mouseY >= panel.bottom() - 33 && mouseX < panel.right() - 150)
                graphics.renderTooltip(font, footer, mouseX, mouseY);
            if (body.contains(mouseX, mouseY)) {
                for (Row row : rows) {
                    int y = body.y() + row.top() - (int) scroll.offset();
                    if (row.field() != null && !row.title().getString().isEmpty() && mouseY >= y
                            && mouseY < y + row.height() && mouseX < row.field().getX() - 6) {
                        graphics.renderTooltip(font,
                                Component.empty().append(row.title()).append("\n").append(row.help()), mouseX, mouseY);
                        break;
                    }
                }
            }

        } finally {
            UiLayout.flush(graphics);
        }
    }

    @Override
    public void tick() {
        if (request != null && ++requestTicks >= 200) {
            request = null;
            pendingWrite = false;
            status = text("timeout");
            warning = true;
            rebuild();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingKey != null) {
            bind(editingKey, keyCode == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN
                    : InputConstants.getKey(keyCode, scanCode));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll.set(
                    scroll.offset() + (keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(34, body.height() - 34));
            clearFocus();
            positionRows();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editingKey != null) {
            bind(editingKey, InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        if (button == 0 && scroll.click(mouseX, mouseY)) {
            clearFocus();
            positionRows();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (body.contains(mouseX, mouseY)) {
            scroll.wheel(vertical);
            clearFocus();
            positionRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scroll.drag(mouseY)) {
            positionRows();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scroll.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (pendingWrite)
            return;
        minecraft.setScreen(minecraft.getConnection() == null ? null : parent);
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new RequestTerminalStatePayload(false));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
