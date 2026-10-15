package net.killstreakcod.render;

import net.killstreakcod.killstreaks.ArmorKit;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Scoped skin replacement; state is restored before armor/held-item layers run.
 * 仅在限定绘制范围内替换皮肤，在护甲及手持物图层执行前恢复状态。
 */
public final class OriginalJuggernautVisibility {
    private OriginalJuggernautVisibility() {}

    public static void renderBase(EntityModel<?> base, LivingEntity entity, Runnable draw) {
        if (!(base instanceof HumanoidModel<?> model) || entity.isInvisible() || entity.isSpectator()) {
            draw.run();
            return;
        }
        boolean head = entity.getItemBySlot(EquipmentSlot.HEAD).is(ArmorKit.HELMET.get());
        boolean chest = entity.getItemBySlot(EquipmentSlot.CHEST).is(ArmorKit.CHEST.get());

        // 人形腿部是整块网格；只穿护腿时仍需保留腿部，避免脱下靴子后裸露的脚消失。
        boolean legs = entity.getItemBySlot(EquipmentSlot.LEGS).is(ArmorKit.LEGS.get())
                && entity.getItemBySlot(EquipmentSlot.FEET).is(ArmorKit.BOOTS.get());
        if (!head && !chest && !legs) {
            draw.run();
            return;
        }
        if (ImportedCastModel.get("jugger22_chest_base") == null) {
            draw.run();
            return;
        }
        Map<ModelPart, Boolean> state = new IdentityHashMap<>();
        if (head) {
            hide(state, model.head);
            hide(state, model.hat);
        }
        if (chest) {
            hide(state, model.body);
            hide(state, model.leftArm);
            hide(state, model.rightArm);
        }
        if (legs) {
            hide(state, model.leftLeg);
            hide(state, model.rightLeg);
        }
        if (model instanceof PlayerModel<?> player) {
            if (chest) {
                hide(state, player.jacket);
                hide(state, player.leftSleeve);
                hide(state, player.rightSleeve);
            }
            if (legs) {
                hide(state, player.leftPants);
                hide(state, player.rightPants);
            }
        }
        try {
            draw.run();
        } finally {
            state.forEach((part, visible) -> part.visible = visible);
        }
    }

    private static void hide(Map<ModelPart, Boolean> state, ModelPart part) {
        state.putIfAbsent(part, part.visible);
        part.visible = false;
    }
}
