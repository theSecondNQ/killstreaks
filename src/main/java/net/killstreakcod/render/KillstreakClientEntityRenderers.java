package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

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

    public static net.minecraftforge.client.extensions.common.IClientItemExtensions itemExtension(String model, String icon) {
        return new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private ImportedEquipmentItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new ImportedEquipmentItemRenderer(model, icon);
                return renderer;
            }
        };
    }

    public static net.minecraftforge.client.extensions.common.IClientItemExtensions minigunExtension() {
        return new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
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
        };
    }

    public static net.minecraftforge.client.extensions.common.IClientItemExtensions armorExtension() {
        return new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
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
        };
    }

    @SubscribeEvent
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void thermalLayers(EntityRenderersEvent.AddLayers event) {
        for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            Object renderer;
            try {
                // Forge restricts this lookup to living types; the registry also contains projectiles.
                renderer = event.getRenderer((net.minecraft.world.entity.EntityType) type);
            }
            catch (ClassCastException nonLivingRenderer) {
                continue;
            }
            if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living) {
                living.addLayer(new ThermalLayer(living));
                if (living.getModel() instanceof net.minecraft.client.model.HumanoidModel)
                    living.addLayer(new OriginalJuggernautLayer(living));
            }
        }
        for (var skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer living = event.getSkin(skin);
            if (living != null) {
                living.addLayer(new ThermalLayer(living));
                if (living.getModel() instanceof net.minecraft.client.model.HumanoidModel)
                    living.addLayer(new OriginalJuggernautLayer(living));
            }
        }
    }
}
