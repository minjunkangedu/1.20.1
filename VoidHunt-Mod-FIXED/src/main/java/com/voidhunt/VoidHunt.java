package com.voidhunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class VoidHunt implements ModInitializer {
    public static final String MOD_ID = "voidhunt";

    public static final RegistryKey<Item> SHADES_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "void_shades"));

    // Wearable on the HEAD slot; renders its own 3D item model (no equipment asset set).
    public static final Item VOID_SHADES = new Item(new Item.Settings()
        .registryKey(SHADES_KEY)
        .maxCount(1)
        .rarity(Rarity.EPIC)
        .component(DataComponentTypes.EQUIPPABLE,
            EquippableComponent.builder(EquipmentSlot.HEAD).build()));

    // Drone item — only used to render the 3D drone model in the world.
    public static final RegistryKey<Item> DRONE_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "void_drone"));
    public static final Item VOID_DRONE = new Item(new Item.Settings()
        .registryKey(DRONE_KEY)
        .maxCount(1)
        .rarity(Rarity.EPIC));

    // Satellite item — renders the 3D orbital-strike satellite during the ultimate.
    public static final RegistryKey<Item> SAT_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "void_satellite"));
    public static final Item VOID_SATELLITE = new Item(new Item.Settings()
        .registryKey(SAT_KEY)
        .maxCount(1)
        .rarity(Rarity.EPIC));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, SHADES_KEY, VOID_SHADES);
        Registry.register(Registries.ITEM, DRONE_KEY, VOID_DRONE);
        Registry.register(Registries.ITEM, SAT_KEY, VOID_SATELLITE);
        // show up in the Combat creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT)
            .register(entries -> { entries.add(VOID_SHADES); entries.add(VOID_DRONE); entries.add(VOID_SATELLITE); });
    }
}
