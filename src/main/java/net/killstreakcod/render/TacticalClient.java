package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeRadioSubtitles;
import net.killstreakcod.audio.SceneEntryAudio;
import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalClientIntegrations;
import net.killstreakcod.render.tablet.TabletTargeting;
import net.killstreakcod.universal.BonusStatePayload;
import net.killstreakcod.universal.RadarPayload;
import net.killstreakcod.universal.ResearchStatePayload;
import net.killstreakcod.universal.TacticalControlPayload;
import net.killstreakcod.universal.TacticalViewPayload;
import net.killstreakcod.universal.TerminalStatePayload;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.killstreakcod.network.StreakPackets;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import static net.killstreakcod.universal.TacticalControlPayload.*;

/**
 * Pilot camera angles and raw input do not depend on the anchored player's rotation.
 * 驾驶视角角度和原始输入不依赖固定在原地的玩家本体旋转。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class TacticalClient {
    public static TerminalStatePayload terminal;
    public static ResearchStatePayload research;
    public static BonusStatePayload bonus = new BonusStatePayload("", false);
    public static RadarPayload radar = new RadarPayload(false, false, false, new int[0]);
    public static TacticalViewPayload state = new TacticalViewPayload(-1, "", 0, 0, 0, false);
    public static int[] mapContacts = new int[0];
    public static String mapReward = "";
    private static CameraType previousType;
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;
    private static net.minecraft.client.player.LocalPlayer previousPlayer, anchorPlayer;
    private static Vec3 bodyAnchor;
    private static float cameraRoll;
    private static float yaw, pitch, bodyYaw, bodyPitch, bodyHeadYaw, bodyTorsoYaw, impactStrength;
    private static double mouseX, mouseY;
    private static boolean mouseCaptured, zoom, secondaryHeld;
    private static int missingTicks, heartbeat, sequence, pendingActions, viewAge;

    private TacticalClient() {}

    public static boolean remote() {
        return state.entityId() >= 0;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static int fuel() {
        return Math.max(0, state.remaining() - viewAge);
    }

    public static int cooldown() {
        return Math.max(0, state.cooldown() - viewAge);
    }

    public static int rocketCooldown() {
        return Math.max(0, state.rocketCooldown() - viewAge);
    }

    public static int reload(int i) {
        return i < 0 || i >= state.reloads().length ? 0 : Math.max(0, state.reloads()[i] - viewAge);
    }

    public static float fov() {
        float base = switch (state.mode()) {
            case "gunship" -> 50;
            case "chopper" -> 65;
            case "wheelson", "precision" -> 75;
            case "cruise" -> 70;
            default -> 80;
        };
        return zoom ? base * .55F : base;
    }

    public static void impact(float n) {
        if (Float.isFinite(n))
            impactStrength = Math.max(impactStrength, Mth.clamp(n, 0, 3));
    }

    public static void acceptWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == previousLevel && mc.player == previousPlayer)
            return;
        resetControl();
        terminal = null;
        research = null;
        radar = new RadarPayload(false, false, false, new int[0]);
        bonus = new BonusStatePayload("", false);
        mapContacts = new int[0];
        mapReward = "";
        TacticalHud.reset();
        previousLevel = mc.level;
        previousPlayer = mc.player;
    }

    public static void view(TacticalViewPayload packet) {
        acceptWorld();
        Minecraft mc = Minecraft.getInstance();
        boolean was = remote(), start = !was || packet.entityId() != state.entityId();
        if (packet.entityId() < 0) {
            restore();
            return;
        }
        if (mc.player == null || !mc.player.isAlive() || MgbEffects.interruptingControl()) {
            resetControl();
            return;
        }
        state = packet;
        AircraftHudRenderer.accept(packet);
        heartbeat = 0;
        viewAge = 0;
        previousLevel = mc.level;
        if (start) {
            secondaryHeld = held(mc.options.keyUse);
            if (!was) {
                previousType = mc.options.getCameraType();
                if (mc.player != null) {
                    anchorPlayer = mc.player;
                    bodyAnchor = mc.player.position();
                    bodyYaw = mc.player.getYRot();
                    bodyPitch = mc.player.getXRot();
                    bodyHeadYaw = mc.player.yHeadRot;
                    bodyTorsoYaw = mc.player.yBodyRot;
                }
            }
            yaw = packet.yaw();
            pitch = packet.pitch();
            sequence = 0;
            pendingActions = 0;
            missingTicks = 0;
            zoom = false;
            mouseCaptured = false;
            for (KeyMapping key : new KeyMapping[] { KillstreakKeyMappings.THERMAL, KillstreakKeyMappings.CYCLE,
                    KillstreakKeyMappings.EXIT, KillstreakKeyMappings.AUTO, KillstreakKeyMappings.FLARES,
                    KillstreakKeyMappings.WEAPON_1, KillstreakKeyMappings.WEAPON_2, KillstreakKeyMappings.WEAPON_3,
                    KillstreakKeyMappings.WEAPON_4, KillstreakKeyMappings.WEAPON_5, KillstreakKeyMappings.ZOOM,
                    KillstreakKeyMappings.BOOST })
                while (key.consumeClick()) {
                }
            mc.setScreen(null);
            mc.options.setCameraType(
                    packet.mode().equals("wheelson") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            mc.mouseHandler.grabMouse();
        } else if (packet.mode().equals("gunship") && StreakPresentation.transitioning()
                && StreakPresentation.currentStage().equals("entry")) {
            // Follow the aircraft's left weapon mount while the entry movie plays.
            // 中文：入场过场播放期间跟随飞机左侧武器挂点。
            yaw = packet.yaw();
            pitch = packet.pitch();
        }
        attachCamera();
    }

    public static void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (mc.getCameraEntity() != mc.player)
                mc.setCameraEntity(mc.player);
            if (bodyAnchor != null && mc.player == anchorPlayer) {
                mc.player.setYRot(bodyYaw);
                mc.player.setXRot(bodyPitch);
                mc.player.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (previousType != null)
            mc.options.setCameraType(previousType);
        previousType = null;
        bodyAnchor = null;
        anchorPlayer = null;
        zoom = false;
        secondaryHeld = false;
        mouseCaptured = false;
        pendingActions = missingTicks = heartbeat = sequence = viewAge = 0;
        state = new TacticalViewPayload(-1, "", 0, 0, 0, false);
        RemoteTerrainRange.reset();
        ThermalVision.update();
    }

    /**
     * Forced interruption has no exit film and must leave DeathScreen available.
     *
     * 中文：强制中断不播放退场片段，且必须允许死亡界面正常使用。
     */
    public static void resetControl() {
        TabletTargeting.reset(false);
        restore();
        SceneEntryAudio.reset();
        StreakPresentation.reset();
        TargetingDeviceAnimations.reset();
        NativeArsenalView.reset();
        mapContacts = new int[0];
        mapReward = "";
    }

    @SubscribeEvent
    public static void respawn(ClientPlayerNetworkEvent.Clone event) {
        resetControl();
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        resetControl();
        previousLevel = null;
        previousPlayer = null;
    }

    private static void attachCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (!remote() || mc.level == null)
            return;
        Entity camera = mc.level.getEntity(state.entityId());
        if (camera == null)
            return;
        if (mc.getCameraEntity() != camera)
            mc.setCameraEntity(camera);
        // ComputeCameraAngles supplies sensor orientation without rewriting vehicle/chassis angles.
        // 中文：通过 ComputeCameraAngles 设置传感器方向，不改写载具及底盘角度。
        freezeBody();
        mc.options.setCameraType(
                state.mode().equals("wheelson") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        ThermalVision.update();
    }

    private static void freezeBody() {
        var player = Minecraft.getInstance().player;
        if (player == null || player != anchorPlayer || !player.isAlive())
            return;
        player.setYRot(bodyYaw);
        player.setXRot(bodyPitch);
        player.yRotO = bodyYaw;
        player.xRotO = bodyPitch;
        player.yHeadRot = player.yHeadRotO = bodyHeadYaw;
        player.yBodyRot = player.yBodyRotO = bodyTorsoYaw;
    }

    public static boolean held(KeyMapping mapping) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !mc.isWindowActive() || !mapping.isConflictContextAndModifierActive())
            return false;
        var key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
            default -> mapping.isDown();
        };
    }

    private static boolean clicked(KeyMapping key) {
        boolean hit = false;
        while (key.consumeClick())
            hit = true;
        return hit;
    }

    @SubscribeEvent
    public static void beforeTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        acceptWorld();
        Minecraft mc = Minecraft.getInstance();
        if ((remote() || StreakPresentation.busy()) && (mc.player == null || !mc.player.isAlive())) {
            resetControl();
            return;
        }
        if (!remote() && !StreakPresentation.busy())
            return;
        if (remote() && mc.player == anchorPlayer && mc.player != null && bodyAnchor != null) {
            mc.player.setPos(bodyAnchor);
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
        if (mc.screen == null) {
            for (KeyMapping key : mc.options.keyHotbarSlots)
                while (key.consumeClick()) {
                }
            for (KeyMapping key : new KeyMapping[] { mc.options.keyInventory, mc.options.keyDrop,
                    mc.options.keySwapOffhand, mc.options.keyTogglePerspective })
                while (key.consumeClick()) {
                }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        acceptWorld();
        StreakPresentation.tick();
        TabletTargeting.tick();
        TargetingDeviceAnimations.tick();
        NativeArsenalView.tick();
        if (mc.isPaused())
            return;
        impactStrength = Math.max(0, impactStrength * .82F - .01F);
        TacticalHud.tick();
        if (mc.player == null || mc.level == null) {
            resetControl();
            return;
        }
        if (!remote())
            return;
        if (!mc.player.isAlive()) {
            resetControl();
            return;
        }
        viewAge++;
        if (++heartbeat > 100) {
            sendExit();
            resetControl();
            return;
        }
        Entity camera = mc.level.getEntity(state.entityId());
        if (camera == null) {
            if (++missingTicks > 80) {
                sendExit();
                resetControl();
            }
            return;
        }
        missingTicks = 0;
        attachCamera();
        for (KeyMapping key : new KeyMapping[] { KillstreakKeyMappings.USE_SLOT_1, KillstreakKeyMappings.USE_SLOT_2,
                KillstreakKeyMappings.USE_SLOT_3, KillstreakKeyMappings.BONUS, KillstreakKeyMappings.MGB,
                KillstreakKeyMappings.OPEN_TERMINAL, KillstreakKeyMappings.BOOST })
            while (key.consumeClick()) {
            }
        int flags = pendingActions;
        pendingActions = 0;
        if (clicked(KillstreakKeyMappings.THERMAL))
            flags |= THERMAL;
        if (clicked(KillstreakKeyMappings.CYCLE))
            flags |= CYCLE;
        if (clicked(KillstreakKeyMappings.EXIT))
            flags |= EXIT;
        if (clicked(KillstreakKeyMappings.AUTO))
            flags |= AUTO;
        if (clicked(KillstreakKeyMappings.FLARES))
            flags |= FLARES;
        if (clicked(KillstreakKeyMappings.WEAPON_1))
            flags |= WEAPON_1;
        if (clicked(KillstreakKeyMappings.WEAPON_2))
            flags |= WEAPON_2;
        if (clicked(KillstreakKeyMappings.WEAPON_3))
            flags |= WEAPON_3;
        if (clicked(KillstreakKeyMappings.WEAPON_4))
            flags |= WEAPON_4;
        if (clicked(KillstreakKeyMappings.WEAPON_5))
            flags |= WEAPON_5;
        if (clicked(KillstreakKeyMappings.ZOOM) && !StreakPresentation.busy())
            zoom = !zoom;
        float forward = 0, strafe = 0;
        boolean useHeld = held(mc.options.keyUse);
        if (useHeld && !secondaryHeld)
            flags |= SECONDARY;
        secondaryHeld = useHeld;
        if (mc.screen == null && mc.isWindowActive()) {
            forward = (held(mc.options.keyUp) ? 1 : 0) - (held(mc.options.keyDown) ? 1 : 0);
            strafe = (held(mc.options.keyRight) ? 1 : 0) - (held(mc.options.keyLeft) ? 1 : 0);
            if (held(mc.options.keyAttack))
                flags |= FIRE;
            if (held(mc.options.keyJump))
                flags |= UP;
            if (held(mc.options.keyShift))
                flags |= DOWN;
            if (held(KillstreakKeyMappings.BOOST))
                flags |= BOOST;
        }
        if (StreakPresentation.busy()) {
            forward = strafe = 0;
            flags &= EXIT;
        }
        StreakPackets.sendToServer(new TacticalControlPayload(yaw, pitch, forward, strafe, flags, ++sequence));
    }

    private static void sendExit() {
        StreakPackets.sendToServer(new TacticalControlPayload(yaw, pitch, 0, 0, EXIT, ++sequence));
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (remote() || StreakPresentation.transitioning() || TabletTargeting.active()) {
            Input i = event.getInput();
            i.forwardImpulse = 0;
            i.leftImpulse = 0;
            i.up = false;
            i.down = false;
            i.left = false;
            i.right = false;
            i.jumping = false;
            i.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        StreakPresentation.beginFrame();
        TargetingDeviceAnimations.beginFrame();
        NativeArsenalView.beginFrame();
        TabletTargeting.frame();
        NativePrecisionScopeHud.frame();
        Minecraft mc = Minecraft.getInstance();
        if (!remote())
            return;
        double x = mc.mouseHandler.xpos(), y = mc.mouseHandler.ypos();
        boolean captured = mc.screen == null && mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive();
        if (captured && mouseCaptured && !StreakPresentation.busy()) {
            double dx = x - mouseX, dy = y - mouseY;
            if (Math.abs(dx) < 5000 && Math.abs(dy) < 5000) {
                double sensitivity = mc.options.sensitivity().get() * .6 + .2;
                double scale = sensitivity * sensitivity * sensitivity * 8 * .15 * (zoom ? .55 : 1);
                yaw = Mth.wrapDegrees(yaw + (float) (dx * scale));
                pitch += (float) (dy * scale * (mc.options.invertYMouse().get() ? -1 : 1));
                pitch = state.mode().equals("cruise") ? Mth.wrapDegrees(pitch)
                        : Mth.clamp(pitch, state.mode().equals("wheelson") ? -45 : -85,
                                state.mode().equals("wheelson") ? 60 : 88);
            }
        }
        if (state.mode().equals("gunship") && mc.level != null
                && mc.level.getEntity(state.entityId()) instanceof TacticalUnitEntity unit) {
            yaw = GunshipFlight.clampYaw(yaw, unit.renderFlightYaw(mc.getFrameTime()));
            pitch = GunshipFlight.clampPitch(pitch);
        }
        mouseX = x;
        mouseY = y;
        mouseCaptured = captured;
        attachCamera();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tabletMouse(InputEvent.MouseButton.Pre event) {
        if (TabletTargeting.mouseButton(event.getButton(), event.getAction())) {
            event.setCanceled(true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Capture a short tap even when press and release both fall between client ticks.
        // 即使按下、松开都发生在两次客户端刻之间，也保留一次短按发射意图。
        if (remote() && !StreakPresentation.busy() && mc.screen == null && mc.isWindowActive()
                && event.getAction() == GLFW.GLFW_PRESS && mc.options.keyUse.isConflictContextAndModifierActive()
                && mc.options.keyUse.matchesMouse(event.getButton())) {
            pendingActions |= SECONDARY;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (TabletTargeting.scroll(event.getScrollDelta())) {
            event.setCanceled(true);
            return;
        }
        if (remote() || StreakPresentation.busy()) {
            event.setCanceled(true);
            if (remote() && !StreakPresentation.busy() && state.mode().equals("gunship")
                    && event.getScrollDelta() != 0)
                pendingActions |= event.getScrollDelta() > 0 ? CYCLE : CYCLE_BACK;
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (remote())
            event.setFOV(fov() + StreakPresentation.fovAdjustment());
        else if (TargetingDeviceAnimations.active())
            event.setFOV(Mth.lerp(
                    (double) TargetingDeviceAnimations
                            .zoom(Minecraft.getInstance().getFrameTime()),
                    event.getFOV(), TargetingDeviceAnimations.aimedFov()));
        else if (scoping())
            event.setFOV(28);
        EntryScenes.overrideFov(event);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (remote()) {
            event.setYaw(yaw);
            event.setPitch(pitch);
        }
        event.setRoll(event.getRoll() + StreakPresentation.cameraRoll());
        EntryScenes.overrideAngles(event);
        Vector3f shake = shakeAngles();
        event.setYaw(event.getYaw() + shake.x);
        event.setPitch(event.getPitch() + shake.y);
        event.setRoll(event.getRoll() + shake.z);
        cameraRoll = event.getRoll();
    }

    private static Vector3f shakeAngles() {
        Minecraft mc = Minecraft.getInstance();
        if (impactStrength <= 0 || mc.level == null)
            return new Vector3f();
        float t = mc.level.getGameTime() + mc.getFrameTime(),
                n = impactStrength * mc.options.screenEffectScale().get().floatValue();
        return new Vector3f((float) Math.cos(t * 3.7) * n * .7F, (float) Math.sin(t * 2.6) * n,
                (float) Math.sin(t * 4.1) * n * .5F);
    }

    /**
     * Camera-local movie meshes need the same impulse as the terrain behind them.
     *
     * 中文：摄像机局部的过场网格需要与后方地形应用相同的震动冲量。
     */
    public static void cinematicShake(PoseStack pose) {
        Vector3f shake = shakeAngles();
        if (shake.lengthSquared() == 0)
            return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Quaternionf unshaken = new Quaternionf().rotationYXZ(
                (float) Math.PI - (camera.getYRot() - shake.x) * Mth.DEG_TO_RAD,
                -(camera.getXRot() - shake.y) * Mth.DEG_TO_RAD, -(cameraRoll - shake.z) * Mth.DEG_TO_RAD);
        pose.mulPose(new Quaternionf(camera.rotation()).invert().mul(unshaken));
    }

    private static boolean scoping() {
        if (TargetingDeviceAnimations.active())
            return TargetingDeviceAnimations.opticsVisible();
        var player = Minecraft.getInstance().player;
        return player != null && player.isUsingItem() && player.getUseItem().is(KillstreakcodMod.DESIGNATOR.get())
                && !player.getUseItem()
                        .getOrCreateTag().getString("Reward").equals("mortar_strike");
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (remote() || StreakPresentation.busy() || TargetingDeviceAnimations.blocksUse()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        if (StreakPresentation.renderHands(event) || remote() || NativeArsenalView.renderHands(event)
                || TargetingDeviceAnimations.renderHands(event))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void arm(RenderArmEvent event) {
        if (remote() || NativeArsenalView.active() || TargetingDeviceAnimations.active()
                || (StreakPresentation.busy() && !(StreakPresentation.oneHandCall()
                        && event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT)))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tabletCrosshair(RenderGuiOverlayEvent.Pre event) {
        if ((TabletTargeting.active() || NativePrecisionScopeHud.visible())
                && event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void guiPre(RenderGuiEvent.Pre event) {
        if (remote() || AircraftCrashClient.active()) {
            if (!Minecraft.getInstance().options.hideGui) {
                if (remote() && StreakPresentation.remoteHudVisible())
                    TacticalHud.remote(event.getGuiGraphics());
                TacticalHud.banner(event.getGuiGraphics());
                StreakPresentation.renderGui(event.getGuiGraphics());
                OptionalClientIntegrations.remoteOverlay(event.getGuiGraphics(),
                        event.getPartialTick());
                NativeRadioSubtitles.render(event.getGuiGraphics());
            } else
                StreakPresentation.renderBlackout(event.getGuiGraphics());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void gui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        if (mc.options.hideGui) {
            StreakPresentation.renderBlackout(event.getGuiGraphics());
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        TacticalHud.normal(graphics);
        TacticalHud.banner(graphics);
        StreakPresentation.renderGui(graphics);
        if (scoping()) {
            NativePrecisionScopeHud.render(graphics);
        } else if (TargetingDeviceAnimations.laserMarking()) {
            int x = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2, c = 0xffb7d87a;
            var hit = mc.player.pick(160, 0, false);
            graphics.hLine(x - 4, x + 4, y, c);
            graphics.vLine(x, y - 4, y + 4, c);
            graphics.drawCenteredString(mc.font,
                    "RANGE " + (int) mc.player.getEyePosition().distanceTo(hit.getLocation()) + " m", x, y + 30, c);
            graphics.drawCenteredString(mc.font,
                    net.minecraft.network.chat.Component.translatable("hud.killstreakcod.designate"), x, y + 65, c);
        }
        NativeRadioSubtitles.render(graphics);
    }

    public static void icon(GuiGraphics graphics, String id, int x, int y, int size) {
        OriginalRewardUi.icon(graphics, id, x + size / 2F, y + size / 2F, size, 0xffffffff, 1);
    }
}
