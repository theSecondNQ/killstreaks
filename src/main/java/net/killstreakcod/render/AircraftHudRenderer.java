package net.killstreakcod.render;

import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.ResearchCombat;
import net.killstreakcod.universal.TacticalViewPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Verified aircraft widget geometry with live mission state; unrelated vehicle and SP branches stay hidden.
 * 以核对过的飞机控件布局显示实时任务状态，按当前状态选择分支，不叠加其他载具或单人教程界面。
 */
public final class AircraftHudRenderer {
    private static final int WHITE = 0xffecf0e9, MUTED = 0xffb2bdb3, RED = 0xffee7565, AMBER = 0xffe1bb75;
    private static final String[] RETICLES = { "Reticle25mm", "Reticle40mm", "Reticle105mm" };
    private static final String[] CALIBERS = { "25mm", "40mm", "105mm" };
    private static final List<String> FIXED_INSTRUMENTS = List.of("FluffTextTitle", "OnlineStatusTitle", "OnlineStatus",
            "FluffStats", "SpeedStats", "FluffStats3", "FluffStats6", "FluffStats7", "GunshipFluffCompass",
            "DroneCompass", "FuelBar");
    private static int entity = -1, weapon = -1, lastCooldown;
    private static int[] lastAmmo = new int[3];
    private static long entered, switched, fired = Long.MIN_VALUE, firstVisible = Long.MIN_VALUE;
    private static List<RemoteHudLayout.Box> occupied = List.of();
    private static int occupiedWidth, occupiedHeight;
    private static long targetTick = Long.MIN_VALUE;
    private static Vec3 target;

    private enum Alignment {
        LEFT, CENTER, RIGHT
    }

    private record Widget(String name, NativeSceneUi.Rect box, float alpha, Alignment alignment, String id) {
        Widget(String name, NativeSceneUi.Rect box, float alpha, Alignment alignment) {
            this(name, box, alpha, alignment, name);
        }

        Widget(String name, NativeSceneUi.Rect box, float alpha) {
            this(name, box, alpha, Alignment.LEFT);
        }
    }

    private AircraftHudRenderer() {}

    static boolean available(String mode) {
        return (mode.equals("gunship") || mode.equals("chopper")) && NativeSceneUi.widget("GunshipWeapons") != null
                && NativeSceneUi.widget("ApacheScreenElements") != null;
    }

    static void reset() {
        entity = weapon = -1;
        lastCooldown = 0;
        lastAmmo = new int[3];
        entered = switched = 0;
        fired = firstVisible = Long.MIN_VALUE;
        occupied = List.of();
        targetTick = Long.MIN_VALUE;
        target = null;
    }

    static void accept(TacticalViewPayload packet) {
        if (!packet.mode().equals("gunship") && !packet.mode().equals("chopper"))
            return;
        long now = Util.getMillis();
        if (entity != packet.entityId()) {
            reset();
            entity = packet.entityId();
            entered = switched = now;
            weapon = packet.weapon();
            lastAmmo = packet.ammo().clone();
        } else {
            if (weapon != packet.weapon()) {
                weapon = packet.weapon();
                switched = now;
            }
            int selected = Mth.clamp(packet.weapon(), 0, 2);
            if (packet.ammo()[selected] < lastAmmo[selected]
                    || packet.mode().equals("chopper") && packet.cooldown() > lastCooldown)
                fired = now;
            lastAmmo = packet.ammo().clone();
        }
        lastCooldown = packet.cooldown();
    }

    static List<RemoteHudLayout.Box> reservations(GuiGraphics gui) {
        return occupiedWidth == gui.guiWidth() && occupiedHeight == gui.guiHeight() ? occupied : List.of();
    }

