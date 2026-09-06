package com.homequest.block;

import com.homequest.HomeQuestMod;
import com.homequest.item.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModBlocks {

    public static QuestChestBlock QUEST_CHEST_BLOCK;

    public static void initialize() {
        // 1. Registrar el bloque con su ResourceKey (setId en Properties es obligatorio en 26.2)
        ResourceKey<Block> blockKey = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(HomeQuestMod.MOD_ID, "quest_chest_block")
        );

        QUEST_CHEST_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            blockKey,
            new QuestChestBlock(blockKey, () -> ModBlockEntities.QUEST_CHEST)
        );

        // 2. Registrar el BlockItem — esto es lo que el jugador tiene en el inventario
        //    y lo que le permite colocar el bloque en el suelo
        ResourceKey<Item> itemKey = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(HomeQuestMod.MOD_ID, "quest_chest")
        );

        BlockItem chestItem = new BlockItem(
            QUEST_CHEST_BLOCK,
            new Item.Properties().setId(itemKey).stacksTo(1)
        );

        Registry.register(BuiltInRegistries.ITEM, itemKey, chestItem);

        // 3. Asignar al campo de ModItems para que HomeQuestMod pueda dar el item al jugador
        ModItems.QUEST_CHEST = chestItem;

        HomeQuestMod.LOGGER.info("[Enhanced-vq] Bloque y BlockItem registrados.");
    }
}
