package net.killstreakcod.render;

import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Native aiming images with side-mounted instruments and space for GD656 and radio captions.
 * 使用原瞄准图像及侧边仪表，为 GD656 和无线电字幕预留空间。
 */
public final class SceneNativeHud {
    private static final int WHITE = 0xffecf0e9, AMBER = 0xffe1bb75, RED = 0xfff07363;

    private SceneNativeHud() {}

    public static int panelWidth(int width) {
        return Math.max(66, Math.min(110, width / 5));
    }

    static boolean render(GuiGraphics graphics, String mode) {
        if (AircraftHudRenderer.render(graphics, mode))
            return true;
        String widget = mode.equals("chopper") ? "ApacheControls" : mode.equals("gunship") ? "GunshipControls" : "";
        if (widget.isEmpty() || NativeSceneUi.widget(widget) == null)
            return false;
        int w = graphics.guiWidth(), h = graphics.guiHeight(), pw = panelWidth(w), right = w - pw - 8,
                selected = TacticalClient.state.weapon(), weapon = Mth.clamp(selected, 0, 2);
        boolean reload = mode.equals("gunship") && selected < 3 && TacticalClient.reload(weapon) > 0;
        String field = mode.equals("chopper") ? "Reticle25mm"
                : reload ? "ReticleInactive" : new String[] { "Reticle25mm", "Reticle40mm", "Reticle105mm" }[weapon];
        String image = reload ? "overlay_gunship_inactive_reticle"
                : new String[] { "overlay_gunship_25mm_reticle", "overlay_gunship_40mm_reticle",
                        "overlay_gunship_105mm_reticle" }[mode.equals("chopper") ? 0 : weapon];
        NativeSceneUi.draw(graphics, image, NativeSceneUi.bounds(widget, field, w, h), reload ? .75F : 1);
        text(graphics, 8, 8, pw,
                (mode.equals("chopper") ? StreakReward.CHOPPER_GUNNER : StreakReward.GUNSHIP).displayName().getString(),
                WHITE);
        text(graphics, 8, 21, pw, I18n.get("hud.killstreakcod.altitude", altitude()), WHITE);
        text(graphics, 8, 32, pw, I18n.get("hud.killstreakcod.velocity",
                String.format(Locale.ROOT, "%.1f", TacticalClient.state.speed())), WHITE);
        text(graphics, 8, 43, pw, (TacticalClient.state.thermal() ? I18n.get("hud.killstreakcod.camera_thermal")
                : I18n.get("hud.killstreakcod.camera_optical")) + " " + (int) TacticalClient.fov(), WHITE);
        int footer = RemoteHudLayout.footerHeight(graphics), usable = Math.max(42, h - footer - 74),
                barHeight = Math.min(86, usable), diagramHeight = Math.min(86, usable);
        var fuel = new NativeSceneUi.Rect(10, 64, 12, barHeight);
        NativeSceneUi.draw(graphics, "overlay_gunship_fuelbar_frame", fuel, 1);
        float fraction = NativeRemoteHud.fuelFraction();
        int top = Math.round(fuel.y() + 2 + (fuel.h() - 4) * (1 - fraction));
        graphics.fill(12, top, 20, Math.round(fuel.y() + fuel.h() - 2), fraction < .2 ? RED : WHITE);
        NativeSceneUi.draw(graphics,
                mode.equals("gunship") ? "veh_diagram_gunship_out" : "veh_diagram_choppergunner_out",
                new NativeSceneUi.Rect(28, 64, Math.min(43, pw - 28), diagramHeight), .68F);
        text(graphics, right, 8, pw, I18n.get("hud.killstreakcod.fuel", TacticalClient.fuel() / 20),
                fraction < .2 ? RED : WHITE);
        text(graphics, right, 21, pw, I18n.get("hud.killstreakcod.hull", TacticalClient.state.health()),
                TacticalClient.state.health() < 35 ? RED : WHITE);
        text(graphics, right, 32, pw, I18n.get("hud.killstreakcod.heading", heading()), WHITE);
        NativeSceneUi.draw(graphics, "overlay_gunship_satellite_icon", new NativeSceneUi.Rect(w / 2F - 9, 8, 18, 18),
                .75F);
        int armamentTop = Math.max(62, Math.round(h * .42F));
        if (mode.equals("gunship")) {
            int[] slots = ResearchWeaponHud.gunshipSlots();
            int rowHeight = Math.clamp((h - footer - armamentTop - 8) / slots.length, 10, 23);
            for (int row = 0; row < slots.length; row++) {
                int i = slots[row];
                String info = "[" + key(ResearchWeaponHud.key(i)) + "] " + ResearchWeaponHud.name(i) + "  "
                        + ResearchWeaponHud.ammo(i);
                text(graphics, right, armamentTop + row * rowHeight, pw, info,
                        i == selected ? reload ? AMBER : WHITE : 0xff78877b);
                if (TacticalClient.reload(i) > 0 && rowHeight >= 20)
                    text(graphics, right, armamentTop + row * rowHeight + 10, pw,
                            I18n.get("hud.killstreakcod.reload_time", (int) Math.ceil(TacticalClient.reload(i) / 20D)),
                            AMBER);
            }
            var camera = Minecraft.getInstance().getCameraEntity();
            boolean limited = camera instanceof TacticalUnitEntity airframe
                    && Math.abs(Mth.wrapDegrees(TacticalClient.yaw()
                            - GunshipFlight.mountYaw(airframe.renderFlightYaw(0)))) > GunshipFlight.YAW_LIMIT - 2
                    || Math.abs(TacticalClient.pitch()) > GunshipFlight.PITCH_LIMIT - 1;
            if (limited)
                text(graphics, 8, 54, pw, I18n.get("hud.killstreakcod.gimbal_limit"), AMBER);
        } else {
            text(graphics, right, armamentTop, pw, I18n.get("hud.killstreakcod.cannon_30mm"), WHITE);
            text(graphics, right, armamentTop + 13, pw, ResearchWeaponHud.rocketCount(), WHITE);
            text(graphics, right, armamentTop + 26, pw,
                    TacticalClient.rocketCooldown() > 0
                            ? I18n.get("hud.killstreakcod.reload_time",
                                    (int) Math.ceil(TacticalClient.rocketCooldown() / 20D))
                            : I18n.get("hud.killstreakcod.flares_count", TacticalClient.state.flares()),
                    WHITE);
        }
        if (TacticalClient.fuel() < 200 && System.currentTimeMillis() % 800 < 400)
            text(graphics, 8, 54, pw, Component.translatable("hud.killstreakcod.native_remote_low_fuel").getString(),
                    RED);
        var font = Minecraft.getInstance().font;
        var lines = font.split(Component.literal(controls()), Math.max(80, w - 16));
        int y = h - lines.size() * font.lineHeight - 4;
        graphics.fill(0, h - footer, w, h, 0x880c1512);
        for (var line : lines) {
            graphics.drawString(font, line, (w - font.width(line)) / 2, y, WHITE, false);
            y += font.lineHeight;
        }
        return true;
    }