    static boolean render(GuiGraphics gui, String mode) {
        Minecraft mc = Minecraft.getInstance();
        if (!available(mode) || !UiLayout.editing() && (mc.level == null || mc.getCameraEntity() == null))
            return false;
        if (entity != TacticalClient.state.entityId())
            accept(TacticalClient.state);
        try (NativeSceneUi.Batch batch = NativeSceneUi.batch(gui)) {
            long now = Util.getMillis();
            if (firstVisible == Long.MIN_VALUE)
                firstVisible = now;
            boolean gunship = mode.equals("gunship");
            float scale = gui.guiHeight() / 1080F;
            Widget root = new Widget(gunship ? "GunshipControls" : "ApacheScreenElements",
                    new NativeSceneUi.Rect(0, 0, gui.guiWidth(), gui.guiHeight()), 1);
            // Boot timing is visual only: aiming and ammunition remain usable throughout the existing entry scene.
            // 启动时序只影响视觉，沿用现有入场镜头，不额外锁住瞄准或把可用武器隐藏数秒。
            float boot = gunship
                    ? Math.max(now - entered,
                            NativeSceneUi.sequenceLength("GunshipControls", "BootUp", 7300) - 300 + now - firstVisible)
                    : now - entered;
            List<RemoteHudLayout.Box> reservations = new ArrayList<>();
            for (String field : FIXED_INSTRUMENTS)
                reserve(reservations, child(root, field, "", 0, scale).box);
            var vision = child(root, "VisionSystem", "", 0, scale).box;
            reserve(reservations, new NativeSceneUi.Rect(vision.x(), vision.y(), vision.w(), vision.h() + 72 * scale));
            Widget exit = child(new Widget(gunship ? "GunshipControls" : "ApacheControls", root.box, 1),
                    "InstructionExit", "", 0, scale);
            reserve(reservations, exit.box);
            reticle(gui, root, gunship, scale, now);
            zoom(gui, root, scale, reservations);
            instruments(gui, root, gunship, scale, boot, reservations);
            fuel(gui, root, scale);
            vision(gui, root, scale);
            compass(gui, root, gunship, scale);
            health(gui, gunship, scale, reservations);
            armament(gui, gunship, scale, reservations);
            warnings(gui, root, scale, reservations);
            damage(gui);
            label(gui, exit, "[" + key(KillstreakKeyMappings.EXIT) + "] " + nativeText("593f2b158c8908b0"), WHITE,
                    scale);
            occupied = List.copyOf(reservations);
            occupiedWidth = gui.guiWidth();
            occupiedHeight = gui.guiHeight();
            return true;
        }
    }

    private static Widget child(Widget parent, String field, String sequence, float time, float scale) {
        NativeSceneUi.Layer layer = NativeSceneUi.childState(parent.name, field, sequence, time, parent.box, scale);
        if (layer == null)
            return new Widget("", new NativeSceneUi.Rect(0, 0, 0, 0), 0);
        var node = NativeSceneUi.widget(parent.name).getAsJsonObject("nodes").getAsJsonObject(field);
        Alignment alignment = !node.has("alignment") ? Alignment.LEFT : switch (node.get("alignment").getAsString()) {
            case "Center" -> Alignment.CENTER;
            case "Right" -> Alignment.RIGHT;
            default -> Alignment.LEFT;
        };
        return new Widget(node.has("widget") ? node.get("widget").getAsString() : parent.name, layer.rect(),
                parent.alpha * layer.alpha(), alignment, parent.id + "/" + field);
    }

    private static void image(GuiGraphics gui, Widget parent, String field, String sequence, float time, float scale) {
        try (UiLayout.Scope itemTag = UiLayout.tag(parent.id + "/" + field)) {
            NativeSceneUi.Layer layer = NativeSceneUi.childState(parent.name, field, sequence, time, parent.box, scale);
            if (layer != null && layer.image() != null)
                NativeSceneUi.drawLayer(gui, new NativeSceneUi.Layer(layer.image(), layer.rect(),
                        layer.alpha() * parent.alpha, layer.rotation(), layer.scale(), layer.sprite()));

        }
    }

    private static void reticle(GuiGraphics gui, Widget root, boolean gunship, float scale, long now) {
        int selected = TacticalClient.state.weapon(), index = gunship ? Mth.clamp(selected, 0, 2) : 0;
        boolean reloading = gunship && selected < 3 && TacticalClient.reload(index) > 0;
        String field = reloading ? "ReticleInactive" : RETICLES[index];
        String active = gunship && !reloading ? "Active" + CALIBERS[index] : "";
        NativeSceneUi.Layer container = NativeSceneUi.childState(root.name, field, active, now - switched, root.box,
                scale);
        if (container == null)
            return;

        try (UiLayout.Scope group = UiLayout.group(gui, "reticle", container.rect())) {
            Widget frame = child(root, field, active, now - switched, scale);
            if (frame.name.equals("GunshipReticle25mmWrapper"))
                frame = child(frame, "Reticle25mmCore", "", 0, scale);
            float firingTime = fired == Long.MIN_VALUE ? Float.MAX_VALUE : now - fired;
            String sequence = firingTime < NativeSceneUi.sequenceLength(frame.name, "Firing", 200) ? "Firing" : "";
            if (index == 2 && !reloading && sequence.isEmpty()) {
                sequence = "Boot";
                firingTime = NativeSceneUi.sequenceLength(frame.name, "Boot", 1280);
            }
            gui.pose().pushPose();
            try {
                var center = container.rect();
                float x = center.x() + center.w() / 2, y = center.y() + center.h() / 2;
                gui.pose().translate(x, y, 0);
                gui.pose().scale(container.scale(), container.scale(), 1);
                gui.pose().translate(-x, -y, 0);
                image(gui, frame, "Reticle", sequence, firingTime, scale);
            } finally {
                gui.pose().popPose();
            }

        }
    }

