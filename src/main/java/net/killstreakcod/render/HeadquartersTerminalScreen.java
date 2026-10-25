package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.ui.TerminalButton;
import net.killstreakcod.render.ui.TerminalScroll;
import net.killstreakcod.render.ui.TerminalUi;
import net.killstreakcod.render.ui.TerminalUi.Rect;
import net.killstreakcod.universal.HeadquartersTerminalMenu;
import net.killstreakcod.universal.KillstreakContent;
import net.killstreakcod.universal.ResearchActionPayload;
import net.killstreakcod.universal.ResearchCatalog;
import net.killstreakcod.universal.ResearchCatalog.Category;
import net.killstreakcod.universal.ResearchCatalog.Cost;
import net.killstreakcod.universal.ResearchCatalog.Node;
import net.killstreakcod.universal.ResearchStatePayload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.killstreakcod.network.StreakPackets;
import org.lwjgl.glfw.GLFW;

/**
 * Research branches and a separately scrollable cost panel, driven by server snapshots.
 * 纵向研究分支与独立滚动的费用面板；解锁及装配状态均以服务端快照为准。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HeadquartersTerminalScreen extends AbstractContainerScreen<HeadquartersTerminalMenu> {
    private static final int ACCENT = TerminalUi.ACCENT, WHITE = TerminalUi.TEXT;
    private static final int MUTED = TerminalUi.MUTED, LOCKED = TerminalUi.WARNING;

    private record TreeEntry(Node node, TerminalButton button, int offset) {
    }

    private ResearchStatePayload state;
    private Category category = Category.RECON;
    private Node selected = ResearchCatalog.node("uav");
    private final List<TreeEntry> branches = new ArrayList<>();
    private final TerminalScroll treeScroll = new TerminalScroll(), detailScroller = new TerminalScroll();
    private final List<TerminalButton> tabs = new ArrayList<>();
    private Rect canvas, details;
    private TerminalButton modeButton, tierButton, primaryButton, callButton, unlockAll;
    private Component status = text("loading");
    private int contentHeight, detailHeight;
    private boolean pending;

    public HeadquartersTerminalScreen(HeadquartersTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        state = TacticalClient.research;
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(KillstreakContent.HEADQUARTERS_TERMINAL_MENU.get(),
                HeadquartersTerminalScreen::new));
    }

    @Override
    protected void init() {
        imageWidth = Math.min(920, width - 16);
        imageHeight = Math.min(540, height - 16);
        super.init();
        int innerWidth = imageWidth - 24;
        int rightWidth = Math.min(290, Math.max(132, imageWidth * 2 / 5));
        canvas = new Rect(leftPos + 12, topPos + 94, innerWidth - rightWidth - 8, imageHeight - 134);
        details = new Rect(canvas.right() + 8, canvas.y(), rightWidth, canvas.height());
        int actionWidth = (innerWidth - 6) / 2;
        modeButton = button(leftPos + 12, topPos + 40, actionWidth, 20, text("loading"),
                () -> send(ResearchActionPayload.MODE, state.streakMode() ? "survival" : "killstreak"));
        modeButton.help(text("mode_hint"));
        tierButton = button(leftPos + 18 + actionWidth, topPos + 40, innerWidth - actionWidth - 6, 20,
                text("terminal_title"), () -> {
                    selected = null;
                    detailScroller.set(0);
                    refreshButtons();
                });
        tabs.clear();
        int tabWidth = (innerWidth - 9) / Category.values().length;
        for (Category entry : Category.values()) {
            TerminalButton tab = button(leftPos + 12 + entry.ordinal() * (tabWidth + 3), topPos + 66, tabWidth, 20,
                    entry.title(), () -> {
                        category = entry;
                        treeScroll.set(0);
                        detailScroller.set(0);
                        selected = ResearchCatalog.definitions(entry).stream().findFirst()
                                .map(ResearchCatalog.Definition::research).orElse(null);
                        rebuildTree();
                        refreshButtons();
                    });
            tabs.add(tab);
        }
        primaryButton = button(details.x() + 6, details.bottom() - 25, details.width() - 12, 20, text("loading"),
                this::perform).primary();
        callButton = button(details.x() + 6, details.bottom() - 25, details.width() - 12, 20, text("call"), () -> {
            if (selected != null && selected.base() && owned(selected))
                send(ResearchActionPayload.CALL, selected.id());
        });
        unlockAll = button(leftPos + imageWidth - 174, topPos + imageHeight - 27, 90, 20, text("unlock_all"),
                () -> send(ResearchActionPayload.UNLOCK_ALL, ""));
        button(leftPos + imageWidth - 78, topPos + imageHeight - 27, 66, 20, Component.translatable("gui.back"),
                this::onClose);
        rebuildTree();
        refreshButtons();
        if (minecraft.getConnection() != null)
            StreakPackets.sendToServer(new ResearchActionPayload(menu.containerId, ResearchActionPayload.REQUEST, ""));
    }

    private TerminalButton button(int x, int y, int width, int height, Component label, Runnable action) {
        return addRenderableWidget(new TerminalButton(x, y, width, height, label, action));
    }

    private void rebuildTree() {
        for (TreeEntry entry : branches)
            removeWidget(entry.button());
        branches.clear();
        int y = 8;
        for (var definition : ResearchCatalog.definitions(category)) {
            addNode(definition.research(), y);
            y += 50;
            for (Node node : definition.upgrades()) {
                addNode(node, y);
                y += 38;
            }
            y += 10;
        }
        contentHeight = y;
        treeScroll.layout(canvas, contentHeight);
    }

    private void addNode(Node node, int offset) {
        TerminalButton entry = button(0, 0, 1, node.base() ? 42 : 32, node.title(), () -> {
            selected = node;
            detailScroller.set(0);
            refreshButtons();
        }).clip(canvas);
        if (node.base())
            entry.icon(node.id());
        branches.add(new TreeEntry(node, entry, offset));
    }

    public void receiveState(ResearchStatePayload payload) {
        if (payload.containerId() != menu.containerId)
            return;
        state = payload;
        pending = false;
        if (!payload.message().isEmpty())
            status = Component.translatable("research.killstreakcod.message." + payload.message());
        else if (status.equals(text("loading")) || status.equals(text("working")))
            status = text("navigation");
        refreshButtons();
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("research.killstreakcod." + key, arguments);
    }

    private boolean owned(Node node) {
        return node != null && state != null && ResearchCatalog.has(state.unlocked(), node.id());
    }

    private boolean equipped(Node node) {
        return owned(node) && !upgradeDisabled(node) && ResearchCatalog.has(state.equipped(), node.id());
    }

    private boolean upgradeDisabled(Node node) {
        return node != null && !node.base() && state != null && state.upgradesDisabled();
    }

    private boolean prerequisites(Node node) {
        return state != null && !upgradeDisabled(node) && (state.creative() || state.tier() >= node.tier()
                && (node.base() || ResearchCatalog.has(state.unlocked(), node.reward().id())));
    }

    private Cost selectedCost() {
        if (state == null)
            return null;
        if (selected == null)
            return ResearchCatalog.terminalCost(state.tier());
        if (!selected.base() && owned(selected))
            return null;
        if (selected.base() && owned(selected)) {
            var definition = ResearchCatalog.definition(selected.id());
            return !state.upgradesDisabled() && state.repairs().contains(selected.index()) ? definition.repair()
                    : definition.call();
        }
        return selected.cost();
    }

    private void refreshButtons() {
        if (modeButton == null)
            return;
        boolean ready = state != null && !pending;
        modeButton.active = ready && !state.busy() && (state.streakMode() || state.canSwitch());
        modeButton.setMessage(state == null ? text("loading")
                : text("mode_button", text(state.streakMode() ? "mode.killstreak" : "mode.survival")));
        tierButton
                .setMessage(state == null ? text("terminal_title") : text("tier_button", text("tier." + state.tier())));
        unlockAll.visible = state != null && state.creative();
        unlockAll.active = ready;
        tierButton.selected(selected == null);
        for (int i = 0; i < tabs.size(); i++)
            tabs.get(i).selected(i == category.ordinal());
        for (TreeEntry entry : branches) {
            Node node = entry.node();
            Component condition = upgradeDisabled(node) ? text("upgrades_disabled")
                    : owned(node) ? text(node.base() ? "owned" : equipped(node) ? "equipped" : "unequipped")
                            : text("requires_tier_levels", text("tier." + node.tier()), node.cost().levels());
            entry.button().selected(node == selected).detail(condition);
        }
        boolean baseOwned = selected != null && selected.base() && owned(selected);
        int buttonWidth = (details.width() - 16) / 2;
        primaryButton.setWidth(baseOwned ? buttonWidth : details.width() - 12);
        callButton.visible = baseOwned;
        callButton.setX(details.x() + 10 + buttonWidth);
        callButton.setWidth(details.width() - 16 - buttonWidth);
        boolean earned = baseOwned && state.count(selected.id()) > 0;
        callButton.setMessage(text(earned ? "call" : "need_earned"));
        callButton.active = ready && earned && (!state.streakMode() || state.creative()) && !state.busy();
        if (upgradeDisabled(selected)) {
            primaryButton.active = false;
            primaryButton.setMessage(text("upgrades_disabled"));
            return;
        }
        if (selected != null && !selected.base() && owned(selected)) {
            primaryButton.active = ready;
            primaryButton.setMessage(pending ? text("working") : text(equipped(selected) ? "unequip" : "equip"));
            return;
        }
        Cost cost = selectedCost();
        boolean affordable = cost != null && minecraft.player != null && cost.affordable(minecraft.player);
        primaryButton.active = ready && affordable
                && (selected == null
                        || baseOwned && (!state.streakMode() || state.creative())
                                && state.requisitionCount(selected.id()) < Integer.MAX_VALUE
                        || !owned(selected) && prerequisites(selected));
        primaryButton.setMessage(pending ? text("working")
                : selected == null ? text("upgrade_terminal")
                        : baseOwned ? text("prepare") : owned(selected) ? text("owned") : text("research"));
    }

    private void perform() {
        if (selected == null)
            send(ResearchActionPayload.UPGRADE, "");
        else if (selected.base() && owned(selected))
            send(ResearchActionPayload.PREPARE, selected.id());
        else if (owned(selected))
            send(equipped(selected) ? ResearchActionPayload.UNEQUIP : ResearchActionPayload.EQUIP, selected.id());
        else
            send(ResearchActionPayload.RESEARCH, selected.id());
    }

    private void send(int action, String target) {
        if (pending || state == null)
            return;
        pending = true;
        status = text("working");
        refreshButtons();
        StreakPackets.sendToServer(new ResearchActionPayload(menu.containerId, action, target));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (UiLayout.Scope layoutFrame = UiLayout.screen(this)) {
            super.render(graphics, mouseX, mouseY, partialTick);

        } finally {
            UiLayout.flush(graphics);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        refreshButtons();
        Rect panel = new Rect(leftPos, topPos, imageWidth, imageHeight);
        TerminalUi.window(graphics, panel);
        line(graphics, title, leftPos + 12, topPos + 13, imageWidth - 160, WHITE);
        var required = state != null && state.upgradesDisabled() ? ResearchCatalog.baseMask()
                : ResearchCatalog.fullMask();
        int learned = state == null ? 0 : state.unlocked().intersect(required).count();
        line(graphics, text("progress", learned, required.count()), panel.right() - 142, topPos + 13, 130, ACCENT);
        TerminalUi.progress(graphics, new Rect(panel.right() - 142, topPos + 27, 130, 2),
                learned / (double) Math.max(1, required.count()), ACCENT);
        TerminalUi.panel(graphics, canvas, TerminalUi.SURFACE);
        TerminalUi.panel(graphics, details, TerminalUi.SURFACE);
        drawTree(graphics);
        drawDetails(graphics);
        line(graphics, status, leftPos + 12, panel.bottom() - 21, imageWidth - (unlockAll.visible ? 202 : 102), MUTED);
        if (canvas.contains(mouseX, mouseY) && branches.isEmpty())
            graphics.renderTooltip(font, text("empty_category"), mouseX, mouseY);
        if (mouseY >= panel.bottom() - 32 && mouseX < panel.right() - (unlockAll.visible ? 182 : 88))
            graphics.renderTooltip(font, status, mouseX, mouseY);
    }

    private void drawTree(GuiGraphics graphics) {
        treeScroll.layout(canvas, contentHeight);
        graphics.enableScissor(canvas.x() + 1, canvas.y() + 1, canvas.right() - 1, canvas.bottom() - 1);
        if (branches.isEmpty())
            wrapped(graphics, text("empty_category"), canvas.x() + 10, canvas.y() + 12, canvas.width() - 24, MUTED);
        for (TreeEntry entry : branches) {
            boolean base = entry.node().base();
            int x = canvas.x() + (base ? 7 : 19);
            int y = canvas.y() + entry.offset() - (int) treeScroll.offset();
            var button = entry.button();
            button.setX(x);
            button.setY(y);
            button.setWidth(canvas.right() - x - 12);
            button.visible = y < canvas.bottom() - 1 && y + button.getHeight() > canvas.y() + 1;
            if (!base) {
                UiDraw.vLine(graphics, "HeadquartersTerminalScreen.drawTree.0", x - 7, y - 9, y + 16,
                        TerminalUi.BORDER);
                UiDraw.hLine(graphics, "HeadquartersTerminalScreen.drawTree.1", x - 7, x - 1, y + 16,
                        TerminalUi.BORDER);
            }
        }
        graphics.disableScissor();
        treeScroll.render(graphics);
    }

    private void drawDetails(GuiGraphics graphics) {
        int x = details.x() + 9, width = details.width() - 23;
        int top = details.y() + 6, bottom = details.y() + details.height() - 31;
        graphics.enableScissor(details.x() + 1, top, details.x() + details.width() - 1, bottom);
        int y = top - (int) detailScroller.offset();
        y = wrapped(graphics, selected == null ? text("terminal_title") : selected.title(), x, y, width, WHITE) + 8;
        if (state != null) {
            Component condition = selected == null ? text("terminal_description")
                    : text("requires_tier_levels", text("tier." + selected.tier()), selected.cost().levels());
            y = wrapped(graphics, condition, x, y, width, selected == null || prerequisites(selected) ? MUTED : LOCKED)
                    + 8;
            if (selected != null) {
                if (!selected.base() && !ResearchCatalog.has(state.unlocked(), selected.reward().id()))
                    y = wrapped(graphics, text("requires_research", selected.reward().displayName()), x, y, width,
                            LOCKED) + 7;
                y = wrapped(graphics, selected.description(), x, y, width, MUTED) + 10;
                if (upgradeDisabled(selected))
                    y = wrapped(graphics, text("upgrades_disabled_hint"), x, y, width, LOCKED) + 8;
                if (!selected.base() && owned(selected) && !upgradeDisabled(selected)) {
                    y = wrapped(graphics, text(equipped(selected) ? "equipped" : "unequipped"), x, y, width,
                            equipped(selected) ? ACCENT : MUTED) + 7;
                    y = wrapped(graphics, text("upgrade_hint"), x, y, width, MUTED) + 8;
                }
            }
            Cost cost = selectedCost();
            if (cost != null) {
                boolean calling = selected != null && selected.base() && owned(selected);
                if (calling) {
                    int requisitions = state.requisitionCount(selected.id());
                    y = wrapped(graphics, text("requisition_count", requisitions), x, y, width,
                            requisitions > 0 ? ACCENT : LOCKED) + 7;
                    int count = state.count(selected.id());
                    y = wrapped(graphics, text("earned_count", count), x, y, width, count > 0 ? ACCENT : LOCKED) + 7;
                    boolean score = selected.reward() != null && TacticalClient.terminal != null
                            && TacticalClient.terminal.scoreMode();
                    y = wrapped(graphics,
                            text(score ? "requires_score" : "requires_kills",
                                    selected.reward() == null ? 30
                                            : score ? selected.reward().score() : selected.reward().kills()),
                            x, y, width, MUTED) + 7;
                    y = wrapped(graphics, text("earning_hint"), x, y, width, MUTED) + 8;
                }
                y = wrapped(graphics,
                        calling ? text(
                                !state.upgradesDisabled() && state.repairs().contains(selected.index()) ? "repair_cost"
                                        : "call_cost")
                                : text("research_cost"),
                        x, y, width, ACCENT) + 8;
                y = wrapped(graphics, text("levels", cost.levels()), x, y, width,
                        state.creative()
                                || minecraft.player != null && minecraft.player.experienceLevel >= cost.levels() ? WHITE
                                        : LOCKED)
                        + 6;
                for (var ingredient : cost.materials()) {
                    ItemStack stack = ingredient.item().getDefaultInstance();
                    graphics.renderItem(stack, x, y);
                    int have = minecraft.player == null ? 0
                            : ResearchCatalog.quantity(minecraft.player, ingredient.item());
                    line(graphics, stack.getHoverName(), x + 20, y + 3, width - 66, WHITE);
                    String amount = have + "/" + ingredient.count();
                    line(graphics, Component.literal(amount), x + width - 42, y + 3, 42,
                            have >= ingredient.count() || state.creative() ? ACCENT : LOCKED);
                    y += 20;
                }
            }
            if (state.creative())
                y = wrapped(graphics, text("creative_hint"), x, y + 7, width, ACCENT);
            if (selected != null && selected.base() && owned(selected) && state.streakMode() && !state.creative())
                y = wrapped(graphics, text("call_mode_hint"), x, y + 7, width, LOCKED);
            if (selected == null && state.tier() == 4)
                y = wrapped(graphics, text("terminal_maximum"), x, y + 7, width, ACCENT);
        }
        detailHeight = y + (int) detailScroller.offset() - top + 10;
        graphics.disableScissor();
        detailScroller.layout(new Rect(details.x(), top, details.width(), bottom - top), detailHeight);
        detailScroller.render(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int focused = -1;
        for (int i = 0; i < branches.size(); i++)
            if (getFocused() == branches.get(i).button()) {
                focused = i;
                break;
            }
        if (focused >= 0 && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END)) {
            int index = keyCode == GLFW.GLFW_KEY_HOME ? 0
                    : keyCode == GLFW.GLFW_KEY_END ? branches.size() - 1
                            : Math.max(0,
                                    Math.min(branches.size() - 1, focused + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1)));
            TreeEntry entry = branches.get(index);
            if (entry.offset() < treeScroll.offset())
                treeScroll.set(entry.offset() - 4);
            else if (entry.offset() + entry.button().getHeight() > treeScroll.offset() + canvas.height() - 4)
                treeScroll.set(entry.offset() + entry.button().getHeight() - canvas.height() + 4);
            entry.button().visible = true;
            setFocused(entry.button());
            selected = entry.node();
            detailScroller.set(0);
            refreshButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && (treeScroll.click(mouseX, mouseY) || detailScroller.click(mouseX, mouseY)))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && (treeScroll.drag(mouseY) || detailScroller.drag(mouseY)))
            return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        treeScroll.release();
        detailScroller.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (details.contains(mouseX, mouseY)) {
            detailScroller.wheel(vertical);
            return true;
        }
        if (canvas.contains(mouseX, mouseY)) {
            treeScroll.wheel(vertical);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    private void line(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        TerminalUi.text(graphics, font, text, x, y, maxWidth, color);
    }

    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            UiDraw.drawString(graphics, "HeadquartersTerminalScreen.wrapped.0", font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }
}
