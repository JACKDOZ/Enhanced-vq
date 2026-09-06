package com.homequest.block;

import com.homequest.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Cofre de Quest — ahora extiende la ChestBlock REAL de Minecraft (antes extendía Block
 * genérico + RenderShape.MODEL con un modelo estático, sin animación de tapa).
 *
 * Se conserva TODA la lógica propia de quests (dar el libro la primera vez, marcar el
 * inicio de las quests, guardar la posición del cofre, sincronizar al cliente) tal cual
 * estaba. Lo único que cambia es que, en vez de abrir el menú manualmente al final,
 * ahora se delega a super.useWithoutItem(...) para que Minecraft maneje la apertura real
 * (sonido, animación de tapa, fusión en cofre doble) exactamente como un cofre vanilla.
 */
public class QuestChestBlock extends ChestBlock {

    public QuestChestBlock(ResourceKey<Block> key,
                            Supplier<BlockEntityType<? extends ChestBlockEntity>> entityTypeSupplier) {
        super(entityTypeSupplier,
              SoundEvents.CHEST_OPEN,
              SoundEvents.CHEST_CLOSE,
              BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST)
                  .setId(key)
                  .strength(2.5f));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuestChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            com.homequest.data.QuestSavedData savedData =
                com.homequest.data.QuestSavedData.get(level.getServer());

            // ¿Este cofre ya es de otro jugador? Antes, CUALQUIERA que abriera un
            // cofre de quest ajeno quedaba vinculado a esa posición, pisando su
            // propio cofre — muy confuso en servers con varios jugadores. Ahora,
            // si la posición ya pertenece a otro UUID, no tocamos su vínculo ni
            // le damos el libro/iniciamos sus quests: lo dejamos abrir el cofre
            // como un cofre normal y listo.
            java.util.UUID owner = savedData.findChestOwner(pos);
            boolean belongsToSomeoneElse = owner != null && !owner.equals(sp.getUUID());

            if (!belongsToSomeoneElse) {
                // Guardar posición del cofre en el SERVIDOR (QuestSavedData)
                savedData.setChestPos(sp.getUUID(), pos);

                BlockEntity be = level.getBlockEntity(pos);

                if (be instanceof QuestChestBlockEntity chest) {
                    // Poner el libro dentro del cofre si no está y el jugador no tiene uno
                    boolean hasBookInChest = false;
                    boolean hasBookInInventory = false;

                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        if (!chest.getItem(i).isEmpty() && chest.getItem(i).getItem() == ModItems.QUEST_BOOK) {
                            hasBookInChest = true;
                            break;
                        }
                    }
                    for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                        ItemStack s = sp.getInventory().getItem(i);
                        if (!s.isEmpty() && s.getItem() == ModItems.QUEST_BOOK) {
                            hasBookInInventory = true;
                            break;
                        }
                    }

                    if (!hasBookInChest && !hasBookInInventory && !savedData.hasReceivedBook(sp.getUUID())) {
                        chest.setItem(0, new ItemStack(ModItems.QUEST_BOOK));
                        savedData.markReceivedBook(sp.getUUID());
                        sp.sendSystemMessage(Component.literal(
                            "§6[Enhanced-vq] §eEl §6§lLibro de Quests§e se colocó en el cofre."));
                    }

                    // Activar quests la primera vez que el jugador interactúa con el cofre
                    // y guardar el bioma de spawn EN ESTE MOMENTO para que NEW_WORLD sea preciso
                    if (!savedData.hasStartedQuests(sp.getUUID())) {
                        savedData.markStartedQuests(sp.getUUID());

                        var biomeHolder = ((net.minecraft.server.level.ServerLevel) level)
                            .getBiome(sp.blockPosition());
                        biomeHolder.unwrapKey().ifPresent(key ->
                            savedData.setSpawnBiome(sp.getUUID(), key.toString()));

                        sp.sendSystemMessage(Component.literal(
                            "§a[Enhanced-vq] §r¡Tus quests han comenzado! Abre el Libro de Quests para verlas."));
                    }
                }

                // Sincronizar estado al cliente después de todos los cambios
                com.homequest.HomeQuestMod.syncToClient(sp, savedData);
            } else {
                sp.sendSystemMessage(Component.literal(
                    "§7[Enhanced-vq] §fEste es el Cofre de Quest de otro jugador — no afecta tu progreso."));
            }
        }

        // Dejar que Minecraft maneje la apertura real del cofre (sonido, animación de tapa,
        // fusión en cofre doble) — esto es lo que antes se hacía a mano con sp.openMenu(...).
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
