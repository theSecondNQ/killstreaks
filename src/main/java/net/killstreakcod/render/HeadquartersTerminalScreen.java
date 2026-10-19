package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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

/**
 * Pannable research branches and a separately scrollable cost panel, driven by server snapshots.
 * 可平移的研究分支与独立滚动的费用面板；解锁及装配状态均以服务端快照为准。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HeadquartersTerminalScreen extends AbstractContainerScreen<HeadquartersTerminalMenu> {
    private static final int PANEL = 0xff0a141b, HEADER = 0xff11212b, CANVAS = 0xff091117;
    private static final int EDGE = 0xff2b4655, GRID = 0xff11232e, ACCENT = 0xff62d4d1;
    private static final int WHITE = 0xffe2eeee, MUTED = 0xff91a9b6, LOCKED = 0xffdf997b;

    private record Box(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }

    private record Visual(Node node, Box box) {
    }

    private ResearchStatePayload state;
    private Category category = Category.RECON;
    private Node selected = ResearchCatalog.node("uav");
    private final List<Visual> visuals = new ArrayList<>();
    private final List<Button> tabs = new ArrayList<>();
    private Box canvas, details;
    private Button modeButton, tierButton, primaryButton, callButton, unlockAll;
    private Component status = text("loading");
    private double scrollX, scrollY, detailScroll;
    private int contentWidth, contentHeight, detailHeight;
    private boolean dragging, pending;

    public HeadquartersTerminalScreen(HeadquartersTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        state = TacticalClient.research;
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(KillstreakContent.HEADQUARTERS_TERMINAL_MENU.get(), HeadquartersTerminalScreen::new));
    }

    @Override
    protected void init() {
        imageWidth = Math.min(900, width - 16);
        imageHeight = Math.min(490, height - 16);
        super.init();
        int rightWidth = net.minecraft.util.Mth.clamp(imageWidth / 3, 132, 242);
        canvas = new Box(leftPos + 10, topPos + 80, imageWidth - rightWidth - 30, imageHeight - 116);
        details = new Box(canvas.x + canvas.width + 10, canvas.y, rightWidth, canvas.height);
        modeButton = addRenderableWidget(Button
                .builder(text("loading"),
                        button -> send(ResearchActionPayload.MODE, state.streakMode() ? "survival" : "killstreak"))
                .bounds(leftPos + imageWidth - 184, topPos + 8, 174, 18).build());
        modeButton.setTooltip(Tooltip.create(text("mode_hint")));
        tierButton = addRenderableWidget(Button.builder(text("terminal_title"), button -> {
            selected = null;
            detailScroll = 0;
        }).bounds(leftPos + imageWidth - 184, topPos + 31, 174, 18).build());
        tabs.clear();
        int tabWidth = (imageWidth - 20) / Category.values().length;
        for (Category entry : Category.values()) {
            Button tab = addRenderableWidget(Button.builder(entry.title(), button -> {
                category = entry;
                scrollX = scrollY = detailScroll = 0;
                selected = ResearchCatalog.definitions(entry).stream().findFirst()
                        .map(ResearchCatalog.Definition::research).orElse(null);
            }).bounds(leftPos + 10 + entry.ordinal() * tabWidth, topPos + 56, tabWidth - 3, 18).build());
            tabs.add(tab);
        }
        primaryButton = addRenderableWidget(Button.builder(text("loading"), button -> perform())
                .bounds(details.x + 6, details.y + details.height - 25, details.width - 12, 19).build());
        callButton = addRenderableWidget(Button.builder(text("call"), button -> {
            if (selected != null && selected.base() && owned(selected))
                send(ResearchActionPayload.CALL, selected.id());
        }).bounds(details.x + 6, details.y + details.height - 25, details.width - 12, 19).build());
        unlockAll = addRenderableWidget(
                Button.builder(text("unlock_all"), button -> send(ResearchActionPayload.UNLOCK_ALL, ""))
                        .bounds(leftPos + imageWidth - 172, topPos + imageHeight - 26, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(leftPos + imageWidth - 74, topPos + imageHeight - 26, 64, 18).build());
        refreshButtons();
        StreakPackets.sendToServer(new ResearchActionPayload(menu.containerId, ResearchActionPayload.REQUEST, ""));
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
        for (int i = 0; i < tabs.size(); i++)
            tabs.get(i).active = i != category.ordinal();
        boolean baseOwned = selected != null && selected.base() && owned(selected);
        int buttonWidth = (details.width - 16) / 2;
        primaryButton.setWidth(baseOwned ? buttonWidth : details.width - 12);
        callButton.visible = baseOwned;
        callButton.setX(details.x + 10 + buttonWidth);
        callButton.setWidth(details.width - 16 - buttonWidth);
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        refreshButtons();
        frame(graphics, new Box(leftPos, topPos, imageWidth, imageHeight), PANEL);
        graphics.renderItem(KillstreakContent.HEADQUARTERS_TERMINAL_ITEM.get().getDefaultInstance(), leftPos + 12,
                topPos + 12);
        line(graphics, title, leftPos + 34, topPos + 13, imageWidth - 224, WHITE);
        var required = state != null && state.upgradesDisabled() ? ResearchCatalog.baseMask()
                : ResearchCatalog.fullMask();
        int learned = state == null ? 0 : state.unlocked().intersect(required).count();
        line(graphics, text("progress", learned, required.count()), leftPos + 12, topPos + 36, imageWidth - 208, MUTED);
        frame(graphics, canvas, CANVAS);
        frame(graphics, details, HEADER);
        drawTree(graphics, mouseX, mouseY);
        drawDetails(graphics);
        line(graphics, status, leftPos + 12, topPos + imageHeight - 20, imageWidth - (unlockAll.visible ? 194 : 96),
                MUTED);
    }

    private void drawTree(GuiGraphics graphics, int mouseX, int mouseY) {
        var definitions = ResearchCatalog.definitions(category);
        int nodeWidth = Math.min(146, canvas.width - 16);
        contentWidth = definitions.size() * (nodeWidth + 20);
        contentHeight = definitions.stream().mapToInt(entry -> 86 + entry.upgrades().size() * 48).max().orElse(0);
        clampScroll();
        visuals.clear();
        graphics.enableScissor(canvas.x + 1, canvas.y + 1, canvas.x + canvas.width - 1, canvas.y + canvas.height - 1);
        for (int x = canvas.x + 20; x < canvas.x + canvas.width; x += 24)
            graphics.vLine(x, canvas.y + 1, canvas.y + canvas.height - 2, GRID);
        for (int y = canvas.y + 20; y < canvas.y + canvas.height; y += 24)
            graphics.hLine(canvas.x + 1, canvas.x + canvas.width - 2, y, GRID);
        if (definitions.isEmpty())
            wrapped(graphics, text("empty_category"), canvas.x + 12, canvas.y + 24, canvas.width - 24, MUTED);
        for (int column = 0; column < definitions.size(); column++) {
            var definition = definitions.get(column);
            int x = canvas.x + 8 + column * (nodeWidth + 20) - (int) scrollX;
            int y = canvas.y + 10 - (int) scrollY;
            if (!definition.upgrades().isEmpty())
                graphics.vLine(x + 5, y + 43, y + 82 + (definition.upgrades().size() - 1) * 48, EDGE);
            drawNode(graphics, definition.research(), new Box(x, y, nodeWidth, 44), mouseX, mouseY);
            for (int index = 0; index < definition.upgrades().size(); index++) {
                int childY = y + 66 + index * 48;
                graphics.hLine(x + 5, x + 17, childY + 17, EDGE);
                drawNode(graphics, definition.upgrades().get(index), new Box(x + 16, childY, nodeWidth - 16, 36),
                        mouseX, mouseY);
            }
        }
        graphics.disableScissor();
    }

    private void drawNode(GuiGraphics graphics, Node node, Box box, int mouseX, int mouseY) {
        boolean chosen = selected == node;
        int color = owned(node) ? node.base() || equipped(node) ? ACCENT : MUTED : prerequisites(node) ? WHITE : MUTED;
        if (upgradeDisabled(node))
            color = MUTED;
        frame(graphics, box, chosen ? 0xff203b47 : HEADER);
        if (chosen || canvas.contains(mouseX, mouseY) && box.contains(mouseX, mouseY))
            graphics.fill(box.x, box.y, box.x + box.width, box.y + 2, ACCENT);
        int labelX = box.x + 7;
        if (node.base()) {
            OriginalRewardUi.icon(graphics, node.id(), box.x + 21, box.y + 21, 29, color, 1);
            labelX += 34;
        }
        line(graphics, node.title(), labelX, box.y + 8, box.x + box.width - labelX - 5, color);
        Component condition = upgradeDisabled(node) ? text("upgrades_disabled")
                : owned(node) ? text(node.base() ? "owned" : equipped(node) ? "equipped" : "unequipped")
                        : text("requires_tier_levels", text("tier." + node.tier()), node.cost().levels());
        line(graphics, condition, labelX, box.y + 22, box.x + box.width - labelX - 5, MUTED);
        visuals.add(new Visual(node, box));
    }

    private void drawDetails(GuiGraphics graphics) {
        int x = details.x + 8, width = details.width - 16;
        int top = details.y + 6, bottom = details.y + details.height - 31;
        graphics.enableScissor(details.x + 1, top, details.x + details.width - 1, bottom);
        int y = top - (int) detailScroll;
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
                        state.creative() || minecraft.player.experienceLevel >= cost.levels() ? WHITE : LOCKED) + 6;
                for (var ingredient : cost.materials()) {
                    ItemStack stack = ingredient.item().getDefaultInstance();
                    graphics.renderItem(stack, x, y);
                    int have = ResearchCatalog.quantity(minecraft.player, ingredient.item());
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
        detailHeight = y + (int) detailScroll - top + 10;
        graphics.disableScissor();
        detailScroll = net.minecraft.util.Mth.clamp(detailScroll, 0, Math.max(0, detailHeight - (bottom - top)));
    }

    private void clampScroll() {
        scrollX = net.minecraft.util.Mth.clamp(scrollX, 0, Math.max(0, contentWidth - canvas.width + 12));
        scrollY = net.minecraft.util.Mth.clamp(scrollY, 0, Math.max(0, contentHeight - canvas.height));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && canvas.contains(mouseX, mouseY)) {
            for (Visual visual : visuals)
                if (visual.box.contains(mouseX, mouseY)) {
                    selected = visual.node;
                    detailScroll = 0;
                    break;
                }
            dragging = true;
            refreshButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            scrollX -= dragX;
            scrollY -= dragY;
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (details.contains(mouseX, mouseY)) {
            detailScroll = Math.max(0, detailScroll - vertical * 22);
            return true;
        }
        if (canvas.contains(mouseX, mouseY)) {
            scrollX -= (hasShiftDown() ? vertical : 0) * 24;
            if (!hasShiftDown())
                scrollY -= vertical * 24;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    private static void frame(GuiGraphics graphics, Box box, int background) {
        graphics.fill(box.x, box.y, box.x + box.width, box.y + box.height, EDGE);
        graphics.fill(box.x + 1, box.y + 1, box.x + box.width - 1, box.y + box.height - 1, background);
    }

    private void line(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0)
            return;
        float scale = Math.min(1, maxWidth / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }
}
