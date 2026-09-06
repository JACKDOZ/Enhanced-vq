package com.homequest.block;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Estado de render propio del Cofre de Quest (igual que el validado en chesttest).
 */
public class QuestChestRenderState extends BlockEntityRenderState {
    public float open;
    public Direction facing = Direction.SOUTH;
    public ChestType type = ChestType.SINGLE;
}
