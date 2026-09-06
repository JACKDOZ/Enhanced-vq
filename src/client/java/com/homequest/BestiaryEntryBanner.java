package com.homequest;

import com.homequest.data.QuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Cartel "Nueva entrada en el Bestiario" en la esquina superior izquierda de la
 * pantalla (HUD, no la pantalla del libro) — mismo mecanismo que
 * {@link QuestCompletedBanner} (incluido el deslizamiento real, no un recorte),
 * pero en su propia franja vertical (debajo de esa), para que ambos puedan
 * mostrarse a la vez sin superponerse si un mismo golpe completa una misión Y
 * desbloqueaba un mob nuevo al mismo tiempo.
 *
 * Se dispara desde HomeQuestClient cada vez que llega un sync con un mob nuevo
 * (conteo de 0 a más de 0) que no estaba desbloqueado antes de ese sync.
 */
public final class BestiaryEntryBanner {
    private BestiaryEntryBanner() {}

    private static final Identifier BANNER_EN = Identifier.fromNamespaceAndPath("homequest", "textures/gui/banners/bestiary_entry_en.png");
    private static final Identifier BANNER_ES = Identifier.fromNamespaceAndPath("homequest", "textures/gui/banners/bestiary_entry_es.png");
    private static final int BANNER_EN_W = 706, BANNER_EN_H = 322;
    private static final int BANNER_ES_W = 706, BANNER_ES_H = 322;

    private static final int DISPLAY_H = 64; // alto en pantalla; el ancho se escala preservando el aspect ratio de cada imagen
    private static final int MARGIN_X = 10;
    // Debajo del cartel de Quest Completada (mismo DISPLAY_H que ese + un margen chico).
    private static final int MARGIN_Y = 10 + 64 + 6;

    private static final long SLIDE_MS = 450;
    private static final long HOLD_MS  = 3000;
    private static final long TOTAL_MS = SLIDE_MS + HOLD_MS + SLIDE_MS;

    private static final Deque<Object> queue = new ArrayDeque<>();
    private static long currentStart = -1L;

    /** Llamar una vez por cada mob que se acaba de desbloquear en el Bestiario. */
    public static void trigger() {
        if (currentStart < 0) {
            currentStart = System.currentTimeMillis();
        } else {
            queue.add(Boolean.TRUE); // el valor no importa, solo cuenta como "uno más pendiente"
        }
    }

    public static void render(GuiGraphicsExtractor g, Minecraft client) {
        if (currentStart < 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - currentStart;

        if (elapsed >= TOTAL_MS) {
            if (!queue.isEmpty()) {
                queue.poll();
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

        g.pose().pushMatrix();
        g.pose().translate(bannerX, MARGIN_Y);
        g.pose().scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, texW, texH, texW, texH);
        g.pose().popMatrix();
    }
}
