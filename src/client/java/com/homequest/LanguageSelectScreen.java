package com.homequest;

import com.homequest.data.QuestData;
import com.homequest.network.FindChestPayload;
import com.homequest.network.SetLanguagePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.UUID;

/**
 * Ventana que aparece UNA sola vez por mundo (la primera vez que un jugador
 * abre el Libro de Quests), para elegir entre Español e Inglés. La elección
 * se guarda por jugador en el servidor, así que no vuelve a preguntar.
 * <p>
 * También hace de "opciones" del libro (el botón ⚙ la reabre en cualquier
 * momento): desde acá también se puede reelegir idioma o pedirle al
 * servidor que busque el Cofre de Quest vinculado — y si ya no existe, que
 * entregue uno nuevo directo al inventario.
 */
public class LanguageSelectScreen extends Screen {

    private static final Identifier BOOK_CLOSED =
        Identifier.fromNamespaceAndPath("homequest", "textures/gui/book_closed.png");
    // Tamaño real de la textura (848x1264) — se usa para mantener la proporción
    // al escalar y para ubicar el título/botones sobre la zona de cuero libre
    // de la tapa (aprox. entre el 16% y el 79% de la altura de la imagen).
    private static final int TEX_W = 848, TEX_H = 1264;

    private static final int BTN_W = 170, BTN_H = 32;

    public LanguageSelectScreen() {
        super(Component.literal("Choose your language / Elige tu idioma"));
    }

    private int esX, esY, enX, enY, findChestX, findChestY;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        Minecraft mc = Minecraft.getInstance();

        // Libro cerrado, centrado, escalado manteniendo su proporción real
        // (igual que el fondo del libro abierto: escala + blit, sin recortar).
        int bookH = Math.min(this.height - 60, 480);
        int bookW = Math.round(bookH * (TEX_W / (float) TEX_H));
        int bx = (this.width - bookW) / 2;
        int by = (this.height - bookH) / 2;
        float scale = bookH / (float) TEX_H;

        g.pose().pushMatrix();
        g.pose().translate(bx, by);
        g.pose().scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, BOOK_CLOSED, 0, 0, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H);
        g.pose().popMatrix();

        // Título y botones apilados verticalmente (la tapa es angosta) sobre
        // la zona de cuero libre del centro de la tapa.
        int centerX = bx + bookW / 2;
        int titleY  = by + Math.round(bookH * 0.34f);

        String title1 = "Choose your language";
        String title2 = "Elige tu idioma";
        g.text(mc.font, Component.literal("§f§l" + title1),
            centerX - mc.font.width("§l" + title1) / 2, titleY, ARGB.opaque(0xFFFFFF), true);
        g.text(mc.font, Component.literal("§f§l" + title2),
            centerX - mc.font.width("§l" + title2) / 2, titleY + 12, ARGB.opaque(0xFFFFFF), true);

        esX = centerX - BTN_W / 2;
        esY = by + Math.round(bookH * 0.46f);
        enX = centerX - BTN_W / 2;
        enY = esY + BTN_H + 10;
        findChestX = centerX - BTN_W / 2;
        findChestY = enY + BTN_H + 16;

        boolean overEs = isOver(mouseX, mouseY, esX, esY);
        boolean overEn = isOver(mouseX, mouseY, enX, enY);
        boolean overFindChest = isOver(mouseX, mouseY, findChestX, findChestY);

        drawButton(g, mc, esX, esY, "§lEspañol", overEs);
        drawButton(g, mc, enX, enY, "§lEnglish", overEn);
        drawButton(g, mc, findChestX, findChestY, "§l⚑ Buscar Cofre / Find Chest", overFindChest);
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + BTN_W && mouseY >= y && mouseY <= y + BTN_H;
    }

    private void drawButton(GuiGraphicsExtractor g, Minecraft mc, int x, int y, String label, boolean hovered) {
        g.fill(x, y, x + BTN_W, y + BTN_H, hovered ? 0xE0355F8B : 0xC01A4A8B);
        g.fill(x, y, x + BTN_W, y + 1, 0xFFFFFFFF);
        g.text(mc.font, Component.literal(label),
            x + (BTN_W - mc.font.width(label)) / 2,
            y + (BTN_H - mc.font.lineHeight) / 2,
            ARGB.opaque(0xFFFFFF), true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            if (isOver((int) mx, (int) my, esX, esY)) {
                chooseLanguage("es");
                return true;
            }
            if (isOver((int) mx, (int) my, enX, enY)) {
                chooseLanguage("en");
                return true;
            }
            if (isOver((int) mx, (int) my, findChestX, findChestY)) {
                findChest();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void chooseLanguage(String lang) {
        UUID uuid = Minecraft.getInstance().player.getUUID();
        // Actualización optimista del cache local: no hay que esperar la
        // vuelta del servidor para poder abrir el libro ya en el idioma elegido.
        QuestData.get().setLanguage(uuid, lang);
        QuestData.get().setLanguageChosen(uuid, true);
        ClientPlayNetworking.send(new SetLanguagePayload(lang));
        Minecraft.getInstance().gui.setScreen(new QuestBookScreen("en".equals(lang)));
    }

    /**
     * Le pide al servidor que ubique el Cofre de Quest vinculado a este
     * jugador (busca en todas las dimensiones cargadas). Si ya no existe
     * (se rompió, hizo despawn con el jugador al morir, o nunca tuvo uno),
     * el servidor limpia el vínculo viejo y entrega un cofre nuevo directo
     * al inventario — el resultado llega como mensaje de chat. Volvemos al
     * libro (en el idioma actual del jugador) para no dejar a nadie
     * colgado en esta pantalla.
     */
    private void findChest() {
        ClientPlayNetworking.send(new FindChestPayload());
        UUID uuid = Minecraft.getInstance().player.getUUID();
        boolean english = "en".equals(QuestData.get().getLanguage(uuid));
        Minecraft.getInstance().gui.setScreen(new QuestBookScreen(english));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

