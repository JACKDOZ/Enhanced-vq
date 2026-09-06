package com.homequest;

import com.homequest.data.QuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Cartel "Quest Completada" en la esquina superior izquierda de la pantalla (HUD,
 * no la pantalla del libro). Se dispara desde HomeQuestClient cada vez que llega
 * un sync con una misión nueva marcada como completada.
 *
 * Fases:
 *   1. Entrada (450ms): el cartel entra deslizándose desde fuera de la pantalla
 *      (por la izquierda) hasta su posición final — todo el cartel se mueve, no
 *      es un recorte/wipe.
 *   2. Sostenido (3000ms), completamente visible en su posición final. El nombre
 *      de la misión se dibuja en el recuadro vacío de la plantilla; si no entra
 *      completo, se desliza hacia la izquierda durante esta fase hasta mostrar el
 *      final del nombre, y se queda ahí el resto del tiempo.
 *   3. Salida (450ms): mismo recorrido que la entrada pero al revés — el cartel
 *      se desliza de vuelta hacia fuera de la pantalla por la izquierda, hasta
 *      desaparecer del todo.
 *
 * Si se completa más de una misión antes de que termine el cartel actual, las
 * siguientes se encolan (con su propio nombre) y se muestran una después de la
 * otra (nunca se pisan ni se pierden).
 */
public final class QuestCompletedBanner {
    private QuestCompletedBanner() {}

    private static final Identifier BANNER_EN = Identifier.fromNamespaceAndPath("homequest", "textures/gui/banners/quest_completed_en.png");
    private static final Identifier BANNER_ES = Identifier.fromNamespaceAndPath("homequest", "textures/gui/banners/quest_completed_es.png");
    private static final int BANNER_EN_W = 703, BANNER_EN_H = 350;
    private static final int BANNER_ES_W = 703, BANNER_ES_H = 350;

    private static final int DISPLAY_H = 64; // alto en pantalla; el ancho se escala preservando el aspect ratio de cada imagen
    private static final int MARGIN_X = 10, MARGIN_Y = 10;

    // Recuadro vacío de "Nombre de la Quest" dentro de la plantilla (coordenadas
    // nativas de la imagen fuente 703x350, iguales en ES y EN — mismo recuadro,
    // solo cambia el texto de arriba/abajo). Con margen adentro del borde dibujado.
    private static final int NAME_BOX_X1 = 225, NAME_BOX_Y1 = 143;
    private static final int NAME_BOX_X2 = 637, NAME_BOX_Y2 = 200;

    private static final long SLIDE_MS    = 450;
    private static final long HOLD_MS     = 3000;
    private static final long TOTAL_MS    = SLIDE_MS + HOLD_MS + SLIDE_MS;
    // El marquee usa casi todo el sostenido, dejando un resto fijo mostrando el
    // final del nombre en vez de scrollear hasta el último instante.
    private static final long SCROLL_MS   = HOLD_MS - 500;

    private record Pending(String questName) {}
    private static final Deque<Pending> queue = new ArrayDeque<>();
    private static String currentQuestName = "";
    private static long currentStart = -1L;

    /** Llamar una vez por cada misión que se acaba de completar, con su nombre ya resuelto en el idioma del jugador. */
    public static void trigger(String questName) {
        if (currentStart < 0) {
            currentQuestName = questName;
            currentStart = System.currentTimeMillis();
        } else {
            queue.add(new Pending(questName));
        }
    }

    public static void render(GuiGraphicsExtractor g, Minecraft client) {
        if (currentStart < 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - currentStart;

        if (elapsed >= TOTAL_MS) {
            Pending next = queue.poll();
            if (next != null) {
                currentQuestName = next.questName();
                currentStart = now;
                elapsed = 0;
            } else {
                currentStart = -1L;
                return;
            }
        }

        boolean english = client.player != null && "en".equals(QuestData.get().getLanguage(client.player.getUUID()));
        Identifier tex = english ? BANNER_EN : BANNER_ES;
        int texW = english ? BANNER_EN_W : BANNER_ES_W;
        int texH = english ? BANNER_EN_H : BANNER_ES_H;

        int dispH = DISPLAY_H;
        float scale = dispH / (float) texH;
        int dispW = Math.round(texW * scale);

        // Posición X del cartel entero: fuera de pantalla (-dispW) en los extremos
        // de la animación, en MARGIN_X mientras está sostenido. Un solo número que
        // se usa tanto para el blit del cartel como para ubicar el texto encima.
        int restX = MARGIN_X;
        int offScreenX = -dispW;
        int bannerX;
        if (elapsed < SLIDE_MS) {
            float t = elapsed / (float) SLIDE_MS;
            bannerX = Math.round(offScreenX + (restX - offScreenX) * t);
        } else if (elapsed >= SLIDE_MS + HOLD_MS) {
            float t = (elapsed - SLIDE_MS - HOLD_MS) / (float) SLIDE_MS;
            bannerX = Math.round(restX + (offScreenX - restX) * t);
        } else {
            bannerX = restX;
        }
        int bannerY = MARGIN_Y;

        g.pose().pushMatrix();
        g.pose().translate(bannerX, bannerY);
        g.pose().scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, texW, texH, texW, texH);
        g.pose().popMatrix();

        // Nombre de la misión: se dibuja aparte (no escalado con la textura) para
        // que el texto de Minecraft se vea nítido en vez de estirado/borroso.
        Minecraft mc = Minecraft.getInstance();
        int boxX = bannerX + Math.round(NAME_BOX_X1 * scale);
        int boxY = bannerY + Math.round(NAME_BOX_Y1 * scale);
        int boxW = Math.round((NAME_BOX_X2 - NAME_BOX_X1) * scale);
        int boxH = Math.round((NAME_BOX_Y2 - NAME_BOX_Y1) * scale);
        int textY = boxY + (boxH - mc.font.lineHeight) / 2;

        int nameW = mc.font.width(currentQuestName);
        int textX = boxX;
        if (nameW > boxW) {
            long holdElapsed = elapsed - SLIDE_MS;
            float scrollT = elapsed >= SLIDE_MS ? Math.max(0f, holdElapsed / (float) SCROLL_MS) : 0f;
            scrollT = Math.min(1f, scrollT);
            textX = boxX - Math.round((nameW - boxW) * scrollT);
        }
        g.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
        g.text(mc.font, Component.literal("§0" + currentQuestName), textX, textY, ARGB.opaque(0x3D1A00), false);
        g.disableScissor();
    }
}
