package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.DroneDeploymentSounds;
import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.SceneEntryAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.killstreaks.NativeCruiseFlight;
import net.killstreakcod.killstreaks.TacticalEquipmentItem;
import net.killstreakcod.render.tablet.SceneTabletScreen;
import net.killstreakcod.render.tablet.TabletHandModel;
import net.killstreakcod.render.tablet.TabletTargeting;
import net.killstreakcod.universal.NativeCurveSet;
import net.killstreakcod.universal.SceneEntryLibrary;
import net.killstreakcod.universal.StreakTimings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraftforge.client.event.RenderHandEvent;

/** 播放呼叫、入场和收起动作，协调平板与视角过渡；连杀激活及遥控状态由服务端管理。 */
public final class StreakPresentation {
    private static String reward = "", stage = "";
    private static int duration, age;
    private static boolean handsRendered;
    private static TabletHandModel hands;
    private static float renderedTime;
    private static float initialVisibility, lastVisibility, initialStow, lastStow;
    private static boolean remoteExit, remoteTabletCall;
    private static int soundMask;
    private static net.minecraft.client.multiplayer.ClientLevel handoffLevel;
    private static long handoffUntil;
    private static ItemStack deployment = ItemStack.EMPTY;
    private static net.minecraft.client.CameraType exitCameraType;
    private static net.minecraft.client.player.LocalPlayer exitPlayer;
    private static net.minecraft.client.multiplayer.ClientLevel exitLevel;

    private StreakPresentation() {}

