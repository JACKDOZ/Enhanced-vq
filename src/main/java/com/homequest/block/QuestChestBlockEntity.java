package com.homequest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cofre de Quest — ahora extiende la ChestBlockEntity REAL de Minecraft (antes reimplementaba
 * Container desde cero, sin animación de tapa real). Al extender la clase real, se hereda
 * gratis: animación de tapa, sonidos, guardado/carga de items, sincronización de red, todo.
 *
 * Los 27 slots (cofre simple) y los métodos getContainerSize()/getItem()/setItem() que usa
 * HomeQuestMod para entregar recompensas siguen funcionando exactamente igual, porque son
 * los mismos métodos que ya expone ChestBlockEntity — no hizo falta tocar esa lógica.
 */
public class QuestChestBlockEntity extends ChestBlockEntity {

    public QuestChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUEST_CHEST, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("§6⚑ Cofre de Quest");
    }
}
