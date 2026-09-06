package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquete servidor → cliente: respuesta a RequestGameStatsPayload con los 3
 * números de la página "Datos de la Partida".
 *
 * mobKills y blocksMined salen de las estadísticas propias de Minecraft
 * (Stats.MOB_KILLS y la suma de Stats.BLOCK_MINED sobre todo el registro de
 * bloques) — no son un contador propio del mod, así que quedan siempre
 * correctos sin que el mod tenga que llevar la cuenta él mismo.
 *
 * blocksPlaced SÍ es un contador propio (QuestSavedData.blocksPlacedTotal):
 * Minecraft no trackea un total de bloques colocados (solo "usos" de item,
 * que mezcla colocar con otras acciones), así que no había de dónde leerlo
 * directo.
 */
public record GameStatsPayload(long mobKills, long blocksMined, long blocksPlaced) implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "game_stats");

    public static final Type<GameStatsPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, GameStatsPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarLong(p.mobKills());
                buf.writeVarLong(p.blocksMined());
                buf.writeVarLong(p.blocksPlaced());
            },
            buf -> new GameStatsPayload(buf.readVarLong(), buf.readVarLong(), buf.readVarLong())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
