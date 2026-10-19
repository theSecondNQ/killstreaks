package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.KillstreakService;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.RegistryObject;

public final class ArmorKit {
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET, BOOTS -> 5;
                case CHESTPLATE -> 10;
                case LEGGINGS -> 8;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public net.minecraft.sounds.SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return KillstreakcodMod.MODID + ":juggernaut";
        }

        @Override
        public float getToughness() {
            return 5;
        }

        @Override
        public float getKnockbackResistance() {
            return .7F;
        }
    };
    public static final RegistryObject<Item> HELMET = armor("juggernaut_helmet", ArmorItem.Type.HELMET),
            CHEST = armor("juggernaut_chestplate", ArmorItem.Type.CHESTPLATE),
            LEGS = armor("juggernaut_leggings", ArmorItem.Type.LEGGINGS),
            BOOTS = armor("juggernaut_boots", ArmorItem.Type.BOOTS);

    private static RegistryObject<Item> armor(String name, ArmorItem.Type type) {
        return KillstreakcodMod.ITEMS.register(name,
                () -> new ArmorItem(MATERIAL, type, new Item.Properties().stacksTo(1)) {
                    @Override
                    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
                        consumer.accept(net.killstreakcod.render.KillstreakClientEntityRenderers.armorExtension());
                    }
                });
    }

    public static void equip(ServerPlayer player) {
        List<RegistryObject<Item>> kit = List.of(BOOTS, LEGS, CHEST, HELMET);
        EquipmentSlot[] slots = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
        for (int i = 0; i < 4; i++) {
            ItemStack old = player.getItemBySlot(slots[i]);
            player.setItemSlot(slots[i], ItemStack.EMPTY);
            if (!old.isEmpty())
                KillstreakService.giveItem(player, old);
            player.setItemSlot(slots[i], kit.get(i).get().getDefaultInstance());
        }
    }

    public static void init() {}
}