    private static void instruments(GuiGraphics gui, Widget root, boolean gunship, float scale, float boot,
            List<RemoteHudLayout.Box> reservations) {
        String sequence = gunship ? "BootUp" : "";
        Widget title = child(root, gunship ? "FluffTextTitle" : "OnlineStatusTitle", sequence, boot, scale);
        label(gui, title,
                gunship ? nativeText("00d7447e14990148") : StreakReward.CHOPPER_GUNNER.displayName().getString(), WHITE,
                scale);
        label(gui, child(root, "OnlineStatus", sequence, boot, scale), nativeText("36a6d88e7c3ef4b3"), WHITE, scale);
        image(gui, root, gunship ? "SatelliteIcon" : "OnlineStatusIcon", sequence, boot, scale);
        var camera = Minecraft.getInstance().getCameraEntity();
        if (gunship) {
            label(gui, child(root, "FluffStats", "", 0, scale), String.format(Locale.ROOT, "X %.0f   Z %.0f",
                    camera == null ? 0 : camera.getX(), camera == null ? 0 : camera.getZ()), WHITE, scale);
            label(gui, child(root, "SpeedStats", "", 0, scale), nativeText("768e0497ae345ed8") + " "
                    + String.format(Locale.ROOT, "%.1f", TacticalClient.state.speed()) + " m/s", WHITE, scale);
            label(gui, child(root, "FluffStats3", "", 0, scale),
                    nativeText("02092e06e07b92fb") + " " + (camera == null ? 198 : Math.round(camera.getY())) + " m",
                    WHITE, scale);
            label(gui, child(root, "FluffStats6", "", 0, scale), I18n.get("hud.killstreakcod.heading", heading()),
                    WHITE, scale);
            label(gui, child(root, "FluffStats7", "", 0, scale),
                    I18n.get("hud.killstreakcod.pitch", Math.round(TacticalClient.pitch())), WHITE, scale);
            Vec3 point = target();
            if (point != null) {
                Widget location = child(root, "GunshipTargetLocationText", "", 0, scale);
                location = new Widget(location.name, RemoteHudLayout.placePanel(gui, location.box, reservations), 1,
                        Alignment.CENTER);
                label(gui, location,
                        nativeText("2c3e296a567bdc38",
                                String.format(Locale.ROOT, "%.0f / %.0f / %.0f", point.x, point.y, point.z)),
                        WHITE, scale);
                reserve(reservations, location.box);
            }
        }
    }

    private static void fuel(GuiGraphics gui, Widget root, float scale) {
        Widget meter = child(root, "FuelBar", "", 0, scale);

        try (UiLayout.Scope group = UiLayout.group(gui, "fuel", meter.box)) {
            image(gui, meter, "Background", "", 0, scale);
            Widget fill = child(meter, "Fill", "", 0, scale);
            float fraction = NativeRemoteHud.fuelFraction();
            var r = fill.box;
            UiDraw.fill(gui, "AircraftHudRenderer.fuel.0", Math.round(r.x()),
                    Math.round(r.y() + r.h() * (1 - fraction)), Math.round(r.x() + r.w()), Math.round(r.y() + r.h()),
                    fraction < .2F ? RED : WHITE);
            label(gui, child(meter, "FuelLabel", "", 0, scale), nativeText("19b7afe9740e34e4"), WHITE, scale);

        }
    }