    public static void start(String id, String phase, int ticks) {
        handoffLevel = null;
        if (phase.equals("reset")) {
            reset();
            return;
        }
        boolean tabletCall = phase.equals("remote_call");
        if (tabletCall)
            phase = "call";
        if (!phase.startsWith("crash_"))
            AircraftCrashClient.reset();
        restoreExitCamera();
        if (phase.equals("confirmed") && !id.equals("sae") && !id.equals("vtol_jet") && !id.equals("stealth_bomber")
                && !id.equals("sentry_gun") && !id.equals("cluster_mine"))
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || MgbEffects.interruptingControl())
            return;
        if (!phase.equals("call") && !phase.equals("acquire") && !phase.equals("entry") && !phase.equals("confirmed")
                && !phase.equals("exit") && !phase.startsWith("crash_"))
            return;
        boolean transitionSoundPlayed = active() && reward.equals(id) && stage.equals("call") && (soundMask & 256) != 0;
        float previous = active() && reward.equals(id) ? lastVisibility : 0;
        float previousStow = active() && reward.equals(id) ? lastStow : 0;
        boolean cancelCall = stage.equals("call") && active();
        remoteExit = phase.equals("exit") && (TacticalClient.remote() || (!cancelCall && controlledReward(id)));
        if (remoteExit) {
            exitCameraType = mc.options.getCameraType();
            exitPlayer = mc.player;
            exitLevel = mc.level;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }
        initialVisibility = remoteExit ? 1 : previous;
        initialStow = previousStow;
        reward = id;
        stage = phase;
        remoteTabletCall = tabletCall;
        if (tabletCall)
            TargetingDeviceAnimations.reset();
        duration = Mth.clamp(ticks, 2, 240);
        age = 0;
        renderedTime = 0;
        soundMask = 0;
        handsRendered = false;
        deployment = ItemStack.EMPTY;
        SceneTabletScreen.begin(id, phase);
        if (stage.equals("confirmed") && mapReward()) {
            initialVisibility = 1;
            initialStow = 0;
        }
        if (stage.equals("entry")) {
            EntryScenes.begin(mc.player.getYRot());
            if (!transitionSoundPlayed)
                SceneEntryAudio.transition(id);
        }
        if (equipmentAction() && reward.equals("sentry_gun"))
            deployment = new ItemStack(KillstreakcodMod.SENTRY_GUN.get());
        if (equipmentAction() && reward.equals("cluster_mine"))
            deployment = new ItemStack(KillstreakcodMod.CLUSTER_MINE.get());
        if (stage.equals("acquire") && reward.equals("wheelson_hs"))
            deployment = new ItemStack(KillstreakcodMod.WHEELSON_CONTROLLER.get());
        if (designatorAction())
            deployment = TacticalEquipmentItem.stack("designator", reward);
        if (markerAction())
            deployment = TacticalEquipmentItem.stack("drop_marker", reward);
        if (busy()) {
            mc.player.stopUsingItem();
            lowerNormalEquipment();
        }
        if (stage.equals("call") && !equipmentAction() && !reward.equals("bomb_drone")) {
            play(StreakSounds.TABLET_GEAR.get(), .34F);
            soundMask |= 1;
            if (reward.equals("mgb"))
                play(StreakSounds.MGB_TABLET.get(), .42F);
        }
        if (equipmentAction() && stage.equals("call") && reward.equals("sentry_gun"))
            play(StreakSounds.SENTRY_PICKUP.get(), .40F);
    }

    private static void restoreExitCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (exitCameraType != null && mc.player == exitPlayer && mc.level == exitLevel)
            mc.options.setCameraType(exitCameraType);
        exitCameraType = null;
        exitPlayer = null;
        exitLevel = null;
    }

    public static void reset() {
        handoffLevel = null;
        AircraftCrashClient.reset();
        restoreExitCamera();
        DroneDeploymentSounds.stop();
        reward = "";
        stage = "";
        duration = age = soundMask = 0;
        renderedTime = initialVisibility = lastVisibility = initialStow = lastStow = 0;
        handsRendered = remoteExit = remoteTabletCall = false;
        deployment = ItemStack.EMPTY;
        EntryScenes.reset();
    }

    public static boolean calling() {
        return active() && (stage.equals("call") || stage.equals("acquire") || stage.equals("confirmed"));
    }

    public static boolean transitioning() {
        return active() && (stage.equals("entry") || stage.equals("exit") || stage.startsWith("crash_"));
    }

    public static boolean remoteHudVisible() {
        return !AircraftCrashClient.active() && (!active() || !stage.equals("entry") || progress() > .985F);
    }

    public static String currentReward() {
        return reward;
    }

    public static String currentStage() {
        return stage;
    }

    public static float elapsed() {
        return active() ? animationTime(Minecraft.getInstance().getFrameTime()) : 0;
    }

    private static boolean designatorAction() {
        return stage.equals("call") && !remoteTabletCall
                && (reward.equals("precision_airstrike") || reward.equals("mortar_strike"));
    }

    private static boolean markerAction() {
        return stage.equals("call")
                && (reward.equals("care_package") || reward.equals("emergency_airdrop") || reward.equals("juggernaut"));
    }

    private static boolean equipmentAction() {
        return stage.equals("acquire") || designatorAction() || markerAction()
                || (stage.equals("call") || stage.equals("confirmed"))
                        && (reward.equals("sentry_gun") || reward.equals("cluster_mine"));
    }

    private static boolean remoteCall() {
        return stage.equals("call") && (remoteTabletCall || StreakTimings.remoteReward(reward));
    }

    private static String animationStage() {
        return stage.equals("call") && remoteTabletCall ? "remote_call" : stage;
    }

    private static boolean active() {
        return !stage.isEmpty() && age < duration;
    }

    public static boolean busy() {
        return calling() || transitioning() || TabletTargeting.active();
    }

    public static boolean oneHandCall() {
        var player = Minecraft.getInstance().player;
        return calling() && compactTerminal() && player != null && player.getMainArm() == HumanoidArm.RIGHT
                && !(player.getMainHandItem().getItem() instanceof MapItem);
    }

    public static void beginFrame() {
        handsRendered = false;
        if (active())
            animationTime(Minecraft.getInstance().getFrameTime());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            reset();
            return;
        }
        if (!active())
            return;

        if (stage.equals("entry") && TacticalClient.remote() && !TacticalClient.cameraReady())
            return;

        // 丢弃动画播放期间的按键，避免它们在之后误触发已储存的奖励。
        for (var mapping : new net.minecraft.client.KeyMapping[] { KillstreakKeyMappings.USE_SLOT_1,
                KillstreakKeyMappings.USE_SLOT_2, KillstreakKeyMappings.USE_SLOT_3, KillstreakKeyMappings.BONUS,
                KillstreakKeyMappings.MGB, KillstreakKeyMappings.OPEN_TERMINAL, KillstreakKeyMappings.BOOST })
            while (mapping.consumeClick()) {
            }
        if (!mc.isPaused()) {
            audio();
            lowerNormalEquipment();
            if (++age >= duration) {
                boolean crashOutro = stage.equals("crash_gunship"), handoff = remoteCall();
                reset();
                if (handoff) {
                    handoffLevel = mc.level;
                    handoffUntil = mc.level.getGameTime() + 8;
                }
                if (crashOutro && !MgbEffects.interruptingControl())
                    start("gunship", "exit", StreakTimings.exit("gunship"));
            }
        }
    }

    public static boolean renderHands(RenderHandEvent event) {
        if (!busy())
            return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return true;
        if (AircraftCrashClient.active())
            return true;
        if (handsRendered)
            return !(oneHandCall() && event.getHand() == InteractionHand.MAIN_HAND);
        handsRendered = true;
        if (hands == null)
            hands = new TabletHandModel();
        if (TabletTargeting.active()) {
            OriginalStreakAnimations.render(TabletTargeting.reward(), "targeting", TabletTargeting.elapsed(),
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), hands,
                    mc.player.getSkinTextureLocation());
            return true;
        }
        float time = animationTime(event.getPartialTick()), p = Mth.clamp(time / duration, 0, 1);
        if (stage.equals("entry")
                && (reward.equals("gunship") || reward.equals("chopper_gunner") || reward.equals("cruise_missile"))) {
            lastVisibility = 0;
            return true;
        } // 三维过场及其最后的传感器淡入淡出不在手部渲染阶段绘制。
        if (reward.equals("cluster_mine") && stage.equals("confirmed") && NativeArsenalView.clusterThrow(time,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight()))
            return true;
        boolean original = (!stage.equals("exit") || remoteExit)
                && OriginalStreakAnimations.available(reward, animationStage());
        if (original) {
            if (OriginalStreakAnimations.render(reward, animationStage(), time, event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight(), hands, mc.player.getSkinTextureLocation())) {
                lastVisibility = stage.equals("call")
                        ? ease(p / .27F) * (mapReward() || remoteCall() ? 1 : 1 - ease((p - .72F) / .28F))
                        : 0;
                return TacticalClient.remote() || !(oneHandCall() && event.getHand() == InteractionHand.MAIN_HAND);
            }
        }
        if (TacticalClient.remote()) {
            lastVisibility = 0;
            return true;
        }
        if (!deployment.isEmpty()) {
            if (designatorAction())
                hands.renderTakeDesignator(event.getPoseStack(), event.getMultiBufferSource(),
                        mc.player.getSkinTextureLocation(), event.getPackedLight(), deployment, p,
                        reward.equals("mortar_strike"));
            else if (stage.equals("call") || stage.equals("acquire"))
                hands.renderTakeEquipment(event.getPoseStack(), event.getMultiBufferSource(),
                        mc.player.getSkinTextureLocation(), event.getPackedLight(), deployment, p);
            else if (reward.equals("cluster_mine"))
                hands.renderThrowEquipment(event.getPoseStack(), event.getMultiBufferSource(),
                        mc.player.getSkinTextureLocation(), event.getPackedLight(), deployment, p);
            else
                hands.renderDeployment(event.getPoseStack(), event.getMultiBufferSource(),
                        mc.player.getSkinTextureLocation(), event.getPackedLight(), deployment, p);
            return true;
        }
        boolean confirmation = stage.equals("confirmed");
        float stow = stage.equals("exit") ? Mth.lerp(ease(p), initialStow, 1)
                : stage.equals("entry") ? ease(p / .55F)
                        : ease((p - (confirmation ? .70F : .72F)) / (confirmation ? .30F : .28F));
        if (stage.equals("call") && (mapReward() || remoteCall()))
            stow = 0;
        float raise = stage.equals("exit") || stage.equals("entry") ? initialVisibility
                : Mth.lerp(ease(p / (confirmation ? .22F : .27F)), initialVisibility, 1);
        float visible = stage.equals("exit") ? initialVisibility * (1 - ease(p)) : raise * (1 - stow);
        lastVisibility = visible;
        lastStow = stow;
        float tap = confirmation ? ease((p - .25F) / .11F) * (1 - ease((p - .43F) / .17F))
                : ease((p - .34F) / .13F) * (1 - ease((p - .53F) / .15F));
        if (stage.equals("call") && mapReward())
            tap = 0;
        if (stage.equals("exit") || stage.equals("entry"))
            tap = 0;
        float transmit = ease((p - (confirmation ? .43F : .53F)) / .17F);
        float movement = (float) Math.min(1, mc.player.getDeltaMovement().horizontalDistance() * 4);
        if (visible > .005F) {
            if (compactTerminal())
                hands.renderCompact(event.getPoseStack(), event.getMultiBufferSource(),
                        mc.player.getSkinTextureLocation(), event.getPackedLight(), visible, tap, time, stow, movement,
                        transmit, reward);
            else
                hands.render(event.getPoseStack(), event.getMultiBufferSource(), mc.player.getSkinTextureLocation(),
                        event.getPackedLight(), visible, tap, time, stow, movement, transmit, false, confirmation,
                        reward);
        }
        return !(oneHandCall() && event.getHand() == InteractionHand.MAIN_HAND);
    }

    public static float fovAdjustment() {
        if (!stage.equals("entry") || !active())
            return 0;
        float remain = 1 - ease(progress());
        return switch (profile()) {
            case "gunship" -> 18 * remain;
            case "chopper" -> 12 * remain;
            case "cruise" -> 24 * remain;
            default -> 8 * remain;
        };
    }

    public static float cameraRoll() {
        if (EntryScenes.active())
            return 0;
        if (stage.equals("entry") && (reward.equals("chopper_gunner") || reward.equals("cruise_missile")))
            return 0;
        if (!stage.equals("entry") || !active())
            return 0;
        if (OriginalStreakAnimations.available(reward, stage))
            return OriginalStreakAnimations.cameraRoll(reward, stage,
                    animationTime(Minecraft.getInstance().getFrameTime()))
                    * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        float p = progress(), remain = 1 - ease(p);
        return (switch (profile()) {
            case "gunship" -> -7 * remain;
            case "chopper" -> (float) Math.sin((age + partial()) * .8) * 1.8F * remain;
            case "cruise" -> 4 * remain;
            default -> 0;
        }) * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
    }

    public static void renderGui(GuiGraphics graphics) {
        try (UiLayout.Scope layoutFrame = UiLayout.frame("hud.presentation")) {
            if (!active())
                return;
            if (AircraftCrashClient.active())
                return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null)
                return;
            int w = graphics.guiWidth(), h = graphics.guiHeight();
            float p = progress();
            if (stage.equals("entry")) {

                // 摄像机切换和原过场保留黑屏淡入淡出，不绘制连接状态标签。
                float scale = Math.min(1, Math.min(w / 260F, h / 190F));
                graphics.pose().pushPose();
                graphics.pose().scale(scale, scale, 1);
                try {
                    entry(graphics, Math.round(w / scale), Math.round(h / scale), p);
                } finally {
                    graphics.pose().popPose();
                }
                return;
            }
            // 呼叫状态由装备动画和原 HUD 表达，不再覆盖上下缘的文字或进度条。
        }
    }

    /**
     * Camera cuts remain part of the movie when the player hides HUD with F1.
     * 玩家按 F1 隐藏 HUD 后，摄像机切换仍作为过场的一部分执行。
     */
    public static void renderBlackout(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (TacticalClient.remote() && !TacticalClient.cameraReady()) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
            return;
        }
        if (!active() && (handoffLevel == null || handoffLevel != mc.level || mc.level.getGameTime() >= handoffUntil))
            return;
        float opacity = !active() ? ease((handoffUntil - mc.level.getGameTime()) / 2F)
                : remoteCall() ? ease((elapsed() - StreakTimings.remoteTabletOffset(reward) - 28) / 6)
                        : stage.equals("entry")
                                ? reward.equals("precision_airstrike") ? age <= 8 ? 1 : 1 - ease((elapsed() - 8) / 8)
                                        : 1 - ease(elapsed() / 8)
                                : 0;
        int cut = (int) (255 * opacity);
        if (cut > 0)
            UiDraw.fill(graphics, "StreakPresentation.renderBlackout.0", 0, 0, graphics.guiWidth(),
                    graphics.guiHeight(), cut << 24);
    }

    private static void entry(GuiGraphics graphics, int w, int h, float p) {
        float time = elapsed(), black = Math.max(0, 1 - p / .38F);
        switch (profile()) {
            case "gunship" -> {
                float stationCut = 1 - ease(Math.abs(time - EntryScenes.GUNSHIP_FLYPAST) / 3.5F);
                float sensorEnd = SceneEntryLibrary.GUNSHIP_EXTERIOR + SceneEntryLibrary.GUNSHIP_INTERIOR;
                float sensorCut = time < sensorEnd ? ease((time - sensorEnd + 4) / 4)
                        : 1 - ease((time - sensorEnd) / 2);
                black = Math.max(1 - ease(time / 8), Math.max(stationCut, sensorCut));
                if (time >= sensorEnd)
                    sensorReticle(graphics, w / 2, h / 2, 24,
                            alpha(0xb8c7c2, (int) (180 * ease((time - sensorEnd) / 2))));
            }
            case "chopper" -> black = Math.max(1 - ease(time / 10), ease((time - 91) / 6));
            case "cruise" -> black = Math.max(1 - ease(time / 6), ease((time - NativeCruiseFlight.BLACK_TICK) / 3));
            default -> {
            }
        }
        if (black > 0)
            UiDraw.fill(graphics, "StreakPresentation.blackout", 0, 0, w, h, alpha(0x020606, (int) (black * 255)));
    }

    private static void sensorReticle(GuiGraphics graphics, int x, int y, int r, int color) {
        int arm = Math.max(4, r / 4);
        UiDraw.hLine(graphics, "StreakPresentation.sensorReticle.0", x - r, x - r + arm, y - r, color);
        UiDraw.vLine(graphics, "StreakPresentation.sensorReticle.1", x - r, y - r, y - r + arm, color);
        UiDraw.hLine(graphics, "StreakPresentation.sensorReticle.2", x + r - arm, x + r, y - r, color);
        UiDraw.vLine(graphics, "StreakPresentation.sensorReticle.3", x + r, y - r, y - r + arm, color);
        UiDraw.hLine(graphics, "StreakPresentation.sensorReticle.4", x - r, x - r + arm, y + r, color);
        UiDraw.vLine(graphics, "StreakPresentation.sensorReticle.5", x - r, y + r - arm, y + r, color);
        UiDraw.hLine(graphics, "StreakPresentation.sensorReticle.6", x + r - arm, x + r, y + r, color);
        UiDraw.vLine(graphics, "StreakPresentation.sensorReticle.7", x + r, y + r - arm, y + r, color);
        UiDraw.hLine(graphics, "StreakPresentation.sensorReticle.8", x - 3, x + 3, y, color);
        UiDraw.vLine(graphics, "StreakPresentation.sensorReticle.9", x, y - 3, y + 3, color);
    }

    private static void play(SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1, volume));
    }

    private static void audio() {
        if (stage.equals("call") && reward.equals("bomb_drone")) {
            if ((soundMask & 64) == 0) {
                play(StreakSounds.BOMB_DRONE_ACTIVATE.get(), .7F);
                soundMask |= 64;
            }
            if ((soundMask & 128) == 0
                    && age >= NativeCurveSet.get("bomb_raise").notifyTick("ps_assault_drone_activate_gear_02", 7)) {
                play(NativeAudio.sfx("bomb_drone_gear2", StreakSounds.BOMB_DRONE_ACTIVATE.get()), .65F);
                soundMask |= 128;
            }
            if (age >= StreakTimings.BOMB_DEPLOY - 12)
                DroneDeploymentSounds.start();
        } else
            DroneDeploymentSounds.stop();
        if (stage.equals("entry") && reward.equals("cruise_missile")) {
            if ((soundMask & 16) == 0 && age >= NativeCruiseFlight.SHELL_TICK) {
                TacticalClient.impact(1.1F);
                soundMask |= 16;
            }
            if ((soundMask & 32) == 0 && age >= NativeCruiseFlight.THRUSTER_TICK) {
                TacticalClient.impact(1.4F);
                soundMask |= 32;
            }
            return;
        }
        if (!calling() || equipmentAction() || reward.equals("bomb_drone") && age < StreakTimings.BOMB_DEPLOY)
            return;
        if (stage.equals("call") && StreakTimings.phoneReward(reward)) {
            if ((soundMask & 2) == 0 && age >= 11 * 20 / 30F) {
                play(StreakSounds.PHONE_UI.get(), .42F);
                soundMask |= 2;
            }
            return;
        }
        if (reward.equals("bomb_drone") && (soundMask & 1) == 0) {
            play(StreakSounds.TABLET_GEAR.get(), .34F);
            soundMask |= 1;
        }
        String action = OriginalStreakAnimations.actionId(reward, animationStage(), age);
        if (stage.equals("call") && (soundMask & 2) == 0 && age >= StreakTimings.remoteTabletOffset(reward) + 3) {
            play(compactTerminal() ? StreakSounds.PHONE_UI.get() : StreakSounds.TABLET_ON.get(), .42F);
            soundMask |= 2;
        }
        boolean selected = action.equals("tablet_tap") || action.equals("scene_vm_ks_tablet_fire_tap")
                || action.equals("smartphone") && age >= duration * .38F || action.isEmpty() && age >= duration * .42F;
        if ((soundMask & 4) == 0 && selected) {
            play(StreakSounds.TABLET_SELECT.get(), .4F);
            soundMask |= 4;
        }
        boolean confirmed = remoteCall() && age - StreakTimings.remoteTabletOffset(reward) >= 28
                || action.endsWith("drop") || action.equals("smartphone") && age >= duration * .62F
                || action.isEmpty() && age >= duration * .62F;
        if ((soundMask & 8) == 0 && confirmed) {
            play(StreakSounds.TABLET_CONFIRM.get(), .42F);
            soundMask |= 8;
        }
        if (remoteCall() && (soundMask & 256) == 0 && age - StreakTimings.remoteTabletOffset(reward) >= 28) {
            SceneEntryAudio.transition(reward);
            soundMask |= 256;
        }
    }

    private static void lowerNormalEquipment() {
        if (compactTerminal())
            return;
        var renderer = Minecraft.getInstance().gameRenderer.itemInHandRenderer;
        if (!oneHandCall())
            renderer.itemUsed(InteractionHand.MAIN_HAND);
        renderer.itemUsed(InteractionHand.OFF_HAND);
    }

    private static int alpha(int color, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | color & 0xffffff;
    }

    private static float progress() {
        return Mth.clamp((age + partial()) / duration, 0, 1);
    }

    private static float partial() {
        return animationTime(Minecraft.getInstance().getFrameTime()) - age;
    }

    private static float animationTime(float partial) {
        if (stage.equals("entry") && TacticalClient.remote() && !TacticalClient.cameraReady())
            return renderedTime;
        if (!Minecraft.getInstance().isPaused())
            renderedTime = Math.max(renderedTime, Math.min(duration, age + partial));
        return renderedTime;
    }

    private static float ease(float value) {
        float n = Mth.clamp(value, 0, 1);
        return n * n * (3 - 2 * n);
    }

    private static boolean compactTerminal() {
        return StreakTimings.phoneReward(reward);
    }

    private static boolean mapReward() {
        return reward.equals("sae") || reward.equals("vtol_jet") || reward.equals("stealth_bomber");
    }

    private static boolean controlledReward(String id) {
        return switch (id) {
            case "gunship", "chopper_gunner", "cruise_missile", "bomb_drone", "wheelson_hs" -> true;
            default -> false;
        };
    }

    private static String profile() {
        return switch (reward) {
            case "gunship" -> "gunship";
            case "chopper_gunner", "chopper" -> "chopper";
            case "cruise_missile", "cruise" -> "cruise";
            case "wheelson_hs", "wheelson" -> "wheelson";
            default -> "drone";
        };
    }
}
