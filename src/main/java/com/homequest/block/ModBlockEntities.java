package com.homequest.block;

import com.homequest.HomeQuestMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {

    public static BlockEntityType<QuestChestBlockEntity> QUEST_CHEST;

    public static void initialize() {
        QUEST_CHEST = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(HomeQuestMod.MOD_ID, "quest_chest"),
            FabricBlockEntityTypeBuilder.<QuestChestBlockEntity>create(
                QuestChestBlockEntity::new,
                ModBlocks.QUEST_CHEST_BLOCK
            ).build()
        );
        HomeQuestMod.LOGGER.info("[Enhanced-vq] BlockEntityType registrado.");
    }
}