    private static void vision(GuiGraphics gui, Widget root, float scale) {
        Widget vision = child(root, "VisionSystem", "", 0, scale);

        try (UiLayout.Scope group = UiLayout.group(gui, "vision", vision.box)) {
            label(gui, vision, TacticalClient.state.thermal() ? nativeText("61cf635aadc8cc43")
                    : I18n.get("hud.killstreakcod.camera_optical"), WHITE, scale);
            label(gui, child(vision, "VisionSystemText", "", 0, scale),
                    "[" + key(KillstreakKeyMappings.THERMAL) + "] " + nativeText("4c8c0d7aaeb7933f"), WHITE, scale);

        }
    }

    private static void zoom(GuiGraphics gui, Widget root, float scale, List<RemoteHudLayout.Box> reservations) {
        Widget zoom = child(root, "ZoomMeter", "", 0, scale);

        try (UiLayout.Scope group = UiLayout.group(gui, "zoom", zoom.box)) {
            image(gui, zoom, "Meter", "", 0, scale);
            var meter = child(zoom, "Meter", "", 0, scale).box;
            reserve(reservations, new NativeSceneUi.Rect(meter.x() - 220 * scale, meter.y() - 28 * scale,
                    meter.w() + 460 * scale, meter.h() + 56 * scale));
            // These are the recovered HUD meter limits; actual weapon FOV remains owned by TacticalClient.
            // 仅用恢复的 HUD 刻度上下限映射标尺，实际武器视场仍由遥控模块决定。
            float fraction = Mth.clamp((80 - TacticalClient.fov()) / 78F, 0, 1);
            Widget guide = child(zoom, "MeterGuide", "", 0, scale);
            guide = new Widget(guide.name, new NativeSceneUi.Rect(guide.box.x(),
                    zoom.box.y() + (754 - fraction * 512) * scale, guide.box.w(), guide.box.h()), 1, guide.alignment);
            image(gui, guide, "ZoomGuide", "", 0, scale);
            label(gui, child(zoom, "CloseRangeMeterLabel", "", 0, scale), "80°", MUTED, scale);
            label(gui, child(zoom, "LongRangeMeterLabel", "", 0, scale), "2°", MUTED, scale);
            label(gui, child(guide, "CurrentZoom", "", 0, scale), Math.round(TacticalClient.fov()) + "°", WHITE, scale);
            Widget binding = child(zoom, "MedRangeMeterLabel", "", 0, scale);
            binding = new Widget(binding.name, new NativeSceneUi.Rect(binding.box.x(), binding.box.y(),
                    Math.max(1, Math.min(220 * scale, gui.guiWidth() - binding.box.x() - 24 * scale)), binding.box.h()),
                    binding.alpha);
            label(gui, binding, "[" + key(KillstreakKeyMappings.ZOOM) + "] " + nativeText("2cdc95c748efc81b"), WHITE,
                    scale);

        }
    }

    private static void compass(GuiGraphics gui, Widget root, boolean gunship, float scale) {
        try (UiLayout.Scope group = UiLayout.group(gui, "compass", NativeSceneUi.bounds(root.name,
                gunship ? "GunshipFluffCompass" : "DroneCompass", gui.guiWidth(), gui.guiHeight()))) {

            if (gunship) {
                Widget compass = child(root, "GunshipFluffCompass", "", 0, scale);
                NativeSceneUi.draw(gui, "overlay_gunship_minicompass_ring", compass.box, .8F);
                NativeSceneUi.drawLayer(gui, new NativeSceneUi.Layer("overlay_gunship_minicompass_needle", compass.box,
                        .9F, heading(), 1, false));
            } else {
                Widget compass = child(root, "DroneCompass", "", 0, scale);
                var box = compass.box;
                NativeSceneUi.draw(gui, "overlay_recon_drone_compass_lines",
                        new NativeSceneUi.Rect(box.x(), box.y(), box.w(), 24 * scale), .8F);
                label(gui,
                        new Widget("",
                                new NativeSceneUi.Rect(box.x() + box.w() / 2 - 55 * scale, box.y() + 26 * scale,
                                        110 * scale, 28 * scale),
                                1, Alignment.CENTER),
                        String.format(Locale.ROOT, "%03d°", heading()), WHITE, scale);
            }

        }
    }

