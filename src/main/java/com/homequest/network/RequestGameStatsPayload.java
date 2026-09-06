package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquete cliente → servidor: el jugador abrió la página "Datos de la Partida" del
 * libro. No lleva datos — es solo una señal para que el servidor lea las
 * estadísticas de Minecraft de ese jugador (Stats.MOB_KILLS, Stats.BLOCK_MINED
 * sumado, etc.) y responda con GameStatsPayload. Se pide bajo demanda, al abrir la
 * página, en vez de mandarlo en cada QuestSyncPayload — sumar TODOS los bloques
 * minados recorre el registro completo de bloques, y no tiene sentido pagar ese
 * costo en cada sync (que puede dispararse muy seguido) si esta página ni
 * siquiera está abierta.
 */
public record RequestGameStatsPayload() implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "request_game_stats");

    public static final Type<RequestGameStatsPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, RequestGameStatsPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> { /* sin datos que escribir */ },
            buf -> new RequestGameStatsPayload()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
