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

    // Domain structures — 3D props rendered inside the Domain Expansion.
    public static final RegistryKey<Item> GEAR_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "void_gear"));
    public static final Item VOID_GEAR = new Item(new Item.Settings()
        .registryKey(GEAR_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> CORE_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "void_core"));
    public static final Item VOID_CORE = new Item(new Item.Settings()
        .registryKey(CORE_KEY).maxCount(1).rarity(Rarity.EPIC));

    // ===== CROW GRAVE set — the Crow Fan weapon + its summonable structures =====
    private static RegistryKey<Item> ck(String id) {
        return RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, id));
    }
    public static final RegistryKey<Item> FAN_KEY  = ck("crow_fan");
    public static final Item CROW_FAN = new Item(new Item.Settings()
        .registryKey(FAN_KEY).maxCount(1).rarity(Rarity.EPIC));   // the held weapon
    public static final RegistryKey<Item> CROW_KEY = ck("crow");
    public static final Item CROW = new Item(new Item.Settings().registryKey(CROW_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> TOMB_KEY = ck("tombstone");
    public static final Item TOMBSTONE = new Item(new Item.Settings().registryKey(TOMB_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> TORII_KEY = ck("torii");
    public static final Item TORII = new Item(new Item.Settings().registryKey(TORII_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> TREE_KEY = ck("dead_tree");
    public static final Item DEAD_TREE = new Item(new Item.Settings().registryKey(TREE_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> MONU_KEY = ck("grave_monument");
    public static final Item GRAVE_MONUMENT = new Item(new Item.Settings().registryKey(MONU_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> GALLOWS_KEY = ck("gallows_cage");
    public static final Item GALLOWS_CAGE = new Item(new Item.Settings().registryKey(GALLOWS_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> SPIKE_KEY = ck("skull_spike");
    public static final Item SKULL_SPIKE = new Item(new Item.Settings().registryKey(SPIKE_KEY).maxCount(1).rarity(Rarity.EPIC));

    // ===== DUEL ARENA set — the Duel Greatsword + colosseum structures =====
    public static final RegistryKey<Item> SWORD_KEY = ck("duel_sword");
    public static final Item DUEL_SWORD = new Item(new Item.Settings().registryKey(SWORD_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> APILLAR_KEY = ck("arena_pillar");
    public static final Item ARENA_PILLAR = new Item(new Item.Settings().registryKey(APILLAR_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> AARCH_KEY = ck("arena_arch");
    public static final Item ARENA_ARCH = new Item(new Item.Settings().registryKey(AARCH_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> HAND_KEY = ck("god_hand");
    public static final Item GOD_HAND = new Item(new Item.Settings().registryKey(HAND_KEY).maxCount(1).rarity(Rarity.EPIC));

    // ===== OCEAN WORLD set — the Sea Trident + undersea structures =====
    public static final RegistryKey<Item> TRIDENT_KEY = ck("sea_trident");
    public static final Item SEA_TRIDENT = new Item(new Item.Settings().registryKey(TRIDENT_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> SHARK_KEY = ck("shark");
    public static final Item SHARK = new Item(new Item.Settings().registryKey(SHARK_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> CORAL_KEY = ck("coral_pillar");
    public static final Item CORAL_PILLAR = new Item(new Item.Settings().registryKey(CORAL_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> TEMPLE_KEY = ck("sea_temple");
    public static final Item SEA_TEMPLE = new Item(new Item.Settings().registryKey(TEMPLE_KEY).maxCount(1).rarity(Rarity.EPIC));

    // ===== BIKER HIGHWAY set — the Neon Pipe + street props =====
    public static final RegistryKey<Item> PIPE_KEY = ck("neon_pipe");
    public static final Item NEON_PIPE = new Item(new Item.Settings().registryKey(PIPE_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> BIKE_KEY = ck("ghost_bike");
    public static final Item GHOST_BIKE = new Item(new Item.Settings().registryKey(BIKE_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> LIGHT_KEY = ck("street_light");
    public static final Item STREET_LIGHT = new Item(new Item.Settings().registryKey(LIGHT_KEY).maxCount(1).rarity(Rarity.EPIC));
    public static final RegistryKey<Item> NGATE_KEY = ck("neon_gate");
    public static final Item NEON_GATE = new Item(new Item.Settings().registryKey(NGATE_KEY).maxCount(1).rarity(Rarity.EPIC));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, SHADES_KEY, VOID_SHADES);
        Registry.register(Registries.ITEM, DRONE_KEY, VOID_DRONE);
        Registry.register(Registries.ITEM, SAT_KEY, VOID_SATELLITE);
        Registry.register(Registries.ITEM, GEAR_KEY, VOID_GEAR);
        Registry.register(Registries.ITEM, CORE_KEY, VOID_CORE);
        Registry.register(Registries.ITEM, FAN_KEY, CROW_FAN);
        Registry.register(Registries.ITEM, CROW_KEY, CROW);
        Registry.register(Registries.ITEM, TOMB_KEY, TOMBSTONE);
        Registry.register(Registries.ITEM, TORII_KEY, TORII);
        Registry.register(Registries.ITEM, TREE_KEY, DEAD_TREE);
        Registry.register(Registries.ITEM, MONU_KEY, GRAVE_MONUMENT);
        Registry.register(Registries.ITEM, GALLOWS_KEY, GALLOWS_CAGE);
        Registry.register(Registries.ITEM, SPIKE_KEY, SKULL_SPIKE);
        Registry.register(Registries.ITEM, SWORD_KEY, DUEL_SWORD);
        Registry.register(Registries.ITEM, APILLAR_KEY, ARENA_PILLAR);
        Registry.register(Registries.ITEM, AARCH_KEY, ARENA_ARCH);
        Registry.register(Registries.ITEM, HAND_KEY, GOD_HAND);
        Registry.register(Registries.ITEM, TRIDENT_KEY, SEA_TRIDENT);
        Registry.register(Registries.ITEM, SHARK_KEY, SHARK);
        Registry.register(Registries.ITEM, CORAL_KEY, CORAL_PILLAR);
        Registry.register(Registries.ITEM, TEMPLE_KEY, SEA_TEMPLE);
        Registry.register(Registries.ITEM, PIPE_KEY, NEON_PIPE);
        Registry.register(Registries.ITEM, BIKE_KEY, GHOST_BIKE);
        Registry.register(Registries.ITEM, LIGHT_KEY, STREET_LIGHT);
        Registry.register(Registries.ITEM, NGATE_KEY, NEON_GATE);
        // multiplayer effect-sharing networking
        VoidNet.registerCommon();
        VoidNet.registerServer();
        // show up in the Combat creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT)
            .register(entries -> {
                entries.add(VOID_SHADES); entries.add(VOID_DRONE); entries.add(VOID_SATELLITE);
                entries.add(CROW_FAN); entries.add(DUEL_SWORD); entries.add(SEA_TRIDENT); entries.add(NEON_PIPE);
            });
    }
}
