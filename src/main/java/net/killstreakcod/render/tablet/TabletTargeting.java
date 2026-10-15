package net.killstreakcod.render.tablet;

import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.ImportedCastModel;
import net.killstreakcod.render.KillstreakKeyMappings;
import net.killstreakcod.render.MgbEffects;
import net.killstreakcod.render.NativeSceneUi;
import net.killstreakcod.render.StreakPresentation;
import net.killstreakcod.render.TacticalClient;
import net.killstreakcod.universal.TacticalMapPayload;
import net.killstreakcod.universal.TacticalTargetPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import static net.killstreakcod.render.tablet.TabletCanvas.Box;

/** 在第一人称平板表面处理地点选择、投影和鼠标交互，并向服务端提交标定结果。 */
public final class TabletTargeting {
    private static Session session;
    private static CameraType finishingType;
    private static LocalPlayer finishingPlayer;
    private static ClientLevel finishingLevel;
    private static final int GREEN = 0xffb7d889, ORANGE = 0xffffab79, WHITE = 0xffe9f1e3, ENEMY = 0xffff6258,
            ALLY = 0xff66c5ff;

    private TabletTargeting() {}

    public static boolean active() {
        return session != null;
    }

    public static String reward() {
        return active() ? session.data.reward() : "";
    }