    private static void health(GuiGraphics gui, boolean gunship, float scale, List<RemoteHudLayout.Box> reservations) {
        float width = 280 * scale, height = 104 * scale;
        float left = Math.max(8, gui.guiWidth() / 2F - 830 * scale);
        var panel = RemoteHudLayout.placePanel(gui,
                new NativeSceneUi.Rect(left, gui.guiHeight() - 145 * scale, width, height), reservations);

        try (UiLayout.Scope group = UiLayout.group(gui, "health", panel)) {
            UiDraw.fill(gui, "AircraftHudRenderer.health.0", Math.round(panel.x()), Math.round(panel.y()),
                    Math.round(panel.x() + panel.w()), Math.round(panel.y() + panel.h()), 0x40081210);
            float size = 88 * scale;
            NativeSceneUi.Rect area = new NativeSceneUi.Rect(panel.x() + 8 * scale, panel.y() + 8 * scale, size, size);
            String diagram = gunship ? "veh_diagram_gunship_" : "veh_diagram_choppergunner_";
            NativeSceneUi.draw(gui, diagram + "out", area, .8F);
            float fraction = Mth.clamp(TacticalClient.state.health() / 100F, 0, 1);
            gui.enableScissor(Math.round(area.x()), Math.round(area.y() + area.h() * (1 - fraction)),
                    Math.round(area.x() + area.w()), Math.round(area.y() + area.h()));
            // Native mask images are opaque stencil inputs; drawing them as RGBA would cover the view with a block.
            // 原 mask 是不透明的模板输入，直接作为 RGBA 贴图绘制会出现整块遮挡；这里用透明辉光显示血量。
            NativeSceneUi.draw(gui, diagram + "glow", area, .18F);
            gui.disableScissor();
            float textX = panel.x() + 112 * scale, textWidth = Math.max(1, panel.w() - 124 * scale);
            label(gui, new Widget("", new NativeSceneUi.Rect(textX, panel.y() + 25 * scale, textWidth, 30 * scale), 1),
                    I18n.get("hud.killstreakcod.hull", TacticalClient.state.health()), fraction < .35F ? RED : WHITE,
                    scale);
            int barY = Math.round(panel.y() + 66 * scale), barHeight = Math.max(1, Math.round(4 * scale));
            UiDraw.fill(gui, "AircraftHudRenderer.health.1", Math.round(textX), barY, Math.round(textX + textWidth),
                    barY + barHeight, 0x77717e74);
            UiDraw.fill(gui, "AircraftHudRenderer.health.2", Math.round(textX), barY,
                    Math.round(textX + textWidth * fraction), barY + barHeight, fraction < .35F ? RED : WHITE);
            reserve(reservations, panel);

        }
    }

    private static void armament(GuiGraphics gui, boolean gunship, float scale,
            List<RemoteHudLayout.Box> reservations) {
        int[] slots = gunship ? ResearchWeaponHud.gunshipSlots() : new int[] { 1, 0 };
        float width = 430 * scale, rowHeight = 38 * scale, height = slots.length * rowHeight + 48 * scale;
        float margin = Math.max(8, 92 * scale);
        var panel = RemoteHudLayout.placePanel(gui, new NativeSceneUi.Rect(gui.guiWidth() - margin - width,
                gui.guiHeight() - 52 * scale - height, width, height), reservations);

        var options = Minecraft.getInstance().options;
        if (gunship) {
            for (int row = 0; row < slots.length; row++) {
                int slot = slots[slots.length - row - 1];
                boolean selected = TacticalClient.state.weapon() == slot;
                int reload = TacticalClient.reload(slot);
                String ammo = reload > 0 ? I18n.get("hud.killstreakcod.reload_time", (int) Math.ceil(reload / 20D))
                        : ResearchWeaponHud.ammo(slot);
                weaponRow(gui, panel, row, rowHeight, key(ResearchWeaponHud.key(slot)), ResearchWeaponHud.name(slot),
                        ammo, selected, reload > 0 ? AMBER : WHITE, scale);
            }
        } else {
            String rockets = ResearchWeaponHud.capability(ResearchCombat.UNLIMITED_ROCKETS) ? "∞"
                    : Integer.toString(TacticalClient.state.rockets());
            weaponRow(gui, panel, 0, rowHeight, key(KillstreakKeyMappings.SECONDARY_FIRE),
                    nativeText("6fe68a58b2fbbb13"), rockets, false, TacticalClient.rocketCooldown() > 0 ? AMBER : WHITE,
                    scale);
            weaponRow(gui, panel, 1, rowHeight, key(KillstreakKeyMappings.primary()), nativeText("65e537e2780b72d6"),
                    "∞", false, WHITE, scale);
        }
        Widget flares = new Widget("", new NativeSceneUi.Rect(panel.x() + 12 * scale,
                panel.y() + slots.length * rowHeight + 15 * scale, panel.w() - 24 * scale, 26 * scale), 1,
                Alignment.RIGHT);
        try (UiLayout.Scope group = UiLayout.group(gui, "flares", flares.box)) {
            label(gui, flares, "[" + key(KillstreakKeyMappings.FLARES) + "] " + nativeText("435dfa11cb806127") + " "
                    + TacticalClient.state.flares(), MUTED, scale);
        }
        reserve(reservations, panel);

    }

