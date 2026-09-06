package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquete cliente → servidor: el jugador terminó todos los pasos del tutorial
 * del libro, o lo saltó con "Saltar". No lleva datos — el servidor solo marca
 * tutorialSeen=true para que no vuelva a aparecer.
 */
public record TutorialSeenPayload() implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "tutorial_seen");

    public static final Type<TutorialSeenPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, TutorialSeenPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> { /* sin datos que escribir */ },
            buf -> new TutorialSeenPayload()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
