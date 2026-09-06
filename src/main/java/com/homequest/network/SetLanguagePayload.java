package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquete cliente → servidor: el jugador eligió idioma (inglés o español) en
 * la ventana que aparece la primera vez que abre el Libro de Quests en un
 * mundo. Se guarda por jugador para no volver a preguntar.
 */
public record SetLanguagePayload(String language) implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "set_language");

    public static final Type<SetLanguagePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SetLanguagePayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.language()),
            buf -> new SetLanguagePayload(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
