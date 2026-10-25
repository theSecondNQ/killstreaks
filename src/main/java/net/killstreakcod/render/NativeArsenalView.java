package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletHandModel;
import net.killstreakcod.universal.NativeArsenalLibrary;
import net.killstreakcod.universal.NativeCurveSet;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/**
 * Native weapon/hand actions sampled in source inches and retargeted to block arms without fingers.
 * 按源资源英寸单位采样原武器和手部动作，再重定向到不显示手指的方块手臂。
 */
public final class NativeArsenalView {
    private enum Phase {
        NONE, RAISE, IDLE, CHARGE, FIRE, SETTLE, DROP
    }

    private static Phase phase = Phase.NONE;
    private static String family = "";
    private static float age;
    private static InteractionHand hand;
    private static boolean drawn;
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static TabletHandModel hands;
    private static TargetingAnimationClips.Frame from;

    private NativeArsenalView() {}

    private static String family(ItemStack stack) {
        return stack.is(KillstreakcodMod.M134_MINIGUN.get()) ? "m13421"
                : stack.is(KillstreakcodMod.CLUSTER_MINE.get()) ? "cluster21" : "";
    }

    private static InteractionHand held() {
        var player = Minecraft.getInstance().player;
        if (player == null)
            return null;
        for (var h : InteractionHand.values())
            if (!family(player.getItemInHand(h)).isEmpty())
                return h;
        return null;
    }

    private static void equip(InteractionHand next) {
        var mc = Minecraft.getInstance();
        if (next == null)
            return;
        family = family(mc.player.getItemInHand(next));
        if (TargetingAnimationClips.rig(family) == null || TargetingAnimationClips.ticks(family + "_raise") <= 0) {
            reset();
            return;
        }
        hand = next;
        owner = mc.player;
        world = mc.level;
        phase = Phase.RAISE;
        age = 0;
        from = null;
    }

