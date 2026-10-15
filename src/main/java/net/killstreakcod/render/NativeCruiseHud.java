package net.killstreakcod.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * TrinityOverlay's original anchors, layers and numeric boot/focus/damage sequences.
 * 使用 TrinityOverlay 的原始锚点、图层及数值化的启动、聚焦和受损动画序列。
 */
public final class NativeCruiseHud {
    private static int unit = -1, health;
    private static long start, damage = Long.MIN_VALUE;

    private NativeCruiseHud() {}

    static boolean render(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        var widget = NativeSceneUi.widget("TrinityOverlay");
        if (widget == null || mc.level == null)
            return false;
        long now = mc.level.getGameTime();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        if (unit != TacticalClient.state.entityId()) {
            unit = TacticalClient.state.entityId();
            health = TacticalClient.state.health();
            start = now;
            damage = Long.MIN_VALUE;
        }
        if (StreakPresentation.currentStage().equals("entry")) {
            start = now;
            return true;
        }
        if (TacticalClient.state.health() < health)
            damage = now;
        health = TacticalClient.state.health();
        float age = (now - start + partial) * 50,
                damaged = damage == Long.MIN_VALUE ? Float.MAX_VALUE : (now - damage + partial) * 50;
        String phase = damaged < 500 ? "Glitch" : "Bootup";
        float time = phase.equals("Glitch") ? damaged : age;
        for (var entry : widget.getAsJsonObject("nodes").entrySet()) {
            String name = entry.getKey();
            if (name.contains("Instruction") || name.contains("Mask"))
                continue;
            String sequence = name.equals("Reticle") && TacticalClient.fov() < 60 && age > 600 ? "ReticleFocus" : phase;
            var layer = NativeSceneUi.state("TrinityOverlay", name, sequence, time, graphics.guiWidth(),
                    graphics.guiHeight());
            if (layer == null || layer.alpha() <= 0)
                continue;
            var rect = layer.rect();
            if (rect.w() <= 0 || rect.h() <= 0)
                continue;
            if (layer.image() != null)
                NativeSceneUi.drawLayer(graphics, layer);
            else if (name.startsWith("BG") || name.equals("Black"))
                fill(graphics, rect, 0, layer.alpha());
            else if (name.startsWith("Line") || name.equals("White"))
                fill(graphics, rect, 0xd4e7c3, layer.alpha());
            else if (name.equals("TimeLeft"))
                text(graphics, rect, "T-" + TacticalClient.fuel() / 20, layer.alpha());
            else if (name.equals("FluffLocked"))
                text(graphics, rect, "LOCKED", layer.alpha());
            else if (name.equals("FluffArm"))
                text(graphics, rect, "ARM", layer.alpha());
            else if (name.equals("FluffAuto"))
                text(graphics, rect, "AUTO", layer.alpha());
            else if (name.equals("FluffRDY"))
                text(graphics, rect, "RDY", layer.alpha());
        }
        return true;
    }

    private static void fill(GuiGraphics graphics, NativeSceneUi.Rect r, int rgb, float alpha) {
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        graphics.fill(Mth.clamp(Math.round(r.x()), 0, w), Mth.clamp(Math.round(r.y()), 0, h),
                Mth.clamp(Math.round(r.x() + r.w()), 0, w), Mth.clamp(Math.round(r.y() + r.h()), 0, h),
                Math.round(alpha * 255) << 24 | rgb);
    }

    private static void text(GuiGraphics graphics, NativeSceneUi.Rect r, String value, float alpha) {
        graphics.drawString(Minecraft.getInstance().font, value, Math.round(r.x()),
                Math.round(r.y() + Math.max(0, r.h() - 9) / 2), Math.round(alpha * 255) << 24 | 0xb8d39d, false);
    }

    static void reset() {
        unit = -1;
        start = 0;
        damage = Long.MIN_VALUE;
    }
}