    /** Shared column widths keep localized weapon names separate from key bindings and ammunition. */
    // 中文：按键、名称与弹药各占独立列，防止长中文提示挤进相邻控件。
    private static void weaponRow(GuiGraphics gui, NativeSceneUi.Rect panel, int row, float height, String key,
            String name, String ammunition, boolean selected, int ammoColor, float scale) {
        try (UiLayout.Scope itemTag = UiLayout.group(gui, "weapon." + row,
                new UiLayout.Box(panel.x(), panel.y() + 6 * scale + row * height, panel.w(), height))) {
            float x = panel.x(), y = panel.y() + 6 * scale + row * height, padding = 12 * scale;
            float textHeight = 30 * scale;
            var box = new NativeSceneUi.Rect(x, y, panel.w(), height - 3 * scale);
            UiDraw.fill(gui, "AircraftHudRenderer.weaponRow.0", Math.round(x), Math.round(y), Math.round(x + panel.w()),
                    Math.round(y + height - 3 * scale), selected ? 0x66081210 : 0x40081210);
            if (selected)
                NativeSceneUi.draw(gui, "overlay_gunship_selectionbox", box, .65F);
            String binding = "[" + key + "]";
            var font = Minecraft.getInstance().font;
            float keyWidth = Math.min(panel.w() * .38F,
                    Math.max(62 * scale, font.width(binding) * 24 * scale / font.lineHeight + 8 * scale));
            float ammoWidth = Math.min(panel.w() * .46F,
                    Math.max(70 * scale, font.width(ammunition) * 24 * scale / font.lineHeight + 4 * scale));
            float ammoX = x + panel.w() - padding - ammoWidth;
            float nameX = x + padding + keyWidth + 12 * scale;
            label(gui, new Widget("", new NativeSceneUi.Rect(x + padding, y, keyWidth, textHeight), 1), binding,
                    selected ? WHITE : MUTED, scale);
            label(gui,
                    new Widget("",
                            new NativeSceneUi.Rect(nameX, y, Math.max(1, ammoX - 12 * scale - nameX), textHeight), 1),
                    name, selected ? WHITE : MUTED, scale);
            if (ammunition.equals("∞")) {
                NativeSceneUi.draw(gui, "apache_icon_infinite", new NativeSceneUi.Rect(
                        x + panel.w() - padding - 45 * scale, y + 5 * scale, 45 * scale, 20 * scale), .9F);
            } else
                label(gui, new Widget("", new NativeSceneUi.Rect(ammoX, y, ammoWidth, textHeight), 1, Alignment.RIGHT),
                        ammunition, ammoColor, scale);

        }
    }

    private static void warnings(GuiGraphics gui, Widget root, float scale, List<RemoteHudLayout.Box> reservations) {
        if (TacticalClient.fuel() < 200 && Util.getMillis() % 800 < 500) {
            Widget warning = child(root, "LowFuelWarning", "", 0, scale);
            label(gui, warning, nativeText("3a17bcb1022319ab"), RED, scale);
            reserve(reservations, warning.box);
        }
        if (TacticalClient.state.health() < 30 && root.name.equals("GunshipControls")) {
            Widget warning = child(root, "CriticalDamageWarning", "", 0, scale);
            Widget text = child(warning, "WarningText", "", 0, scale);
            label(gui, new Widget(text.name, text.box, 1), nativeText("476237bde4745277"), RED, scale);
            reserve(reservations, text.box);
        }
        var camera = Minecraft.getInstance().getCameraEntity();
        if (root.name.equals("GunshipControls") && camera instanceof TacticalUnitEntity) {
            TacticalUnitEntity plane = (TacticalUnitEntity) camera;
            if (Math.abs(Mth.wrapDegrees(
                    TacticalClient.yaw() - GunshipFlight.mountYaw(plane.renderFlightYaw(0)))) > GunshipFlight.YAW_LIMIT
                            - 2
                    || Math.abs(TacticalClient.pitch()) > GunshipFlight.PITCH_LIMIT - 1)
                label(gui, child(root, "GunshipWeaponStatus", "", 0, scale), I18n.get("hud.killstreakcod.gimbal_limit"),
                        AMBER, scale);
        }
    }

