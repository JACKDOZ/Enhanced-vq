package com.homequest.item;

import com.homequest.HomeQuestMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.function.Function;

public class ModItems {

    // NOTE: QUEST_CHEST (placeable) is registered in ModBlocks as a BlockItem.
    // It is referenced here as a field for easy access from HomeQuestMod.
    // It gets assigned by ModBlocks.initialize() — must run FIRST.
    public static Item QUEST_CHEST;

    // Quest book — opens the quest screen
    public static final QuestBookItem QUEST_BOOK = register(
        "quest_book",
        QuestBookItem::new,
        new Item.Properties().stacksTo(1)
    );

    // Corona — recompensa por completar el libro al 100%.
    // PLACEHOLDER: por ahora es una COPIA de las stats/textura/modelo del casco de
    // netherite (mismo ArmorMaterial vanilla ArmorMaterials.NETHERITE, misma
    // durabilidad base 37 — la del juego, sin tocar el ítem minecraft:netherite_helmet
    // en absoluto). Es un ítem nuevo con su propio id (homequest:corona) que
    // simplemente REUSA la textura/asset de netherite hasta que llegue el arte final;
    // cuando llegue, solo hay que reemplazar corona.png y el equipment json —
    // no hace falta tocar este archivo.
    public static final int CORONA_BASE_DURABILITY = 37; // igual que netherite
    public static final Item CORONA = register(
        "corona",
        Item::new,
        new Item.Properties().stacksTo(1)
            .humanoidArmor(ArmorMaterials.NETHERITE, ArmorType.HELMET)
            .durability(ArmorType.HELMET.getDurability(CORONA_BASE_DURABILITY))
    );

    public static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(HomeQuestMod.MOD_ID, name)
        );
        T item = factory.apply(props.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static void initialize() {
        HomeQuestMod.LOGGER.info("[Enhanced-vq] Items registrados.");
    }
}