    static String controls() {
        var o = Minecraft.getInstance().options;
        String controls = key(ResearchWeaponHud.fireKey()) + " "
                + Component.translatable("hud.killstreakcod.native_action_fire").getString() + "   "
                + key(KillstreakKeyMappings.THERMAL) + " "
                + Component.translatable("hud.killstreakcod.native_action_thermal").getString() + "   "
                + key(KillstreakKeyMappings.ZOOM) + " "
                + Component.translatable("hud.killstreakcod.native_action_zoom").getString() + "   "
                + key(KillstreakKeyMappings.EXIT) + " "
                + Component.translatable("hud.killstreakcod.native_action_exit").getString();
        if (TacticalClient.state.mode().equals("gunship"))
            controls += "   " + key(KillstreakKeyMappings.WEAPON_1) + "/" + key(KillstreakKeyMappings.WEAPON_2) + "/"
                    + key(KillstreakKeyMappings.WEAPON_3) + " "
                    + Component.translatable("hud.killstreakcod.native_action_weapon").getString();
        else
            controls += "   " + key(o.keyUse) + " "
                    + Component.translatable("hud.killstreakcod.native_action_rockets").getString() + "   "
                    + key(o.keyUp) + "/" + key(o.keyLeft) + "/" + key(o.keyDown) + "/" + key(o.keyRight) + " / "
                    + key(o.keyJump) + " / " + key(o.keyShift);
        return controls;
    }

    private static void text(GuiGraphics graphics, int x, int y, int width, String text, int color) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.plainSubstrByWidth(text, width), x, y, color, false);
    }

    private static int altitude() {
        var camera = Minecraft.getInstance().getCameraEntity();
        return camera == null ? 0 : (int) camera.getY();
    }

    private static int heading() {
        return Math.floorMod(Math.round(TacticalClient.yaw()) + 180, 360);
    }

    private static String key(net.minecraft.client.KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }
}
