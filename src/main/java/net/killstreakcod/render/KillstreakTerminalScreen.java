package net.killstreakcod.render;

import net.killstreakcod.audio.VoiceFaction;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.render.ui.TerminalButton;
import net.killstreakcod.render.ui.TerminalScroll;
import net.killstreakcod.render.ui.TerminalUi;
import net.killstreakcod.render.ui.TerminalUi.Rect;
import net.killstreakcod.universal.ResearchCatalog;
import net.killstreakcod.universal.SaveKillstreakLoadoutPayload;
import net.killstreakcod.universal.TerminalStatePayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 连杀配置使用真实 GUI 坐标；服务端快照与尚未保存的本地选择分别维护。 */
public final class KillstreakTerminalScreen extends Screen {
    private final String[] selectedIds = new String[3];
    private final TerminalButton[] slots = new TerminalButton[3];
    private final List<TerminalButton> rewardButtons = new ArrayList<>();
    private final List<StreakReward> filtered = new ArrayList<>();
    private final TerminalScroll scroll = new TerminalScroll();
    private String[] serverIds = new String[3];
    private int[] counts = new int[3], progress = new int[3];
    private int kills, score, selectedSlot, saveTicks, columns, visibleRows, cardStep;
    private boolean scoreMode, editable, juggernautEnabled, saving, dirty, warning;
    private String faction = VoiceFaction.SPECGRU;
    private String query = "";
    private Rect panel, library;
    private EditBox search;
    private TerminalButton save, settings;
    private Component status = text("ready");

    public KillstreakTerminalScreen(TerminalStatePayload initialState) {
        super(text("title"));
        applyState(initialState, false);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("screen.killstreakcod." + key, arguments);
    }

