package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Verified per-reward HUD art with live Minecraft telemetry and conditions.
 * 使用已核对的各奖励专属 HUD 图像，结合 Minecraft 实时测量值和状态显示。
 */
public final class NativeRemoteHud {
    private record Sprite(ResourceLocation texture, int width, int height, int x, int y, int w, int h) {
    }

    private record Button(KeyMapping key, String label) {
    }

    private static final Map<String, Sprite> layers = new HashMap<>();
    private static final Map<String, Set<String>> profiles = new HashMap<>(), required = new HashMap<>();
    private static final Map<String, String> cockpit = new HashMap<>();
    private static final int WHITE = 0xffecf0e9, RED = 0xfff07363, AMBER = 0xffe1bb75;
    private static boolean loaded;
    private static int lastId = -1, lastHealth = 100, initialFuel = 1;
    private static long damagedAt = Long.MIN_VALUE;

    private NativeRemoteHud() {}

    public static void reset() {
        lastId = -1;
        lastHealth = 100;
        initialFuel = 1;
        damagedAt = Long.MIN_VALUE;
        NativeChopperScreens.reset();
    }

    public static void reload() {
        loaded = false;
        layers.clear();
        profiles.clear();
        required.clear();
        cockpit.clear();
        reset();
    }

    private static void load() {
        if (loaded)
            return;
        loaded = true;
        var mc = Minecraft.getInstance();
        try (var reader = mc.getResourceManager()
                .getResourceOrThrow(
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/remote-hud-assets.json"))
                .openAsReader()) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.getAsJsonObject("layers").entrySet()) {
                var row = entry.getValue().getAsJsonObject();
                var b = row.getAsJsonArray("bounds");
                var texture = row.has("texture") ? ResourceLocation.parse(row.get("texture").getAsString())
                        : ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                                "textures/gui/mwii/" + row.get("name").getAsString() + ".png");
                int tw = row.get("width").getAsInt(), th = row.get("height").getAsInt(), x = b.get(0).getAsInt(),
                        y = b.get(1).getAsInt(), w = b.get(2).getAsInt() - x, h = b.get(3).getAsInt() - y;
                if (tw > 0 && th > 0 && x >= 0 && y >= 0 && w > 0 && h > 0 && x + w <= tw && y + h <= th
                        && mc.getResourceManager().getResource(texture).isPresent())
                    layers.put(entry.getKey(), new Sprite(texture, tw, th, x, y, w, h));
            }
            for (var entry : root.getAsJsonObject("profiles").entrySet()) {
                Set<String> names = new HashSet<>();
                for (var name : entry.getValue().getAsJsonArray())
                    names.add(name.getAsString());
                profiles.put(entry.getKey(), names);
            }
            if (root.has("required"))
                for (var entry : root.getAsJsonObject("required").entrySet()) {
                    Set<String> names = new HashSet<>();
                    for (var name : entry.getValue().getAsJsonArray())
                        names.add(name.getAsString());
                    required.put(entry.getKey(), names);
                }
            else
                required.putAll(profiles);
            if (root.has("cockpitMaterials"))
                for (var entry : root.getAsJsonObject("cockpitMaterials").entrySet())
                    cockpit.put(entry.getKey(), entry.getValue().getAsString());
        }
        catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Native remote HUD unavailable; using the existing layout",
                    failure);
        }
    }

    private static boolean available(String mode) {
        load();
        Set<String> main = required.get(mode);
        return main != null && !main.isEmpty() && layers.keySet().containsAll(main);
    }

    private static Sprite sprite(String name) {
        return profiles.getOrDefault(TacticalClient.state.mode(), Set.of()).contains(name) ? layers.get(name) : null;
    }

    static ResourceLocation screenTexture(String name) {
        load();
        Sprite s = profiles.getOrDefault("chopper_screens", Set.of()).contains(name) ? layers.get(name) : null;
        return s == null ? null : s.texture;
    }

    static Map<String, String> cockpitMaterials() {
        load();
        return Map.copyOf(cockpit);
    }

    public static boolean render(GuiGraphics graphics) {
        String mode = TacticalClient.state.mode();
        if (!available(mode))
            return false;
        var mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;
        if (lastId != TacticalClient.state.entityId()) {
            lastId = TacticalClient.state.entityId();
            lastHealth = TacticalClient.state.health();
            initialFuel = Math.max(1, TacticalClient.fuel());
            damagedAt = Long.MIN_VALUE;
        }
        if (TacticalClient.state.health() < lastHealth)
            damagedAt = mc.level.getGameTime();
        lastHealth = TacticalClient.state.health();
        try (StateGuard guard = new StateGuard(graphics)) {
            damage(graphics, mode);
            switch (mode) {
                case "bomb_drone" -> drone(graphics);
                case "cruise" -> cruise(graphics);
                case "wheelson" -> wheelson(graphics);
                case "chopper" -> {
                    if (!SceneNativeHud.render(graphics, mode))
                        chopper(graphics);
                }
                case "gunship" -> {
                    if (!SceneNativeHud.render(graphics, mode))
                        gunship(graphics);
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static void drone(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        Sprite optical = layers.get("ui_bomber_drone_overlay"),
                grid = layers.get("overlay_bomb_drone_targeting_squares");

        // 保留此前无人机中心覆盖率小于 2% 的设计，避免视频画面变白。
        draw(graphics, optical, 0, 0, w, h, 0, 0, optical.width, optical.height, WHITE, .35F * effects());
        float fit = Math.min(w * .80F / grid.w, h * .64F / grid.h), gx = w / 2F - (512 - grid.x) * fit,
                gy = h / 2F - (246 - grid.y) * fit;

        // 参考视频 00:40 显示的是四段短瞄准角标，而非固定目标网格；只复用原方框贴图的外侧角落像素。
        // 目标标记仍跟随服务端确认可见的目标，不在中心放置虚假目标。
        float halfW = w * .0725F, halfH = h * .17F, len = Math.max(3, h * .014F), thickness = Math.max(.6F, h / 1080F);
        for (int sx : new int[] { -1, 1 })
            for (int sy : new int[] { -1, 1 }) {
                float x = w / 2F + sx * halfW, y = h / 2F + sy * halfH;
                draw(graphics, grid, sx < 0 ? x : x - len, y, len, thickness, 499, 233, 6, 1, WHITE, .9F);
                draw(graphics, grid, x, sy < 0 ? y : y - len, thickness, len, 499, 233, 1, 6, WHITE, .9F);
            }
        boolean warning = TacticalClient.fuel() < 200 || TacticalClient.state.health() < 35;
        if (warning) {
            Sprite outline = layers.get("overlay_bomb_drone_red_outline"),
                    icon = layers.get("overlay_bomb_drone_warning_icon");
            float pulse = .45F + (float) Math.sin(mc.level.getGameTime() * .35) * .15F;
            draw(graphics, outline, gx - 5, gy - 5, grid.w * fit + 10, grid.h * fit + 10, outline.x, outline.y,
                    outline.w, outline.h, WHITE, pulse);
            draw(graphics, icon, w / 2F - 72, h - 84, 18, 18, icon.x, icon.y, icon.w, icon.h, RED, 1);
            text(graphics,
                    Component.translatable(TacticalClient.fuel() < 200 ? "hud.killstreakcod.native_drone_low_battery"
                            : "hud.killstreakcod.native_drone_damaged").getString(),
                    w / 2 - 48, h - 79, RED);
        }
        text(graphics, StreakReward.BOMB_DRONE.displayName().getString(), 16, 30, WHITE);
        text(graphics, TacticalClient.state.thermal() ? "IR / WHOT" : "EO / TV", w - 85, 30, WHITE);
        text(graphics, "ALT " + altitude() + " M", 16, 49, WHITE);
        text(graphics, "VEL " + speed() + " M/S", 16, 62, WHITE);
        text(graphics, "HULL " + TacticalClient.state.health() + "%", w - 85, 49, WHITE);
        text(graphics, "BAT " + TacticalClient.fuel() / 20 + "s", w - 85, 62, warning ? RED : WHITE);
        OriginalRewardUi.icon(graphics, "bomb_drone", 29, h - 66, 25, WHITE, 1);
        text(graphics, "C4 ARMED", 49, h - 70, WHITE);
        var o = mc.options;
        Component hint = Component.translatable("hud.killstreakcod.native_drone_controls", key(o.keyAttack), movement(),
                key(o.keyJump), key(o.keyShift), key(KillstreakKeyMappings.THERMAL), key(KillstreakKeyMappings.ZOOM),
                key(KillstreakKeyMappings.EXIT));
        graphics.fill(0, h - 44, w, h, 0xb80c1512);
        graphics.drawWordWrap(mc.font, hint, 12, h - 35, w - 24, WHITE);
    }

    private static void cruise(GuiGraphics graphics) {
        if (NativeCruiseHud.render(graphics)) {
            footer(graphics,
                    new Button[] { button(KillstreakKeyMappings.BOOST, "boost"),
                            button(Minecraft.getInstance().options.keyAttack, "detonate"),
                            button(KillstreakKeyMappings.EXIT, "exit") });
            warning(graphics);
            return;
        }
        var o = Minecraft.getInstance().options;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        full(graphics, "ui_tanto_logo", 12, 10, 55, 12, WHITE, .9F);
        text(graphics, StreakReward.CRUISE_MISSILE.displayName().getString() + " / GUIDANCE", 12, 28, WHITE);
        text(graphics, "T-" + TacticalClient.fuel() / 20 + "s", w - 68, 12, WHITE);
        float side = Math.min(h * .52F, w * .43F);
        centred(graphics, "overlay_cruise_reticle", side, WHITE, 1);
        centred(graphics, "overlay_cruise_cross", side * .24F, WHITE, .9F);
        if (TacticalClient.state.speed() > 32)
            centred(graphics, "hud_reticle_radial_glow", side * .25F, WHITE, .32F * effects());
        text(graphics, "ALT " + altitude() + " M", 12, 49, WHITE);
        text(graphics, "VEL " + speed() + " M/S", 12, 62, WHITE);
        text(graphics, "PITCH " + (int) TacticalClient.pitch() + "°", w - 95, 49, WHITE);
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null) {
            Vec3 from = camera.getEyePosition(),
                    to = from.add(Vec3.directionFromRotation(TacticalClient.pitch(), TacticalClient.yaw()).scale(256));
            var hit = Minecraft.getInstance().level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, camera));
            text(graphics, "RNG " + (int) from.distanceTo(hit.getLocation()) + " M", w - 95, 62, WHITE);
        }
        footer(graphics, new Button[] { button(KillstreakKeyMappings.BOOST, "boost"), button(o.keyAttack, "detonate"),
                button(KillstreakKeyMappings.EXIT, "exit") });
        warning(graphics);
    }

    private static void wheelson(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        var o = mc.options;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        text(graphics, StreakReward.WHEELSON.displayName().getString() + " / RWS", 12, 12, WHITE);
        text(graphics, TacticalClient.state.thermal() ? "IR / WHOT" : "EO / TV", w - 88, 12, WHITE);
        centred(graphics, "mp_wheelson_reticle_new", Math.min(h * .48F, w * .43F), WHITE, 1);
        glow(graphics);
        text(graphics, "VEL " + speed() + " M/S", 12, 29, WHITE);
        text(graphics, "AZ " + heading() + "°", 12, 42, WHITE);
        int fy = footerTop(graphics, 8);
        float side = Math.min(68, h * .24F), x = 12, y = fy - side - 25;
        full(graphics, "overlay_wheelson_diagram_body", x, y, side, side, WHITE, .85F);
        Entity camera = mc.getCameraEntity();
        float chassis = camera instanceof TacticalUnitEntity u ? u.getYRot() : TacticalClient.yaw();
        rotated(graphics, "overlay_wheelson_diagram_turret", x + side / 2, y + side / 2, side,
                Mth.wrapDegrees(TacticalClient.yaw() - chassis), WHITE, 1);
        text(graphics, "HULL " + TacticalClient.state.health() + "%", (int) (x + side + 4), (int) (y + 12),
                healthTint());
        full(graphics, "mp_wheelson_icon_battery", x + side + 3, y + 29, 10, 10, WHITE, 1);
        text(graphics, TacticalClient.fuel() / 20 + "s", (int) (x + side + 18), (int) (y + 30), WHITE);
        full(graphics, "mp_wheelson_icon_infinite", w - 46, fy - 39, 24, 24, WHITE, .9F);
        text(graphics, "AMMO", w - 52, fy - 16, WHITE);
        int sy = fy - 7;
        graphics.hLine(12, 100, sy, WHITE);
        float speedFraction = Mth.clamp(TacticalClient.state.speed() / 5.6F, 0, 1);
        full(graphics, "mp_wheelson_speed_bar_arrow", 9 + speedFraction * 86, sy - 10, 10, 10, WHITE, 1);
        zoom(graphics);
        if (camera != null && camera.isInWaterOrBubble())
            full(graphics, "icon_submerged", w / 2F - 10, 42, 20, 20, AMBER, 1);
        footer(graphics,
                new Button[] { button(o.keyUp, "forward"), button(o.keyDown, "reverse"), button(o.keyLeft, "left"),
                        button(o.keyRight, "right"), button(o.keyAttack, "fire"),
                        button(KillstreakKeyMappings.AUTO, "sentry"), button(KillstreakKeyMappings.ZOOM, "zoom"),
                        button(KillstreakKeyMappings.EXIT, "exit") });
        warning(graphics);
    }

    private static void chopper(GuiGraphics graphics) {
        var o = Minecraft.getInstance().options;
        int w = graphics.guiWidth();
        full(graphics, "ui_tanto_logo", 12, 10, 55, 12, WHITE, .9F);
        text(graphics, StreakReward.CHOPPER_GUNNER.displayName().getString(), 12, 28, WHITE);
        text(graphics, TacticalClient.state.thermal() ? "WHOT / IR" : "DAY / TV", w - 92, 12, WHITE);
        centred(graphics, "overlay_gunship_25mm_reticle", reticleSize(graphics), WHITE, 1);
        glow(graphics);
        zoom(graphics);
        fuel(graphics);
        text(graphics, "ALT " + altitude() + " M", 12, 47, WHITE);
        text(graphics, "VEL " + speed() + " M/S", 12, 60, WHITE);
        int fy = footerTop(graphics, 8);
        diagram(graphics, "choppergunner", w - 102, fy - 104, 58);
        text(graphics, "HULL " + TacticalClient.state.health() + "%", w - 106, fy - 36, healthTint());
        full(graphics, "apache_icon_infinite", 17, fy - 70, 23, 15, WHITE, 1);
        text(graphics, "30 MM", 45, fy - 66, WHITE);
        full(graphics, "overlay_gunship_selectionbox", 12, fy - 45, 142, 18, WHITE, .8F);
        text(graphics, "ROCKETS " + TacticalClient.state.rockets() + " / 8", 18, fy - 40,
                TacticalClient.rocketCooldown() > 0 ? AMBER : WHITE);
        text(graphics, TacticalClient.rocketCooldown() > 0 ? "RELOAD " + seconds(TacticalClient.rocketCooldown())
                : "FLARES " + TacticalClient.state.flares(), 18, fy - 22, WHITE);
        full(graphics, "overlay_gunship_satellite_icon", w - 130, 31, 22, 11, WHITE, .8F);
        text(graphics, "LINK", w - 105, 33, WHITE);
        footer(graphics,
                new Button[] { button(o.keyUp, "move"), button(o.keyJump, "up"), button(o.keyShift, "down"),
                        button(o.keyAttack, "cannon"), button(o.keyUse, "rockets"),
                        button(KillstreakKeyMappings.FLARES, "flares"),
                        button(KillstreakKeyMappings.THERMAL, "thermal"), button(KillstreakKeyMappings.EXIT, "exit") });
        text(graphics, movement() + " / " + key(KillstreakKeyMappings.ZOOM) + " " + label("zoom"), 12, 75, WHITE);
        warning(graphics);
    }

    private static void gunship(GuiGraphics graphics) {
        var o = Minecraft.getInstance().options;
        int w = graphics.guiWidth(), h = graphics.guiHeight(), weapon = Mth.clamp(TacticalClient.state.weapon(), 0, 2);
        full(graphics, "overlay_gunship_vignette_left", 0, 0, w * .14F, h, WHITE, .35F * effects());
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(w, 0, 0);
            graphics.pose().scale(-1, 1, 1);
            full(graphics, "overlay_gunship_vignette_left", 0, 0, w * .14F, h, WHITE, .35F * effects());
        } finally {
            graphics.pose().popPose();
        }
        text(graphics, StreakReward.GUNSHIP.displayName().getString() + " / FIRE CONTROL", 12, 12, WHITE);
        text(graphics, TacticalClient.state.thermal() ? "WHOT / IR" : "DAY / TV", w - 92, 12, WHITE);
        String[] reticles = { "overlay_gunship_25mm_reticle", "overlay_gunship_40mm_reticle",
                "overlay_gunship_105mm_reticle" };
        boolean loading = TacticalClient.reload(weapon) > 0;
        centred(graphics, loading ? "overlay_gunship_inactive_reticle" : reticles[weapon], reticleSize(graphics),
                loading ? AMBER : WHITE, loading ? .75F : 1);
        glow(graphics);
        zoom(graphics);
        fuel(graphics);
        int fy = footerTop(graphics, 5);
        String[] labels = { "25 MM", "40 MM", "105 MM" };
        KeyMapping[] keys = { KillstreakKeyMappings.WEAPON_1, KillstreakKeyMappings.WEAPON_2,
                KillstreakKeyMappings.WEAPON_3 };
        int[] capacities = { 25, 5, 1 };
        for (int i = 0; i < 3; i++) {
            int yy = fy - 66 + i * 19, cc = i == weapon ? WHITE : 0xff919d95, reload = TacticalClient.reload(i);
            if (i == weapon)
                full(graphics, "overlay_gunship_selectionbox", 10, yy - 3, 154, 19, WHITE, 1);
            text(graphics, "[" + key(keys[i]) + "] " + labels[i], 16, yy, cc);
            text(graphics,
                    reload > 0 ? "LOAD"
                            : String.format(Locale.ROOT, "%02d/%02d", TacticalClient.state.ammo()[i], capacities[i]),
                    103, yy, reload > 0 ? AMBER : cc);
        }
        full(graphics, "apache_icon_infinite", 12, fy - 89, 20, 15, WHITE, .8F);
        text(graphics, "RESERVE", 37, fy - 84, WHITE);
        diagram(graphics, "gunship", w - 127, fy - 116, 65);
        text(graphics, "HULL " + TacticalClient.state.health() + "%", w - 128, fy - 43, healthTint());
        full(graphics, "overlay_gunship_minicompass_ring", w - 70, 43, 36, 36, WHITE, .8F);
        rotated(graphics, "overlay_gunship_minicompass_needle", w - 52, 61, 36, -heading(), WHITE, 1);
        text(graphics, heading() + "°", w - 66, 84, WHITE);
        full(graphics, "overlay_gunship_satellite_icon", 12, 30, 22, 11, WHITE, .8F);
        text(graphics, "LINK / ALT " + altitude() + " M", 39, 32, WHITE);
        Entity camera = Minecraft.getInstance().getCameraEntity();
        float arc = camera instanceof TacticalUnitEntity unit
                ? Math.abs(
                        Mth.wrapDegrees(TacticalClient.yaw() - GunshipFlight.mountYaw(unit.renderFlightYaw(partial()))))
                : 0;
        boolean limited = arc > GunshipFlight.YAW_LIMIT - 2
                || Math.abs(TacticalClient.pitch()) > GunshipFlight.PITCH_LIMIT - 1;
        text(graphics,
                limited ? "GIMBAL LIMIT"
                        : "AZ ±" + (int) GunshipFlight.YAW_LIMIT + "° / EL ±" + (int) GunshipFlight.PITCH_LIMIT + "°",
                w - 128, fy - 28, limited ? AMBER : WHITE);
        text(graphics, loading ? "RELOAD " + seconds(TacticalClient.reload(weapon)) : "ORBIT AUTO", w - 128, fy - 13,
                loading ? AMBER : WHITE);
        footer(graphics,
                new Button[] { button(o.keyAttack, "fire"), button(KillstreakKeyMappings.CYCLE, "weapon"),
                        button(KillstreakKeyMappings.THERMAL, "thermal"), button(KillstreakKeyMappings.ZOOM, "zoom"),
                        button(KillstreakKeyMappings.EXIT, "exit") });
        warning(graphics);
    }

    private static float reticleSize(GuiGraphics graphics) {
        return Math.max(48,
                Math.min(graphics.guiHeight() * .58F, (graphics.guiHeight() - footerHeight(graphics, 8) - 45) * .9F));
    }

    private static void diagram(GuiGraphics graphics, String kind, int x, int y, int size) {
        full(graphics, "veh_diagram_" + kind + "_out", x, y, size, size, WHITE, .8F);
        Sprite glow = sprite("veh_diagram_" + kind + "_glow");
        if (glow == null)
            return;

        // 原辉光图像已有正确的透明轮廓，不要直接绘制不透明 RGB 遮罩。
        float amount = Mth.clamp(TacticalClient.state.health() / 100F, 0, 1);
        int top = (int) (glow.height * (1 - amount));
        draw(graphics, glow, x, y + size * top / (float) glow.height, size,
                size * (glow.height - top) / (float) glow.height, 0, top, glow.width, glow.height - top, healthTint(),
                .65F);
    }

    private static void fuel(GuiGraphics graphics) {
        int w = graphics.guiWidth();
        float height = Math.max(35, Math.min(100, graphics.guiHeight() * .35F));
        float value = Mth.clamp(TacticalClient.fuel() / (float) initialFuel, 0, 1);
        graphics.fill(w - 17, 52, w - 14, 52 + (int) height, 0x70212623);
        graphics.fill(w - 17, 52 + (int) (height * (1 - value)), w - 14, 52 + (int) height, value < .2 ? RED : WHITE);
        full(graphics, "overlay_gunship_fuelbar_frame", w - 20, 49, 9, height + 6, WHITE, .9F);
        text(graphics, TacticalClient.fuel() / 20 + "s", w - 36, 39, value < .2 ? RED : WHITE);
    }

    private static void zoom(GuiGraphics graphics) {
        String mode = TacticalClient.state.mode();
        float base = mode.equals("gunship") ? 50 : mode.equals("chopper") ? 65 : 75;
        float fraction = Mth.clamp((base - TacticalClient.fov()) / (base * .45F), 0, 1),
                height = Math.max(36, Math.min(110, graphics.guiHeight() * .4F));
        full(graphics, "overlay_gunship_zoomnotches", graphics.guiWidth() - 33, 53, 7, height, WHITE, .8F);
        full(graphics, "overlay_gunship_zoomguide", graphics.guiWidth() - 45, 50 + height * (1 - fraction), 9, 18,
                WHITE, 1);
    }

    private static void damage(GuiGraphics graphics, String mode) {
        var mc = Minecraft.getInstance();
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float time = damagedAt == Long.MIN_VALUE ? 100 : mc.level.getGameTime() + partial() - damagedAt,
                pulse = Mth.clamp(1 - time / 8, 0, 1) * effects();
        if (mode.equals("wheelson") && lastHealth < 70)
            full(graphics, "overlay_cruise_vignette_segment", 0, 0, w, h, WHITE,
                    (100 - lastHealth) / 100F * .25F * effects());
        if ((mode.equals("chopper") || mode.equals("gunship")) && lastHealth < 70) {
            int crack = lastHealth > 50 ? 1 : lastHealth > 30 ? 2 : lastHealth > 15 ? 3 : 4;
            full(graphics, "overlay_gunship_damagecrack_" + crack, 0, 0, w, h, WHITE, .26F * effects());
        }
        if (pulse > 0) {
            full(graphics, "overlay_cruise_flash", 0, 0, w, h, RED, pulse * .20F);
            full(graphics, "overlay_gunship_damageglitch", 0, 0, w, h, WHITE, pulse * .24F);
        }
    }

    private static void warning(GuiGraphics graphics) {
        if (TacticalClient.fuel() < Math.min(200, initialFuel / 5))
            graphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.translatable("hud.killstreakcod.native_remote_low_fuel"), graphics.guiWidth() / 2, 35,
                    RED);
    }

    private static void glow(GuiGraphics graphics) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera instanceof TacticalUnitEntity unit && unit.firing())
            centred(graphics, "hud_reticle_radial_glow", 22, WHITE, .35F * effects());
    }

    public static boolean contact(GuiGraphics graphics, int x, int y, int tint) {
        String mode = TacticalClient.state.mode();
        if (!available(mode))
            return false;
        if (!mode.equals("bomb_drone") && !mode.equals("cruise"))
            return false;
        try (StateGuard guard = new StateGuard(graphics)) {
            if (mode.equals("bomb_drone")) {
                Sprite grid = layers.get("overlay_bomb_drone_targeting_squares");
                draw(graphics, grid, x - 10, y - 10, 20, 20, 499, 233, 25, 25, tint, 1);
            }

            // 原 64 像素图像内有九个独立的 10 像素单元；每个目标只使用中央单元，避免一个实体周围出现九个标记。
            else {
                Sprite boxes = sprite("overlay_cruise_squares");
                draw(graphics, boxes, x - 7, y - 7, 14, 14, 27, 27, 10, 10, tint, 1);
            }
        }
        return true;
    }

    private static int heading() {
        return Math.floorMod((int) TacticalClient.yaw() + 180, 360);
    }

    private static int altitude() {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera == null ? 0 : (int) camera.getY();
    }

    private static String speed() {
        return String.format(Locale.ROOT, "%.1f", TacticalClient.state.speed());
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20F);
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    private static float effects() {
        return Minecraft.getInstance().options.screenEffectScale().get().floatValue();
    }

    private static int healthTint() {
        return lastHealth < 30 ? RED : lastHealth < 70 ? AMBER : WHITE;
    }

    private static String key(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static String movement() {
        var o = Minecraft.getInstance().options;
        return key(o.keyUp) + "/" + key(o.keyLeft) + "/" + key(o.keyDown) + "/" + key(o.keyRight);
    }

    private static String label(String label) {
        return Component.translatable("hud.killstreakcod.native_action_" + label).getString();
    }

    private static Button button(KeyMapping key, String label) {
        return new Button(key, label);
    }

    private static int columns(GuiGraphics graphics, int n) {
        return Math.min(n, Math.max(2, (graphics.guiWidth() - 24) / 95));
    }

    private static int footerHeight(GuiGraphics graphics, int n) {
        return ((n + columns(graphics, n) - 1) / columns(graphics, n)) * 18 + 10;
    }

    private static int footerTop(GuiGraphics graphics, int n) {
        return graphics.guiHeight() - footerHeight(graphics, n);
    }

    private static void footer(GuiGraphics graphics, Button[] buttons) {
        int columns = columns(graphics, buttons.length), width = (graphics.guiWidth() - 24) / columns,
                top = footerTop(graphics, buttons.length);
        for (int i = 0; i < buttons.length; i++) {
            int x = 12 + (i % columns) * width, y = top + (i / columns) * 18;
            Button b = buttons[i];
            String key = key(b.key);
            full(graphics, key.equals("?") ? "ui_keybind_backing_unbound" : "ui_keybind_backing", x, y - 2, 16, 16,
                    WHITE, .85F);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x + 8, y + 2, 0);
                graphics.pose().scale(.7F, .7F, 1);
                graphics.drawCenteredString(Minecraft.getInstance().font, key, 0, 0, WHITE);
            } finally {
                graphics.pose().popPose();
            }
            text(graphics, Minecraft.getInstance().font.plainSubstrByWidth(label(b.label), Math.max(12, width - 24)),
                    x + 21, y + 1, WHITE);
        }
    }

    private static void text(GuiGraphics graphics, String text, int x, int y, int tint) {
        graphics.drawString(Minecraft.getInstance().font, text, x, y, tint, false);
    }

    private static void centred(GuiGraphics graphics, String name, float side, int tint, float alpha) {
        full(graphics, name, (graphics.guiWidth() - side) / 2, (graphics.guiHeight() - side) / 2, side, side, tint,
                alpha);
    }

    private static void full(GuiGraphics graphics, String name, float x, float y, float w, float h, int tint,
            float alpha) {
        Sprite s = sprite(name);
        if (s != null)
            draw(graphics, s, x, y, w, h, 0, 0, s.width, s.height, tint, alpha);
    }

    private static void rotated(GuiGraphics graphics, String name, float x, float y, float side, float degrees,
            int tint, float alpha) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
            full(graphics, name, -side / 2, -side / 2, side, side, tint, alpha);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void draw(GuiGraphics graphics, Sprite s, float x, float y, float width, float height, int u, int v,
            int sw, int sh, int tint, float alpha) {
        if (s == null || alpha <= 0 || width <= 0 || height <= 0 || sw <= 0 || sh <= 0)
            return;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F,
                Mth.clamp(alpha, 0, 1));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / sw, height / sh, 1);
        try {
            graphics.blit(s.texture, 0, 0, sw, sh, u, v, sw, sh, s.width, s.height);
        } finally {
            graphics.pose().popPose();
            graphics.setColor(1, 1, 1, 1);
        }
    }

    /**
     * One state capture per overlay instead of synchronous GL queries for every sprite.
     * 每层覆盖界面只保存一次渲染状态，避免为每个精灵执行同步 OpenGL 查询。
     */
    private static final class StateGuard implements AutoCloseable {
        final GuiGraphics g;
        final boolean blending;
        final float[] color;
        final int[] factors;

        StateGuard(GuiGraphics g) {
            this.g = g;
            g.flush();
            blending = GL11.glIsEnabled(GL11.GL_BLEND);
            color = RenderSystem.getShaderColor().clone();
            factors = new int[] { GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) };
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1, 1, 1, 1);
        }

        @Override
        public void close() {
            g.flush();
            g.setColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
            if (blending)
                RenderSystem.enableBlend();
            else
                RenderSystem.disableBlend();
        }
    }
}
