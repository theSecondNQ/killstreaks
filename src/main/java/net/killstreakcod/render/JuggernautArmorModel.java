package net.killstreakcod.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Slot marker for vanilla armor passes; OriginalJuggernautLayer draws the source meshes.
 * 作为原版护甲绘制阶段的槽位标记，实际原资源网格由 OriginalJuggernautLayer 绘制。
 */
public final class JuggernautArmorModel extends HumanoidModel<LivingEntity> {
    public JuggernautArmorModel() {
        super(layer().bakeRoot());
    }

    public JuggernautArmorModel forSlot(EquipmentSlot slot) {
        setAllVisible(false);
        return this;
    }

    private static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (String name : new String[] { "head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg" })
            root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }
}
