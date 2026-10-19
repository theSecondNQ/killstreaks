package net.killstreakcod.render;

import net.killstreakcod.audio.VoiceFaction;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.universal.SaveKillstreakLoadoutPayload;
import net.killstreakcod.universal.ResearchCatalog;
import net.killstreakcod.universal.TerminalStatePayload;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.killstreakcod.network.StreakPackets;

public final class KillstreakTerminalScreen extends Screen {
    private static final int PANEL = 0xF00A1012;
    private static final int PANEL_INNER = 0xFF10191B;
    private static final int EDGE = 0xFF40514A;
    private static final int GREEN = 0xFF9BBB62;
    private static final int MUTED = 0xFF9BA9A0;
    private static final int WHITE = 0xFFE7ECE5;
    private static final int AMBER = 0xFFE0B35D;

    private final String[] selectedIds = new String[3];
    private int kills;
    private int score;
    private int readyMask;
    private int selectedSlot;
    private boolean scoreMode;
    private boolean editable;
    private boolean juggernautEnabled;
    private int[] counts = new int[3], progress = new int[3];
    private String[] serverIds = new String[3];
    private boolean serverScoreMode;
    private boolean saving;
    private boolean dirty;
    private String faction = VoiceFaction.SPECGRU;
    private Component status = Component.translatable("screen.killstreakcod.ready");
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int slotX;
    private int slotY;
    private int slotWidth;
    private int slotHeight;
    private int slotStep;
    private int rewardsX;
    private int rewardsY;
    private int rewardWidth;
    private int rewardHeight;
    private int rewardStep;
    private int modeY;
    private int footerY;
    private float uiScale = 1;
    private int uiX, uiY;

    public KillstreakTerminalScreen(TerminalStatePayload initialState) {
        super(Component.translatable("screen.killstreakcod.title"));
        applyState(initialState, false);
    }

    public void receiveState(TerminalStatePayload state) {
        boolean wasSaving = saving;
        boolean matches = Arrays.equals(selectedIds, new String[] { state.slot1(), state.slot2(), state.slot3() })
                && faction.equals(state.faction());
        if (wasSaving) {
            status = Component.translatable(matches ? "screen.killstreakcod.saved" : "screen.killstreakcod.rejected");
            saving = false;
            dirty = false;
            applyState(state, false);
        } else
            applyState(state, true);
    }

    private void applyState(TerminalStatePayload state, boolean preserveSelection) {
        if (!preserveSelection || !saving && !dirty) {
            selectedIds[0] = state.slot1();
            selectedIds[1] = state.slot2();
            selectedIds[2] = state.slot3();
            faction = VoiceFaction.normalize(state.faction());
        }
        scoreMode = state.scoreMode();
        kills = state.kills();
        score = state.score();
        readyMask = state.readyMask();
        editable = state.editable();
        juggernautEnabled = state.juggernautEnabled();
        counts = state.counts();
        progress = state.progress();
        serverIds = new String[] { state.slot1(), state.slot2(), state.slot3() };
        serverScoreMode = state.scoreMode();
    }

    @Override
    protected void init() {
        panelWidth = 820;
        panelHeight = 450;
        uiScale = Math.min((width - 12F) / panelWidth, (height - 12F) / panelHeight);
        uiX = (int) ((width - panelWidth * uiScale) / 2);
        uiY = (int) ((height - panelHeight * uiScale) / 2);
        panelX = 0;
        panelY = 0;
        slotX = panelX + 16;
        slotY = panelY + 112;
        slotWidth = Math.min(210, panelWidth / 3);
        slotHeight = 48;
        slotStep = 56;
        rewardsX = panelX + 246;
        rewardsY = panelY + 106;
        int rewardsAreaWidth = Math.max(90, panelWidth - (rewardsX - panelX) - 16);
        rewardWidth = (rewardsAreaWidth - 12) / 3;
        int availableRewardHeight = Math.max(154, panelHeight - 157);
        rewardHeight = Math.max(20, Math.min(38, (availableRewardHeight - 24) / 7));
        rewardStep = rewardHeight + 4;
        modeY = slotY + slotStep * 3 - 3;
        footerY = panelY + panelHeight - 33;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(uiX, uiY, 0);
        graphics.pose().scale(uiScale, uiScale, 1);
        mouseX = (int) ((mouseX - uiX) / uiScale);
        mouseY = (int) ((mouseY - uiY) / uiScale);
        graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, EDGE);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.fill(panelX + 8, panelY + 8, panelX + panelWidth - 8, panelY + panelHeight - 8, PANEL_INNER);
        graphics.fill(panelX + 14, panelY + 38, panelX + panelWidth - 14, panelY + 39, EDGE);