    private static void change(Phase next) {
        from = sample(0);
        phase = next;
        age = 0;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()
                || TacticalClient.remote() || StreakPresentation.busy() || MgbEffects.interruptingControl()) {
            reset();
            return;
        }
        if (owner != null && (owner != mc.player || world != mc.level))
            reset();
        if (mc.isPaused())
            return;
        InteractionHand next = held();
        if (phase == Phase.NONE) {
            equip(next);
            return;
        }
        boolean same = next == hand && next != null && family(mc.player.getItemInHand(next)).equals(family);
        if (!same && phase != Phase.DROP)
            change(Phase.DROP);
        if (same && family.equals("m13421")) {
            boolean firing = HandheldWeaponInput.using(hand);
            if (firing) {
                Phase wanted = HandheldWeaponInput.ticks() < NativeArsenalLibrary.CHARGE_TICKS ? Phase.CHARGE
                        : Phase.FIRE;
                if (phase != wanted)
                    change(wanted);
            } else if (phase == Phase.CHARGE || phase == Phase.FIRE)
                change(Phase.SETTLE);
        }
        age++;
        if (phase == Phase.RAISE && age >= duration("raise")
                || phase == Phase.SETTLE && age >= duration("fire_settle")) {
            phase = Phase.IDLE;
            age = 0;
            from = null;
        }
        if (phase == Phase.DROP && age >= duration("drop")) {
            reset();
            equip(next);
        }
    }

    private static float duration(String action) {
        return Math.max(1, TargetingAnimationClips.ticks(family + "_" + action));
    }

    private static TargetingAnimationClips.Frame sample(float partial) {
        if (phase == Phase.NONE)
            return null;
        String action = switch (phase) {
            case RAISE -> "raise";
            case CHARGE -> "charge";
            case FIRE -> "fire";
            case SETTLE -> "fire_settle";
            case DROP -> "drop";
            default -> "idle";
        };
        float time = age + partial;
        if (phase == Phase.CHARGE && owner != null)
            time = HandheldWeaponInput.ticks() + partial;
        if (phase == Phase.FIRE && owner != null)
            time = (HandheldWeaponInput.ticks() - NativeArsenalLibrary.CHARGE_TICKS + partial) % duration("fire");
        return TargetingAnimationClips.sample(family, action, time, phase == Phase.IDLE && family.equals("cluster21"),
                null, from, Math.min(1, (age + partial) / 2));
    }

    public static boolean active() {
        return phase != Phase.NONE && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }

    public static void beginFrame() {
        drawn = false;
    }

    public static boolean renderHands(RenderHandEvent event) {
        if (!active())
            return false;
        var mc = Minecraft.getInstance();
        if (drawn || mc.screen != null)
            return true;
        var frame = sample(event.getPartialTick());
        if (frame == null)
            return false;
        drawn = true;
        draw(frame, family.equals("m13421") ? "m13421_vm" : "cluster21_vm", hand, event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight(), phase == Phase.FIRE,
                HandheldWeaponInput.ticks() + event.getPartialTick());
        return true;
    }

    public static boolean clusterThrow(float ticks, PoseStack pose, MultiBufferSource buffers, int light) {
        String action = ticks < 8 ? "hold_fire" : ticks < 20 ? "fire" : "drop";
        float time = ticks - (ticks < 8 ? 0 : ticks < 20 ? 8 : 20);
        var frame = TargetingAnimationClips.sample("cluster21", action, time, false, null, null, 1);
        if (frame == null)
            return false;

        // 原资源投掷通知位于开火第一帧，物体释放后即移除手持显示。
        draw(frame, ticks < NativeArsenalLibrary.THROW_RELEASE ? "cluster21_vm" : "", InteractionHand.MAIN_HAND, pose,
                buffers, light, false, ticks);
        return true;
    }

    private static void draw(TargetingAnimationClips.Frame frame, String id, InteractionHand h, PoseStack pose,
            MultiBufferSource buffers, int light, boolean firing, float ticks) {
        var mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        if (hands == null)
            hands = new TabletHandModel();
        Map<String, Matrix4f> cm = new HashMap<>();
        frame.cameraBones.forEach(
                (n, m) -> cm.put(n, new Matrix4f(m).m30(m.m30() * 2.54F).m31(m.m31() * 2.54F).m32(m.m32() * 2.54F)));
        pose.pushPose();
        try {
            HumanoidArm arm = h == InteractionHand.MAIN_HAND ? mc.player.getMainArm()
                    : mc.player.getMainArm().getOpposite();
            if (arm == HumanoidArm.LEFT)
                pose.scale(-1, 1, 1);
            hands.renderTrackedHands(pose, buffers, mc.player.getSkinTextureLocation(), light, cm, false);
            if (!id.isEmpty()) {
                ImportedCastModel model = ImportedCastModel.get(id);
                if (model != null)
                    model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, cm);
            }
            if (id.equals("m13421_vm")) {
                // 与模型共用实际手持矩阵；收到的本机弹迹等到本帧炮口绘制时才固定世界起点。
                FirstPersonMuzzle.position(pose, cm.get("tag_flash")).ifPresent(TracerEffects::minigunMuzzle);
                if (firing)
                    MinigunMuzzleEffects.render(pose, buffers, cm.get("tag_flash"), ticks);
            }
        } finally {
            pose.popPose();
        }
    }

    public static Map<String, Matrix4f> gunPose(ImportedCastModel model, boolean firing, int using, float partial) {
        NativeCurveSet clip = NativeCurveSet.get(
                firing ? (using < NativeArsenalLibrary.CHARGE_TICKS ? "m13421_charge" : "m13421_fire") : "m13421_idle");
        float frame = firing
                ? (using < NativeArsenalLibrary.CHARGE_TICKS ? (using + partial) * clip.fps / 20
                        : ((using - NativeArsenalLibrary.CHARGE_TICKS + partial) * clip.fps / 20)
                                % Math.max(1, clip.last))
                : 0;
        Map<String, Matrix4f> result = new HashMap<>();
        var bones = model.bindBones();
        for (var b : bones) {
            Matrix4f local = new Matrix4f(b.localMatrix());
            if (b.name().equals("j_rotating_barrels"))
                local.mul(clip.local(b.name(), 0, b.localPosition(), b.localRotation(), b.scale()).invert()
                        .mul(clip.local(b.name(), frame, b.localPosition(), b.localRotation(), b.scale())));
            if (b.parent() >= 0)
                local = new Matrix4f(result.get(bones.get(b.parent()).name())).mul(local);
            result.put(b.name(), local);
        }
        return result;
    }

    public static void reset() {
        phase = Phase.NONE;
        family = "";
        age = 0;
        hand = null;
        owner = null;
        world = null;
        from = null;
        drawn = false;
        FirstPersonMuzzle.reset();
    }
}