    @Override
    protected void init() {
        if (search != null)
            query = search.getValue();
        panel = TerminalUi.window(width, height, 880, 520);
        int x = panel.x() + 12, innerWidth = panel.width() - 24;
        settings = button(panel.right() - 100, panel.y() + 7, 88, 20, text("settings.open"),
                () -> minecraft.setScreen(new TerminalSettingsScreen(this)));
        int slotWidth = (innerWidth - 12) / 3;
        boolean compact = panel.height() < 300;
        int slotHeight = compact ? 36 : 46;
        int slotY = panel.y() + (compact ? 50 : 54);
        for (int i = 0; i < slots.length; i++) {
            int slot = i;
            slots[i] = button(x + i * (slotWidth + 6), slotY, slotWidth, slotHeight, Component.empty(), () -> {
                selectedSlot = slot;
                refreshControls();
            }).layoutId("slot." + i);
        }
        int headingY = slotY + slotHeight + (compact ? 6 : 8);
        int searchWidth = Math.min(160, innerWidth / 2);
        search = addRenderableWidget(
                new EditBox(font, panel.right() - 14 - searchWidth, headingY - 2, searchWidth, 16, text("ui.search")));
        search.setBordered(false);
        search.setTextColor(TerminalUi.TEXT);
        search.setHint(text("ui.search"));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            scroll.set(0);
            filterRewards();
        });
        int libraryY = headingY + 19;
        library = new Rect(x, libraryY, innerWidth, Math.max(1, panel.bottom() - 36 - libraryY));
        columns = innerWidth >= 650 ? 3 : innerWidth >= 400 ? 2 : 1;
        cardStep = compact ? 36 : 40;
        visibleRows = Math.max(1, library.height() / cardStep);
        rewardButtons.clear();
        for (StreakReward reward : StreakReward.SELECTABLE) {
            TerminalButton entry = button(0, 0, 1, cardStep - 4, reward.displayName(), () -> selectReward(reward));
            entry.icon(reward.id());
            rewardButtons.add(entry);
        }
        button(panel.right() - 142, panel.bottom() - 27, 62, 20, Component.translatable("gui.cancel"), this::onClose);
        save = button(panel.right() - 74, panel.bottom() - 27, 62, 20, text("save"), this::saveSelection).primary();
        filterRewards();
        refreshControls();
    }

    private TerminalButton button(int x, int y, int width, int height, Component label, Runnable action) {
        return addRenderableWidget(new TerminalButton(x, y, width, height, label, action));
    }

    public void receiveState(TerminalStatePayload state) {
        if (saving) {
            boolean matches = Arrays.equals(selectedIds, new String[] { state.slot1(), state.slot2(), state.slot3() })
                    && faction.equals(state.faction());
            status = text(matches ? "saved" : "rejected");
            warning = !matches;
            saving = false;
            dirty = false;
            applyState(state, false);
        } else {
            applyState(state, true);
        }
        refreshControls();
    }

    String voiceFaction() {
        return faction;
    }

    void settingsFaction(String value) {
        faction = VoiceFaction.normalize(value);
    }

    private void applyState(TerminalStatePayload state, boolean preserveSelection) {
        if (!preserveSelection || !dirty) {
            selectedIds[0] = state.slot1();
            selectedIds[1] = state.slot2();
            selectedIds[2] = state.slot3();
            faction = VoiceFaction.normalize(state.faction());
        }
        scoreMode = state.scoreMode();
        kills = state.kills();
        score = state.score();
        editable = state.editable();
        juggernautEnabled = state.juggernautEnabled();
        counts = state.counts();
        progress = state.progress();
        serverIds = new String[] { state.slot1(), state.slot2(), state.slot3() };
    }

    private void filterRewards() {
        filtered.clear();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        for (StreakReward reward : StreakReward.SELECTABLE)
            if (needle.isEmpty() || reward.displayName().getString().toLowerCase(Locale.ROOT).contains(needle)
                    || reward.id().contains(needle))
                filtered.add(reward);
        positionRewards();
        refreshControls();
    }

    private void positionRewards() {
        if (library == null || rewardButtons.isEmpty())
            return;
        int rows = (filtered.size() + columns - 1) / columns;
        int viewportHeight = visibleRows * cardStep;
        scroll.layout(new Rect(library.x(), library.y(), library.width(), viewportHeight), rows * cardStep);
        int firstRow = (int) Math.round(scroll.offset() / cardStep);
        int cardWidth = (library.width() - 12 - (columns - 1) * 6) / columns;
        for (int i = 0; i < StreakReward.SELECTABLE.size(); i++) {
            var button = rewardButtons.get(i);
            int index = filtered.indexOf(StreakReward.SELECTABLE.get(i));
            int row = index < 0 ? -1 : index / columns - firstRow;
            button.visible = index >= 0 && row >= 0 && row < visibleRows;
            if (button.visible) {
                button.setX(library.x() + index % columns * (cardWidth + 6));
                button.setY(library.y() + row * cardStep);
                button.setWidth(cardWidth);
            }
        }
    }

    private void refreshControls() {
        if (save == null)
            return;
        save.active = mayEdit() && dirty;
        save.setMessage(text(saving ? "saving" : "save"));
        settings.active = !saving;
        for (int i = 0; i < slots.length; i++) {
            StreakReward reward = StreakReward.byId(selectedIds[i]).orElse(StreakReward.UAV);
            int stock = 0, advance = 0;
            for (int j = 0; j < serverIds.length; j++)
                if (reward.id().equals(serverIds[j])) {
                    stock = counts[j];
                    advance = progress[j];
                    break;
                }
            slots[i].setMessage(Component.literal((i + 1) + "  ").append(reward.displayName()));
            slots[i].selected(i == selectedSlot);
            slots[i].detail(Component.literal((stock > 0 ? "×" + stock + "  ·  " : "") + advance + " / "
                    + (scoreMode ? reward.score() : reward.kills())));
            slots[i].icon(slots[i].getWidth() >= 175 ? reward.id() : "");
            slots[i].setTooltip(Tooltip.create(Component.empty().append(reward.displayName()).append("\n")
                    .append(text("slot_stock", i + 1, stock)).append("\n").append(text("call_keys"))));
            slots[i].active = !saving;
        }
        for (int i = 0; i < rewardButtons.size(); i++) {
            var reward = StreakReward.SELECTABLE.get(i);
            var button = rewardButtons.get(i);
            boolean disabled = reward == StreakReward.JUGGERNAUT && !juggernautEnabled;
            Component detail = disabled ? text("reward_disabled")
                    : survival() && !ResearchCatalog.has(TacticalClient.research.unlocked(), reward.id())
                            ? Component.translatable("research.killstreakcod.not_researched")
                            : text("threshold", reward.kills(), reward.score());
            button.detail(detail);
            button.selected(reward.id().equals(selectedIds[selectedSlot]));
            button.active = mayEdit() && !disabled;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (UiLayout.Scope layoutFrame = UiLayout.screen(this)) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            TerminalUi.window(graphics, panel);
            int x = panel.x() + 12;
            TerminalUi.text(graphics, font, title, x, panel.y() + 13, settings.getX() - x - 10, TerminalUi.TEXT);
            TerminalUi.text(graphics, font, text("ui.progress", kills, score), x, panel.y() + 39,
                    panel.width() / 2 - 24, TerminalUi.MUTED);
            Component mode = text("ui.mode",
                    Component.translatable(scoreMode ? "mode.killstreakcod.score" : "mode.killstreakcod.kills"));
            int modeX = panel.x() + panel.width() / 2;
            TerminalUi.text(graphics, font, mode, modeX, panel.y() + 39, panel.right() - modeX - 12, TerminalUi.ACCENT);
            int headingY = search.getY() + 2;
            TerminalUi.text(graphics, font, text("available_header"), x, headingY, search.getX() - x - 12,
                    TerminalUi.MUTED);
            TerminalUi.panel(graphics, new Rect(search.getX() - 4, search.getY() - 3, search.getWidth() + 6, 20),
                    TerminalUi.SURFACE);
            if (filtered.isEmpty())
                TerminalUi.wrapped(graphics, font, text("ui.no_results"), library.x() + 8, library.y() + 12,
                        library.width() - 24, TerminalUi.MUTED);
            Component message = !editable && !dirty && !saving ? text("locked_message") : status;
            TerminalUi.text(graphics, font, message, x, panel.bottom() - 21, panel.width() - 166,
                    warning || !editable ? TerminalUi.WARNING : TerminalUi.MUTED);
            positionRewards();
            for (Renderable widget : renderables)
                widget.render(graphics, mouseX, mouseY, partialTick);
            scroll.render(graphics);
            if (mouseY >= panel.bottom() - 32 && mouseX < panel.right() - 150)
                graphics.renderTooltip(font, message, mouseX, mouseY);
            if (mouseY >= panel.y() + 35 && mouseY < panel.y() + 51)
                graphics.renderTooltip(font,
                        Component.empty().append(mode).append("\n").append(text("world_mode_help")), mouseX, mouseY);

        } finally {
            UiLayout.flush(graphics);
        }
    }

    @Override
    public void tick() {
        if (saving && ++saveTicks >= 200) {
            saving = false;
            status = text("ui.save_timeout");
            warning = true;
            refreshControls();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            setFocused(search);
            return true;
        }
        for (int i = 0; i < rewardButtons.size(); i++) {
            if (getFocused() != rewardButtons.get(i))
                continue;
            int step = switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> -columns;
                case GLFW.GLFW_KEY_DOWN -> columns;
                case GLFW.GLFW_KEY_LEFT -> -1;
                case GLFW.GLFW_KEY_RIGHT -> 1;
                default -> 0;
            };
            int index = filtered.indexOf(StreakReward.SELECTABLE.get(i));
            if (step == 0 || index < 0)
                break;
            int next = Math.max(0, Math.min(filtered.size() - 1, index + step));
            int row = next / columns;
            int topRow = (int) Math.round(scroll.offset() / cardStep);
            if (row < topRow)
                scroll.set(row * cardStep);
            else if (row >= topRow + visibleRows)
                scroll.set((row - visibleRows + 1) * cardStep);
            positionRewards();
            setFocused(rewardButtons.get(StreakReward.SELECTABLE.indexOf(filtered.get(next))));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && library.contains(mouseX, mouseY) && scroll.click(mouseX, mouseY)) {
            positionRewards();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scroll.drag(mouseY)) {
            positionRewards();
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (library.contains(mouseX, mouseY)) {
            scroll.set(scroll.offset() - vertical * cardStep);
            positionRewards();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void selectReward(StreakReward reward) {
        if (!mayEdit())
            return;
        for (int slot = 0; slot < selectedIds.length; slot++) {
            if (slot != selectedSlot
                    && StreakReward.byId(selectedIds[slot]).orElse(StreakReward.UAV).kills() == reward.kills()) {
                String previous = selectedIds[selectedSlot];
                selectedIds[selectedSlot] = reward.id();
                selectedIds[slot] = previous;
                changed("swapped");
                return;
            }
        }
        selectedIds[selectedSlot] = reward.id();
        changed("unsaved");
    }

    private void changed(String message) {
        dirty = true;
        warning = false;
        status = text(message);
        refreshControls();
    }

    private void saveSelection() {
        if (!mayEdit() || !dirty)
            return;
        if (Arrays.stream(selectedIds).distinct().count() != 3) {
            status = text("duplicate");
            warning = true;
            return;
        }
        saving = true;
        saveTicks = 0;
        status = text("saving");
        PacketDistributor.sendToServer(
                new SaveKillstreakLoadoutPayload(selectedIds[0], selectedIds[1], selectedIds[2], faction));
        refreshControls();
    }

    private static boolean survival() {
        return TacticalClient.research != null && !TacticalClient.research.streakMode();
    }

    private boolean mayEdit() {
        return editable && !saving;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
