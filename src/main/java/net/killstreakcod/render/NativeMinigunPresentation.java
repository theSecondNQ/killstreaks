package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.NativeCurveSet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Original M134 charge/fire joint curves and hand sockets, fitted to Minecraft's block arms.
 * 使用原 M134 蓄转与开火关节曲线及手部挂点，适配 Minecraft 方块手臂。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class NativeMinigunPresentation {
    private NativeMinigunPresentation() {}

    private static final java.util.Map<net.minecraft.world.item.ItemStack, java.lang.ref.WeakReference<LivingEntity>> OWNERS = new java.util.WeakHashMap<>();

    public static LivingEntity wielder(net.minecraft.world.item.ItemStack stack) {
        var owner = OWNERS.get(stack);
        return owner == null ? null : owner.get();
    }

    private static NativeCurveSet clip(boolean using, int ticks) {
        return NativeCurveSet.get(using ? (ticks < 14 ? "m134_spinup" : "m134_fire") : "m134_hold");
    }

    private static float frame(NativeCurveSet clip, boolean using, int ticks, float partial) {
        float clock = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() + partial;
        return using
                ? (ticks < 14 ? Math.min(clip.last, (ticks + partial) * clip.fps / 20)
                        : ((ticks - 14 + partial) * clip.fps / 20) % Math.max(1, clip.last))
                : Math.min(clip.last, 180 + 2 * (float) Math.sin(clock * .075F));
    }

    public static Map<String, Matrix4f> wrists(ImportedCastModel model, Map<String, Matrix4f> pose, float fit) {
        var result = new HashMap<String, Matrix4f>();
        Vector3f origin = model.sourceOrigin();
        for (String side : new String[] { "le", "ri" }) {
            Matrix4f socket = pose.get("tag_ik_loc_" + side);
            if (socket == null)
                continue;
            var transformed = new Matrix4f(socket);
            transformed.m30((socket.m30() - origin.x) * fit).m31((socket.m31() - origin.y) * fit)
                    .m32((socket.m32() - origin.z) * fit);
            result.put("j_wrist_" + side, transformed);
        }
        return result;
    }

    public static void body(HumanoidModel<?> model, LivingEntity entity, float age) {
        if (!entity.getMainHandItem().is(KillstreakcodMod.M134_MINIGUN.get()))
            return;
        OWNERS.put(entity.getMainHandItem(), new java.lang.ref.WeakReference<>(entity));
        boolean local = entity == Minecraft.getInstance().player;
        boolean using = local ? HandheldWeaponInput.firing() : entity.isUsingItem();
        int ticks = local ? HandheldWeaponInput.ticks() : using ? entity.getTicksUsingItem() : 0;
        var source = clip(using, ticks);
        float frame = frame(source, using, ticks, age - (int) age);
        Vector3f left = motion(source, "j_shoulder_le", frame, using ? 0 : 180),
                right = motion(source, "j_shoulder_ri", frame, using ? 0 : 180);
        float sway = (float) Math.sin(age * .075F) * .015F;
        model.rightArm.xRot = -1.08F + model.head.xRot + right.y * .6F + sway;
        model.rightArm.yRot = model.head.yRot + right.z * .4F;
        model.rightArm.zRot = .03F + right.x * .4F;
        model.leftArm.xRot = -1.17F + model.head.xRot + left.y * .6F + sway;
        model.leftArm.yRot = model.head.yRot + .55F + left.z * .4F;
        model.leftArm.zRot = -.15F + left.x * .4F;
    }

    private static Vector3f motion(NativeCurveSet clip, String bone, float at, float reference) {
        Quaternionf a = clip.local(bone, at, new Vector3f(), new Quaternionf(), new Vector3f(1))
                .getNormalizedRotation(new Quaternionf());
        Quaternionf b = clip.local(bone, reference, new Vector3f(), new Quaternionf(), new Vector3f(1))
                .getNormalizedRotation(new Quaternionf());
        return b.invert().mul(a).getEulerAnglesXYZ(new Vector3f());
    }

    @SubscribeEvent
    public static void arm(RenderArmEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == event.getPlayer() && mc.player.getMainHandItem().is(KillstreakcodMod.M134_MINIGUN.get())
                && !StreakPresentation.busy() && !TacticalClient.remote())
            event.setCanceled(true);
    }
}
