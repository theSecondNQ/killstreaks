package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.KillstreakService;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ArmorKit {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL,
            KillstreakcodMod.MODID);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS
            .register("juggernaut",
                    () -> new ArmorMaterial(
                            Map.of(ArmorItem.Type.HELMET, 5, ArmorItem.Type.CHESTPLATE, 10, ArmorItem.Type.LEGGINGS, 8,
                                    ArmorItem.Type.BOOTS, 5, ArmorItem.Type.BODY, 10),
                            0, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.EMPTY,
                            List.of(new ArmorMaterial.Layer(
                                    ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "juggernaut"))),
                            5, .7F));
    public static final DeferredItem<Item> HELMET = armor("juggernaut_helmet", ArmorItem.Type.HELMET),
            CHEST = armor("juggernaut_chestplate", ArmorItem.Type.CHESTPLATE),
            LEGS = armor("juggernaut_leggings", ArmorItem.Type.LEGGINGS),
            BOOTS = armor("juggernaut_boots", ArmorItem.Type.BOOTS);

    private static DeferredItem<Item> armor(String name, ArmorItem.Type type) {
        return KillstreakcodMod.ITEMS.register(name,
                () -> new ArmorItem(MATERIAL, type, new Item.Properties().stacksTo(1)));
    }

    public static void equip(ServerPlayer player) {
        DeferredItem<?>[] kit = { BOOTS, LEGS, CHEST, HELMET };
        EquipmentSlot[] slots = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
        for (int i = 0; i < 4; i++) {
            ItemStack old = player.getItemBySlot(slots[i]);
            player.setItemSlot(slots[i], ItemStack.EMPTY);
            if (!old.isEmpty())
                KillstreakService.giveItem(player, old);
            player.setItemSlot(slots[i], kit[i].get().getDefaultInstance());
        }
    }

    public static void init() {}
}
