package net.killstreakcod.render;

import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
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
    private static final int WHITE = 0xffe5ede4, MUTED = 0xff849486, RED = 0xffee7565, AMBER = 0xffe1bb75;
    private static final String[] RETICLES = { "Reticle25mm", "Reticle40mm", "Reticle105mm" };
    private static final String[] CALIBERS = { "25mm", "40mm", "105mm" };
    private static int entity = -1, weapon = -1, lastCooldown;
    private static int[] lastAmmo = new int[3];
    private static long entered, switched, fired = Long.MIN_VALUE, firstVisible = Long.MIN_VALUE;
    private static List<RemoteHudLayout.Box> occupied = List.of();
    private static int occupiedWidth, occupiedHeight;
    private static long targetTick = Long.MIN_VALUE;
    private static Vec3 target;

    private record Widget(String name, NativeSceneUi.Rect box, float alpha) {
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
        if (!available(mode) || mc.level == null || mc.getCameraEntity() == null)
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
            reticle(gui, root, gunship, scale, now);
            instruments(gui, root, gunship, scale, boot, reservations);
            fuel(gui, root, scale);
            vision(gui, root, scale);
            zoom(gui, root, scale);
            compass(gui, root, gunship, scale);
            health(gui, root, gunship, scale, reservations);
            armament(gui, root, gunship, scale, reservations);
            warnings(gui, root, scale, reservations);
            damage(gui);
            Widget exit = child(new Widget(gunship ? "GunshipControls" : "ApacheControls", root.box, 1),
                    "InstructionExit", "", 0, scale);
            label(gui, exit, "[" + key(KillstreakKeyMappings.EXIT) + "] " + nativeText("593f2b158c8908b0"), WHITE,
                    scale);
            reserve(reservations, exit.box);
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
        return new Widget(node.has("widget") ? node.get("widget").getAsString() : parent.name, layer.rect(),
                parent.alpha * layer.alpha());
    }

    private static void image(GuiGraphics gui, Widget parent, String field, String sequence, float time, float scale) {
        NativeSceneUi.Layer layer = NativeSceneUi.childState(parent.name, field, sequence, time, parent.box, scale);
        if (layer != null && layer.image() != null)
            NativeSceneUi.drawLayer(gui, new NativeSceneUi.Layer(layer.image(), layer.rect(),
                    layer.alpha() * parent.alpha, layer.rotation(), layer.scale(), layer.sprite()));
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
            label(gui, child(root, "FluffStats", "", 0, scale),
                    String.format(Locale.ROOT, "X %.0f   Z %.0f", camera.getX(), camera.getZ()), WHITE, scale);
            label(gui, child(root, "SpeedStats", "", 0, scale), nativeText("768e0497ae345ed8") + " "
                    + String.format(Locale.ROOT, "%.1f", TacticalClient.state.speed()) + " m/s", WHITE, scale);
            label(gui, child(root, "FluffStats3", "", 0, scale),
                    nativeText("02092e06e07b92fb") + " " + Math.round(camera.getY()) + " m", WHITE, scale);
            label(gui, child(root, "FluffStats6", "", 0, scale), I18n.get("hud.killstreakcod.heading", heading()),
                    WHITE, scale);
            label(gui, child(root, "FluffStats7", "", 0, scale),
                    I18n.get("hud.killstreakcod.pitch", Math.round(TacticalClient.pitch())), WHITE, scale);
            label(gui, child(root, "FluffStats8", "", 0, scale), nativeText("4ecbf294698936f8"), MUTED, scale);
            Vec3 point = target();
            if (point != null) {
                Widget location = child(root, "GunshipTargetLocationText", "", 0, scale);
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
        image(gui, meter, "Background", "", 0, scale);
        Widget fill = child(meter, "Fill", "", 0, scale);
        float fraction = NativeRemoteHud.fuelFraction();
        var r = fill.box;
        gui.fill(Math.round(r.x()), Math.round(r.y() + r.h() * (1 - fraction)), Math.round(r.x() + r.w()),
                Math.round(r.y() + r.h()), fraction < .2F ? RED : WHITE);
        label(gui, child(meter, "FuelLabel", "", 0, scale), nativeText("19b7afe9740e34e4"), WHITE, scale);
    }

    private static void vision(GuiGraphics gui, Widget root, float scale) {
        Widget vision = child(root, "VisionSystem", "", 0, scale);
        label(gui, vision, TacticalClient.state.thermal() ? nativeText("61cf635aadc8cc43")
                : I18n.get("hud.killstreakcod.camera_optical"), WHITE, scale);
        label(gui, child(vision, "VisionSystemText", "", 0, scale),
                "[" + key(KillstreakKeyMappings.THERMAL) + "] " + nativeText("4c8c0d7aaeb7933f"), WHITE, scale);
    }

    private static void zoom(GuiGraphics gui, Widget root, float scale) {
        Widget zoom = child(root, "ZoomMeter", "", 0, scale);
        image(gui, zoom, "Meter", "", 0, scale);
        // These are the recovered HUD meter limits; actual weapon FOV remains owned by TacticalClient.
        // 仅用恢复的 HUD 刻度上下限映射标尺，实际武器视场仍由遥控模块决定。
        float fraction = Mth.clamp((80 - TacticalClient.fov()) / 78F, 0, 1);
        Widget guide = child(zoom, "MeterGuide", "", 0, scale);
        guide = new Widget(guide.name, new NativeSceneUi.Rect(guide.box.x(),
                zoom.box.y() + (754 - fraction * 512) * scale, guide.box.w(), guide.box.h()), 1);
        image(gui, guide, "ZoomGuide", "", 0, scale);
        label(gui, child(zoom, "CloseRangeMeterLabel", "", 0, scale), "80°", MUTED, scale);
        label(gui, child(zoom, "LongRangeMeterLabel", "", 0, scale), "2°", MUTED, scale);
        label(gui, child(guide, "CurrentZoom", "", 0, scale), Math.round(TacticalClient.fov()) + "°", WHITE, scale);
        label(gui, child(zoom, "MedRangeMeterLabel", "", 0, scale),
                "[" + key(KillstreakKeyMappings.ZOOM) + "] " + nativeText("2cdc95c748efc81b"), WHITE, scale);
    }

    private static void compass(GuiGraphics gui, Widget root, boolean gunship, float scale) {
        if (gunship) {
            Widget compass = child(root, "GunshipFluffCompass", "", 0, scale);
            NativeSceneUi.draw(gui, "overlay_gunship_minicompass_ring", compass.box, .8F);
            NativeSceneUi.drawLayer(gui, new NativeSceneUi.Layer("overlay_gunship_minicompass_needle", compass.box, .9F,
                    heading(), 1, false));
        } else {
            Widget compass = child(root, "DroneCompass", "", 0, scale);
            var box = compass.box;
            NativeSceneUi.draw(gui, "overlay_recon_drone_compass_lines",
                    new NativeSceneUi.Rect(box.x(), box.y(), box.w(), 24 * scale), .8F);
            label(gui, compass, String.format(Locale.ROOT, "%03d°", heading()), WHITE, scale);
        }
    }

    private static void health(GuiGraphics gui, Widget root, boolean gunship, float scale,
            List<RemoteHudLayout.Box> reservations) {
        Widget health = child(root, "VehicleHealthBar", "", 0, scale);
        float size = 179 * scale;
        NativeSceneUi.Rect area = new NativeSceneUi.Rect(health.box.x() + health.box.w() / 2 - size / 2,
                health.box.y() + 40 * scale, size, size);
        area = RemoteHudLayout.avoidFeedback(gui, area);
        String diagram = gunship ? "veh_diagram_gunship_" : "veh_diagram_choppergunner_";
        NativeSceneUi.draw(gui, diagram + "out", area, .8F);
        float fraction = Mth.clamp(TacticalClient.state.health() / 100F, 0, 1);
        gui.enableScissor(Math.round(area.x()), Math.round(area.y() + area.h() * (1 - fraction)),
                Math.round(area.x() + area.w()), Math.round(area.y() + area.h()));
        // Native mask images are opaque stencil inputs; drawing them as RGBA would cover the view with a block.
        // 原 mask 是不透明的模板输入，直接作为 RGBA 贴图绘制会出现整块遮挡；这里用透明辉光显示血量。
        NativeSceneUi.draw(gui, diagram + "glow", area, .5F);
        gui.disableScissor();
        label(gui, new Widget("", new NativeSceneUi.Rect(area.x(), area.y() + area.h(), area.w(), 22 * scale), 1),
                TacticalClient.state.health() + "%", fraction < .35F ? RED : WHITE, scale);
        reserve(reservations, new NativeSceneUi.Rect(area.x(), area.y(), area.w(), area.h() + 25 * scale));
    }

    private static void armament(GuiGraphics gui, Widget root, boolean gunship, float scale,
            List<RemoteHudLayout.Box> reservations) {
        Widget weapons = child(root, gunship ? "WeaponBar" : "Weapons", "", 0, scale);
        var area = weapons.box;
        NativeSceneUi.Rect panel = new NativeSceneUi.Rect(area.x() - 105 * scale, area.y() - 60 * scale,
                Math.max(area.w(), 380 * scale), 195 * scale);
        var safe = RemoteHudLayout.avoidFeedback(gui, panel);
        weapons = new Widget(weapons.name, new NativeSceneUi.Rect(area.x() + safe.x() - panel.x(),
                area.y() + safe.y() - panel.y(), area.w(), area.h()), 1);
        if (gunship) {
            for (int slot = 2; slot >= 0; slot--) {
                Widget name = child(weapons, "Weapon" + CALIBERS[slot], "", 0, scale);
                boolean selected = TacticalClient.state.weapon() == slot;
                if (selected)
                    NativeSceneUi.draw(gui, "overlay_gunship_selectionbox", name.box, .8F);
                label(gui, name, "[" + key(ResearchWeaponHud.key(slot)) + "] " + ResearchWeaponHud.name(slot),
                        selected ? WHITE : MUTED, scale);
                Widget ammo = child(weapons, "Ammo" + CALIBERS[slot], "", 0, scale);
                label(gui, ammo, ResearchWeaponHud.ammo(slot), TacticalClient.reload(slot) > 0 ? AMBER : WHITE, scale);
            }
            float extraY = safe.y() - 28 * scale;
            for (int slot : ResearchWeaponHud.gunshipSlots())
                if (slot >= 3) {
                    label(gui, new Widget("", new NativeSceneUi.Rect(safe.x(), extraY, safe.w(), 26 * scale), 1),
                            "[" + key(ResearchWeaponHud.key(slot)) + "] " + ResearchWeaponHud.name(slot) + " "
                                    + ResearchWeaponHud.ammo(slot),
                            WHITE, scale);
                    extraY -= 28 * scale;
                }
            reserve(reservations, new NativeSceneUi.Rect(safe.x(), extraY, safe.w(), safe.h() + safe.y() - extraY));
        } else {
            var keys = Minecraft.getInstance().options;
            label(gui, child(weapons, "CannonName", "", 0, scale), nativeText("65e537e2780b72d6"), WHITE, scale);
            label(gui, child(weapons, "RocketName", "", 0, scale), nativeText("6fe68a58b2fbbb13"), WHITE, scale);
            label(gui, child(weapons, "CannonKeybind", "", 0, scale), "[" + key(keys.keyAttack) + "]", WHITE, scale);
            label(gui, child(weapons, "RocketKeybind", "", 0, scale), "[" + key(keys.keyUse) + "]", WHITE, scale);
            Widget rounds = child(weapons, "CannonAmmo", "", 0, scale);
            NativeSceneUi.draw(gui, "apache_icon_infinite", rounds.box, 1);
            label(gui, child(weapons, "RocketAmmo", "", 0, scale), ResearchWeaponHud.rocketCount(), WHITE, scale);
            reserve(reservations, safe);
        }
        Widget flares = child(root, gunship ? "FlaresIndicator" : "Flares", "", 0, scale);
        label(gui, flares, "[" + key(KillstreakKeyMappings.FLARES) + "] " + nativeText("435dfa11cb806127") + " "
                + TacticalClient.state.flares(), WHITE, scale);
        reserve(reservations, flares.box);
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

    private static void label(GuiGraphics gui, Widget widget, String value, int color, float scale) {
        var box = widget.box;
        if (box.w() <= 0 || box.h() <= 0 || widget.alpha <= 0)
            return;
        var font = Minecraft.getInstance().font;
        float size = Math.max(5, 24 * scale) / font.lineHeight;
        size = Math.min(size, box.w() / Math.max(1, font.width(value)));
        if (size <= 0)
            return;
        gui.pose().pushPose();
        try {
            gui.pose().translate(box.x(), box.y() + (box.h() - font.lineHeight * size) / 2, 0);
            gui.pose().scale(size, size, 1);
            int alpha = Mth.clamp(Math.round(widget.alpha * 255), 4, 255);
            gui.drawString(font, value, 0, 0, (color & 0xffffff) | alpha << 24, false);
        } finally {
            gui.pose().popPose();
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