    public static float elapsed() {
        return active() ? session.age + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true) : 0;
    }

    public static long revision() {
        return active() ? session.revision : 0;
    }

    public static void open(TacticalMapPayload data) {
        reset(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || TacticalClient.remote()
                || MgbEffects.interruptingControl())
            return;
        session = new Session(data, mc);
        TacticalClient.mapReward = data.reward();
        TacticalClient.mapContacts = new int[0];
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.stopUsingItem();
        KeyMapping.releaseAll();
        mc.mouseHandler.releaseMouse();
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        SceneTabletScreen.begin(data.reward(), "targeting");
    }

    public static void reset(boolean cancel) {
        restoreFinishingCamera();
        Session old = session;
        if (old == null)
            return;
        session = null;
        Minecraft mc = Minecraft.getInstance();
        if (cancel && mc.player == old.player && mc.level == old.level && mc.getConnection() != null
                && mc.player.isAlive())
            PacketDistributor.sendToServer(new TacticalTargetPayload(new int[0], 0, true));
        mc.options.setCameraType(old.cameraType);
        KeyMapping.releaseAll();
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        if (mc.screen == null && mc.player != null && mc.player.isAlive() && mc.level != null && mc.isWindowActive())
            mc.mouseHandler.grabMouse();
        TacticalClient.mapReward = "";
        TacticalClient.mapContacts = new int[0];
    }

    private static void restoreFinishingCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (finishingType != null)
            mc.options.setCameraType(finishingType);
        finishingType = null;
        finishingPlayer = null;
        finishingLevel = null;
    }

    public static void confirmed(String reward) {
        if (active() && reward().equals(reward)) {
            Session old = session;
            SceneTabletScreen.holdMap(reward);
            reset(false);
            finishingType = old.cameraType;
            finishingPlayer = old.player;
            finishingLevel = old.level;
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    public static void result(boolean accepted) {
        if (!active())
            return;
        session.pending = false;
        if (accepted)
            confirmed(reward());
        else {
            session.status = Component.translatable("screen.killstreakcod.target_rejected");
            session.revision++;
        }
    }

    public static void tick() {
        if (!active()) {
            Minecraft mc = Minecraft.getInstance();
            if (finishingType != null && (mc.player != finishingPlayer || mc.level != finishingLevel
                    || !StreakPresentation.currentStage().equals("confirmed") || !StreakPresentation.busy()))
                restoreFinishingCamera();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Session s = session;
        if (mc.player != s.player || mc.level != s.level || !s.player.isAlive() || s.player.isSpectator()
                || MgbEffects.interruptingControl()) {
            reset(false);
            return;
        }
        if (mc.screen != null) {
            reset(!s.pending);
            return;
        }
        if (mc.isPaused())
            return;
        s.age++;
        if (s.contacts != TacticalClient.mapContacts) {
            s.contacts = TacticalClient.mapContacts;
            s.contactAge = 0;
        } else
            s.contactAge++;
        if (s.pending && ++s.pendingTicks > 200) {
            s.pending = false;
            s.status = Component.translatable("screen.killstreakcod.target_timeout");
            s.revision++;
        }
        for (KeyMapping key : mc.options.keyHotbarSlots)
            while (key.consumeClick()) {
            }
        for (KeyMapping key : new KeyMapping[] { mc.options.keyInventory, mc.options.keyDrop, mc.options.keySwapOffhand,
                mc.options.keyTogglePerspective, KillstreakKeyMappings.OPEN_TERMINAL, KillstreakKeyMappings.USE_SLOT_1,
                KillstreakKeyMappings.USE_SLOT_2, KillstreakKeyMappings.USE_SLOT_3, KillstreakKeyMappings.BONUS,
                KillstreakKeyMappings.MGB, KillstreakKeyMappings.BOOST })
            while (key.consumeClick()) {
            }
    }

    public static void frame() {
        if (active())
            session.pointer();
    }

    public static void surface(ImportedCastModel model) {
        if (active())
            session.project(model.surface("m/offhand_vm_tablet_image_ui"));
    }

    public static void paint(NativeImage image) {
        if (active())
            session.paint(new TabletCanvas(image));
    }

    public static boolean mouseButton(int button, int action) {
        if (!active())
            return false;
        Session s = session;
        s.pointer();
        if (action == GLFW.GLFW_RELEASE) {
            if (button == 2)
                s.panning = false;
            return true;
        }
        if (action != GLFW.GLFW_PRESS || s.age < 12 || s.pending || s.hover == null)
            return true;
        s.click(button);
        return true;
    }

    public static boolean scroll(double delta) {
        if (!active())
            return false;
        Session s = session;
        s.pointer();
        if (s.hover != null && s.map.contains(s.hover.x, s.hover.y) && !s.pending) {
            s.zoom(delta, s.hover.x, s.hover.y);
            s.revision++;
        }
        return true;
    }

    public static boolean keyboard(long window, int key, int scan, int action, int modifiers) {
        if (!active())
            return false;
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow() || mc.screen != null)
            return false;
        // Preserve Minecraft's own screenshot/fullscreen handlers and release notifications.
        // 中文：保留 Minecraft 自带截图、全屏处理及按键释放通知。
        if (mc.options.keyScreenshot.matches(key, scan) || mc.options.keyFullscreen.matches(key, scan))
            return false;
        if (action != GLFW.GLFW_RELEASE && !session.pending) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                reset(true);
                return true;
            }
            if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E)
                session.rotate(key == GLFW.GLFW_KEY_Q ? -15 : 15);
            if (key == GLFW.GLFW_KEY_SPACE) {
                session.zoom = 1;
                session.panX = session.panZ = 0;
                session.revision++;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                session.submit();
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE)
                session.undo();
        }
        return true;
    }

    private record Point(double x, double y) {
    }

    private record Projected(double x, double y, double reciprocalW, double u, double v) {
    }

    private static final class Session {
        final TacticalMapPayload data;
        final LocalPlayer player;
        final ClientLevel level;
        final CameraType cameraType;
        final int[] terrain;
        final List<int[]> selected = new ArrayList<>();
        final List<Float> headings = new ArrayList<>();
        final List<Box> slots = new ArrayList<>();
        final Box map, panel, launch, cancel, undo, minus, plus, home, left, right;
        int age, activeSlot, pendingTicks, contactAge;
        long revision;
        double zoom = 1, panX, panZ;
        float heading;
        boolean pending, panning, centered;
        Point hover, drag;
        List<Projected> projected = List.of();
        long projectedAt;
        int[] contacts;
        Component status = Component.empty();

        Session(TacticalMapPayload data, Minecraft mc) {
            this.data = data;
            player = mc.player;
            level = mc.level;
            cameraType = mc.options.getCameraType();
            heading = player.getYRot();
            int w = SceneTabletScreen.WIDTH, h = SceneTabletScreen.HEIGHT;
            var nm = NativeSceneUi.bounds("LocationSelectorTablet", "Map", w, h);
            var np = NativeSceneUi.bounds("LocationSelectorTablet", "AccentInfo", w, h);
            map = nm == null ? new Box(190, 28, w - 200, h - 83)
                    : new Box(Math.round(nm.x()), Math.round(nm.y()), Math.round(nm.w()), Math.round(nm.h()) - 54);
            panel = np == null ? new Box(10, 28, 174, h - 83)
                    : new Box(Math.round(np.x()), Math.round(np.y()), Math.round(np.w()) - 5, Math.round(np.h()) - 54);
            cancel = new Box(10, h - 30, 74, 24);
            undo = new Box(90, h - 30, 74, 24);
            launch = new Box(w - 130, h - 30, 120, 24);
            minus = new Box(map.x + map.w - 61, map.y + 5, 24, 24);
            plus = new Box(map.x + map.w - 31, map.y + 5, 24, 24);
            home = new Box(map.x + 5, map.y + 5, 52, 24);
            left = new Box(panel.x, panel.y + panel.h - 29, 28, 24);
            right = new Box(panel.x + panel.w - 28, panel.y + panel.h - 29, 28, 24);
            for (int i = 0; i < required(); i++)
                slots.add(new Box(panel.x + 2, panel.y + 115 + i * 45, panel.w - 4, 39));
            terrain = new int[data.colors().length];
            for (int z = 0; z < data.width(); z++)
                for (int x = 0; x < data.width(); x++) {
                    int i = z * data.width() + x, c = data.colors()[i],
                            prev = data.heights()[Math.max(0, z - 1) * data.width() + x];
                    if (c == 0 || data.heights()[i] == Integer.MIN_VALUE)
                        c = ((x / 4 + z / 4) % 2 == 0) ? 0x26322d : 0x1a2420;
                    else {
                        double shade = prev == Integer.MIN_VALUE ? 1
                                : Mth.clamp(1 + (data.heights()[i] - prev) * .075, .62, 1.32);
                        c = (Mth.clamp((int) (((c >> 16) & 255) * shade), 0, 255) << 16)
                                | (Mth.clamp((int) (((c >> 8) & 255) * shade), 0, 255) << 8)
                                | Mth.clamp((int) ((c & 255) * shade), 0, 255);
                    }
                    terrain[i] = 0xff000000 | c;
                }
        }

        int required() {
            return data.reward().equals("sae") ? 3 : 1;
        }

        boolean cluster() {
            return data.reward().equals("sae");
        }

        boolean point() {
            return data.reward().equals("vtol_jet");
        }

        boolean directional() {
            return !cluster() && !point();
        }

        double scale() {
            return Math.min(map.w, map.h) * zoom / (TacticalMapPayload.WIDTH);
        }

        double cx() {
            return map.x + map.w / 2D;
        }

        double cy() {
            return map.y + map.h / 2D;
        }

        int sx(double x) {
            return (int) Math.round(cx() + (x - data.originX() - panX) * scale());
        }

        int sz(double z) {
            return (int) Math.round(cy() + (z - data.originZ() - panZ) * scale());
        }

        int wx(double x) {
            return (int) Math.round(data.originX() + panX + (x - cx()) / scale());
        }

        int wz(double y) {
            return (int) Math.round(data.originZ() + panZ + (y - cy()) / scale());
        }

        int index(int x, int z) {
            int ix = (int) Math.round((x - data.originX() + 66) * (data.width() - 1) / 132D),
                    iz = (int) Math.round((z - data.originZ() + 66) * (data.width() - 1) / 132D);
            return ix < 0 || iz < 0 || ix >= data.width() || iz >= data.width() ? -1 : iz * data.width() + ix;
        }

        boolean mapInput(Point p) {
            return map.contains(p.x, p.y) && !minus.contains(p.x, p.y) && !plus.contains(p.x, p.y)
                    && !home.contains(p.x, p.y);
        }

        void project(List<ImportedCastModel.SurfaceVertex> vertices) {
            // Minecraft's hand pose contains inverse view rotation, while the shader
            // applies ModelViewMat again. Both matrices are needed to match the LCD.
            // 中文：Minecraft 手部姿态含有逆视角旋转，而着色器还会再次应用 ModelViewMat；匹配液晶屏时需要同时考虑这两个
            // 矩阵。
            ArrayList<Projected> points = new ArrayList<>();
            var projection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix());
            for (var v : vertices) {
                Vector4f p = projection.transform(new Vector4f(v.x(), v.y(), v.z(), 1));
                if (!Float.isFinite(p.w) || p.w <= .001F) {
                    projected = List.of();
                    return;
                }
                points.add(new Projected(p.x / p.w * .5 + .5, .5 - p.y / p.w * .5, 1D / p.w, v.u(), v.v()));
            }
            projected = points;
            projectedAt = System.nanoTime();
            if (!centered && age >= 12 && !points.isEmpty()) {
                double x = 0, y = 0;
                for (var p : points) {
                    x += p.x;
                    y += p.y;
                }
                Minecraft mc = Minecraft.getInstance();
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x / points.size() * mc.getWindow().getScreenWidth(),
                        y / points.size() * mc.getWindow().getScreenHeight());
                centered = true;
            }
        }

        Point hit() {
            if (System.nanoTime() - projectedAt > 250_000_000L)
                return null;
            Minecraft mc = Minecraft.getInstance();
            double x = mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth(),
                    y = mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight();
            for (int i = 0; i + 2 < projected.size(); i += 3) {
                var a = projected.get(i);
                var b = projected.get(i + 1);
                var c = projected.get(i + 2);
                double det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
                if (Math.abs(det) < 1e-9)
                    continue;
                double ba = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / det,
                        bb = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / det, bc = 1 - ba - bb;
                if (ba < 0 || bb < 0 || bc < 0)
                    continue;
                double w = ba * a.reciprocalW + bb * b.reciprocalW + bc * c.reciprocalW,
                        u = (ba * a.u * a.reciprocalW + bb * b.u * b.reciprocalW + bc * c.u * c.reciprocalW) / w,
                        v = (ba * a.v * a.reciprocalW + bb * b.v * b.reciprocalW + bc * c.v * c.reciprocalW) / w;
                if (u >= 0 && v >= 0 && u < 1 && v < 1)
                    return new Point(u * SceneTabletScreen.WIDTH, v * SceneTabletScreen.HEIGHT);
            }
            return null;
        }

        void pointer() {
            Point next = hit();
            if (panning && drag != null && next != null && !pending) {
                panX -= (next.x - drag.x) / scale();
                panZ -= (next.y - drag.y) / scale();
                clampPan();
                revision++;
            }
            hover = next;
            drag = next;
        }

        void paint(TabletCanvas c) {
            int w = SceneTabletScreen.WIDTH, h = SceneTabletScreen.HEIGHT;
            c.fill(new Box(0, 0, w, h), 0xff111b16);
            c.image(NativeSceneUi.pixels("overlay_tablet_killstreak_background"), new NativeSceneUi.Rect(0, 0, w, h),
                    .4F, 0, 1, -1);
            c.text(Component.translatable("streak.killstreakcod." + data.reward()), new Box(12, 3, w - 130, 23), 22,
                    GREEN, 1, false);
            c.text(selected.size() + "/" + required() + "  ×" + String.format(Locale.ROOT, "%.1f", zoom),
                    new Box(w - 115, 3, 103, 23), 18, GREEN, 1, true);
            c.clip(map);
            c.fill(map, 0xff202b23);
            for (int y = map.y; y < map.y + map.h; y++)
                for (int x = map.x; x < map.x + map.w; x++) {
                    int i = index(wx(x + .5), wz(y + .5));
                    if (i >= 0)
                        c.pixel(x, y, terrain[i]);
                }
            for (int d = -64; d <= 64; d += 16) {
                c.line(sx(data.originX() + d), map.y, sx(data.originX() + d), map.y + map.h, 0x284e6658);
                c.line(map.x, sz(data.originZ() + d), map.x + map.w, sz(data.originZ() + d), 0x284e6658);
            }
            int sweep = map.x + (age % 100) * map.w / 100;
            c.line(sweep, map.y, sweep, map.y + map.h, 0x4868df9b);
            if (data.reward().equals(TacticalClient.mapReward) && contactAge <= 40) {
                int[] contacts = TacticalClient.mapContacts;
                for (int i = 0; i + 4 < contacts.length; i += 5)
                    c.arrow(sx(contacts[i] / 10D), sz(contacts[i + 2] / 10D), contacts[i + 3], 7,
                            contacts[i + 4] == 1 ? ENEMY : ALLY);
            }
            c.arrow(sx(player.getX()), sz(player.getZ()), player.getYRot(), 9, GREEN);
            for (int i = 0; i < selected.size(); i++) {
                int[] p = selected.get(i);
                target(c, sx(p[0]), sz(p[1]), headings.get(i), i == activeSlot ? ORANGE : GREEN);
                c.text("0" + (i + 1), new Box(sx(p[0]) + 10, sz(p[1]) - 22, 32, 21), 18, ORANGE, 1, false);
            }
            if (hover != null && mapInput(hover) && !pending)
                target(c, (int) hover.x, (int) hover.y, heading, WHITE);
            c.unclip();
            c.outline(map, 0xff586858);
            // Source decoration sequences overlay the live world map and fade their startup logo.
            // 中文：原装饰动画序列覆盖实时世界地图，并让启动标志淡出。
            c.clip(new Box(0, 0, w, h - 54));
            for (var layer : NativeSceneUi.layers("LocationSelectorTablet", "bootup", age * 50, w, h, false))
                c.image(NativeSceneUi.pixels(layer.image()), layer.rect(), layer.alpha(), layer.rotation(),
                        layer.scale(), layer.sprite() ? Math.floorMod((int) (age / 20F * 32), 20) : -1);
            c.unclip();
            c.image(SceneTabletScreen.icon(data.reward()),
                    new NativeSceneUi.Rect(panel.x + (panel.w - 68) / 2F, panel.y + 5, 68, 68), .85F, 0, 1, -1);
            c.text(Component.translatable("screen.killstreakcod.tablet.targets"),
                    new Box(panel.x + 2, panel.y + 83, panel.w - 4, 22), 19, GREEN, 1, true);
            for (int i = 0; i < slots.size(); i++) {
                Box b = slots.get(i);
                boolean ready = i < selected.size();
                c.fill(b, ready ? 0xff314a2b : 0xff202b25);
                c.outline(b, i == activeSlot ? ORANGE : ready ? GREEN : 0xff526351);
                c.text(Component.translatable("screen.killstreakcod.tablet.slot", i + 1,
                        Component.translatable("screen.killstreakcod.tablet." + (ready ? "ready" : "select"))),
                        new Box(b.x + 6, b.y + 5, b.w - 12, 28), 21, ready ? GREEN : WHITE, 1, true);
            }
            int ly = panel.y + 260;
            String[] labels = { "enemy", "ally", "self" };
            int[] colors = { ENEMY, ALLY, GREEN };
            for (int i = 0; i < 3; i++) {
                c.arrow(panel.x + 9, ly + i * 27 + 10, 180, 6, colors[i]);
                c.text(Component.translatable("screen.killstreakcod.tablet." + labels[i]),
                        new Box(panel.x + 23, ly + i * 27, panel.w - 25, 23), 18, colors[i], 1, false);
            }
            if (cluster())
                c.text(Component.translatable("screen.killstreakcod.cluster_area"),
                        new Box(left.x, left.y, panel.w, 24), 17, GREEN, 1, true);
            else if (point())
                c.text(Component.translatable("screen.killstreakcod.tablet.point_target"),
                        new Box(left.x, left.y, panel.w, 24), 17, GREEN, 1, true);
            else {
                button(c, left, "‹", true);
                button(c, right, "›", true);
                c.text(Math.floorMod(Math.round(heading) + 180, 360) + "°",
                        new Box(left.x + 32, left.y, panel.w - 64, 24), 18, GREEN, 1, true);
            }
            button(c, minus, "−", zoom > 1);
            button(c, plus, "+", zoom < 6);
            button(c, home, "HOME", true);
            int bx = hover != null && mapInput(hover) ? wx(hover.x) : data.originX(),
                    bz = hover != null && mapInput(hover) ? wz(hover.y) : data.originZ(), ix = index(bx, bz);
            String altitude = ix < 0 || data.heights()[ix] == Integer.MIN_VALUE ? "--"
                    : Integer.toString(data.heights()[ix]);
            c.fill(new Box(map.x, map.y + map.h - 24, map.w, 24), 0xe00d1612);
            c.text("X " + bx + "  Y " + altitude + "  Z " + bz + "   N ↑",
                    new Box(map.x + 6, map.y + map.h - 24, map.w - 12, 24), 18, WHITE, 1, false);
            Component line = pending ? Component.translatable("screen.killstreakcod.target_sending")
                    : status.getString().isEmpty() ? Component.translatable(
                            "screen.killstreakcod.tablet." + (point() ? "point_navigation" : "surface_navigation"))
                            : status;
            c.text(line, new Box(12, h - 54, w - 24, 21), 16, pending ? GREEN : WHITE, 1, true);
            button(c, cancel, "tablet.cancel", !pending);
            button(c, undo, "tablet.undo", !selected.isEmpty() && !pending);
            button(c, launch, pending ? "tablet.sending" : "tablet.deploy", selected.size() == required() && !pending);
            if (hover != null) {
                int x = (int) hover.x, y = (int) hover.y;
                c.line(x - 8, y, x - 3, y, WHITE);
                c.line(x + 3, y, x + 8, y, WHITE);
                c.line(x, y - 8, x, y - 3, WHITE);
                c.line(x, y + 3, x, y + 8, WHITE);
                c.ring(x, y, 2, WHITE);
            }
        }

        void button(TabletCanvas c, Box box, String text, boolean enabled) {
            enabled &= !pending;
            boolean over = enabled && hover != null && box.contains(hover.x, hover.y);
            c.fill(box, over ? 0xff3d5434 : enabled ? 0xff28362b : 0xff17201b);
            c.outline(box, enabled ? GREEN : 0xff445146);
            Component label = text.startsWith("tablet.") ? Component.translatable("screen.killstreakcod." + text)
                    : Component.literal(text);
            c.text(label, new Box(box.x + 3, box.y + 2, box.w - 6, box.h - 4), 18, enabled ? GREEN : 0xff67756b, 1,
                    true);
        }

        void target(TabletCanvas c, int x, int y, float yaw, int color) {
            if (point())
                c.ring(x, y, 7, color);
            else if (data.reward().equals("stealth_bomber")) {
                double a = Math.toRadians(yaw), dx = -Math.sin(a), dz = Math.cos(a);
                for (int n = -map.h; n <= map.h; n += 15)
                    c.arrow(x + (int) (dx * n), y + (int) (dz * n), yaw, 4, 0x958bdc9b);
                c.outline(new Box(x - 6, y - 6, 13, 13), color);
            } else
                c.ring(x, y, cluster() ? Math.max(8, (int) Math
                        .round((TacticalUnitEntity.SAE_CLUSTER_RADIUS + TacticalUnitEntity.SAE_BLAST_RADIUS) * scale()))
                        : 15, color);
            c.line(x - 5, y, x + 5, y, color);
            c.line(x, y - 5, x, y + 5, color);
        }

        void touch() {
            revision++;
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(StreakSounds.TABLET_SELECT.get(), 1));
        }

        void click(int button) {
            if (button == 2 && mapInput(hover)) {
                panning = true;
                drag = hover;
                return;
            }
            if (button == 1 && map.contains(hover.x, hover.y)) {
                undo();
                touch();
                return;
            }
            if (button != 0)
                return;
            double x = hover.x, y = hover.y;
            if (cancel.contains(x, y)) {
                reset(true);
                return;
            }
            if (undo.contains(x, y)) {
                undo();
                touch();
                return;
            }
            if (launch.contains(x, y)) {
                touch();
                submit();
                return;
            }
            if (minus.contains(x, y) || plus.contains(x, y)) {
                zoom(minus.contains(x, y) ? -1 : 1, cx(), cy());
                touch();
                return;
            }
            if (home.contains(x, y)) {
                zoom = 1;
                panX = panZ = 0;
                touch();
                return;
            }
            if (directional() && (left.contains(x, y) || right.contains(x, y))) {
                rotate(left.contains(x, y) ? -15 : 15);
                touch();
                return;
            }
            for (int i = 0; i < slots.size(); i++)
                if (slots.get(i).contains(x, y)) {
                    if (i > selected.size())
                        return;
                    activeSlot = i;
                    if (i < headings.size())
                        heading = headings.get(i);
                    touch();
                    return;
                }
            if (mapInput(hover)) {
                int bx = wx(x), bz = wz(y), i = index(bx, bz);
                if (i < 0 || data.colors()[i] == 0 || data.heights()[i] == Integer.MIN_VALUE) {
                    status = Component.translatable("screen.killstreakcod.target_unavailable");
                    revision++;
                    return;
                }
                if (activeSlot < selected.size()) {
                    selected.set(activeSlot, new int[] { bx, bz });
                    headings.set(activeSlot, heading);
                } else {
                    selected.add(new int[] { bx, bz });
                    headings.add(heading);
                    activeSlot = selected.size() - 1;
                }
                if (selected.size() < required())
                    activeSlot = selected.size();
                status = Component.empty();
                touch();
            }
        }

        void rotate(float degrees) {
            if (!directional())
                return;
            heading = Mth.wrapDegrees(heading + degrees);
            if (activeSlot < headings.size())
                headings.set(activeSlot, heading);
            revision++;
        }

        void undo() {
            if (!selected.isEmpty()) {
                selected.remove(selected.size() - 1);
                headings.remove(headings.size() - 1);
                activeSlot = Math.min(selected.size(), required() - 1);
                if (!headings.isEmpty())
                    heading = headings.get(Math.min(activeSlot, headings.size() - 1));
            }
            status = Component.empty();
            revision++;
        }

        void submit() {
            if (pending || selected.size() != required() || age < 12)
                return;
            int[] points = new int[selected.size() * 2];
            float[] angles = new float[headings.size()];
            for (int i = 0; i < selected.size(); i++) {
                points[i * 2] = selected.get(i)[0];
                points[i * 2 + 1] = selected.get(i)[1];
                angles[i] = headings.get(i);
            }
            pending = true;
            pendingTicks = 0;
            panning = false;
            revision++;
            PacketDistributor.sendToServer(new TacticalTargetPayload(points, angles, false));
        }

        void clampPan() {
            double extent = 66.5;
            panX = Mth.clamp(panX, -Math.max(0, extent - map.w / (2 * scale())),
                    Math.max(0, extent - map.w / (2 * scale())));
            panZ = Mth.clamp(panZ, -Math.max(0, extent - map.h / (2 * scale())),
                    Math.max(0, extent - map.h / (2 * scale())));
        }

        void zoom(double amount, double x, double y) {
            double bx = data.originX() + panX + (x - cx()) / scale(), bz = data.originZ() + panZ + (y - cy()) / scale();
            zoom = Mth.clamp(zoom * Math.pow(1.25, amount), 1, 6);
            panX = bx - data.originX() - (x - cx()) / scale();
            panZ = bz - data.originZ() - (y - cy()) / scale();
            clampPan();
        }
    }
}
