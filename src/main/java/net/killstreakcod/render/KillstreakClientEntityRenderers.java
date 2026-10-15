package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.ArmorKit;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KillstreakClientEntityRenderers {
    private KillstreakClientEntityRenderers() {}

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TacticalUnitModel.LAYER, TacticalUnitModel::layer);
        event.registerLayerDefinition(WheelsonModel.LAYER_LOCATION, WheelsonModel::createBodyLayer);
        event.registerLayerDefinition(SupportHelicopterModel.LAYER_LOCATION, SupportHelicopterModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(KillstreakcodMod.TACTICAL_UNIT.get(), TacticalUnitRenderer::new);
        event.registerEntityRenderer(KillstreakcodMod.WHEELSON.get(), WheelsonRenderer::new);
        event.registerEntityRenderer(KillstreakcodMod.SUPPORT_HELICOPTER.get(), SupportHelicopterRenderer::new);
    }

    @SubscribeEvent
    public static void extensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        originalItem(event, KillstreakcodMod.CONTROLLER.get(), "tablet_uav", "killstreak_controller");
        originalItem(event, KillstreakcodMod.WHEELSON_CONTROLLER.get(), "tablet_wheelson", "wheelson_controller");
        originalItem(event, KillstreakcodMod.SENTRY_GUN.get(), "item_sentry", "sentry_gun");
        originalItem(event, KillstreakcodMod.DROP_MARKER.get(), "drop_marker", "drop_marker");
        originalItem(event, KillstreakcodMod.DESIGNATOR.get(), "designator_precision", "designator");
        originalItem(event, KillstreakcodMod.CLUSTER_MINE.get(), "cluster_mine", "cluster_mine");
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private MinigunItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new MinigunItemRenderer();
                return renderer;
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose,
                    net.minecraft.client.player.LocalPlayer player, net.minecraft.world.entity.HumanoidArm arm,
                    net.minecraft.world.item.ItemStack stack, float partial, float equip, float swing) {
                float sign = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
                pose.translate(sign * .43, -.64 - equip * .5, -.92);
                return true;
            }
        }, KillstreakcodMod.M134_MINIGUN.get());
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private JuggernautArmorModel model;

            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    net.minecraft.world.entity.LivingEntity livingEntity, net.minecraft.world.item.ItemStack stack,
                    net.minecraft.world.entity.EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                if (model == null)
                    model = new JuggernautArmorModel();
                return model.forSlot(slot);
            }
        }, ArmorKit.HELMET.get(), ArmorKit.CHEST.get(), ArmorKit.LEGS.get(), ArmorKit.BOOTS.get());
    }

    private static void originalItem(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event,
            net.minecraft.world.item.Item item, String model, String icon) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private ImportedEquipmentItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new ImportedEquipmentItemRenderer(model, icon);
                return renderer;
            }
        }, item);
    }

    @SubscribeEvent
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void thermalLayers(EntityRenderersEvent.AddLayers event) {
        for (var type : event.getEntityTypes()) {
            var renderer = event.getRenderer(type);
            if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living) {
                living.addLayer(new ThermalLayer(living));
                if (living.getModel() instanceof net.minecraft.client.model.HumanoidModel)
                    living.addLayer(new OriginalJuggernautLayer(living));
            }
        }
        for (var skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living) {
                living.addLayer(new ThermalLayer(living));
                if (living.getModel() instanceof net.minecraft.client.model.HumanoidModel)
                    living.addLayer(new OriginalJuggernautLayer(living));
            }
        }
    }
}
