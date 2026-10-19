package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletHandModel;

import java.util.HashMap;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Original targeting gestures are equipment use, independent of reward acquisition/tablet calls.
 * 原指示设备手势属于装备使用动作，与奖励获得动画及平板呼叫分别处理。
 */
public final class TargetingDeviceAnimations {
    private enum Phase {
        NONE, RAISE, IDLE, FIRE, LOWER, DROP
    }

    private enum Aim {
        REST, UP, DOWN
    }

    private static Phase phase = Phase.NONE;
    private static Aim aim = Aim.REST;
    private static String reward = "", family = "";
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static float age, aimAge, aimStartZoom;
    private static boolean rendered;
    private static TargetingAnimationClips.Transform aimFrom;
    private static TargetingAnimationClips.Frame transitionFrom;
    private static TabletHandModel hands;
    private static final float LASER_GESTURE_RATE = 1.65F, SPOTTER_GESTURE_RATE = 1.5F, ADS_RATE = 1.6F,
            LASER_ZOOM_TICKS = 4;

    private TargetingDeviceAnimations() {}

    private static String reward(ItemStack stack) {
        if (!stack.is(KillstreakcodMod.DESIGNATOR.get()))
            return "";
        String id = stack.getOrCreateTag().getString("Reward");
        return id.equals("mortar_strike") ? id : "precision_airstrike";
    }

