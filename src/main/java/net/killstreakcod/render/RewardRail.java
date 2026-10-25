package net.killstreakcod.render;

import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.universal.NativePresentationConfig;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 三种栏位共享库存与进度来源；收起只改变显示，不改变奖励或呼叫按键。 */
public final class RewardRail {
    private record Slot(String key, String reward, int count, int progress, int threshold, int reserve,
            KeyMapping binding, int tint) {
    }

    private static long used = Util.getMillis();
    private static Object world;
    private static float visible = 1, from = 1;
    private static boolean wasExpanded = true;
    private static long transitionStarted;

    private RewardRail() {}

    public static void reveal() {
        used = Util.getMillis();
        wasExpanded = false;
    }

    public static void render(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (world != mc.level || mc.level != null && (mc.isPaused() || !mc.isWindowActive())) {
            world = mc.level;
            used = now;
        }
        boolean expanded = !NativePresentationConfig.REWARD_RAIL_AUTO_HIDE.get() || now - used < 3000
                || mc.screen != null || UiLayout.editing();
        if (expanded != wasExpanded) {
            from = visible;
            transitionStarted = now;
            wasExpanded = expanded;
        }
        float progress = Math.min(1, Math.max(0, now - transitionStarted) / 180F);
        float eased = 1 - (float) Math.pow(1 - progress, 3);
        visible = from + ((expanded ? 1 : 0) - from) * eased;
        if (mc.options.screenEffectScale().get() <= 0)
            visible = expanded ? 1 : 0;
        float openness = visible;
        List<Slot> slots = slots();
        int style = NativePresentationConfig.REWARD_RAIL_STYLE.get();
        int row = style == 0 ? TacticalClient.research != null && !TacticalClient.research.streakMode() ? 39 : 29 : 32;
        int y = Math.max(8, (g.guiHeight() - slots.size() * row) / 2);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int width = style == 0 ? 124 : 30, x = g.guiWidth() - 6 - width, top = y + i * row;
            try (UiLayout.Scope group = UiLayout.group(g, "slot." + slot.key(),
                    new UiLayout.Box(x - 3, top, width + 3, row - 2))) {
                if (openness > 0) {
                    g.pose().pushPose();
                    g.pose().translate((width + 8) * (1 - openness), 0, 0);
                    try {
                        cell(g, slot, x, top, width, row - 2, style, openness);
                    } finally {
                        g.pose().popPose();
                    }
                }
                if (openness < 1) {
                    int tint = slot.count() > 0 ? slot.tint() : 0xff7d8c85;
                    UiDraw.fill(g, "RewardRail.render.0", g.guiWidth() - 4, top + 7, g.guiWidth() - 2, top + row - 7,
                            Math.round((1 - openness) * 230) << 24 | tint & 0xffffff);
                }
            }
        }
    }

    private static List<Slot> slots() {
        var t = TacticalClient.terminal;
        List<Slot> result = new ArrayList<>();
        int mgb = t != null ? t.mgbCount() : TacticalClient.bonus.mgb() ? 1 : 0;
        if (mgb > 0)
            result.add(new Slot("mgb", "mgb", mgb, 0, 1, mgb, KillstreakKeyMappings.MGB, 0xfff07363));
        if (!TacticalClient.bonus.queue().isEmpty()) {
            String[] queue = TacticalClient.bonus.queue().split(",");
            String reward = StreakReward.byId(queue[0]).orElse(StreakReward.UAV).id();
            int same = 0;
            for (String id : queue)
                if (id.equals(reward))
                    same++;
            result.add(new Slot("bonus", reward, same, 0, 1, same, KillstreakKeyMappings.BONUS, 0xffdfc989));
        }
        if (t != null) {
            String[] rewards = { t.slot1(), t.slot2(), t.slot3() };
            KeyMapping[] keys = { KillstreakKeyMappings.USE_SLOT_1, KillstreakKeyMappings.USE_SLOT_2,
                    KillstreakKeyMappings.USE_SLOT_3 };
            for (int i = 0; i < 3; i++) {
                StreakReward reward = StreakReward.byId(rewards[i]).orElse(StreakReward.UAV);
                int reserve = TacticalClient.research != null && !TacticalClient.research.streakMode()
                        ? TacticalClient.research.requisitionCount(rewards[i])
                        : t.counts()[i];
                result.add(new Slot(Integer.toString(i), rewards[i], t.counts()[i], t.progress()[i],
                        t.scoreMode() ? reward.score() : reward.kills(), reserve, keys[i], 0xffb9e29a));
            }
        }
        return result;
    }

    private static void cell(GuiGraphics g, Slot s, int x, int y, int width, int height, int style, float alpha) {
        int color = s.count() > 0 ? s.tint() : 0xffa9b1a6;
        if (style == 0) {
            UiDraw.fill(g, "RewardRail.cell.0", x, y, x + width, y + height, Math.round(208 * alpha) << 24 | 0x0e1513);
            if (s.count() > 0)
                UiDraw.fill(g, "RewardRail.ready", x, y, x + 2, y + height,
                        Math.round(255 * alpha) << 24 | color & 0xffffff);
        }
        OriginalRewardUi.icon(g, s.reward(), x + 14, y + 13, 23, color, alpha);
        if (style == 2)
            return;
        if (style == 1) {
            int meter = Math.round(23 * Math.min(1, s.progress() / (float) Math.max(1, s.threshold())));
            UiDraw.fill(g, "RewardRail.cell.1", x - 3, y + 2, x - 1, y + 25, Math.round(95 * alpha) << 24 | 0x9ea899);
            UiDraw.fill(g, "RewardRail.cell.2", x - 3, y + 25 - meter, x - 1, y + 25,
                    Math.round(255 * alpha) << 24 | color & 0xffffff);
            for (int i = 0; i < 8; i++)
                UiDraw.fill(g, "RewardRail.cell.3", x + i * 3, y + 28, x + i * 3 + 2, y + 30,
                        Math.round((i < s.count() ? 255 : 55) * alpha) << 24 | color & 0xffffff);
            return;
        }
        var font = Minecraft.getInstance().font;
        int tint = Math.round(255 * alpha) << 24 | color & 0xffffff;
        String name = Component.translatable("streak.killstreakcod." + s.reward()).getString();
        UiDraw.drawString(g, "RewardRail.cell.4", font, font.plainSubstrByWidth(name, 90), x + 30, y + 3, tint, false);
        String status = "[" + s.binding().getTranslatedKeyMessage().getString() + "] x" + s.count() + "  "
                + s.progress() + "/" + s.threshold();
        UiDraw.drawString(g, "RewardRail.cell.5", font, font.plainSubstrByWidth(status, 90), x + 30, y + 14, tint,
                false);
        if (height > 30)
            UiDraw.drawString(g, "RewardRail.cell.6", font,
                    Component.translatable("hud.killstreakcod.requisition_count", s.reserve()), x + 30, y + 24, tint,
                    false);
        UiDraw.fill(g, "RewardRail.cell.7", x + 30, y + height - 2,
                x + 30 + Math.round(87 * Math.min(1, s.progress() / (float) s.threshold())), y + height - 1, tint);
    }
}
