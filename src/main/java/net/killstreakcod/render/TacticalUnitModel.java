package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

/**
 * Articulated authored meshes using the 128-pixel material cells in the 512-pixel atlas.
 * 使用可活动的制作网格，512 像素图集中每个材质单元为 128 像素。
 */
public final class TacticalUnitModel extends EntityModel<TacticalUnitEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "tactical_units"), "main");
    private final ModelPart root;

    public TacticalUnitModel(ModelPart root) {
        this.root = root;
    }

    private static CubeListBuilder cube(int m, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs((m % 4) * 128, (m / 4) * 128).addBox(x, y, z, w, h, d);
    }

    private static PartDefinition part(PartDefinition p, String name, float x, float y, float z) {
        return p.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
    }

    private static PartDefinition box(PartDefinition p, String name, int m, float x, float y, float z, float w, float h,
            float d) {

        // 大型机身及舱盖之前的 UV 展开会越入相邻材质单元。
        if (2 * (w + d) > 124 || h + d > 124) {
            PartDefinition group = part(p, name, 0, 0, 0);
            int nx = (int) Math.ceil(w / 28), nz = (int) Math.ceil(d / 28);
            for (int i = 0; i < nx; i++)
                for (int j = 0; j < nz; j++)
                    box(group, "panel" + i + "_" + j, m, x + i * w / nx, y, z + j * d / nz, w / nx, h, d / nz);
            return group;
        }
        return p.addOrReplaceChild(name, cube(m, x, y, z, w, h, d), PartPose.ZERO);
    }

    private static void rotated(PartDefinition p, String name, int m, float x, float y, float z, float w, float h,
            float d, float pitch, float yaw, float roll) {
        p.addOrReplaceChild(name, cube(m, -w / 2, -h / 2, -d / 2, w, h, d),
                PartPose.offsetAndRotation(x, y, z, pitch, yaw, roll));
    }

    private static void rod(PartDefinition p, String name, int m, float x, float y, float z, float endX, float endY,
            float endZ, float thickness) {
        float dx = endX - x, dy = endY - y, dz = endZ - z, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        p.addOrReplaceChild(name, cube(m, -thickness / 2, -thickness / 2, 0, thickness, thickness, len),
                PartPose.offsetAndRotation(x, y, z, (float) -Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)),
                        (float) Math.atan2(dx, dz), 0));
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (Kind k : Kind.values()) {
            PartDefinition p = part(root, k.name(), 0, 24, 0);
            switch (k) {
                case OVERWATCH, CHOPPER, DELIVERY -> helicopter(p, k);
                case UAV, PRECISION, SAE, VTOL, STEALTH, GUNSHIP -> aircraft(p, k);
                case COUNTER_UAV -> {
                } // 按要求不显示机体，也不显示备用网格。
                case ADVANCED_UAV -> advancedDrone(p);
                case BOMB_DRONE -> drone(p);
                case SENTRY, WHEELSON -> turret(p, k);
                case CRATE, JUGG_CRATE -> crate(p, k);
                case CLUSTER, MINE -> {
                    box(p, "base", 1, -3, -2, -3, 6, 2, 6);
                    box(p, "cap", 8, -2, -3, -2, 4, 1, 4);
                    box(p, "led", 7, -.5F, -3.3F, -.5F, 1, .3F, 1);
                    for (int i = 0; i < 4; i++)
                        box(p, "bolt" + i, 1, i % 2 == 0 ? -2.5F : 2, -2.2F, i < 2 ? -2.5F : 2, .5F, .3F, .5F);
                    if (k == Kind.CLUSTER)
                        for (int i = 0; i < 5; i++) {
                            float a = i * Mth.TWO_PI / 5;
                            box(p, "charge" + i, 3, (float) Math.cos(a) * 3 - .8F, -1.4F, (float) Math.sin(a) * 3 - .8F,
                                    1.6F, 1, 1.6F);
                        }
                }
                case CRUISE, BOMB, ROCKET -> missile(p, k);
            }
        }
        return LayerDefinition.create(mesh, 512, 512);
    }

    private static void helicopter(PartDefinition p, Kind k) {
        boolean cargo = k == Kind.DELIVERY, attack = k == Kind.CHOPPER;
        float length = cargo ? 32 : attack ? 27 : 29, width = attack ? 9 : 14;
        box(p, "fuselage", 0, -width / 2, -15, -length / 2, width, 9, length);
        box(p, "belly", 8, -width / 2 + .8F, -6, -length / 2 + 3, width - 1.6F, 3, length - 5);
        box(p, "nose", 0, -width / 2 + 1, -11, -length / 2 - 5, width - 2, 5, 7);
        if (attack) {
            rotated(p, "pilot_glass", 2, 0, -17, -4, 6, 4, 8, .14F, 0, 0);
            rotated(p, "gunner_glass", 2, 0, -14, -13, 5.5F, 3.5F, 8, .2F, 0, 0);
            box(p, "canopy_frame", 1, -.4F, -19, -8, .8F, 5, 13);
            box(p, "nose_sensor", 9, -2.5F, -9, -length / 2 - 6, 5, 4, 3);
            box(p, "sensor_lens", 2, -1.2F, -8.3F, -length / 2 - 6.15F, 2.4F, 2.4F, .2F);
        } else {
            for (int s = 0; s < 2; s++) {
                float x = s == 0 ? -5.8F : .4F;
                rotated(p, "windscreen" + s, 2, x + 2.7F, -13, -length / 2 - 4, 5.4F, 5, .35F, -.23F, 0, 0);
                box(p, "door" + s, 8, s == 0 ? -width / 2 - .1F : width / 2 - .2F, -13, -3, .3F, 7, 12);
                box(p, "door_glass" + s, 2, s == 0 ? -width / 2 - .15F : width / 2 - .1F, -12, -1, .25F, 3.2F, 6);
                box(p, "door_handle" + s, 1, s == 0 ? -width / 2 - .3F : width / 2, -8.5F, 3, .3F, .5F, 2);
            }
            box(p, "windshield_divider", 1, -.4F, -16, -length / 2 - 4.2F, .8F, 8, .5F);
            box(p, "roof_sensor", 9, -2, -18, -4, 4, 3, 5);
            box(p, "roof_lens", 2, -1.2F, -17.4F, -4.2F, 2.4F, 1.5F, .3F);
        }
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -6 : 2;
            box(p, "engine" + s, 9, x, -18, 3, 4, 4, 10);
            box(p, "intake" + s, 3, x + .4F, -17.6F, 2.75F, 3.2F, 3.1F, .35F);
            box(p, "exhaust" + s, 1, x + .7F, -17, 13, 2.6F, 2.8F, 2);
            box(p, "exhaust_bore" + s, 3, x + 1, -16.7F, 15.05F, 2, 2.2F, .2F);
            for (int vent = 0; vent < 4; vent++)
                box(p, "engine_louvre" + s + "_" + vent, 1, x + .5F, -18.15F, 5 + vent * 1.5F, 3, .2F, .45F);
        }
        box(p, "tail", 0, -1.5F, -13, length / 2 - 1, 3, 3, 23);
        box(p, "tail_tip", 9, -1, -13, length / 2 + 19, 2, 4, 5);
        rotated(p, "fin", 0, 0, -16, length / 2 + 21, 1.7F, 12, 7, -.28F, 0, 0);
        box(p, "stabilizer", 0, -10, -12, length / 2 + 13, 20, 1, 5);
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -8 : 8;
            box(p, "skid" + s, 1, x - .5F, -1, -14, 1, 1, 31);
            rod(p, "front_strut" + s, 1, s == 0 ? -4.5F : 4.5F, -6, -8, x, -1, -8, .9F);
            rod(p, "rear_strut" + s, 1, s == 0 ? -4.5F : 4.5F, -6, 8, x, -1, 8, .9F);
            rotated(p, "skid_tip" + s, 1, x, -1.5F, -15, .9F, 1, 5, .22F, 0, 0);
        }
        box(p, "rotor_shaft", 1, -.8F, -22, -.8F, 1.6F, 5, 1.6F);
        PartDefinition rotor = part(p, "rotor", 0, -22, 0);
        box(rotor, "hub", 9, -2, -1, -2, 4, 2, 4);
        for (int i = 0; i < 4; i++) {
            PartDefinition blade = rotor.addOrReplaceChild("blade" + i, CubeListBuilder.create(),
                    PartPose.rotation(0, i * Mth.HALF_PI, 0));
            box(blade, "root", 1, 1, -.15F, -1, 8, .35F, 2);
            box(blade, "foil", 3, 9, -.15F, -1.6F, 19, .3F, 3.2F);
            box(blade, "tip", 14, 28, -.15F, -1.4F, 3, .3F, 2.8F);
        }
        PartDefinition tail = part(p, "tail_rotor", 2, -13, length / 2 + 21);
        box(tail, "hub", 1, -1, -1, -1, 2, 2, 2);
        for (int i = 0; i < 4; i++)
            tail.addOrReplaceChild("blade" + i, cube(3, -.25F, -.6F, 1, .5F, 1.2F, 7),
                    PartPose.rotation(i * Mth.HALF_PI, 0, 0));
        PartDefinition turret = part(p, "turret", 0, -5, -length / 2);
        box(turret, "housing", 9, -2.4F, -2, -2.4F, 4.8F, 4, 4.8F);
        PartDefinition barrel = part(turret, "barrel", 0, 0, -2);
        if (!cargo) {
            PartDefinition drum = part(barrel, "drum", 0, 0, -1);
            for (int i = 0; i < 3; i++) {
                float a = i * Mth.TWO_PI / 3;
                float x = (float) Math.cos(a) * .8F, y = (float) Math.sin(a) * .8F;
                box(drum, "tube" + i, 1, x - .35F, y - .35F, -11, .7F, .7F, 11);
                box(drum, "bore" + i, 3, x - .25F, y - .25F, -11.1F, .5F, .5F, .15F);
            }
            box(barrel, "mount", 1, -1.6F, -1.6F, -2, 3.2F, 3.2F, 2);
        } else {
            box(turret, "cargo_camera", 2, -1.4F, -1, -2.5F, 2.8F, 2, .3F);
            box(p, "cable", 1, -.1F, -3, -.1F, .2F, 32, .2F);
            box(p, "cargo_hatch", 9, -4, -3.1F, -6, 8, .2F, 12);
            rod(p, "winch_support", 1, 6, -12, 5, 10, -12, 5, .8F);
            box(p, "winch", 9, 8.5F, -13, 4, 3, 2, 2);
        }
        if (attack)
            for (int s = 0; s < 2; s++) {
                float x = s == 0 ? -13 : 8;
                box(p, "rack" + s, 0, x, -8, -2, 5, 1.5F, 10);
                box(p, "rocket_pod" + s, 9, x, -7, -5, 5, 4, 10);
                for (int row = 0; row < 2; row++)
                    for (int col = 0; col < 3; col++)
                        box(p, "rocket_tube" + s + "_" + row + col, 3, x + .6F + col * 1.3F, -6.5F + row * 1.5F, -5.15F,
                                .8F, .8F, .3F);
            }
        box(p, "light", 7, -width / 2 - .1F, -10, -3, .3F, 1, 1);
        box(p, "light2", 6, width / 2 - .2F, -10, -3, .3F, 1, 1);
        box(p, "tail_beacon", 6, -.4F, -22, length / 2 + 20, .8F, .5F, .8F);
    }

    private static void aircraft(PartDefinition p, Kind k) {
        boolean big = k == Kind.GUNSHIP, stealth = k == Kind.STEALTH, drone = k == Kind.UAV;
        float length = big ? 62 : stealth ? 34 : drone ? 27 : 38, width = big ? 12 : stealth ? 8 : 6;
        box(p, "body", stealth ? 9 : 0, -width / 2, -13, -length / 2, width, big ? 9 : stealth ? 3 : 5, length);
        box(p, "nose", 0, -width / 2 + 1, -12, -length / 2 - 5, width - 2, big ? 6 : 2.6F, 6);
        box(p, "nose_tip", 9, -width / 2 + 2, -11.5F, -length / 2 - 7, width - 4, big ? 4.8F : 1.6F, 2);
        if (big) {
            box(p, "cockpit", 2, -4.4F, -13.6F, -length / 2 - 1, 8.8F, 3, 5);
            box(p, "windscreen_frame", 1, -.3F, -13.9F, -length / 2 - 1.2F, .6F, 3.5F, 5.5F);
        } else if (!drone)
            rotated(p, "canopy", 2, 0, -14.4F, -length / 2 + 8, 4.7F, 3, 9, -.1F, 0, 0);
        if (!stealth)
            for (int s = 0; s < 2; s++) {
                int sign = s == 0 ? -1 : 1;
                float span = big ? 36 : drone ? 28 : 24;
                PartDefinition wing = part(p, "wing" + s, sign * width / 2, -11, -2);
                wing.addOrReplaceChild("foil", cube(0, sign < 0 ? -span : 0, 0, -6, span, 1, 11),
                        PartPose.rotation(0, sign * .2F, 0));
                box(wing, "edge", 9, sign < 0 ? -span : 0, -.1F, -6, span, .35F, .5F);
                box(wing, "flap", 8, sign < 0 ? -span * .8F : 2, .3F, 4, span * .65F, .4F, 1);
                if (big)
                    for (int j = 0; j < 2; j++) {
                        float x = sign * (14 + j * 15);
                        box(p, "engine" + s + j, 9, x - 2.4F, -10.5F, -11, 4.8F, 5, 17);
                        box(p, "intake" + s + j, 3, x - 1.8F, -9.9F, -11.2F, 3.6F, 3.8F, .3F);
                        box(p, "exhaust" + s + j, 1, x - 1.6F, -9.5F, 6, 3.2F, 3, 2);
                        PartDefinition prop = part(p, "prop" + s + j, x, -8, -12);
                        box(prop, "hub", 1, -1.1F, -1.1F, -.8F, 2.2F, 2.2F, 1.8F);
                        for (int blade = 0; blade < 4; blade++)
                            prop.addOrReplaceChild("blade" + blade, cube(3, -.55F, 1, -.3F, 1.1F, 8, .6F),
                                    PartPose.rotation(0, 0, blade * Mth.HALF_PI));
                    }
                if (k == Kind.VTOL) {
                    box(p, "pod" + s, 9, sign * 6 - 2, -12, 2, 4, 4, 9);
                    PartDefinition nozzle = part(p, "nozzle" + s, sign * 6, -10, 8);
                    box(nozzle, "exhaust", 1, -1.7F, -1.7F, -1, 3.4F, 3.4F, 4);
                    box(nozzle, "bore", 3, -1.3F, -1.3F, 3.05F, 2.6F, 2.6F, .2F);
                }
                box(p, "wing_lamp" + s, s == 0 ? 6 : 7, sign < 0 ? -span - width / 2 : span + width / 2 - .5F, -11.1F,
                        -3, .5F, .5F, .8F);
            }
        if (!stealth) {
            box(p, "tail_wing", 0, -12, -11, length / 2 - 8, 24, 1, 7);
            rotated(p, "fin", 0, 0, -16, length / 2 - 3, 1.1F, 11, 7, -.18F, 0, 0);
            box(p, "rudder", 9, -.65F, -20, length / 2 - 1, 1.3F, 8, 2);
        }
        if (drone) {
            box(p, "sensor", 9, -2, -8, -9, 4, 3, 4);
            box(p, "lens", 2, -1.2F, -7.2F, -9.2F, 2.4F, 1.8F, .3F);
            PartDefinition prop = part(p, "prop", 0, -11, length / 2 + 1);
            box(prop, "hub", 1, -.6F, -.6F, -.6F, 1.2F, 1.2F, 1.2F);
            box(prop, "blade", 3, -8, -.5F, -.3F, 16, 1, .6F);
            box(p, "antenna", 1, -.3F, -18, 1, .6F, 5, .6F);
        }
        if (k == Kind.PRECISION) {
            for (int s = 0; s < 2; s++) {
                int sign = s == 0 ? -1 : 1;
                box(p, "turbofan" + s, 9, sign * 6 - 2.6F, -17, 8, 5.2F, 6, 14);
                box(p, "fan_intake" + s, 3, sign * 6 - 2.1F, -16.5F, 7.7F, 4.2F, 5, .4F);
                for (int rib = 0; rib < 4; rib++)
                    box(p, "fan_blade" + s + rib, 1, sign * 6 - 1.8F + rib * .9F, -16, 7.55F, .25F, 4, .2F);
                for (int j = 0; j < 3; j++) {
                    box(p, "hardpoint" + s + j, 9, sign * (8 + j * 5) - .6F, -10, -4, 1.2F, 3, 6);
                    box(p, "payload" + s + j, 1, sign * (8 + j * 5) - .9F, -7, -5, 1.8F, 1.8F, 8);
                }
            }
            box(p, "nose_cannon", 1, -.65F, -10, -length / 2 - 8, 1.3F, 1.3F, 7);
            box(p, "cannon_bore", 3, -.4F, -9.75F, -length / 2 - 8.15F, .8F, .8F, .2F);
        }
        if (k == Kind.SAE || k == Kind.VTOL) {
            for (int s = 0; s < 2; s++) {
                int sign = s == 0 ? -1 : 1;
                box(p, "intake" + s, 9, sign * 4 - 1.2F, -13, -10, 2.4F, 3, 9);
                box(p, "intake_bore" + s, 3, sign * 4 - .8F, -12.6F, -10.15F, 1.6F, 2.2F, .2F);
                box(p, "wing_weapon" + s, 1, sign * 14 - 1, -10, -5, 2, 2, 12);
                box(p, "weapon_tip" + s, 9, sign * 14 - .7F, -9.7F, -7, 1.4F, 1.4F, 2);
            }
            box(p, "spine", 8, -1.5F, -15, 0, 3, 2, 13);
            if (k == Kind.VTOL) {
                box(p, "lift_fan", 9, -3.5F, -15.3F, -2, 7, .4F, 7);
                for (int i = 0; i < 5; i++)
                    box(p, "fan_grille" + i, 3, -3, -15.4F, -1.5F + i * 1.1F, 6, .2F, .45F);
            }
        }
        if (stealth) {
            for (int s = 0; s < 2; s++)
                for (int j = 0; j < 7; j++) {
                    int sign = s == 0 ? -1 : 1;
                    float x = sign < 0 ? -(8 + j * 5) : 3 + j * 5;
                    box(p, "wing_taper" + s + j, 9, x, -11, -16 + j * 2.3F, 5, 1.1F, 24 - j * 2);
                    box(p, "leading_edge" + s + j, 1, x, -11.1F, -16 + j * 2.3F, 5, .3F, .45F);
                }
            for (int s = 0; s < 2; s++) {
                float x = s == 0 ? -5 : 2;
                box(p, "inlet" + s, 3, x, -13.1F, -5, 3, .4F, 7);
                box(p, "exhaust_slot" + s, 3, x, -10.8F, 12, 3, .6F, 1);
            }
            box(p, "sensor", 2, -.8F, -9.9F, -17, 1.6F, .6F, 1);
        }
        if (big) {
            for (int i = 0; i < 3; i++) {
                float z = -9 + i * 9;
                float bore = i == 0 ? 1.1F : i == 1 ? 1.7F : 2.5F;
                box(p, "gun_mount" + i, 9, -7.5F, -8.8F, z - 2, 3.5F, 4, 4);
                box(p, "cannon" + i, 1, -15 - i * 2, -8 - bore / 2, z - bore / 2, 9 + i * 2, bore, bore);
                box(p, "cannon_bore" + i, 3, -15.1F - i * 2, -8 - bore * .34F, z - bore * .34F, .2F, bore * .68F,
                        bore * .68F);
            }
            box(p, "radome", 8, -3, -18, 4, 6, 5, 8);
            box(p, "sensor_pod", 9, -3, -5, -21, 6, 3, 7);
            box(p, "sensor_lens", 2, -2, -3.8F, -21.2F, 4, 1.5F, .3F);
            for (int i = 0; i < 6; i++) {
                box(p, "window_l" + i, 2, -6.1F, -12.7F, -20 + i * 7, .2F, 2.2F, 3);
                box(p, "window_r" + i, 2, 5.9F, -12.7F, -20 + i * 7, .2F, 2.2F, 3);
            }
            box(p, "ramp", 9, -5, -6, length / 2 - 1, 10, 2, 1);
            box(p, "aerial", 1, -.3F, -22, 10, .6F, 6, .6F);
        }
        if (!drone && !stealth && !big) {
            box(p, "exhaust", 1, -2, -12, length / 2, 4, 3, 1.5F);
            box(p, "exhaust_bore", 3, -1.5F, -11.5F, length / 2 + 1.55F, 3, 2, .2F);
        }
    }

    private static void advancedDrone(PartDefinition p) {
        box(p, "body", 9, -4, -11, -17, 8, 3, 30);
        box(p, "sensor", 2, -2, -8, -13, 4, 2, 4);
        box(p, "radar", 8, -3, -13, -3, 6, 2, 12);
        for (int s = 0; s < 2; s++)
            for (int i = 0; i < 8; i++) {
                int sign = s == 0 ? -1 : 1;
                float x = sign < 0 ? -(8 + i * 4) : 4 + i * 4;
                box(p, "wing" + s + i, 9, x, -10, -12 + i * 2.1F, 4, .8F, 22 - i * 2);
                box(p, "edge" + s + i, 1, x, -10.1F, -12 + i * 2.1F, 4, .25F, .4F);
            }
        box(p, "exhaust", 3, -2, -10, 13, 4, 1.2F, .6F);
        box(p, "light", 7, -.5F, -13.2F, 1, 1, .3F, 1);
    }

    private static void drone(PartDefinition p) {
        box(p, "frame", 9, -3, -6, -3, 6, 3, 6);
        box(p, "c4", 0, -2.5F, -3, -2.5F, 5, 2, 5);
        box(p, "camera_housing", 3, -1.4F, -5.4F, -4.2F, 2.8F, 2.2F, 1.6F);
        box(p, "camera", 2, -.8F, -4.9F, -4.35F, 1.6F, 1.2F, .2F);
        box(p, "battery", 8, -2, -7, -2, 4, 1, 4);
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1 : 1) * 6, z = (i < 2 ? -1 : 1) * 6;
            rod(p, "arm" + i, 1, 0, -5, 0, x, -5, z, .85F);
            PartDefinition r = part(p, "rotor" + i, x, -6, z);
            box(r, "hub", 1, -.7F, -.5F, -.7F, 1.4F, 1, 1.4F);
            box(r, "blade", 3, -4, -.3F, -.4F, 8, .5F, .8F);
            box(p, "leg" + i, 1, x - .25F, -4, z - .25F, .5F, 4, .5F);
        }
        box(p, "detonator", 4, -.9F, -2.6F, -2.8F, 1.8F, 1.2F, .4F);
        box(p, "led", 7, -.5F, -7.2F, -.5F, 1, .3F, 1);
    }

    private static void turret(PartDefinition p, Kind k) {
        boolean rover = k == Kind.WHEELSON;
        if (rover) {
            box(p, "chassis", 0, -11, -10, -14, 22, 6, 28);
            box(p, "belly", 9, -8, -4, -12, 16, 2, 24);
            box(p, "bumper", 1, -12, -5, -16, 24, 3, 3);
            box(p, "battery", 8, -8, -13, 3, 16, 3, 9);
            box(p, "front_camera", 9, -3, -9, -14.3F, 6, 3, .5F);
            box(p, "camera_lens", 2, -1, -8.4F, -14.6F, 2, 1.8F, .4F);
            for (int s = 0; s < 2; s++) {
                float sx = s == 0 ? -1 : 1;
                box(p, "fender" + s, 9, s == 0 ? -13 : 10, -10, -14, 3, 1.1F, 28);
                box(p, "side_rail" + s, 1, sx * 11 - .5F, -12, -10, 1, 2, 19);
                for (int i = 0; i < 4; i++) {
                    PartDefinition wheel = part(p, "wheel" + s + i, sx * 12, -4, -10 + i * 7);
                    box(wheel, "tire", 3, -2, -4, -4, 4, 8, 8);
                    box(wheel, "hub", 1, s == 0 ? -2.1F : 1.1F, -2, -2, 1, 4, 4);
                    for (int cleat = 0; cleat < 6; cleat++)
                        rotated(wheel, "tread" + cleat, 11, 0, (float) Math.cos(cleat * Mth.TWO_PI / 6) * 3.8F,
                                (float) Math.sin(cleat * Mth.TWO_PI / 6) * 3.8F, 4.2F, .8F, 1.8F,
                                cleat * Mth.TWO_PI / 6, 0, 0);
                }
            }
            for (int i = 0; i < 5; i++)
                box(p, "rear_vent" + i, 3, -6 + i * 2.5F, -11.3F, 12, .9F, 1.2F, .4F);
            box(p, "antenna", 1, 8, -23, 8, .45F, 13, .45F);
        } else {
            box(p, "base", 9, -4, -4, -4, 8, 4, 8);
            box(p, "stem", 0, -1.5F, -18, -1.5F, 3, 14, 3);
            box(p, "height_collar", 1, -2, -10, -2, 4, 2, 4);
            for (int i = 0; i < 3; i++) {
                float a = i * Mth.TWO_PI / 3, x = (float) Math.sin(a) * 11, z = (float) Math.cos(a) * 11;
                rod(p, "leg" + i, 1, 0, -5, 0, x, -.5F, z, 1.6F);
                box(p, "foot" + i, 3, x - 1.5F, -.8F, z - 1.5F, 3, .8F, 3);
            }
        }
        PartDefinition t = part(p, "turret", 0, rover ? -15 : -21, 0);
        box(t, "case", 0, -5, -3, -5, 10, 6, 11);
        box(t, "rear_motor", 9, -3, -2, 6, 6, 4, 3);
        box(t, "ammo", 8, 4, -2, 0, 4, 6, 8);
        box(t, "ammo_lid", 1, 4, -2.3F, 0, 4, .5F, 8);
        box(t, "sensor_housing", 3, -2.8F, -6, -4.4F, 5.6F, 3, 4.4F);
        box(t, "sensor", 2, -2, -5.3F, -4.6F, 4, 1.8F, .3F);
        box(t, "rangefinder", 2, 3, -4, -5.2F, 1.2F, 1.2F, .3F);
        box(t, "lamp", 7, -.5F, -6.2F, -2, 1, .3F, 1);
        PartDefinition b = part(t, "barrel", 0, 0, -5);
        if (rover) {
            box(b, "breech", 9, -1.8F, -1.8F, -4, 3.6F, 3.6F, 4);
            box(b, "tube", 1, -1, -1, -15, 2, 2, 11);
            box(b, "muzzle", 1, -1.4F, -1.4F, -17, 2.8F, 2.8F, 2);
            box(b, "bore", 3, -.8F, -.8F, -17.1F, 1.6F, 1.6F, .2F);
            for (int i = 0; i < 3; i++)
                box(b, "vent" + i, 3, -1.1F, -.4F, -13 + i * 2, .2F, .8F, 1);
        } else {
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.PI / 3, x = (float) Math.cos(a) * 1.5F, y = (float) Math.sin(a) * 1.5F;
                box(b, "tube" + i, 1, x - .4F, y - .4F, -13, .8F, .8F, 13);
                box(b, "bore" + i, 3, x - .27F, y - .27F, -13.1F, .54F, .54F, .2F);
            }
            box(b, "brace", 1, -2, -2, -10, 4, 4, .7F);
            box(b, "rear_brace", 9, -2, -2, -3, 4, 4, .8F);
        }
        for (int i = 0; i < 5; i++)
            box(t, "feed_link" + i, 1, 3 + i * .35F, 1.2F, 1 + i * .8F, .7F, .8F, .5F);
    }

    private static void crate(PartDefinition p, Kind k) {
        box(p, "box", k == Kind.JUGG_CRATE ? 9 : 5, -10, -16, -9, 20, 16, 18);
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? -7 : 5;
            box(p, "strap" + i, 11, x, -16.2F, -9.2F, 2, 16.4F, 18.4F);
        }
        box(p, "latch", 1, -1.5F, -11, -9.4F, 3, 3, .6F);
        box(p, "handle", 1, -3, -16.8F, -1, 6, .8F, 2);
        for (int s = 0; s < 2; s++) {
            box(p, "corner" + s, 1, s == 0 ? -10.15F : 8.9F, -16.15F, -9.15F, 1.3F, 16.3F, 1.3F);
            box(p, "label" + s, 14, s == 0 ? -4 : 1, -8, -9.25F, 3, 2.5F, .15F);
        }
        PartDefinition lid = part(p, "lid", 0, -16, 9);
        box(lid, "panel", 0, -10, -1, -18, 20, 2, 18);
        box(lid, "hinge", 1, -6, .6F, -.8F, 12, 1.2F, 1.2F);
        PartDefinition parachute = part(p, "parachute", 0, -52, 0);
        box(parachute, "canopy", 5, -22, 0, -22, 44, .8F, 44);
        for (int i = 0; i < 4; i++)
            rod(parachute, "line" + i, 11, i % 2 == 0 ? -20 : 20, .8F, i < 2 ? -20 : 20, i % 2 == 0 ? -8 : 8, 34,
                    i < 2 ? -7 : 7, .25F);
    }

    private static void missile(PartDefinition p, Kind k) {
        boolean cruise = k == Kind.CRUISE;
        float length = cruise ? 30 : 14;
        box(p, "body", cruise ? 0 : 1, -2, -6, -length / 2, 4, 4, length);
        box(p, "tip", 9, -1.4F, -5.4F, -length / 2 - 3, 2.8F, 2.8F, 4);
        box(p, "nose", 1, -.8F, -4.8F, -length / 2 - 4, 1.6F, 1.6F, 1.5F);
        box(p, "wing", 0, -(cruise ? 10 : 4), -4.5F, 1, cruise ? 20 : 8, .7F, 4);
        box(p, "fin", 0, -.5F, -9, cruise ? 10 : 4, 1, 6, 4);
        box(p, "exhaust", 9, -1.7F, -5.7F, length / 2 - 1, 3.4F, 3.4F, 2);
        box(p, "flame", 4, -1, -5, length / 2 + 1.05F, 2, 2, .2F);
        box(p, "seam", 1, -2.05F, -6.05F, -2, 4.1F, 4.1F, .45F);
        if (cruise)
            box(p, "intake", 3, -1.1F, -2.1F, -4, 2.2F, .5F, 4);
    }

    @Override
    public void setupAnim(TacticalUnitEntity unit, float swing, float amount, float age, float yaw, float pitch) {
        for (ModelPart p : root.getAllParts().toList())
            p.resetPose();
        for (Kind k : Kind.values())
            root.getChild(k.name()).visible = k == unit.kind();
        ModelPart p = root.getChild(unit.kind().name());
        if (unit.kind() == Kind.OVERWATCH || unit.kind() == Kind.CHOPPER || unit.kind() == Kind.DELIVERY) {
            p.getChild("rotor").yRot = age * 1.35F;
            p.getChild("tail_rotor").xRot = age * 2;
            p.getChild("turret").yRot = Mth.wrapDegrees(unit.aimYaw() - unit.getYRot()) * Mth.DEG_TO_RAD;
            ModelPart barrel = p.getChild("turret").getChild("barrel");
            barrel.xRot = unit.aimPitch() * Mth.DEG_TO_RAD;
            if (unit.kind() != Kind.DELIVERY && unit.firing()) {
                barrel.getChild("drum").zRot = age * 1.2F;
                barrel.z += (float) Math.sin(age * 5) * .25F;
            }
            if (unit.kind() == Kind.DELIVERY)
                p.getChild("cable").visible = unit.tickCount % 230 > 55 && unit.tickCount % 230 < 135;
        }
        if (unit.kind() == Kind.BOMB_DRONE)
            for (int i = 0; i < 4; i++)
                p.getChild("rotor" + i).yRot = age * 2.4F;
        if (unit.kind() == Kind.UAV)
            p.getChild("prop").zRot = age * 1.6F;
        if (unit.kind() == Kind.GUNSHIP)
            for (int i = 0; i < 2; i++)
                for (int j = 0; j < 2; j++)
                    p.getChild("prop" + i + j).zRot = age * 1.3F;
        if (unit.kind() == Kind.VTOL)
            for (int i = 0; i < 2; i++)
                p.getChild("nozzle" + i).xRot = unit.stage() == 1 ? -Mth.HALF_PI : 0;

        // 依据精确同步的机枪瞄准角抵消 MobRenderer 的机体插值，不依赖精度较低的原版头部数据包。
        if (unit.kind() == Kind.SENTRY || unit.kind() == Kind.WHEELSON) {
            float partial = Mth.clamp(age - unit.tickCount, 0, 1);
            float body = Mth.rotLerp(partial, unit.yBodyRotO, unit.yBodyRot);
            ModelPart t = p.getChild("turret");
            t.yRot = Mth.wrapDegrees(unit.renderAimYaw(partial) - body) * Mth.DEG_TO_RAD;
            ModelPart barrel = t.getChild("barrel");
            barrel.xRot = unit.renderAimPitch(partial) * Mth.DEG_TO_RAD;
            if (unit.firing()) {
                if (unit.kind() == Kind.SENTRY) {
                    barrel.zRot = age * 1.8F;
                    barrel.z += Math.abs(Mth.sin(age * 5)) * .2F;
                } else
                    barrel.z += (float) Math.sin(age * 5) * .4F;
            }
            if (unit.kind() == Kind.WHEELSON)
                for (int s = 0; s < 2; s++)
                    for (int i = 0; i < 4; i++)
                        p.getChild("wheel" + s + i).xRot = swing * 1.6F;
        }
        if (unit.kind() == Kind.CRATE || unit.kind() == Kind.JUGG_CRATE) {
            p.getChild("lid").xRot = -Math.max(0, unit.stage()) * .011F;
            p.getChild("parachute").visible = unit.stage() < 0;
        }
        if (unit.kind() == Kind.CRUISE || unit.kind() == Kind.BOMB || unit.kind() == Kind.ROCKET)
            p.xRot = unit.getXRot() * Mth.DEG_TO_RAD;
        else if (!unit.groundUnit())
            p.zRot = (float) Math.sin(age * .1) * .015F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer v, int light, int overlay, float red, float green, float blue, float alpha) {
        root.render(pose, v, light, overlay, red, green, blue, alpha);
    }
}