    private static void damage(GuiGraphics gui) {
        int health = TacticalClient.state.health();
        if (health >= 75)
            return;
        int severity = health < 25 ? 4 : health < 45 ? 3 : health < 60 ? 2 : 1;
        float effects = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        NativeSceneUi.draw(gui, "overlay_gunship_damagecrack_" + severity,
                new NativeSceneUi.Rect(0, 0, gui.guiWidth(), gui.guiHeight()), .22F * effects);
    }

    private static String labelGroup(Widget widget) {
        String field = widget.id.substring(widget.id.lastIndexOf('/') + 1);
        return switch (field) {
            case "FluffTextTitle", "OnlineStatusTitle" -> "status.title";
            case "OnlineStatus" -> "status.connection";
            case "FluffStats" -> "telemetry.position";
            case "SpeedStats" -> "telemetry.speed";
            case "FluffStats3" -> "telemetry.height";
            case "FluffStats6" -> "telemetry.heading";
            case "FluffStats7" -> "telemetry.pitch";
            case "GunshipTargetLocationText" -> "target.coordinates";
            case "ExitControl" -> "controls.exit";
            case "LowFuelWarning" -> "warning.fuel";
            case "WarningText" -> "warning.damage";
            default -> null;
        };
    }

    private static void label(GuiGraphics gui, Widget widget, String value, int color, float scale) {
        try (UiLayout.Scope itemTag = UiLayout.group(gui, labelGroup(widget), widget.box)) {
            var box = widget.box;
            if (box.w() <= 0 || box.h() <= 0 || widget.alpha <= 0 || value.isEmpty())
                return;
            var font = Minecraft.getInstance().font;
            float size = Math.min(Math.max(5, 24 * scale), box.h()) / font.lineHeight;
            if (size <= 0)
                return;
            int maxWidth = Math.max(1, (int) (box.w() / size));
            if (font.width(value) > maxWidth) {
                int suffix = font.width("…");
                value = maxWidth < suffix ? "" : font.plainSubstrByWidth(value, maxWidth - suffix) + "…";
            }
            float spare = Math.max(0, box.w() - font.width(value) * size);
            float offset = widget.alignment == Alignment.RIGHT ? spare
                    : widget.alignment == Alignment.CENTER ? spare / 2 : 0;
            gui.pose().pushPose();
            try {
                gui.pose().translate(box.x() + offset, box.y() + (box.h() - font.lineHeight * size) / 2, 0);
                gui.pose().scale(size, size, 1);
                int alpha = Mth.clamp(Math.round(widget.alpha * 255), 4, 255);
                UiDraw.drawString(gui, "AircraftHudRenderer.label.0", font, value, 0, 0,
                        (color & 0xffffff) | alpha << 24, true);
            } finally {
                gui.pose().popPose();
            }

        }
    }

    private static void reserve(List<RemoteHudLayout.Box> reservations, NativeSceneUi.Rect box) {
        if (box.w() > 0 && box.h() > 0)
            reservations.add(new RemoteHudLayout.Box(Math.round(box.x()), Math.round(box.y()), Math.round(box.w()),
                    Math.round(box.h())));
    }

    private static String nativeText(String id, Object... args) {
        return Component.translatable("hud.killstreakcod.aircraft." + id, args).getString();
    }

    private static int heading() {
        return Math.floorMod(Math.round(TacticalClient.yaw()) + 180, 360);
    }

    private static Vec3 target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return new Vec3(21, 66, 100);
        long tick = mc.level.getGameTime();
        if (targetTick == Long.MIN_VALUE || tick < targetTick || tick - targetTick >= 4) {
            targetTick = tick;
            Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 to = from.add(Vec3.directionFromRotation(TacticalClient.pitch(), TacticalClient.yaw()).scale(2048));
            var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    mc.getCameraEntity()));
            target = hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
        }
        return target;
    }

    private static String key(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }
}
