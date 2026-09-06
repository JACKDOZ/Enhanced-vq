package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquete cliente → servidor: el jugador pulsó "Obtener" en el libro
 * para reclamar la recompensa de una quest completada.
 */
public record ClaimRewardPayload(String questId) implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "claim_reward");

    public static final Type<ClaimRewardPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ClaimRewardPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.questId()),
            buf -> new ClaimRewardPayload(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