        graphics.drawString(font, Component.translatable("screen.killstreakcod.title"), panelX + 18, panelY + 17, GREEN,
                false);
        drawActionButton(graphics, panelWidth - 170, 12, 154, 23,
                Component.translatable("screen.killstreakcod.whitelist_open"), mouseX, mouseY, saving ? MUTED : GREEN);
        drawActionButton(graphics, panelWidth - 330, 12, 148, 23,
                Component.translatable("screen.killstreakcod.skins_title"), mouseX, mouseY, saving ? MUTED : GREEN);
        graphics.drawString(font, Component.translatable("screen.killstreakcod.progress", kills, score), panelX + 18,
                panelY + 49, WHITE, false);
        graphics.drawWordWrap(font, Component.translatable("screen.killstreakcod.instructions"), panelX + 18,
                panelY + 67, panelWidth - 36, MUTED);
        graphics.drawString(font, Component.translatable("screen.killstreakcod.loadout_header"), slotX, panelY + 91,
                GREEN, false);
        graphics.drawString(font, Component.translatable("screen.killstreakcod.available_header"), rewardsX,
                panelY + 91, GREEN, false);

        for (int slot = 0; slot < 3; slot++)
            drawSlot(graphics, slot, mouseX, mouseY);
        drawRewardGrid(graphics, mouseX, mouseY);
        graphics.drawString(font,
                Component.translatable("screen.killstreakcod.world_mode",
                        Component.translatable(scoreMode ? "mode.killstreakcod.score" : "mode.killstreakcod.kills")),
                slotX, modeY + 6, GREEN, false);
        if (modeY + 38 <= footerY)
            graphics.drawWordWrap(font,
                    Component.translatable(survival() ? "research.killstreakcod.survival_call_help"
                            : "screen.killstreakcod.world_mode_help"),
                    slotX, modeY + 29, slotWidth, mayEdit() ? MUTED : AMBER);
        graphics.drawString(font, Component.translatable("screen.killstreakcod.faction_header"), slotX, modeY + 65,
                GREEN, false);
        String[] factions = { VoiceFaction.KORTAC, VoiceFaction.SPECGRU };
        for (int i = 0; i < 2; i++)
            drawActionButton(graphics, slotX + i * 108, modeY + 80, 102, 22,
                    Component.translatable("faction.killstreakcod." + factions[i]), mouseX, mouseY,
                    faction.equals(factions[i]) ? GREEN : MUTED);
        graphics.drawWordWrap(font, Component.translatable("screen.killstreakcod.faction_help"), slotX, modeY + 110,
                slotWidth, MUTED);
        drawActionButton(graphics, panelX + panelWidth - 150, footerY, 76, 22, Component.translatable("gui.cancel"),
                mouseX, mouseY, MUTED);
        drawActionButton(graphics, panelX + panelWidth - 66, footerY, 50, 22,
                Component.translatable("screen.killstreakcod.save"), mouseX, mouseY, saving ? MUTED : GREEN);
        graphics.drawString(font, status, panelX + 16, footerY + 7, saving ? AMBER : GREEN, false);
        graphics.drawString(font, Component.translatable("screen.killstreakcod.call_keys"), panelX + 18,
                panelY + panelHeight - 18, MUTED, false);
        graphics.pose().popPose();
    }

    private void drawSlot(GuiGraphics graphics, int slot, int mouseX, int mouseY) {
        int y = slotY + slot * slotStep;
        boolean hovered = inside(mouseX, mouseY, slotX, y, slotWidth, slotHeight);
        boolean active = selectedSlot == slot;
        int border = active ? GREEN : hovered ? AMBER : EDGE;
        graphics.fill(slotX, y, slotX + slotWidth, y + slotHeight, border);
        graphics.fill(slotX + 1, y + 1, slotX + slotWidth - 1, y + slotHeight - 1, hovered ? 0xFF1D2926 : 0xFF121A1B);
        StreakReward reward = StreakReward.byId(selectedIds[slot]).orElse(StreakReward.UAV);
        TacticalClient.icon(graphics, reward.id(), slotX + slotWidth - 48, y + 9, 32);
        int stock = 0, advance = 0;
        for (int i = 0; i < 3; i++)
            if (reward.id().equals(serverIds[i])) {
                stock = counts[i];
                advance = serverScoreMode == scoreMode ? progress[i] : 0;
            }
        boolean ready = stock > 0;
        graphics.drawString(font, Component.translatable("screen.killstreakcod.slot_stock", slot + 1, stock), slotX + 8,
                y + 6, active ? GREEN : MUTED, false);
        graphics.drawString(font, trim(reward.displayName().getString(), slotWidth - 54), slotX + 8, y + 19, WHITE,
                false);
        Component cycle = reward == StreakReward.JUGGERNAUT && !juggernautEnabled
                ? Component.translatable("screen.killstreakcod.reward_disabled")
                : survival() && !ResearchCatalog.has(TacticalClient.research.unlocked(), reward.id())
                        ? Component.translatable("research.killstreakcod.not_researched")
                        : Component.translatable("screen.killstreakcod.slot_cycle", advance,
                                scoreMode ? reward.score() : reward.kills());
        graphics.drawString(font, trim(cycle.getString(), slotWidth - 54), slotX + 8, y + 33, ready ? GREEN : MUTED,
                false);
        if (ready)
            graphics.fill(slotX + slotWidth - 8, y + 5, slotX + slotWidth - 4, y + 9, GREEN);
    }

    private void drawRewardGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < StreakReward.SELECTABLE.size(); index++) {
            StreakReward reward = StreakReward.SELECTABLE.get(index);
            int column = index % 3;
            int row = index / 3;
            int x = rewardsX + column * (rewardWidth + 6);
            int y = rewardsY + row * rewardStep;
            boolean hovered = inside(mouseX, mouseY, x, y, rewardWidth, rewardHeight);
            boolean equipped = Arrays.asList(selectedIds).contains(reward.id());
            boolean assignedHere = reward.id().equals(selectedIds[selectedSlot]);
            boolean disabled = reward == StreakReward.JUGGERNAUT && !juggernautEnabled;
            int border = assignedHere ? GREEN : equipped ? 0xFF728557 : hovered ? AMBER : EDGE;
            graphics.fill(x, y, x + rewardWidth, y + rewardHeight, border);
            graphics.fill(x + 1, y + 1, x + rewardWidth - 1, y + rewardHeight - 1,
                    hovered ? 0xFF25302A : equipped ? 0xFF19221F : 0xFF111819);
            TacticalClient.icon(graphics, reward.id(), x + 4, y + 4, Math.min(28, rewardHeight - 8));
            int textWidth = Math.max(36, rewardWidth - 42);
            graphics.drawString(font, trim(reward.displayName().getString(), textWidth), x + 37, y + 4,
                    disabled ? MUTED : WHITE, false);
            Component threshold = disabled ? Component.translatable("screen.killstreakcod.reward_disabled")
                    : survival() && !ResearchCatalog.has(TacticalClient.research.unlocked(), reward.id())
                            ? Component.translatable("research.killstreakcod.not_researched")
                            : Component.translatable("screen.killstreakcod.threshold", reward.kills(), reward.score());
            graphics.drawString(font, trim(threshold.getString(), textWidth), x + 37, y + rewardHeight - 11, MUTED,
                    false);
        }
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int width, int height, Component label,
            int mouseX, int mouseY, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hovered ? accent : EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered ? 0xFF26302B : 0xFF12191A);
        float scale = Math.min(1, (width - 8F) / Math.max(1, font.width(label)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2F, y + (height - font.lineHeight * scale) / 2F, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, label, 0, 0, hovered ? WHITE : accent);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) ((mouseX - uiX) / uiScale);
        int my = (int) ((mouseY - uiY) / uiScale);
        if (inside(mx, my, panelWidth - 330, 12, 148, 23)) {
            if (!saving)
                Minecraft.getInstance().setScreen(new OperatorSkinScreen(this));
            return true;
        }
        if (inside(mx, my, panelWidth - 170, 12, 154, 23)) {
            if (!saving)
                Minecraft.getInstance().setScreen(new KillstreakWhitelistScreen(this));
            return true;
        }
        for (int slot = 0; slot < 3; slot++) {
            int y = slotY + slot * slotStep;
            if (inside(mx, my, slotX, y, slotWidth, slotHeight)) {
                selectedSlot = slot;
                return true;
            }
        }
        if (inside(mx, my, panelX + 16, modeY, slotWidth, 23)) {
            status = Component.translatable("screen.killstreakcod.world_mode_help");
            return true;
        }
        for (int i = 0; i < 2; i++)
            if (inside(mx, my, slotX + i * 108, modeY + 80, 102, 22)) {
                if (!mayEdit()) {
                    status = Component.translatable("screen.killstreakcod.locked_message");
                    return true;
                }
                faction = i == 0 ? VoiceFaction.KORTAC : VoiceFaction.SPECGRU;
                dirty = true;
                status = Component.translatable("screen.killstreakcod.unsaved");
                return true;
            }
        for (int index = 0; index < StreakReward.SELECTABLE.size(); index++) {
            int x = rewardsX + (index % 3) * (rewardWidth + 6);
            int y = rewardsY + (index / 3) * rewardStep;
            if (inside(mx, my, x, y, rewardWidth, rewardHeight)) {
                selectReward(StreakReward.SELECTABLE.get(index));
                return true;
            }
        }
        if (inside(mx, my, panelX + panelWidth - 150, footerY, 76, 22)) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (inside(mx, my, panelX + panelWidth - 66, footerY, 50, 22)) {
            if (!saving)
                saveSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectReward(StreakReward reward) {
        if (reward == StreakReward.JUGGERNAUT && !juggernautEnabled) {
            status = Component.translatable("message.killstreakcod.reward_disabled", reward.displayName());
            return;
        }
        if (!mayEdit()) {
            status = Component.translatable("screen.killstreakcod.locked_message");
            return;
        }
        for (int slot = 0; slot < 3; slot++) {
            if (slot != selectedSlot
                    && StreakReward.byId(selectedIds[slot]).orElse(StreakReward.UAV).kills() == reward.kills()) {
                String oldSelection = selectedIds[selectedSlot];
                selectedIds[selectedSlot] = reward.id();
                selectedIds[slot] = oldSelection;
                dirty = true;
                status = Component.translatable("screen.killstreakcod.swapped");
                return;
            }
        }
        selectedIds[selectedSlot] = reward.id();
        dirty = true;
        status = Component.translatable("screen.killstreakcod.unsaved");
    }

    private void saveSelection() {
        if (!mayEdit()) {
            status = Component.translatable("screen.killstreakcod.locked_message");
            return;
        }
        if (selectedIds[0].equals(selectedIds[1]) || selectedIds[0].equals(selectedIds[2])
                || selectedIds[1].equals(selectedIds[2])) {
            status = Component.translatable("screen.killstreakcod.duplicate");
            return;
        }
        saving = true;
        status = Component.translatable("screen.killstreakcod.saving");
        StreakPackets.sendToServer(
                new SaveKillstreakLoadoutPayload(selectedIds[0], selectedIds[1], selectedIds[2], faction));
    }

    private static boolean survival() {
        return TacticalClient.research != null && !TacticalClient.research.streakMode();
    }

    private boolean mayEdit() {
        return editable && !saving;
    }

    private String trim(String value, int maxWidth) {
        return font.plainSubstrByWidth(value, maxWidth);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
