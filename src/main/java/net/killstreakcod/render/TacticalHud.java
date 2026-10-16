package net.killstreakcod.render;

import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.StreakEarnedPayload;
import net.killstreakcod.universal.TacticalVisibility;

import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Five independent instrument layouts with shared low level drawing helpers.
 * 五套独立仪表布局共用底层绘制辅助方法。
 */
public final class TacticalHud {
    private static final int GREEN = 0xffb9e29a, WHITE = 0xffecf0e9, CYAN = 0xffb2e2e4, RED = 0xfff07363;
    private static int terrainTicks;
    private static ResourceLocation radarMap;
    private static DynamicTexture radarTexture;
    private static int terrainX, terrainZ;

    private TacticalHud() {}

    public static void earned(StreakEarnedPayload p) {
        StreakNoticeRenderer.earned(p);
    }

    public static void reset() {
        StreakNoticeRenderer.reset();
        NativeRemoteHud.reset();
        if (radarMap != null)
            Minecraft.getInstance().getTextureManager().release(radarMap);
        radarMap = null;
        radarTexture = null;
    }

    public static void tick() {
        StreakNoticeRenderer.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && (TacticalClient.radar.active() || TacticalClient.radar.jammed())
                && !TacticalClient.remote()
                && (++terrainTicks >= 40 || radarTexture == null || Math.abs(mc.player.getBlockX() - terrainX) > 8
                        || Math.abs(mc.player.getBlockZ() - terrainZ) > 8)) {
            terrainTicks = 0;
            terrainX = mc.player.getBlockX();
            terrainZ = mc.player.getBlockZ();
            updateTerrain();
        }
    }

    private static void updateTerrain() {
        Minecraft mc = Minecraft.getInstance();
        if (radarTexture == null) {
            radarTexture = new DynamicTexture(new NativeImage(64, 64, false));
            radarTexture.setFilter(false, false);
            radarMap = mc.getTextureManager().register("killstreak_radar", radarTexture);
        }
        NativeImage image = radarTexture.getPixels();
        if (image == null)
            return;
        for (int z = 0; z < 64; z++)
            for (int x = 0; x < 64; x++) {
                int bx = terrainX + (x - 32) * 4, bz = terrainZ + (z - 32) * 4;
                var at = new net.minecraft.core.BlockPos(bx, 0, bz);
                int color = 0x23302b;
                if (mc.level.hasChunkAt(at)) {
                    int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
                    at = new net.minecraft.core.BlockPos(bx, y, bz);
                    color = mc.level.getBlockState(at).getMapColor(mc.level, at).col;
                }
                image.setPixelRGBA(x, z, rgba(color));
            }
        radarTexture.upload();
    }

    static int rgba(int color) {
        return 0xff000000 | ((color & 255) << 16) | (color & 0xff00) | ((color >> 16) & 255);
    }

    public static void banner(GuiGraphics graphics) {
        StreakNoticeRenderer.render(graphics);
    }

    public static void normal(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        var t = TacticalClient.terminal;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        String[] bonus = TacticalClient.bonus.queue().isEmpty() ? new String[0]
                : TacticalClient.bonus.queue().split(",");
        int mgb = t != null ? t.mgbCount() : TacticalClient.bonus.mgb() ? 1 : 0;
        int base = Math.max(8, (h - ((3 + (bonus.length > 0 ? 1 : 0) + (mgb > 0 ? 1 : 0)) * 29 - 2)) / 2);
        int extras = (bonus.length > 0 ? 1 : 0) + (mgb > 0 ? 1 : 0);
        if (t != null) {
            String[] ids = { t.slot1(), t.slot2(), t.slot3() };
            for (int i = 0; i < 3; i++) {
                int x = w - 130, y = base + (extras + i) * 29, n = t.counts()[i];
                graphics.fill(x, y, w - 6, y + 27, 0xd00e1513);
                TacticalClient.icon(graphics, ids[i], x + 3, y + 2, 23);
                int c = n > 0 ? GREEN : 0xffa9b1a6;
                String name = Component.translatable("streak.killstreakcod." + ids[i]).getString();
                graphics.drawString(mc.font, mc.font.plainSubstrByWidth(name, 90), x + 30, y + 3, c, false);
                var reward = StreakReward.byId(ids[i]).orElse(StreakReward.UAV);
                int threshold = t.scoreMode() ? reward.score() : reward.kills();
                String button = key(i == 0 ? KillstreakKeyMappings.USE_SLOT_1
                        : i == 1 ? KillstreakKeyMappings.USE_SLOT_2 : KillstreakKeyMappings.USE_SLOT_3);
                graphics.drawString(mc.font, "[" + button + "]  x" + n + "  " + t.progress()[i] + "/" + threshold,
                        x + 30, y + 14, c, false);
                bar(graphics, x + 30, y + 25, 87, 1, t.progress()[i], threshold, c);
            }
        }
        int extra = mgb > 0 ? 1 : 0;
        if (bonus.length > 0) {
            String reward = StreakReward.byId(bonus[0]).orElse(StreakReward.UAV).id();
            int same = 0;
            for (String id : bonus)
                if (id.equals(reward))
                    same++;
            String name = Component.translatable("hud.killstreakcod.package_slot",
                    Component.translatable("streak.killstreakcod." + reward)).getString();
            extraSlot(graphics, w - 130, base + extra++ * 29, reward, name,
                    Component.translatable("hud.killstreakcod.package_slot_count", key(KillstreakKeyMappings.BONUS),
                            same, bonus.length).getString(),
                    0xffdfc989);
        }
        if (mgb > 0)
            extraSlot(graphics, w - 130, base, "mgb", Component.translatable("streak.killstreakcod.mgb").getString(),
                    "[" + key(KillstreakKeyMappings.MGB) + "]  x" + mgb, RED);
        radar(graphics);
    }

    private static void extraSlot(GuiGraphics graphics, int x, int y, String id, String name, String status,
            int color) {
        var font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + 124, y + 27, 0xd00e1513);
        OriginalRewardUi.icon(graphics, id, x + 14.5F, y + 13.5F, 23, WHITE, 1);
        graphics.drawString(font, font.plainSubstrByWidth(name, 90), x + 30, y + 3, color, false);
        graphics.drawString(font, font.plainSubstrByWidth(status, 90), x + 30, y + 14, color, false);
        graphics.fill(x + 30, y + 25, x + 117, y + 26, color);
    }

    private static void radar(GuiGraphics graphics) {
        var r = TacticalClient.radar;
        if (!r.active() && !r.jammed())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        int x = 9, y = 12, size = 94, cx = x + size / 2, cy = y + size / 2;
        float yaw = mc.player.getYRot();
        double a = Math.toRadians(yaw), scale = size / 160D;
        graphics.fill(x, y, x + size, y + size, 0xff122019);
        graphics.enableScissor(x, y, x + size, y + size);
        if (radarMap != null && !r.jammed()) {
            int draw = (int) (256 * scale);
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(180 - yaw));
            int ox = (int) ((terrainX - mc.player.getX()) * scale), oz = (int) ((terrainZ - mc.player.getZ()) * scale);
            graphics.blit(radarMap, -draw / 2 + ox, -draw / 2 + oz, draw, draw, 0, 0, 64, 64, 64, 64);
            graphics.pose().popPose();
        }
        if (r.jammed()) {
            for (int i = 0; i < 18; i++) {
                int dy = (int) ((System.currentTimeMillis() / 35 + i * 13) % size);
                graphics.hLine(x, x + size, y + dy, 0x886f7a6c);
            }
            graphics.drawCenteredString(mc.font, I18n.get("hud.killstreakcod.no_signal"), cx, cy, WHITE);
        } else {
            for (int i = 1; i < 4; i++) {
                graphics.hLine(x, x + size, y + i * size / 4, 0x66596b56);
                graphics.vLine(x + i * size / 4, y, y + size, 0x66596b56);
            }
            for (int i = 0; i + 3 < r.contacts().length; i += 4) {
                Vec3 position = new Vec3(r.contacts()[i] / 10D, r.contacts()[i + 1] / 10D, r.contacts()[i + 2] / 10D);
                if (!TacticalVisibility.surfacePointVisible(position))
                    continue;
                double dx = r.contacts()[i] / 10D - mc.player.getX(), dz = r.contacts()[i + 2] / 10D - mc.player.getZ();
                int px = (int) (cx + (-dx * Math.cos(a) - dz * Math.sin(a)) * scale),
                        py = (int) (cy + (dx * Math.sin(a) - dz * Math.cos(a)) * scale);
                if (px < x + 3 || px > x + size - 3 || py < y + 3 || py > y + size - 3)
                    continue;
                if (r.advanced())
                    arrow(graphics, px, py, r.contacts()[i + 3] - yaw, RED, 4);
                else
                    graphics.fill(px - 2, py - 2, px + 2, py + 2, RED);
            }
            arrow(graphics, cx, cy, 0, GREEN, 6);
        }
        graphics.disableScissor();
        graphics.renderOutline(x, y, size, size, 0xffb9ce9d);
        graphics.drawString(mc.font,
                (r.advanced() ? StreakReward.ADVANCED_UAV : StreakReward.UAV).displayName().getString() + " / "
                        + Math.floorMod((int) yaw + 180, 360) + "°",
                x, y + size + 3, GREEN, false);
        // North moves around the compass rim as the player turns.
        // 中文：玩家转向时，北方标记沿罗盘边缘移动。
        double north = Math.toRadians(180 - yaw);
        int nx = (int) (cx + Math.sin(north) * (size / 2 - 8)), ny = (int) (cy - Math.cos(north) * (size / 2 - 8));
        graphics.drawCenteredString(mc.font, "N", nx, ny - 4, WHITE);
    }

    public static void remote(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        var s = TacticalClient.state;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        boolean original = NativeRemoteHud.render(graphics);
        if (!original)
            OriginalHud.remote(graphics);
        if (!original)
            switch (s.mode()) {
                case "bomb_drone" -> drone(graphics);
                case "cruise" -> cruise(graphics);
                case "wheelson" -> wheelson(graphics);
                case "chopper" -> chopper(graphics);
                case "gunship" -> gunship(graphics);
                default ->
                    graphics.drawCenteredString(mc.font, I18n.get("hud.killstreakcod.connecting"), w / 2, h / 2, WHITE);
            }
        contacts(graphics);
        if (!original && TacticalClient.fuel() < 200 && System.currentTimeMillis() % 800 < 400)
            graphics.drawCenteredString(mc.font, I18n.get("hud.killstreakcod.native_remote_low_fuel"), w / 2, h - 70,
                    RED);
    }

    private static void drone(GuiGraphics graphics) {
        int w = graphics.guiWidth(), h = graphics.guiHeight(), x = w / 2, y = h / 2;
        frame(graphics, WHITE, 13, 35);
        compass(graphics, WHITE, 15);
        text(graphics, StreakReward.BOMB_DRONE.displayName().getString(), 18, 39, WHITE);
        text(graphics, "● REC / " + (TacticalClient.state.thermal() ? "IR" : "EO"), w - 91, 39, WHITE);
        brackets(graphics, x, y, 38, 26, WHITE);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, WHITE);
        telemetry(graphics, WHITE, 19, 62);
        verticalBar(graphics, w - 35, 65, 6, Math.max(8, h - 145), TacticalClient.fuel(), 600, WHITE);
        text(graphics, I18n.get("hud.killstreakcod.battery_label"), w - 41, 53, WHITE);
        TacticalClient.icon(graphics, "bomb_drone", 18, h - 75, 27);
        text(graphics, I18n.get("hud.killstreakcod.c4_armed_time", TacticalClient.fuel() / 20), 51, h - 64, WHITE);
        hint(graphics,
                I18n.get("hud.killstreakcod.native_drone_controls", key(Minecraft.getInstance().options.keyAttack),
                        movement(), key(Minecraft.getInstance().options.keyJump),
                        key(Minecraft.getInstance().options.keyShift), key(KillstreakKeyMappings.THERMAL),
                        key(KillstreakKeyMappings.ZOOM), key(KillstreakKeyMappings.EXIT)),
                WHITE);
    }

    private static void cruise(GuiGraphics graphics) {
        int w = graphics.guiWidth(), h = graphics.guiHeight(), x = w / 2, y = h / 2;
        frame(graphics, GREEN, 8, 25);
        compass(graphics, GREEN, 10);
        text(graphics,
                StreakReward.CRUISE_MISSILE.displayName().getString() + " / " + I18n.get("hud.killstreakcod.guidance"),
                16, 31, GREEN);
        brackets(graphics, x, y, 48, 31, GREEN);
        circle(graphics, x, y, 12, GREEN);
        graphics.hLine(x - 28, x - 8, y, GREEN);
        graphics.hLine(x + 8, x + 28, y, GREEN);
        graphics.vLine(x, y - 28, y - 8, GREEN);
        graphics.vLine(x, y + 8, y + 28, GREEN);
        telemetry(graphics, GREEN, 16, 55);
        text(graphics, I18n.get("hud.killstreakcod.pitch", (int) TacticalClient.pitch()), w - 90, 55, GREEN);
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null) {
            Vec3 from = camera.getEyePosition(),
                    to = from.add(Vec3.directionFromRotation(TacticalClient.pitch(), TacticalClient.yaw()).scale(256));
            var hit = Minecraft.getInstance().level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, camera));
            text(graphics, I18n.get("hud.killstreakcod.impact_coordinates", (int) hit.getLocation().x,
                    (int) hit.getLocation().z), 16, h - 67, GREEN);
        }
        bar(graphics, 16, h - 54, w - 32, 3, TacticalClient.fuel(), 300, GREEN);
        hint(graphics,
                I18n.get("hud.killstreakcod.cruise_controls",
                        key(Minecraft.getInstance().options.keyAttack) + "/" + key(KillstreakKeyMappings.BOOST),
                        key(KillstreakKeyMappings.ZOOM), key(KillstreakKeyMappings.EXIT)),
                GREEN);
    }

    private static void wheelson(GuiGraphics graphics) {
        int w = graphics.guiWidth(), h = graphics.guiHeight(), x = w / 2, y = h / 2;
        frame(graphics, CYAN, 18, 31);
        compass(graphics, CYAN, 14);
        text(graphics,
                StreakReward.WHEELSON.displayName().getString() + " / " + I18n.get("hud.killstreakcod.ground_link"), 24,
                37, CYAN);
        graphics.hLine(x - 24, x - 6, y, CYAN);
        graphics.hLine(x + 6, x + 24, y, CYAN);
        graphics.vLine(x, y - 19, y - 6, CYAN);
        graphics.vLine(x, y + 6, y + 19, CYAN);
        text(graphics, String.format(Locale.ROOT, "%.1f M/S", TacticalClient.state.speed()), w - 90, 58, CYAN);
        text(graphics, I18n.get("hud.killstreakcod.turret", Math.floorMod((int) TacticalClient.yaw() + 180, 360)), 24,
                58, CYAN);
        TacticalClient.icon(graphics, "wheelson_hs", 24, h - 79, 32);
        text(graphics, I18n.get("hud.killstreakcod.hull", TacticalClient.state.health()), 64, h - 69, CYAN);
        bar(graphics, 64, h - 56, 100, 4, TacticalClient.state.health(), 100, CYAN);
        text(graphics, I18n.get("hud.killstreakcod.auto_sentry_key", key(KillstreakKeyMappings.AUTO)), w - 140, h - 65,
                CYAN);
        hint(graphics,
                I18n.get("hud.killstreakcod.wheelson_controls",
                        key(Minecraft.getInstance().options.keyUp) + "/" + key(Minecraft.getInstance().options.keyDown),
                        key(Minecraft.getInstance().options.keyLeft) + "/"
                                + key(Minecraft.getInstance().options.keyRight),
                        key(Minecraft.getInstance().options.keyAttack), key(KillstreakKeyMappings.AUTO),
                        key(KillstreakKeyMappings.EXIT)),
                CYAN);
    }

    private static void chopper(GuiGraphics graphics) {
        int w = graphics.guiWidth(), h = graphics.guiHeight(), x = w / 2, y = h / 2;
        compass(graphics, GREEN, 15);
        text(graphics,
                StreakReward.CHOPPER_GUNNER.displayName().getString() + " / "
                        + (TacticalClient.state.thermal() ? I18n.get("hud.killstreakcod.native_action_thermal")
                                : I18n.get("hud.killstreakcod.optical")),
                15, 37, GREEN);
        text(graphics, I18n.get("hud.killstreakcod.hull", TacticalClient.state.health()), w - 98, 37, GREEN);
        graphics.hLine(x - 35, x - 9, y, GREEN);
        graphics.hLine(x + 9, x + 35, y, GREEN);
        graphics.vLine(x, y - 22, y - 7, GREEN);
        graphics.vLine(x, y + 7, y + 22, GREEN);
        circle(graphics, x, y, 5, GREEN);
        for (int i = -2; i <= 2; i++) {
            int yy = y + i * 21;
            graphics.hLine(x - 68, x - 43, yy, 0xff7fab69);
            graphics.hLine(x + 43, x + 68, yy, 0xff7fab69);
            text(graphics, Integer.toString((int) TacticalClient.pitch() + i * 10), x + 74, yy - 4, GREEN);
        }
        telemetry(graphics, GREEN, 15, 57);
        int rockets = TacticalClient.state.rockets(), flares = TacticalClient.state.flares();
        text(graphics, I18n.get("hud.killstreakcod.native_action_rockets"), 15, h - 78, GREEN);
        for (int i = 0; i < 8; i++)
            graphics.fill(15 + i * 9, h - 65, 21 + i * 9, h - 57, i < rockets ? GREEN : 0xff364333);
        text(graphics, I18n.get("hud.killstreakcod.flares_key", flares, key(KillstreakKeyMappings.FLARES)), w - 130,
                h - 74, GREEN);
        text(graphics, TacticalClient.rocketCooldown() > 0 ? I18n.get("hud.killstreakcod.rocket_reload")
                : I18n.get("hud.killstreakcod.weapon_ready"), 15, h - 53, GREEN);
        hint(graphics, I18n.get("hud.killstreakcod.chopper_controls", movement(), altitude(),
                key(Minecraft.getInstance().options.keyAttack), key(Minecraft.getInstance().options.keyUse),
                key(KillstreakKeyMappings.THERMAL), key(KillstreakKeyMappings.ZOOM), key(KillstreakKeyMappings.EXIT)),
                GREEN);
    }

    private static void gunship(GuiGraphics graphics) {
        int w = graphics.guiWidth(), h = graphics.guiHeight(), x = w / 2, y = h / 2,
                weapon = TacticalClient.state.weapon();
        int c = WHITE;
        frame(graphics, c, 10, 25);
        compass(graphics, c, 10);
        text(graphics,
                StreakReward.GUNSHIP.displayName().getString() + " / " + I18n.get("hud.killstreakcod.fire_control"), 16,
                31, c);
        text(graphics, TacticalClient.state.thermal() ? I18n.get("hud.killstreakcod.camera_thermal")
                : I18n.get("hud.killstreakcod.camera_optical"), w - 84, 31, c);
        if (weapon == 0) {
            graphics.hLine(x - 19, x + 19, y, c);
            graphics.vLine(x, y - 19, y + 19, c);
            brackets(graphics, x, y, 30, 24, c);
        } else if (weapon == 1) {
            circle(graphics, x, y, 23, c);
            graphics.hLine(x - 38, x - 26, y, c);
            graphics.hLine(x + 26, x + 38, y, c);
            graphics.vLine(x, y - 38, y - 26, c);
            graphics.vLine(x, y + 26, y + 38, c);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, c);
        } else {
            line(graphics, x, y - 30, x + 30, y, c);
            line(graphics, x + 30, y, x, y + 30, c);
            line(graphics, x, y + 30, x - 30, y, c);
            line(graphics, x - 30, y, x, y - 30, c);
            brackets(graphics, x, y, 45, 35, c);
            graphics.hLine(x - 8, x + 8, y, c);
            graphics.vLine(x, y - 8, y + 8, c);
        }
        String[] labels = { "25 MM", "40 MM", "105 MM" };
        KeyMapping[] keys = { KillstreakKeyMappings.WEAPON_1, KillstreakKeyMappings.WEAPON_2,
                KillstreakKeyMappings.WEAPON_3 };
        int[] capacities = { 25, 5, 1 }, reloadTime = { 60, 80, 120 };
        for (int i = 0; i < 3; i++) {
            int yy = h - 110 + i * 18, cc = i == weapon ? c : 0xff829084,
                    remaining = Mth.clamp(TacticalClient.state.ammo()[i], 0, capacities[i]),
                    reload = TacticalClient.reload(i);
            graphics.fill(15, yy - 2, 157, yy + 14, i == weapon ? 0xa83b4841 : 0x50131c18);
            text(graphics, "[" + key(keys[i]) + "] " + labels[i], 20, yy, cc);
            text(graphics,
                    reload > 0 ? I18n.get("hud.killstreakcod.load") : "%02d/%02d".formatted(remaining, capacities[i]),
                    113, yy, reload > 0 ? 0xffdfb778 : cc);
            bar(graphics, 20, yy + 11, 131, 2, reload > 0 ? reloadTime[i] - reload : remaining,
                    reload > 0 ? reloadTime[i] : capacities[i], reload > 0 ? 0xffdfb778 : cc);
        }
        var camera = Minecraft.getInstance().getCameraEntity();
        float arc = camera instanceof TacticalUnitEntity unit
                ? Math.abs(Mth.wrapDegrees(TacticalClient.yaw() - GunshipFlight.mountYaw(
                        unit.renderFlightYaw(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)))))
                : 0;
        boolean limited = arc > GunshipFlight.YAW_LIMIT - 2
                || Math.abs(TacticalClient.pitch()) > GunshipFlight.PITCH_LIMIT - 1;
        text(graphics,
                limited ? I18n.get("hud.killstreakcod.gimbal_limit")
                        : I18n.get("hud.killstreakcod.gimbal_limits", (int) GunshipFlight.YAW_LIMIT,
                                (int) GunshipFlight.PITCH_LIMIT),
                w - 156, h - 112, limited ? 0xffdfb778 : 0xff829084);
        text(graphics, I18n.get("hud.killstreakcod.orbit_altitude", altitudeValue()), w - 156, h - 93, c);
        int reload = TacticalClient.reload(weapon);
        text(graphics,
                reload > 0 ? I18n.get("hud.killstreakcod.reload_time", String.format(Locale.ROOT, "%.1f", reload / 20F))
                        : TacticalClient.cooldown() > 0 ? I18n.get("hud.killstreakcod.firing_cycle")
                                : I18n.get("hud.killstreakcod.weapon_ready"),
                w - 156, h - 77, reload > 0 ? 0xffdfb778 : c);
        text(graphics,
                I18n.get("hud.killstreakcod.ammo_count", TacticalClient.state.ammo()[weapon], capacities[weapon]),
                w - 156, h - 64, c);
        text(graphics, I18n.get("hud.killstreakcod.exit_key", key(KillstreakKeyMappings.EXIT)), w - 156, h - 51, c);
        hint(graphics, I18n.get("hud.killstreakcod.gunship_controls", key(Minecraft.getInstance().options.keyAttack),
                key(KillstreakKeyMappings.CYCLE), key(KillstreakKeyMappings.THERMAL), key(KillstreakKeyMappings.ZOOM)),
                c);
    }

    private static void contacts(GuiGraphics graphics) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null || TacticalClient.radar.jammed())
            return;
        Vec3 origin = camera.getEyePosition(),
                f = Vec3.directionFromRotation(TacticalClient.pitch(), TacticalClient.yaw()),
                r = Vec3.directionFromRotation(0, TacticalClient.yaw() + 90), up = r.cross(f);
        double focal = graphics.guiHeight() / (2 * Math.tan(Math.toRadians(TacticalClient.fov()) / 2));
        int[] data = TacticalClient.radar.contacts();
        for (int i = 0; i + 3 < data.length; i += 4) {
            Vec3 position = new Vec3(data[i] / 10D, data[i + 1] / 10D, data[i + 2] / 10D);
            if (!TacticalVisibility.pointVisible(position))
                continue;
            Vec3 d = position.subtract(origin);
            double depth = d.dot(f);
            if (depth < 1)
                continue;
            int x = (int) (graphics.guiWidth() / 2 + d.dot(r) * focal / depth),
                    y = (int) (graphics.guiHeight() / 2 - d.dot(up) * focal / depth);
            if (x < 12 || y < 48 || x > graphics.guiWidth() - 12 || y > graphics.guiHeight() - 80)
                continue;
            int color = TacticalClient.state.thermal() ? WHITE : RED;
            if (!NativeRemoteHud.contact(graphics, x, y, color))
                brackets(graphics, x, y, 8, 10, color);
        }
    }

    static void arrow(GuiGraphics graphics, int x, int y, float angle, int c, int size) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        for (int i = 0; i < size; i++)
            graphics.hLine(-i / 2, i / 2, -size + i, c);
        graphics.fill(-1, 0, 2, size / 2, c);
        graphics.pose().popPose();
    }

    static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int c) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = x1 + (x2 - x1) * i / Math.max(1, n), y = y1 + (y2 - y1) * i / Math.max(1, n);
            graphics.fill(x, y, x + 1, y + 1, c);
        }
    }

    private static void circle(GuiGraphics graphics, int x, int y, int r, int c) {
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI / 36;
            int xx = x + (int) (Math.cos(a) * r), yy = y + (int) (Math.sin(a) * r);
            graphics.fill(xx, yy, xx + 1, yy + 1, c);
        }
    }

    private static void brackets(GuiGraphics graphics, int x, int y, int w, int h, int c) {
        for (int sign : new int[] { -1, 1 }) {
            graphics.hLine(x - w, x - w + 8, y + sign * h, c);
            graphics.hLine(x + w - 8, x + w, y + sign * h, c);
            graphics.vLine(x + sign * w, y - h, y - h + 8, c);
            graphics.vLine(x + sign * w, y + h - 8, y + h, c);
        }
    }

    private static void frame(GuiGraphics graphics, int c, int margin, int top) {
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        graphics.hLine(margin, w - margin, top, c);
        graphics.vLine(margin, top, h - 50, c);
        graphics.vLine(w - margin, top, h - 50, c);
        graphics.hLine(margin, w - margin, h - 50, c);
    }

    private static void compass(GuiGraphics graphics, int c, int y) {
        int cx = graphics.guiWidth() / 2;
        float heading = TacticalClient.yaw();
        for (int d = -60; d <= 60; d += 15) {
            int x = cx + (int) (d * Math.min(2, (graphics.guiWidth() - 32) / 120F));
            int n = Math.floorMod((int) heading + 180 + d, 360);
            graphics.vLine(x, y + 10, y + 14, c);
            if (d % 30 == 0)
                graphics.drawCenteredString(Minecraft.getInstance().font, Integer.toString(n), x, y, c);
        }
    }

    private static void telemetry(GuiGraphics graphics, int c, int x, int y) {
        text(graphics, I18n.get("hud.killstreakcod.altitude", altitudeValue()), x, y, c);
        text(graphics, I18n.get("hud.killstreakcod.velocity",
                String.format(Locale.ROOT, "%.1f", TacticalClient.state.speed())), x, y + 13, c);
    }

    private static int altitudeValue() {
        var camera = Minecraft.getInstance().getCameraEntity();
        return camera == null ? 0 : (int) camera.getY();
    }

    private static String movement() {
        var o = Minecraft.getInstance().options;
        return key(o.keyUp) + key(o.keyLeft) + key(o.keyDown) + key(o.keyRight);
    }

    private static String altitude() {
        var o = Minecraft.getInstance().options;
        return key(o.keyJump) + "/" + key(o.keyShift);
    }

    private static String key(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static void text(GuiGraphics graphics, String s, int x, int y, int c) {
        graphics.drawString(Minecraft.getInstance().font, s, x, y, c, false);
    }

    private static void hint(GuiGraphics graphics, String s, int c) {
        int h = graphics.guiHeight();
        graphics.fill(0, h - 44, graphics.guiWidth(), h, 0xb80c1512);
        graphics.drawWordWrap(Minecraft.getInstance().font, Component.literal(s), 12, h - 35, graphics.guiWidth() - 24,
                c);
    }

    private static void bar(GuiGraphics graphics, int x, int y, int w, int h, int value, int max, int c) {
        graphics.fill(x, y, x + w, y + h, 0xff354336);
        graphics.fill(x, y, x + (int) (w * Mth.clamp(value / (float) Math.max(1, max), 0, 1)), y + h, c);
    }

    private static void verticalBar(GuiGraphics graphics, int x, int y, int w, int h, int value, int max, int c) {
        graphics.fill(x, y, x + w, y + h, 0xff354336);
        graphics.fill(x, y + h - (int) (h * Mth.clamp(value / (float) Math.max(1, max), 0, 1)), x + w, y + h, c);
    }
}