    private static InteractionHand held() {
        var player = Minecraft.getInstance().player;
        if (player == null)
            return null;
        if (player.isUsingItem())
            return player.getUseItem().is(KillstreakcodMod.DESIGNATOR.get()) ? player.getUsedItemHand() : null;
        if (player.getMainHandItem().is(KillstreakcodMod.DESIGNATOR.get()))
            return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(KillstreakcodMod.DESIGNATOR.get()))
            return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean aiming() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isUsingItem() && player.getUsedItemHand() == hand
                && reward(player.getUseItem()).equals(reward);
    }

    private static boolean available(String kind) {
        ImportedCastModel model = ImportedCastModel
                .get(kind.equals("laser") ? "designator_mortar_vm" : "designator_precision_vm");
        return model != null && Math.abs(model.sourceUnitScale() - .0254F) < .000001F
                && TargetingAnimationClips.available(kind);
    }

    private static void equip(InteractionHand next) {
        var player = Minecraft.getInstance().player;
        if (player == null || next == null)
            return;
        ItemStack stack = player.getItemInHand(next);
        String id = reward(stack), kind = id.equals("mortar_strike") ? "laser" : "spotter";
        if (id.isEmpty() || !available(kind))
            return;
        reward = id;
        family = kind;
        hand = next;
        phase = Phase.RAISE;
        age = aimAge = aimStartZoom = 0;
        aim = Aim.REST;
        aimFrom = null;
        transitionFrom = null;
    }

    private static float gestureRate() {
        return phase == Phase.RAISE || phase == Phase.DROP
                ? family.equals("laser") ? LASER_GESTURE_RATE : SPOTTER_GESTURE_RATE
                : 1;
    }

    private static float duration() {
        return (switch (phase) {
            case RAISE -> TargetingAnimationClips.ticks(family + "_raise");
            case FIRE -> TargetingAnimationClips.ticks(family + "_fire");
            case LOWER ->
                (TargetingAnimationClips.ticks("spotter_ads_down")
                        + TargetingAnimationClips.ticks("spotter_ads_settle"))
                        / ADS_RATE;
            case DROP -> TargetingAnimationClips.ticks(family + "_drop");
            default -> Float.POSITIVE_INFINITY;
        }) / gestureRate();
    }

    private static void changeAim(boolean up) {
        if (up && aim == Aim.UP || !up && aim != Aim.UP)
            return;
        var previous = sampleAim(0);
        float previousZoom = zoom(0);
        aim = up ? Aim.UP : Aim.DOWN;
        aimAge = 0;
        aimFrom = previous;
        aimStartZoom = previousZoom;
    }

    private static void stow() {
        transitionFrom = sample(0);
        if (family.equals("spotter") && zoom(0) > .001F) {
            changeAim(false);
            phase = Phase.LOWER;
            aimAge = 0;
        } else {
            changeAim(false);
            phase = Phase.DROP;
            aimFrom = null;
        }
        age = 0;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()
                || TacticalClient.remote() || StreakPresentation.busy() || MgbEffects.interruptingControl()) {
            reset();
            return;
        }
        if (mc.isPaused())
            return;
        InteractionHand next = held();
        if (phase == Phase.NONE) {
            equip(next);
            return;
        }
        boolean same = next == hand && next != null && reward(mc.player.getItemInHand(next)).equals(reward);
        if ((phase == Phase.IDLE || phase == Phase.RAISE) && !same)
            stow();
        if (phase == Phase.IDLE || phase == Phase.RAISE && family.equals("laser"))
            changeAim(aiming());
        age++;
        aimAge++;
        if (phase == Phase.IDLE) {
            if (age > 100000)
                age = 0;
            return;
        }
        float total = duration();
        if (age < total)
            return;
        float carry = age - total;
        switch (phase) {
            case RAISE -> {
                phase = Phase.IDLE;
                age = carry;
                transitionFrom = null;
                changeAim(aiming());
            }
            case FIRE -> {
                transitionFrom = sample(0);
                age = carry;
                if (family.equals("spotter")) {
                    changeAim(false);
                    phase = Phase.LOWER;
                    aimAge = carry;
                } else {
                    changeAim(false);
                    phase = Phase.DROP;
                }
            }
            case LOWER -> {
                phase = Phase.DROP;
                age = carry;
                aim = Aim.REST;
                aimFrom = null;
                transitionFrom = null;
            }
            case DROP -> {
                reset();
                equip(next);
            }
            default -> {
            }
        }
    }

    /**
     * Only a successful server designation starts the marking clip; airspace failures keep the item.
     * 仅在服务端成功确认目标后播放标记动作；空域检查失败时保留物品。
     */
    public static void confirm(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (!id.equals("precision_airstrike") && !id.equals("mortar_strike"))
            return;
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || TacticalClient.remote()
                || StreakPresentation.busy() || MgbEffects.interruptingControl())
            return;
        String kind = id.equals("mortar_strike") ? "laser" : "spotter";
        if (!available(kind))
            return;
        if (!family.equals(kind) || phase == Phase.NONE) {
            reset();
            reward = id;
            family = kind;
            hand = mc.player.getUsedItemHand();
            aim = Aim.UP;
            aimAge = kind.equals("spotter") ? TargetingAnimationClips.ticks("spotter_ads_up") / ADS_RATE
                    : LASER_ZOOM_TICKS;
        }
        transitionFrom = sample(0);
        phase = Phase.FIRE;
        age = 0;
        changeAim(true);
        if (id.equals("precision_airstrike"))
            NativePrecisionScopeHud.marked();
    }

    private static TargetingAnimationClips.Transform sampleAim(float partial) {
        if (!family.equals("spotter") || aim == Aim.REST)
            return null;
        float time = (aimAge + partial) * ADS_RATE, down = TargetingAnimationClips.ticks("spotter_ads_down");
        var result = aim == Aim.UP ? TargetingAnimationClips.aim("spotter_ads_up", time)
                : time < down ? TargetingAnimationClips.aim("spotter_ads_down", time)
                        : TargetingAnimationClips.aim("spotter_ads_settle", time - down);
        if (result != null && aimFrom != null && time < 2) {
            float f = Mth.clamp(time / 2, 0, 1);
            result.position.set(new Vector3f(aimFrom.position).lerp(result.position, f));
            result.scale.set(new Vector3f(aimFrom.scale).lerp(result.scale, f));
            Quaternionf target = new Quaternionf(result.rotation);
            if (aimFrom.rotation.dot(target) < 0)
                target.mul(-1);
            result.rotation.set(aimFrom.rotation).slerp(target, f).normalize();
        }
        return result;
    }

    private static TargetingAnimationClips.Frame sample(float partial) {
        if (phase == Phase.NONE || family.isEmpty())
            return null;
        String action = switch (phase) {
            case RAISE -> "raise";
            case FIRE -> "fire";
            case DROP -> "drop";
            default -> "idle";
        };
        float time = (age + partial) * gestureRate();
        return TargetingAnimationClips.sample(family, action, time, phase == Phase.IDLE, sampleAim(partial),
                transitionFrom, Mth.clamp(time / 2, 0, 1));
    }

    public static float zoom(float partial) {
        if (phase == Phase.NONE || aim == Aim.REST || family.equals("spotter") && phase == Phase.DROP)
            return 0;
        float duration = family.equals("laser") ? LASER_ZOOM_TICKS
                : TargetingAnimationClips.ticks(aim == Aim.UP ? "spotter_ads_up" : "spotter_ads_down") / ADS_RATE,
                f = Mth.clamp((aimAge + partial) / Math.max(1, duration), 0, 1);
        return aim == Aim.UP ? Mth.lerp(f, aimStartZoom, 1) : aimStartZoom * (1 - f);
    }

    public static float aimedFov() {
        return family.equals("laser") ? 40 : 28;
    }

    public static boolean opticsVisible() {
        return active() && family.equals("spotter") && zoom(partial()) > .85F;
    }

    public static boolean precisionFullyAimed() {
        return active() && family.equals("spotter") && phase == Phase.IDLE && aiming() && zoom(0) >= .999F;
    }

    public static boolean precisionFiring() {
        return active() && family.equals("spotter") && phase == Phase.FIRE;
    }

    public static boolean laserMarking() {
        return active() && family.equals("laser") && (aiming() || phase == Phase.FIRE);
    }

    public static boolean blocksUse() {
        return active() && (phase == Phase.FIRE || phase == Phase.LOWER || phase == Phase.DROP);
    }

    public static boolean active() {
        return phase != Phase.NONE && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private static float partial() {
        return Minecraft.getInstance().getFrameTime();
    }

    public static void beginFrame() {
        rendered = false;
    }

    public static boolean renderHands(RenderHandEvent event) {
        if (!active())
            return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || rendered)
            return true;
        var frame = sample(event.getPartialTick());
        ImportedCastModel model = ImportedCastModel
                .get(family.equals("laser") ? "designator_mortar_vm" : "designator_precision_vm");
        if (frame == null || model == null)
            return false;
        rendered = true;

        // 原瞄准镜几何在 IW 武器渲染阶段包围镜片；完全进入瞄准后，视图由 Minecraft 全屏光学画面接管。
        // 在该画面之外保留原抬起、稳定、标记和收起姿态。
        if (family.equals("spotter") && zoom(event.getPartialTick()) > .85F)
            return true;
        if (hands == null)
            hands = new TabletHandModel();
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            HumanoidArm side = hand == InteractionHand.MAIN_HAND ? mc.player.getMainArm()
                    : mc.player.getMainArm().getOpposite();
            if (side == HumanoidArm.LEFT)
                pose.scale(-1, 1, 1);

            // 保留的 Minecraft 皮肤手臂适配器使用厘米单位，因此仅转换位移部分。
            HashMap<String, Matrix4f> armBones = new HashMap<>();
            frame.cameraBones.forEach((name, m) -> armBones.put(name,
                    new Matrix4f(m).m30(m.m30() * 2.54F).m31(m.m31() * 2.54F).m32(m.m32() * 2.54F)));
            hands.renderTrackedHands(pose, event.getMultiBufferSource(), mc.player.getSkinTextureLocation(),
                    event.getPackedLight(), armBones, false);
            model.renderScene(pose, event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                    frame.cameraBones);
        } finally {
            pose.popPose();
        }
        return true;
    }

    public static void reset() {
        phase = Phase.NONE;
        aim = Aim.REST;
        reward = family = "";
        age = aimAge = aimStartZoom = 0;
        aimFrom = null;
        transitionFrom = null;
        rendered = false;
        NativePrecisionScopeHud.reset();
    }

    public static void reload() {
        reset();
        hands = null;
        NativeArsenalView.reset();
        TargetingAnimationClips.clear();
    }
}
