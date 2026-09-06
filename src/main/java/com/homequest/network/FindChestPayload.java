package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquete cliente → servidor: el jugador pulsó "Buscar Cofre" en las opciones
 * del libro. No lleva datos — es solo una señal para que el servidor busque
 * el Cofre de Quest vinculado a este jugador en todas las dimensiones
 * cargadas y, si ya no existe (se rompió, hizo despawn con el jugador, etc.),
 * le entregue uno nuevo en el inventario.
 */
public record FindChestPayload() implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "find_chest");

    public static final Type<FindChestPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, FindChestPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> { /* sin datos que escribir */ },
            buf -> new FindChestPayload()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
