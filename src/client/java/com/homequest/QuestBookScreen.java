package com.homequest;

import com.homequest.data.QuestData;
import com.homequest.network.ClaimRewardPayload;
import com.homequest.network.RequestGameStatsPayload;
import com.homequest.network.TutorialSeenPayload;
import com.homequest.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestBookScreen extends Screen {

    private static final Identifier BG = Identifier.fromNamespaceAndPath("homequest", "textures/gui/quest_book_bg2.png");
    private static final int BW = 340, BH = 220;
    // Resolución nativa del PNG de fondo, YA recortado a su bounding box real (el
    // archivo quest_book_bg2.png traía un margen transparente: el arte real ocupaba
    // sólo el rectángulo (101,39)-(1571,1092) dentro del lienzo de 1629x1150). El PNG
    // se recortó a esos 1470x1053 px, que es el mismo sistema de coordenadas usado por
    // el resto de constantes de este archivo (PAGE_L/PAGE_R/TAB_HOME/ANIM_DEST, todas
    // calibradas sobre el arte "limpio" de ~1456-1470x1052-1053). Antes esta constante
    // apuntaba al lienzo sin recortar (1629x1150), lo que hacía que el fondo se
    // dibujara más chico/desplazado que el resto de los elementos (pestañas, ícono de
    // la casita, marco de las páginas) → todo quedaba fuera de lugar.
    private static final int BG_NATIVE_W = 1470, BG_NATIVE_H = 1053;

    // ── Animación de cambio de página (páginas + tapa + casita, SIN las pestañas laterales) ──
    // Calibrado usando los bordes rojos de las páginas como referencia (mucho más precisos
    // que iconos pequeños): en quest_book_bg.png (1470x1070, arte viejo) el borde IZQ iba de
    // (116,103) a (657,907) y el DER de (734,103) a (1282,906). En el gif (800x610) el borde
    // IZQ va de (28,67) a (377,585) y el DER de (428,67) a (781,585). De ahí sale la
    // transformación:
    //   bg_x = gif_x * 1.5484 + 72.64      bg_y = gif_y * 1.5511 - 1
    // NOTA (arte nuevo, quest_book_bg.png 1468x1052): se remidió el borde rojo sobre el
    // archivo nuevo y quedó prácticamente en el mismo lugar (IZQ (116,104)-(656,906),
    // DER (734,104)-(1282,906)), por eso esta transformación y el rectángulo ANIM_DEST_*
    // de abajo siguen sirviendo sin cambios.
    // Como las pestañas de colores no están en este gif, se dibuja siempre el fondo estático
    // completo primero y la animación se superpone solo sobre el rectángulo de páginas/tapa.
    private static final int ANIM_FRAME_COUNT = 6;
    private static final int ANIM_FRAME_W = 800, ANIM_FRAME_H = 610;
    private static final int ANIM_FRAME_MS = 100; // 6 frames x 100ms = 600ms
    private static final int ANIM_TOTAL_MS = ANIM_FRAME_COUNT * ANIM_FRAME_MS;
    // Rectángulo destino (en el espacio de píxeles 1470x1070 de quest_book_bg.png) donde se
    // dibuja la animación, calculado con la transformación de arriba.
    private static final float ANIM_DEST_X = 73f;
    private static final float ANIM_DEST_Y = -1f;
    private static final float ANIM_DEST_W = 1239f;
    private static final float ANIM_DEST_H = 946f;
    private static final Identifier[] ANIM_FRAMES = new Identifier[ANIM_FRAME_COUNT];
    static {
        for (int i = 0; i < ANIM_FRAME_COUNT; i++) {
            ANIM_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest",
                "textures/gui/book_flip/frame_" + i + ".png");
        }
    }
    // ── Logo animado (manzana dorada encantada) para la pestaña "Logros" ──
    // Recortado del gif que se usó como referencia: 8 frames con el brillo de
    // encantamiento recorriendo la manzana. Se cicla en loop continuo (no está atado
    // a tabAnimStart, es puramente decorativo sobre la pestaña rosa).
    private static final int LOGO_FRAME_COUNT = 8;
    // Rotada 20° a la derecha (sentido horario) respecto del gif original; el recorte
    // a bounding box después de rotar da estas dimensiones (antes 265x280).
    private static final int LOGO_FRAME_W = 271, LOGO_FRAME_H = 309;
    private static final int LOGO_FRAME_MS = 120; // 8 frames x 120ms = 960ms por loop
    private static final Identifier[] LOGO_FRAMES = new Identifier[LOGO_FRAME_COUNT];
    static {
        for (int i = 0; i < LOGO_FRAME_COUNT; i++) {
            LOGO_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest",
                "textures/gui/achievement_logo/frame_" + i + ".png");
        }
    }
    // ── Ícono estático del botón creeper (arriba izq., pestaña Completadas) ──
    // Antes venía dibujado en el arte de fondo; en el arte nuevo ya no, así que ahora
    // se dibuja por código como un ícono más. Aparece con una animación de "saliendo
    // de abajo de la página" cada vez que se pasa de página (ver ANIM_TOTAL_MS más
    // arriba) — el gif original no incluía este ícono en sus frames, así que en vez de
    // rehacer esa animación completa se le da una entrada propia, más simple.
    private static final int CREEPER_ICON_W = 38, CREEPER_ICON_H = 39;
    private static final int CREEPER_ICON_DISPLAY_H = 8;
    // Ojo: entre la casita y donde el lomo (tapa) se vuelve sólido en todo el ancho hay
    // un hueco transparente (la casita lo "tapa" con su propio dibujo, pero un ícono
    // suelto ahí queda flotando sobre nada en vez de apoyado en la tapa). Medido en el
    // arte: la tapa es sólida recién desde y=8 (espacio 220px) en adelante — ahí es
    // donde "termina la casita por abajo" y arranca el ícono, no al revés.
    private static final int CREEPER_TOP_Y = 8;
    private static final Identifier CREEPER_ICON = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/creeper.png");
    // Revelado escalonado (NO es un slide): después de que termina el flip de página
    // (ANIM_TOTAL_MS), el creeper se muestra de a poco en pasos discretos — un pedazo
    // más grande (de arriba hacia abajo) cada paso — durante 1 segundo, como si fuera
    // saliendo de detrás de la página. Con recorte (scissor), no con transparencia real,
    // porque blit() en esta versión no tiene un overload con alpha/tint confirmado.
    private static final int CREEPER_REVEAL_STEPS = 6;
    private static final long CREEPER_REVEAL_MS = 1000;

    // ── Ícono "Guerrero" (hacha) — 7mo slot, DEBAJO de Logros ──
    // Con el arte nuevo (quest_book_bg2.png) la FORMA de las 7 pestañas ya viene
    // horneada, Guerrero incluida — antes no era así, y hacía falta dibujar la forma
    // de esta pestaña aparte con guerrero_tab_bg.png (ver drawGuerreroTab, eliminado).
    // Lo único que sigue haciendo falta por código es el ÍCONO del hacha encima,
    // estático (no animado, a diferencia de brújula/pico/bloque/redstone/libro).
    private static final int GUERRERO_AXE_W = 120, GUERRERO_AXE_H = 140;
    private static final Identifier GUERRERO_AXE =
        Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/guerrero_axe.png");


    // ── Ícono animado del libro encantado (pestaña "Mago") ──
    private static final int ENCH_BOOK_FRAME_COUNT = 32, ENCH_BOOK_FRAME_W = 160, ENCH_BOOK_FRAME_H = 140, ENCH_BOOK_FRAME_MS = 50;
    private static final Identifier[] ENCH_BOOK_FRAMES = new Identifier[ENCH_BOOK_FRAME_COUNT];
    static {
        for (int i = 0; i < ENCH_BOOK_FRAME_COUNT; i++)
            ENCH_BOOK_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/enchanted_book/frame_" + i + ".png");
    }
    // Reemplaza el glifo de texto ✔/○ de antes por la carita del creeper: gris
    // mientras la misión no está completada, a color apenas se completa.
    private static final int QUEST_MARK_W = 765, QUEST_MARK_H = 764;
    private static final int QUEST_MARK_SIZE = 9; // tamaño en pantalla (mismo lugar/tamaño que el mark viejo)
    private static final Identifier QUEST_MARK_COLOR = Identifier.fromNamespaceAndPath("homequest", "textures/gui/quest_mark/creeper_color.png");
    private static final Identifier QUEST_MARK_GRAY  = Identifier.fromNamespaceAndPath("homequest", "textures/gui/quest_mark/creeper_gray.png");

    // ── Íconos animados de las 4 pestañas de categoría (arte nuevo: pestañas lisas,
    // sin ícono dibujado adentro, a diferencia del arte viejo).
    // IMPORTANTE sobre el timing: los gifs originales tenían muchísimos frames más de
    // los que conviene empaquetar (brújula: 1696 @50ms, pico: 825 @50ms). La primera
    // versión muestreaba parejo a lo largo de TODO el clip, lo que juntaba frames muy
    // lejanos entre sí (varios segundos reales de diferencia) y los reproducía rápido
    // → se veía saltado y acelerado. Ahora se usa un tramo de frames CONSECUTIVOS del
    // original (sin saltarse ninguno) con su duración real de frame, así el movimiento
    // es tan suave y a la misma velocidad que en el gif fuente. Redstone y el bloque
    // (52 y 50 frames) entraban completos sin necesidad de recortar, así que van
    // íntegros también a su velocidad original.
    private static final int COMPASS_FRAME_COUNT = 32, COMPASS_FRAME_W = 140, COMPASS_FRAME_H = 120, COMPASS_FRAME_MS = 50;
    private static final int PICKAXE_FRAME_COUNT = 32, PICKAXE_FRAME_W = 130, PICKAXE_FRAME_H = 130, PICKAXE_FRAME_MS = 50;
    private static final int BLOCK_FRAME_COUNT = 52, BLOCK_FRAME_W = 192, BLOCK_FRAME_H = 210, BLOCK_FRAME_MS = 100;
    private static final int REDSTONE_FRAME_COUNT = 50, REDSTONE_FRAME_W = 104, REDSTONE_FRAME_H = 99, REDSTONE_FRAME_MS = 60;
    private static final Identifier[] COMPASS_FRAMES = new Identifier[COMPASS_FRAME_COUNT];
    private static final Identifier[] PICKAXE_FRAMES = new Identifier[PICKAXE_FRAME_COUNT];
    private static final Identifier[] BLOCK_FRAMES = new Identifier[BLOCK_FRAME_COUNT];
    private static final Identifier[] REDSTONE_FRAMES = new Identifier[REDSTONE_FRAME_COUNT];
    static {
        for (int i = 0; i < COMPASS_FRAME_COUNT; i++)
            COMPASS_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/compass/frame_" + i + ".png");
        for (int i = 0; i < PICKAXE_FRAME_COUNT; i++)
            PICKAXE_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/pickaxe/frame_" + i + ".png");
        for (int i = 0; i < BLOCK_FRAME_COUNT; i++)
            BLOCK_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/block/frame_" + i + ".png");
        for (int i = 0; i < REDSTONE_FRAME_COUNT; i++)
            REDSTONE_FRAMES[i] = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/redstone/frame_" + i + ".png");
    }
    // Momento (ms de sistema) en que empezó la animación de cambio de página; -1 = sin animación
    private long tabAnimStart = -1L;

    // ── Pestañas: coordenadas relativas a (bx, by) del libro ──
    // Recalibradas sobre el arte nuevo (quest_book_bg.png 1468x1052), midiendo
    // directamente los píxeles de cada ícono/pestaña y convirtiendo a espacio 340x220
    // con sx=340/1468, sy=220/1052.
    //
    // Principal: ahora son DOS íconos separados arriba a la izquierda (casita + creeper).
    // Casita = pestaña "Principal" de siempre. Creeper = botón nuevo, reservado para
    // contenido futuro (todavía no tiene pestaña propia, ver handleClick más abajo).
    private static final int TAB_HOME_X1 = 44,  TAB_HOME_X2 = 69;
    private static final int TAB_HOME_Y1 = 0,   TAB_HOME_Y2 = 22;
    private static final int TAB_CREEPER_X1 = 71, TAB_CREEPER_X2 = 92;
    private static final int TAB_CREEPER_Y1 = 0,  TAB_CREEPER_Y2 = 22;
    // Atajo a Bestiario: mismo tamaño y misma altura que la pestaña del creeper (no
    // se le resta ni suma nada al ancho/alto), solo corrida a la derecha con 2px de
    // separación — igual que el propio hueco entre Casa y Creeper.
    private static final int TAB_WITHER_X1 = TAB_CREEPER_X2 + 2;
    private static final int TAB_WITHER_X2 = TAB_WITHER_X1 + (TAB_CREEPER_X2 - TAB_CREEPER_X1);
    private static final int TAB_WITHER_Y1 = TAB_CREEPER_Y1, TAB_WITHER_Y2 = TAB_CREEPER_Y2;
    private static final int WITHER_ICON_W = 64, WITHER_ICON_H = 64;
    private static final Identifier WITHER_ICON = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/wither.png");
    // Silueta idéntica a wither.png pero en blanco-amarillento, para el parpadeo de
    // aviso: al dibujarla encima con el mismo blit, solo se ilumina donde el alpha de
    // la textura original ya era opaco (la cabeza), nunca el cuadrado que la rodea.
    private static final Identifier WITHER_ICON_GLOW = Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/wither_glow.png");
    // El listón rojo que cuelga del borde inferior del libro es puramente decorativo
    // (Mago ahora tiene su propia pestaña de color, ver más abajo, así que ya no
    // necesita coordenadas propias de click).
    // Explorador / Minero / Constructor / Técnico / Mago / Logros: 6 pestañas de color
    // ya dibujadas en la textura (verde, dorado, morado, celeste, rosa, gris). Mago
    // (libro encantado) es el anteúltimo slot; Logros (manzana) pasó a ser el último.
    // Recalibrado sobre el arte nuevo (quest_book_bg.png 1456x1052).
    // Medidas por detección de color pixel a pixel sobre quest_book_bg2.png YA
    // recortado (1470x1053, ver BG_NATIVE_W/H): para cada una de las 7 pestañas se
    // escaneó una columna/fila central buscando la transición de color madera→píldora
    // y píldora→madera, y el resultado se convirtió al sistema 340x220. Verde y azul
    // (pestañas 1 y 7) se usaron como referencia para X1/X2 porque no tienen las
    // florituras decorativas (líneas curvas que conectan algunas pestañas) que
    // contaminaban la medición en dorado/violeta/cyan/gris.
    private static final int TAB_X1 = 305, TAB_X2 = 328;
    private static final int[] TAB_Y1 = { 24, 47, 72, 96, 123, 149, 173 };
    private static final int[] TAB_Y2 = { 41, 65, 90, 114, 140, 167, 191 };
    // Slot en TAB_Y1/TAB_Y2 → valor real de selectedTab. Los primeros 4 son directos
    // (Explorador=1...Técnico=4); los últimos tres permiten que Mago quede antes que
    // Logros visualmente sin tener que renumerarla. 6 = TAB_MAGO, 7 = TAB_GUERRERO.
    // Con el arte nuevo las 7 formas de pestaña ya vienen horneadas — Guerrero
    // (slot 6) ya no necesita dibujarse aparte con código como antes.
    private static final int[] TAB_SLOT_TO_SELECTED = { 1, 2, 3, 4, 6, 5, 7 };
    // "Logros" (slot 5, la gris): la FORMA ya viene horneada en el arte nuevo; el
    // único ícono que se dibuja por código encima es un tilde simple (no hay un gif
    // propio de "logros" todavía) — ver el bloque más abajo, junto a los demás.

    // Detectado píxel a píxel sobre quest_book_bg.png (arte viejo: 1470x1070 → 340x220).
    // Borde rojo IZQ: (116,103)→(657,907) | DER: (734,103)→(1282,906).
    // Con el arte nuevo (1468x1052) se volvió a medir el borde rojo y quedó prácticamente
    // en el mismo lugar (IZQ (116,104)→(656,906), DER (734,104)→(1282,906)), así que
    // PAGE_L_*/PAGE_R_* siguen valiendo sin cambios.
    // PAGE_L recalculado igual que PAGE_R: mismo ~4px de padding medido desde el marco
    // rojo de la página izquierda (que es prácticamente del mismo tamaño que el de la
    // derecha, (116,104)→(656,906)). Antes quedaba con mucho más margen que la derecha
    // sin motivo, cortando contenido de forma innecesaria.
    // PAGE_L recalculado con el mismo método pixel-a-pixel que PAGE_R_BORDER: se
    // escaneó el marco rojo real de la página izquierda en quest_book_bg2.png
    // (nativo x=116→657, y=103→907) y se convirtió a 340x220 con un padding chico
    // y parejo a los dos lados (~3-4px), en vez del valor viejo (PAGE_L_X=45) que
    // dejaba un hueco de ~18px con la línea roja real — mucho más que el resto de
    // los márgenes del libro — y por eso los íconos se veían "flotando" lejos del borde.
    private static final int PAGE_L_X = 30,  PAGE_L_Y = 24, PAGE_L_W = 119, PAGE_L_H = 163;
    // PAGE_R recalculado directamente desde el marco rojo de la página derecha (con ~4px de
    // padding para no tocar el borde) — antes quedaba mucho más angosto de lo que el marco
    // realmente permite, lo que hacía que la descripción se recortara sin necesidad.
    private static final int PAGE_R_X = 174, PAGE_R_Y = 27, PAGE_R_W = 115, PAGE_R_H = 154;
    // Marco rojo REAL de la página derecha (sin el ~4-8px de padding hacia adentro que
    // tiene PAGE_R_X/Y/W/H). Medido pixel a pixel sobre quest_book_bg2.png ya recortado
    // escaneando dónde empieza/termina la línea roja en cada borde. Se usa SOLO para la
    // ilustración del bestiario (drawBestiaryIllustratedDetail): esa imagen (detail.png)
    // ya trae su propio marco rojo pegado a los bordes del archivo (0px de margen), así
    // que si se dibuja dentro del rectángulo PAGE_R (encogido para el texto) su marco
    // queda separado/adentro del marco real del libro → efecto de "doble marco" que se
    // notaba como fondo falso. Usando este rectángulo sin encoger, ambos marcos calzan.
    private static final int PAGE_R_BORDER_X = 170, PAGE_R_BORDER_Y = 22,
                              PAGE_R_BORDER_W = 127, PAGE_R_BORDER_H = 168;
    private static final int ROW_H = 12;
    // El padding que PAGE_L_Y/PAGE_R_Y ya traen (~3-4px, ver comentarios arriba) alcanza
    // para separar el CONTENIDO del borde rojo real, pero no para el título en negrita:
    // el glifo empieza a dibujarse desde su borde superior, así que con esos ~3-4px la
    // parte de arriba de la letra queda pegada/pisando la línea roja (reportado: "Warrior"
    // tocando el borde de arriba). Este padding EXTRA se suma solo al primer renglón de
    // cada página/subpágina (título o link "« Menú") — el resto de las filas ya quedan
    // bien porque se separan solas via ROW_H. No toca los rectángulos de scissor (esos
    // siguen usando PAGE_L_Y/PAGE_R_Y "crudos"), así que no se pierde área de scroll.
    // Recalibrado tras feedback con capturas reales: 7px dejaba el título con MÁS aire
    // del pedido ("muy lejos de la línea roja"). Bajado a 4 — separación chica pero
    // sin tocar, que es lo que se pidió ("unos pixeles solamente").
    private static final int TITLE_TOP_PAD = 4;
    // Misma idea que TITLE_TOP_PAD pero para la fila de abajo (indicador "X-Y/Z" +
    // flecha ▼), que por estar pegada al límite inferior de PAGE_L_H caía sobre el
    // decorado de la esquina inferior. Se resta de bottomRowY para separarla un poco.
    // 4px la alejaba demasiado del borde Y la pegaba contra la última fila de la
    // lista de arriba (quedaba "muy pegado al texto, muy lejos de la línea roja").
    // Bajado a 2 — con eso alcanza para no tocar el borde sin comerse el aire que
    // separa el indicador del último renglón de la lista.
    private static final int BOTTOM_ROW_PAD = 2;
    // Padding chico SOLO para el link "« Menú" (texto normal, sin negrita) — no
    // necesita tanto aire como el título en negrita (TITLE_TOP_PAD) porque el glifo
    // normal no sube tanto por encima de su línea base. Antes usaba el mismo
    // TITLE_TOP_PAD que el título y quedaba con más aire del necesario/pedido —
    // el link debe casi tocar el borde, bien pegado arriba.
    // 2px seguía sin alcanzar (el link tocaba igual) — subido a 5.
    private static final int BACK_LINK_TOP_PAD = 5;

    /** X centrado para un texto ya formateado (con códigos § incluidos, así
     *  mc.font.width mide el ancho real con negrita si corresponde) dentro de una
     *  franja [boxX, boxX+boxW). Usado para que los títulos y el indicador de
     *  página aprovechen el espacio libre en vez de quedar pegados a la izquierda. */
    private static int centeredTextX(Minecraft mc, String formatted, int boxX, int boxW) {
        return boxX + Math.max(0, (boxW - mc.font.width(formatted)) / 2);
    }
    // Bestiario: filas mucho más altas que las de misiones — pedido explícito de que
    // solo entren 2 por pantalla y el resto se vea haciendo scroll, para apreciar bien
    // la animación (antes eran 22px/4 filas, quedaban chicas).
    private static final int BESTIARY_ICON_SIZE = 56;
    // Ancho reservado para la columna del ícono. Los PNG no son cuadrados — mobs anchos
    // como Vex (239x140) o Ravager (186x140) medirían >90px de ancho a 56px de alto y se
    // comerían el texto del nombre; drawBestiaryIcon ahora recibe este límite y escala
    // por lo que sea más chico (alto o ancho), así que un mob ancho queda un poco menos
    // alto en vez de invadir la columna de texto.
    private static final int BESTIARY_ICON_COL_W = 64;
    private static final int BESTIARY_ROW_H = 63;
    // VISIBLE_ROWS: resta título (ROW_H) + fila de indicador/flecha (ROW_H)
    // Fórmula corregida: antes no restaba TITLE_TOP_PAD ni BOTTOM_ROW_PAD, así que
    // asumía más espacio disponible del que en realidad queda una vez que esos 2
    // paddings existen — con valores altos de cualquiera de los 2, la última fila
    // de la lista terminaba pisando el indicador de página de abajo ("muy pegado
    // al texto"). Ahora se descuenta todo lo que realmente ocupa el margen superior
    // (TITLE_TOP_PAD + la fila del título) y el inferior (fila reservada + su padding).
    private static final int VISIBLE_ROWS = (PAGE_L_H - TITLE_TOP_PAD - ROW_H - ROW_H - BOTTOM_ROW_PAD) / ROW_H;
    // Resta: link "Menú" + título "Bestiario" + fila de pestañas Comunes/Especiales/Jefes
    // (esas 3 filas de arriba). Ya no se resta una fila fija para la flecha ▼ abajo —
    // ahora se ancla dinámicamente justo después de la última fila dibujada (ver
    // drawBestiarySubpage), así que no hace falta reservarle espacio de antemano.
    private static final int BESTIARY_VISIBLE_ROWS = (PAGE_L_H - ROW_H * 3) / BESTIARY_ROW_H; // = 2, filas grandes

    private record QuestEntry(String id, String name, String[] desc, String[] rewards, boolean isReference) {}
    private record Tab(String label, List<QuestEntry> quests) {}

    private final List<Tab> tabs = new ArrayList<>();
    private int selectedTab  = 0;
    private int selectedIndex = -1;
    // Toggle "ocultar completadas": solo aplica a las 5 pestañas de categoría
    // (Explorador/Minero/Constructor/Técnico/Guerrero); no tiene sentido en Completadas
    // (que ya está pensada al revés, mostrar solo eso) ni en Logros (referencia).
    private boolean hideCompleted = false;
    // Pestaña virtual del creeper: no vive en `tabs` (no es una lista fija), se arma
    // al vuelo juntando lo completado de Explorador/Minero/Constructor/Técnico/Guerrero —
    // ver currentTabQuests()/buildCompletedQuests().
    private static final int TAB_MAGO = 6;      // real, vive en `tabs` (pociones + encantamientos)
    private static final int TAB_GUERRERO = 7;  // real, vive en `tabs` (cazador de mobs + incursiones)
    private static final int TAB_COMPLETED = 8; // virtual, no vive en `tabs`
    private static final int TAB_MENU = 9;      // virtual, no vive en `tabs` — Progreso/Bestiario/etc.
    // Offset de scroll por tab (índice del primer elemento visible)
    private final int[] scrollOffset = new int[10]; // Principal, Explorador, Minero, Constructor, Técnico, Logros, Mago, Guerrero, Completadas(creeper), Menú

    // Botones página derecha — ancho fijo, caben cómodos dentro de PAGE_R_W=119
    private int claimBtnX, claimBtnY, claimBtnW = 102, claimBtnH = 11;
    private int pinBtnX,   pinBtnY,   pinBtnW = 102,   pinBtnH = 11;
    private int optionsBtnX, optionsBtnY, optionsBtnW = 14, optionsBtnH = 12;
    // Un solo botón (≡) que funciona EXACTAMENTE como cualquier otra pestaña: selecciona
    // TAB_MENU (ver más abajo, junto a TAB_GUERRERO/TAB_COMPLETED), dispara la misma
    // animación de vuelta de página y dibuja adentro de las páginas del libro — no es
    // una ventana aparte. Adentro de esa pestaña hay un sub-estado (menuSubPage) para
    // saber si se ve el listado "Progreso / Bestiario" o el contenido de cada uno.
    private int menuBtnX, menuBtnY, menuBtnW = 14, menuBtnH = 12;
    private String menuSubPage = null; // null = listado, "progress", "bestiary"

    // ── Tutorial de primer uso ──
    // Se activa solo si el jugador nunca lo vio ni lo saltó (QuestData.hasSeenTutorial).
    // Mientras está activo, handleClick() absorbe todos los clicks del libro real —
    // el tutorial es un overlay que se dibuja encima de todo al final de
    // extractRenderState(), no una pantalla aparte, así que no hace falta duplicar
    // el fondo del libro ni reposicionar nada.
    private boolean tutorialActive;
    private int tutorialStep = 0;
    private static final int TUTORIAL_STEPS = 4;
    private int menuRowX, menuRowW, menuRowProgressY, menuRowBestiaryY, menuRowStatsY, menuRowH = 12;
    private int menuBackX, menuBackY, menuBackW; // link "< Menú" para volver del sub-contenido al listado
    private int selectedBestiaryIndex = -1;
    // Scroll del texto que va ENCIMA de la ilustración de página completa (zombie,
    // esqueleto, enderman, ...) — los dibujos y los íconos de recompensa son fijos
    // (son parte de la imagen), solo el texto se puede desplazar.
    private int bestiaryDetailScroll = 0;
    private int bestiaryDetailMaxScroll = 0;
    private int bestiaryDetailScrollBtnX, bestiaryDetailScrollUpY, bestiaryDetailScrollDownY;
    // Tooltip del ícono de loot real bajo el mouse (ver drawBestiaryLootIcons). Se
    // dibuja DIFERIDO al final de extractRenderState, igual que deferredOverCreeper/
    // deferredOverHome más abajo — así queda por ENCIMA de todo (texto de la página,
    // ilustración, etc.) en vez de recortado por el scissor de la página derecha, que
    // es lo que pasaba antes (se cortaba a los costados si el tooltip no entraba en el
    // ancho de la página).
    private boolean bestiaryLootTooltipShown = false;
    private int bestiaryLootTooltipX, bestiaryLootTooltipY;
    private String bestiaryLootTooltipName, bestiaryLootTooltipNote;
    // 0 = Comunes, 1 = Especiales, 2 = Jefes — las 3 pestañas clickeables de arriba
    // de la lista del Bestiario (dentro de la misma página, no cambian de pestaña
    // del libro).
    private int bestiaryCategory = 0;
    private final int[] bestiaryTabX = new int[3];
    private final int[] bestiaryTabW = new int[3];
    private int bestiaryTabY;
    private java.util.List<Bestiary.Entry> bestiaryOrder = new java.util.ArrayList<>();
    // Para no reconstruir bestiaryOrder (allocar + copiar la lista) en cada uno de los
    // ~60 frames por segundo mientras el Bestiario está abierto: la lista de mobs de
    // una categoría no cambia salvo que el jugador cambie de pestaña (Común/Especial/
    // Jefes) — el estado "matado"/revelado de cada mob se lee aparte, por fila, en cada
    // frame, así que cachear el ORDEN por categoría es seguro incluso si el jugador
    // mata algo mientras tiene el libro abierto (el libro no pausa el juego).
    private int bestiaryOrderBuiltForCategory = -1;
    private String claimBtnQuestId = null;

    // Botones de la página izquierda, en la fila del título (junto al label de la pestaña)
    private int hideToggleX, hideToggleY, hideToggleSize = 9;
    private int claimAllBtnX, claimAllBtnY, claimAllBtnW, claimAllBtnH = 9;

    // Zonas de scroll (flechas ▲▼)
    private int scrollUpBtnY, scrollDownBtnY, scrollBtnX, scrollDownBtnX;

    // ── Iconografía de botones del HUD (madera + pergamino, ver Iconografia.png) ──
    // Reemplaza los glifos de texto (▲▼≡⚙) y el cuadrado de color plano del toggle
    // "ocultar completadas" por los íconos recortados de la propuesta del usuario.
    // Todos comparten el mismo helper de dibujo (drawIconButton) para que el efecto
    // de profundidad + animación de click sea IDÉNTICO en todos: eso es lo que hace
    // que se sientan parte de un mismo sistema en vez de botones sueltos.
    private static final Identifier ICON_SCROLL_UP   = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/scroll_up.png");
    private static final Identifier ICON_SCROLL_DOWN = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/scroll_down.png");
    private static final Identifier ICON_MENU        = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/menu.png");
    private static final Identifier ICON_GEAR        = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/gear.png");
    private static final Identifier ICON_EYE_SHOW    = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/eye_show.png");
    private static final Identifier ICON_EYE_HIDE    = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/eye_hide.png");
    private static final Identifier ICON_CLAIM_ALL   = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/claim_all.png");
    // Silueta pre-recortada (mismo dibujo, negro semitransparente) usada SOLO para la
    // sombra de este ícono — ver overload de drawIconButton más abajo. Los demás íconos
    // (eye/gear/menu/scroll) siguen con la sombra cuadrada de siempre porque su
    // textura llena todo el cuadrado de 96x96 (son "medallón" completo); este en cambio
    // desde que se le sacó el fondo es solo el cofre+brotes con recorte irregular, así
    // que una sombra cuadrada quedaba flotando de fondo detrás sin coincidir con el
    // dibujo — se notaba como un cuadrado gris raro, ni pegado ni centrado al ícono.
    private static final Identifier ICON_CLAIM_ALL_SHADOW = Identifier.fromNamespaceAndPath("homequest", "textures/gui/icons/claim_all_shadow.png");
    private static final int ICON_NATIVE = 96; // los 7 PNG (recortados exacto al borde) son cuadrados de 96x96

    // Timestamp (System.currentTimeMillis) del último click por botón, clave = nombre
    // corto del botón ("gear", "menu", "scrollUp", etc.). Alimenta pressT() para la
    // animación de "hundido" — un solo Map chico en vez de un campo long por botón,
    // así agregar un botón nuevo no pide tocar 3 lugares distintos.
    private final java.util.Map<String, Long> btnPressAt = new java.util.HashMap<>();
    private static final long BTN_PRESS_MS = 120; // duración total del "bounce" de click

    // ── Aviso de nuevo mob desbloqueado en el Bestiario ──
    // static: sobrevive a que la pantalla se recree cada vez que se abre el libro
    // (una instancia nueva de QuestBookScreen por apertura), así el parpadeo no se
    // "olvida" con solo cerrar y volver a abrir el libro sin pasar por Bestiario.
    // knownUnlockedMobKeys == null representa "todavía no se leyó ni una vez en esta
    // sesión del juego" — la primera lectura solo fija la base, sin hacer parpadear
    // nada (si no, todo mob ya desbloqueado antes de instalar esta función parpadearía
    // una vez al primer abrir el libro, lo cual sería ruido, no una notificación real).
    private static java.util.Set<String> knownUnlockedMobKeys = null;
    private static boolean bestiaryHasNewUnlock = false;

    /** Compara los mobs desbloqueados ahora contra la última lectura y marca el aviso si hay uno nuevo. */
    private void refreshBestiaryUnlockTracking() {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        if (uuid == null) return;
        java.util.Set<String> current = new java.util.HashSet<>();
        for (Bestiary.Entry e : Bestiary.common())  if (Bestiary.isKilled(e, uuid)) current.add(e.iconId());
        for (Bestiary.Entry e : Bestiary.special()) if (Bestiary.isKilled(e, uuid)) current.add(e.iconId());
        for (Bestiary.Entry e : Bestiary.bosses())  if (Bestiary.isKilled(e, uuid)) current.add(e.iconId());
        if (knownUnlockedMobKeys == null) {
            knownUnlockedMobKeys = current;
            return;
        }
        for (String key : current) {
            if (!knownUnlockedMobKeys.contains(key)) { bestiaryHasNewUnlock = true; break; }
        }
        knownUnlockedMobKeys = current;
    }

    /** Punto único para entrar a Bestiario — usado por la fila del menú Y por el atajo de la cabeza de wither. */
    private void enterBestiary() {
        menuSubPage = "bestiary";
        bestiaryCategory = 0;
        scrollOffset[TAB_MENU] = 0;
        selectedBestiaryIndex = -1;
        bestiaryHasNewUnlock = false; // ya lo vio: se apaga el parpadeo hasta el próximo desbloqueo
    }

    private final boolean english;

    /**
     * Nombre de una misión por id, sin abrir el libro — usado por
     * QuestCompletedBanner (HomeQuestClient) para saber qué texto poner en el
     * cartel lateral cuando se completa una misión, sin depender de que el libro
     * esté abierto en ese momento (puede completarse una misión con el libro
     * cerrado). Construye una instancia descartable solo para leer `tabs`, que ya
     * tiene TODOS los nombres como texto fijo — una sola fuente de verdad en vez
     * de duplicar la lista de nombres en otro lugar.
     */
    public static String questNameFor(String questId, boolean english) {
        QuestBookScreen tmp = new QuestBookScreen(english);
        for (Tab tab : tmp.tabs) {
            for (QuestEntry q : tab.quests()) {
                if (q.id().equals(questId)) return q.name();
            }
        }
        return questId; // no debería pasar, pero mejor esto que null
    }

    public QuestBookScreen(boolean english) {
        super(Component.literal(english ? "Quest Book" : "Libro de Quests"));
        this.english = english;
        buildTabs();
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        this.tutorialActive = uuid != null && !QuestData.get().hasSeenTutorial(uuid);
    }

    private void buildTabs() {
        // ── PRINCIPAL ──
        List<QuestEntry> principal = new ArrayList<>();
        principal.add(q(QuestData.QUEST_FIRST_HOME, "Primera Casa", "First Home",
            new String[]{"Coloca el Cofre en tu base.", "Pon Cama, Mesa de Trabajo", "y Horno en radio de 8."},
            new String[]{"Place the Chest at your base.", "Place a Bed, Crafting Table,", "and Furnace within 8 blocks."},
            new String[]{"2x Pico de Piedra", "10x Pan", "1x Caña de Pescar"},
            new String[]{"2x Stone Pickaxe", "10x Bread", "1x Fishing Rod"}));
        principal.add(q(QuestData.QUEST_NEW_BEGINNING, "Un Nuevo Comienzo", "A New Beginning",
            new String[]{"Craftea una Mesa de Crafteo."},
            new String[]{"Craft a Crafting Table."},
            new String[]{"2x Pan"},
            new String[]{"2x Bread"}));
        principal.add(q(QuestData.QUEST_HANDS_ON, "Manos a la Obra", "Hands On",
            new String[]{"Fabrica herramientas de piedra", "de cada tipo."},
            new String[]{"Craft stone tools", "of every type."},
            new String[]{"32x Antorcha"},
            new String[]{"32x Torches"}));
        principal.add(q(QuestData.QUEST_FIRST_INGOTS, "Los Primeros Lingotes", "The First Ingots",
            new String[]{"Consigue 10 lingotes de hierro."},
            new String[]{"Get 10 iron ingots."},
            new String[]{"1x Caña de Pescar"},
            new String[]{"1x Fishing Rod"}));
        principal.add(q(QuestData.QUEST_WELL_PROTECTED, "Bien Protegido", "Well Protected",
            new String[]{"Equipa armadura completa de hierro."},
            new String[]{"Wear a full set of iron armor."},
            new String[]{"20x Zanahoria"},
            new String[]{"20x Carrots"}));
        principal.add(q(QuestData.QUEST_BEST_DEFENSE, "La Mejor Defensa", "The Best Defense",
            new String[]{"Fabrica un escudo."},
            new String[]{"Craft a shield."},
            new String[]{"16x Flechas"},
            new String[]{"16x Arrows"}));
        principal.add(q(QuestData.QUEST_FOUND_IT, "¡Lo Encontré!", "Found It!",
            new String[]{"Consigue un diamante."},
            new String[]{"Get a diamond."},
            new String[]{"32x Carbón"},
            new String[]{"32x Coal"}));
        principal.add(q(QuestData.QUEST_READY_FOR_ALL, "Listo para Todo", "Ready for Anything",
            new String[]{"Fabrica un pico de diamante."},
            new String[]{"Craft a diamond pickaxe."},
            new String[]{"32x Antorcha", "16x Bistec"},
            new String[]{"32x Torches", "16x Steak"}));
        principal.add(q(QuestData.QUEST_INTO_UNKNOWN, "Hacia lo Desconocido", "Into the Unknown",
            new String[]{"Entra al Nether."},
            new String[]{"Enter the Nether."},
            new String[]{"1x Poción"},
            new String[]{"1x Potion"}));
        principal.add(q(QuestData.QUEST_PLAYING_FIRE, "Jugando con Fuego", "Playing with Fire",
            new String[]{"Consigue 5 Blaze Rods."},
            new String[]{"Get 5 Blaze Rods."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        principal.add(q(QuestData.QUEST_BETWEEN_DIMS, "Entre Dimensiones", "Between Dimensions",
            new String[]{"Consigue 8 Ender Pearls."},
            new String[]{"Get 8 Ender Pearls."},
            new String[]{"1x Ballesta"},
            new String[]{"1x Crossbow"}));
        principal.add(q(QuestData.QUEST_ENDER_EYE, "La Mirada del Fin", "The Eye of the End",
            new String[]{"Crea un Ojo de Ender."},
            new String[]{"Craft an Eye of Ender."},
            new String[]{"4x Blaze Powder"},
            new String[]{"4x Blaze Powder"}));
        principal.add(q(QuestData.QUEST_LOST_FORTRESS, "La Fortaleza Perdida", "The Lost Fortress",
            new String[]{"Encuentra el Stronghold."},
            new String[]{"Find the Stronghold."},
            new String[]{"1x Manzana de Oro"},
            new String[]{"1x Golden Apple"}));
        principal.add(q(QuestData.QUEST_BEGINNING_END, "El Comienzo del Final", "The Beginning of the End",
            new String[]{"Derrota al Ender Dragon."},
            new String[]{"Defeat the Ender Dragon."},
            new String[]{"64x Cohetes"},
            new String[]{"64x Fireworks"}));
        principal.add(q(QuestData.QUEST_TAKE_FLIGHT, "Alza el Vuelo", "Take Flight",
            new String[]{"Consigue una Elytra."},
            new String[]{"Get an Elytra."},
            new String[]{"64x Cohetes"},
            new String[]{"64x Fireworks"}));
        principal.add(q(QuestData.QUEST_DEFYING_DARK, "Desafiando la Oscuridad", "Defying the Dark",
            new String[]{"Invoca al Wither."},
            new String[]{"Summon the Wither."},
            new String[]{"1x Tótem de la Inmortalidad"},
            new String[]{"1x Totem of Undying"}));
        principal.add(q(QuestData.QUEST_NEW_HOPE, "Una Nueva Esperanza", "A New Hope",
            new String[]{"Consigue una Nether Star."},
            new String[]{"Get a Nether Star."},
            new String[]{"1x Cabeza de Wither"},
            new String[]{"1x Wither Skeleton Skull"}));
        tabs.add(new Tab(english ? "Main" : "Principal", principal));

        // ── EXPLORADOR ──
        List<QuestEntry> explorador = new ArrayList<>();
        explorador.add(q(QuestData.QUEST_NEW_WORLD, "Un Nuevo Mundo", "A New World",
            new String[]{"Descubre un bioma nuevo."},
            new String[]{"Discover a new biome."},
            new String[]{"8x Antorcha"},
            new String[]{"8x Torches"}));
        explorador.add(q(QuestData.QUEST_BEYOND_HORIZON, "Más Allá del Horizonte", "Beyond the Horizon",
            new String[]{"Descubre 3 biomas."},
            new String[]{"Discover 3 biomes."},
            new String[]{"1x Mapa del Tesoro"},
            new String[]{"1x Treasure Map"}));
        explorador.add(q(QuestData.QUEST_NO_BORDERS, "Sin Fronteras", "No Borders",
            new String[]{"Descubre 10 biomas."},
            new String[]{"Discover 10 biomes."},
            new String[]{"1x Manzana de Oro"},
            new String[]{"1x Golden Apple"}));
        explorador.add(q(QuestData.QUEST_ALL_SEEN, "No Queda Nada por Ver", "Nothing Left to See",
            new String[]{"Descubre todos los biomas."},
            new String[]{"Discover every biome."},
            new String[]{"1x Tridente", "1x Libro Encantado"},
            new String[]{"1x Trident", "1x Enchanted Book"}));
        explorador.add(q(QuestData.QUEST_SIGNS_OF_LIFE, "Señales de Vida", "Signs of Life",
            new String[]{"Encuentra una aldea."},
            new String[]{"Find a village."},
            new String[]{"10x Papas"},
            new String[]{"10x Potatoes"}));
        explorador.add(q(QuestData.QUEST_ANCIENT_SANDS, "Arenas Antiguas", "Ancient Sands",
            new String[]{"Encuentra un templo del desierto."},
            new String[]{"Find a desert temple."},
            new String[]{"16x TNT"},
            new String[]{"16x TNT"}));
        explorador.add(q(QuestData.QUEST_JUNGLE_SECRETS, "La Selva Esconde Secretos", "The Jungle Hides Secrets",
            new String[]{"Encuentra un templo de la jungla."},
            new String[]{"Find a jungle temple."},
            new String[]{"32x Flechas"},
            new String[]{"32x Arrows"}));
        explorador.add(q(QuestData.QUEST_COLD_HOME, "Frío Hogar", "Cold Home",
            new String[]{"Encuentra un iglú."},
            new String[]{"Find an igloo."},
            new String[]{"16x Bistec"},
            new String[]{"16x Steak"}));
        explorador.add(q(QuestData.QUEST_ECHOES_PAST, "Ecos del Pasado", "Echoes of the Past",
            new String[]{"Encuentra un naufragio."},
            new String[]{"Find a shipwreck."},
            new String[]{"8x Cuero"},
            new String[]{"8x Leather"}));
        explorador.add(q(QuestData.QUEST_UNDER_WAVES, "Bajo las Olas", "Under the Waves",
            new String[]{"Encuentra ruinas oceánicas."},
            new String[]{"Find ocean ruins."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        explorador.add(q(QuestData.QUEST_SEA_KINGDOM, "El Reino del Mar", "The Kingdom of the Sea",
            new String[]{"Encuentra un monumento oceánico."},
            new String[]{"Find an ocean monument."},
            new String[]{"16x Prismarina"},
            new String[]{"16x Prismarine"}));
        explorador.add(q(QuestData.QUEST_HOSTILE_TERRITORY, "Territorio Hostil", "Hostile Territory",
            new String[]{"Encuentra un Outpost."},
            new String[]{"Find a Pillager Outpost."},
            new String[]{"32x Flechas"},
            new String[]{"32x Arrows"}));
        explorador.add(q(QuestData.QUEST_FOREST_HOUSE, "La Casa del Bosque", "The House in the Woods",
            new String[]{"Encuentra una mansión."},
            new String[]{"Find a woodland mansion."},
            new String[]{"64x Esmeraldas"},
            new String[]{"64x Emeralds"}));
        explorador.add(q(QuestData.QUEST_INFERNAL_FORTRESS, "Fortaleza Infernal", "Infernal Fortress",
            new String[]{"Encuentra una fortaleza del Nether."},
            new String[]{"Find a Nether fortress."},
            new String[]{"1x Libro Encantado"},
            new String[]{"1x Enchanted Book"}));
        explorador.add(q(QuestData.QUEST_GOLD_KINGDOM, "El Reino del Oro", "The Kingdom of Gold",
            new String[]{"Encuentra un bastión."},
            new String[]{"Find a bastion remnant."},
            new String[]{"16x Bloques de Oro"},
            new String[]{"16x Gold Blocks"}));
        explorador.add(q(QuestData.QUEST_FORGOTTEN_FORTRESS, "La Fortaleza Olvidada", "The Forgotten Fortress",
            new String[]{"Encuentra el Stronghold."},
            new String[]{"Find the Stronghold."},
            new String[]{"2x Ojos de Ender"},
            new String[]{"2x Eyes of Ender"}));
        explorador.add(q(QuestData.QUEST_STRANGE_WORLD, "Un Mundo Extraño", "A Strange World",
            new String[]{"Llega al End."},
            new String[]{"Reach the End."},
            new String[]{"32x Cohetes"},
            new String[]{"32x Fireworks"}));
        explorador.add(q(QuestData.QUEST_LOST_CITY, "La Ciudad Perdida", "The Lost City",
            new String[]{"Encuentra una End City."},
            new String[]{"Find an End City."},
            new String[]{"64x Cohetes"},
            new String[]{"64x Fireworks"}));
        explorador.add(q(QuestData.QUEST_NO_NOISE, "No Hagas Ruido", "Don't Make a Sound",
            new String[]{"Encuentra una Ancient City."},
            new String[]{"Find an Ancient City."},
            new String[]{"16x Zanahorias Doradas"},
            new String[]{"16x Golden Carrots"}));
        explorador.add(q(QuestData.QUEST_TRIAL_VALOR, "Pon a Prueba tu Valor", "Put Your Courage to the Test",
            new String[]{"Encuentra una Trial Chamber."},
            new String[]{"Find a Trial Chamber."},
            new String[]{"32x Flechas Espectrales"},
            new String[]{"32x Spectral Arrows"}));
        tabs.add(new Tab(english ? "Explorer" : "Explorador", explorador));

        // ── MINERO ──
        List<QuestEntry> minero = new ArrayList<>();
        minero.add(q(QuestData.QUEST_STAY_WARM, "No Pasaremos Frío", "Staying Warm",
            new String[]{"Consigue 10 de carbón."},
            new String[]{"Get 10 coal."},
            new String[]{"4x Chuletas de Cerdo"},
            new String[]{"4x Porkchops"}));
        minero.add(q(QuestData.QUEST_WINTER_FUEL, "Combustible para el Invierno", "Winter Fuel",
            new String[]{"Consigue 64 de carbón."},
            new String[]{"Get 64 coal."},
            new String[]{"16x Bistec"},
            new String[]{"16x Steak"}));
        minero.add(q(QuestData.QUEST_COAL_KING, "Rey del Tizón", "King of Coal",
            new String[]{"Consigue 256 de carbón."},
            new String[]{"Get 256 coal."},
            new String[]{"64x Bloques de Carbón"},
            new String[]{"64x Coal Blocks"}));
        minero.add(q(QuestData.QUEST_METAL_AGE, "Edad de los Metales", "Age of Metals",
            new String[]{"Consigue 10 hierro."},
            new String[]{"Get 10 iron."},
            new String[]{"32x Carbón"},
            new String[]{"32x Coal"}));
        minero.add(q(QuestData.QUEST_IRON_FEVER, "Fiebre del Hierro", "Iron Fever",
            new String[]{"Consigue 64 hierro."},
            new String[]{"Get 64 iron."},
            new String[]{"16x Pan"},
            new String[]{"16x Bread"}));
        minero.add(q(QuestData.QUEST_IRON_WILL, "Voluntad de Hierro", "Iron Will",
            new String[]{"Consigue 256 hierro."},
            new String[]{"Get 256 iron."},
            new String[]{"1x Libro Encantado"},
            new String[]{"1x Enchanted Book"}));
        minero.add(q(QuestData.QUEST_GOOD_CONDUCTORS, "Buenos Conductores", "Good Conductors",
            new String[]{"Consigue 10 de cobre."},
            new String[]{"Get 10 copper."},
            new String[]{"32x Tablones de Roble"},
            new String[]{"32x Oak Planks"}));
        minero.add(q(QuestData.QUEST_LIBERTY_STATUE, "Estatua de la Libertad", "Statue of Liberty",
            new String[]{"Consigue 64 de cobre."},
            new String[]{"Get 64 copper."},
            new String[]{"16x Bistec"},
            new String[]{"16x Steak"}));
        minero.add(q(QuestData.QUEST_COVETED_SHINE, "Brillo Codiciable", "Coveted Shine",
            new String[]{"Consigue 10 de oro."},
            new String[]{"Get 10 gold."},
            new String[]{"10x Lingotes de Oro"},
            new String[]{"10x Gold Ingots"}));
        minero.add(q(QuestData.QUEST_MIDAS_TOUCH, "Toque de Midas", "Midas Touch",
            new String[]{"Consigue 64 de oro."},
            new String[]{"Get 64 gold."},
            new String[]{"16x Bloques de Oro"},
            new String[]{"16x Gold Blocks"}));
        minero.add(q(QuestData.QUEST_SPARKLING_DUST, "Polvo Chispeante", "Sparkling Dust",
            new String[]{"Consigue 10 de redstone."},
            new String[]{"Get 10 redstone."},
            new String[]{"32x Redstone"},
            new String[]{"32x Redstone"}));
        minero.add(q(QuestData.QUEST_CONTINUOUS_CURRENT, "Corriente Continua", "Continuous Current",
            new String[]{"Consigue 128 de redstone."},
            new String[]{"Get 128 redstone."},
            new String[]{"16x Repetidores"},
            new String[]{"16x Repeaters"}));
        minero.add(q(QuestData.QUEST_WIZARD_DYE, "Tinte de Hechicero", "Wizard's Dye",
            new String[]{"Consigue 10 lapislázuli."},
            new String[]{"Get 10 lapis lazuli."},
            new String[]{"16x Lapislázuli"},
            new String[]{"16x Lapis Lazuli"}));
        minero.add(q(QuestData.QUEST_ULTRAMARINE, "Azul Ultramar", "Ultramarine",
            new String[]{"Consigue 64 lapislázuli."},
            new String[]{"Get 64 lapis lazuli."},
            new String[]{"1x Mesa de Encantamientos"},
            new String[]{"1x Enchanting Table"}));
        minero.add(q(QuestData.QUEST_FIVE_CHOSEN, "Los Cinco Elegidos", "The Chosen Five",
            new String[]{"Consigue 5 diamantes."},
            new String[]{"Get 5 diamonds."},
            new String[]{"1x Pico de Diamante"},
            new String[]{"1x Diamond Pickaxe"}));
        minero.add(q(QuestData.QUEST_SHINE_HOARDER, "Acaparador de Brillos", "Shine Hoarder",
            new String[]{"Consigue 32 diamantes."},
            new String[]{"Get 32 diamonds."},
            new String[]{"8x Bloques de Diamante"},
            new String[]{"8x Diamond Blocks"}));
        minero.add(q(QuestData.QUEST_UNBREAKABLE, "¡Inquebrantable!", "Unbreakable!",
            new String[]{"Consigue 64 diamantes."},
            new String[]{"Get 64 diamonds."},
            new String[]{"4x Lingotes de Netherite"},
            new String[]{"4x Netherite Ingots"}));
        minero.add(q(QuestData.QUEST_ANCIENT_ECHOES, "Ecos de la Antigüedad", "Echoes of Antiquity",
            new String[]{"Consigue 1 Ancient Debris."},
            new String[]{"Get 1 Ancient Debris."},
            new String[]{"1x Fragmento de Netherite"},
            new String[]{"1x Netherite Scrap"}));
        minero.add(q(QuestData.QUEST_FORGED_HELL, "Forjado en el Infierno", "Forged in Hell",
            new String[]{"Consigue 8 Ancient Debris."},
            new String[]{"Get 8 Ancient Debris."},
            new String[]{"1x Lingote de Netherite"},
            new String[]{"1x Netherite Ingot"}));
        minero.add(q(QuestData.QUEST_VILLAGER_FAVOR, "El Favor del Aldeano", "The Villager's Favor",
            new String[]{"Consigue una esmeralda."},
            new String[]{"Get an emerald."},
            new String[]{"5x Esmeraldas"},
            new String[]{"5x Emeralds"}));
        tabs.add(new Tab(english ? "Miner" : "Minero", minero));

        // ── CONSTRUCTOR ──
        List<QuestEntry> constructor = new ArrayList<>();
        constructor.add(q(QuestData.QUEST_HONEST_WORK, "Es Trabajo Honesto", "Honest Work",
            new String[]{"Coloca 50 bloques."},
            new String[]{"Place 50 blocks."},
            new String[]{"64x Tablones de Roble"},
            new String[]{"64x Oak Planks"}));
        constructor.add(q(QuestData.QUEST_FIRM_FOUNDATIONS, "Cimientos Firmes", "Firm Foundations",
            new String[]{"Coloca 250 bloques."},
            new String[]{"Place 250 blocks."},
            new String[]{"64x Piedra"},
            new String[]{"64x Stone"}));
        constructor.add(q(QuestData.QUEST_MASON_HANDS, "Manos de Albañil", "Mason's Hands",
            new String[]{"Coloca 1000 bloques."},
            new String[]{"Place 1,000 blocks."},
            new String[]{"32x Lingotes de Hierro"},
            new String[]{"32x Iron Ingots"}));
        constructor.add(q(QuestData.QUEST_GREAT_ARCHITECT, "Gran Arquitecto", "Great Architect",
            new String[]{"Coloca 5000 bloques."},
            new String[]{"Place 5,000 blocks."},
            new String[]{"5x Diamantes", "1x Lingote de Netherite"},
            new String[]{"5x Diamonds", "1x Netherite Ingot"}));
        constructor.add(q(QuestData.QUEST_STEP_BY_STEP, "Paso a Paso", "Step by Step",
            new String[]{"Coloca 10 escaleras."},
            new String[]{"Place 10 stairs."},
            new String[]{"32x Escaleras de Roble"},
            new String[]{"32x Oak Stairs"}));
        constructor.add(q(QuestData.QUEST_HALF_BLOCK, "A Medio Bloque", "Halfway There",
            new String[]{"Coloca 10 losas."},
            new String[]{"Place 10 slabs."},
            new String[]{"32x Losas de Roble"},
            new String[]{"32x Oak Slabs"}));
        constructor.add(q(QuestData.QUEST_ESCAPE_HATCH, "Escotilla de Escape", "Escape Hatch",
            new String[]{"Coloca 10 trapdoors."},
            new String[]{"Place 10 trapdoors."},
            new String[]{"16x Trampillas de Roble"},
            new String[]{"16x Oak Trapdoors"}));
        constructor.add(q(QuestData.QUEST_KNOCK_BEFORE, "Toca Antes de Entrar", "Knock Before Entering",
            new String[]{"Coloca 10 puertas."},
            new String[]{"Place 10 doors."},
            new String[]{"16x Puertas de Roble"},
            new String[]{"16x Oak Doors"}));
        constructor.add(q(QuestData.QUEST_FRAGILE_TRANSP, "Frágil Transparencia", "Fragile Transparency",
            new String[]{"Coloca 20 cristales."},
            new String[]{"Place 20 panes of glass."},
            new String[]{"32x Vidrio"},
            new String[]{"32x Glass"}));
        constructor.add(q(QuestData.QUEST_NO_PASS, "¡No Pasarán!", "They Shall Not Pass!",
            new String[]{"Coloca 20 vallas."},
            new String[]{"Place 20 fences."},
            new String[]{"32x Vallas de Roble"},
            new String[]{"32x Oak Fences"}));
        constructor.add(q(QuestData.QUEST_SAFE_NIGHTS, "Noches Seguras", "Safe Nights",
            new String[]{"Coloca 10 faroles."},
            new String[]{"Place 10 lanterns."},
            new String[]{"16x Faroles"},
            new String[]{"16x Lanterns"}));
        constructor.add(q(QuestData.QUEST_MARINE_LIGHT, "Iluminación Marina", "Marine Lighting",
            new String[]{"Coloca 10 linternas."},
            new String[]{"Place 10 sea lanterns."},
            new String[]{"16x Linternas Marinas"},
            new String[]{"16x Sea Lanterns"}));
        constructor.add(q(QuestData.QUEST_DETAILS_MATTER, "Detalles que Suman", "Details Matter",
            new String[]{"Coloca 20 bloques decorativos."},
            new String[]{"Place 20 decorative blocks."},
            new String[]{"8x Macetas"},
            new String[]{"8x Flower Pots"}));
        constructor.add(q(QuestData.QUEST_CHISEL_STONE, "Cincel y Piedra", "Chisel and Stone",
            new String[]{"Coloca 20 bloques tallados."},
            new String[]{"Place 20 chiseled blocks."},
            new String[]{"32x Ladrillos de Piedra Cincelados"},
            new String[]{"32x Chiseled Stone Bricks"}));
        constructor.add(q(QuestData.QUEST_PIG_HOUSE, "Casita de Cerdito", "Piggy's Little House",
            new String[]{"Coloca 20 bloques de ladrillo."},
            new String[]{"Place 20 brick blocks."},
            new String[]{"64x Ladrillos"},
            new String[]{"64x Bricks"}));
        constructor.add(q(QuestData.QUEST_CLASSIC_TEMPLE, "Templo Clásico", "Classic Temple",
            new String[]{"Coloca 20 bloques de cuarzo."},
            new String[]{"Place 20 quartz blocks."},
            new String[]{"32x Bloques de Cuarzo"},
            new String[]{"32x Quartz Blocks"}));
        constructor.add(q(QuestData.QUEST_COLOR_PALETTE, "Paleta de Colores", "Color Palette",
            new String[]{"Coloca 20 hormigón."},
            new String[]{"Place 20 concrete blocks."},
            new String[]{"8x Esmeraldas"},
            new String[]{"8x Emeralds"}));
        constructor.add(q(QuestData.QUEST_STICKY_STRUCT, "Estructuras Pegajosas", "Sticky Structures",
            new String[]{"Coloca 20 bloques de miel."},
            new String[]{"Place 20 honey blocks."},
            new String[]{"16x Bloques de Miel"},
            new String[]{"16x Honey Blocks"}));
        constructor.add(q(QuestData.QUEST_PLAZA_MEETING, "¡Reunión en la Plaza!", "Town Square Meeting!",
            new String[]{"Coloca una campana."},
            new String[]{"Place a bell."},
            new String[]{"16x Esmeraldas"},
            new String[]{"16x Emeralds"}));
        constructor.add(q(QuestData.QUEST_BEACON_LIGHT, "Luz Guía del Infinito", "Guiding Light of the Infinite",
            new String[]{"Coloca un beacon."},
            new String[]{"Place a beacon."},
            new String[]{"2x Lingotes de Netherite", "4x Bloques de Diamante"},
            new String[]{"2x Netherite Ingots", "4x Diamond Blocks"}));
        tabs.add(new Tab(english ? "Builder" : "Constructor", constructor));

        // ── TÉCNICO ──
        List<QuestEntry> tecnico = new ArrayList<>();
        tecnico.add(q(QuestData.QUEST_MOVEMENT_START, "Empieza el Movimiento", "The Movement Begins",
            new String[]{"Craftea un pistón."},
            new String[]{"Craft a piston."},
            new String[]{"4x Pistones"},
            new String[]{"4x Pistons"}));
        tecnico.add(q(QuestData.QUEST_STICKY_MECH, "Mecanismo Adhesivo", "Sticky Mechanism",
            new String[]{"Craftea un pistón pegajoso."},
            new String[]{"Craft a sticky piston."},
            new String[]{"4x Pistones Pegajosos"},
            new String[]{"4x Sticky Pistons"}));
        tecnico.add(q(QuestData.QUEST_SIGNAL_PATIENCE, "Paciencia en la Señal", "Patience in the Signal",
            new String[]{"Craftea un repetidor."},
            new String[]{"Craft a repeater."},
            new String[]{"8x Repetidores"},
            new String[]{"8x Repeaters"}));
        tecnico.add(q(QuestData.QUEST_BINARY_LOGIC, "Lógica Binaria", "Binary Logic",
            new String[]{"Craftea un comparador."},
            new String[]{"Craft a comparator."},
            new String[]{"8x Comparadores"},
            new String[]{"8x Comparators"}));
        tecnico.add(q(QuestData.QUEST_BRUTE_FORCE, "Fuerza Bruta", "Brute Force",
            new String[]{"Coloca 10 pistones."},
            new String[]{"Place 10 pistons."},
            new String[]{"32x Antorchas de Redstone"},
            new String[]{"32x Redstone Torches"}));
        tecnico.add(q(QuestData.QUEST_BACK_FORTH, "Ida y Vuelta", "Back and Forth",
            new String[]{"Coloca 10 pistones pegajosos."},
            new String[]{"Place 10 sticky pistons."},
            new String[]{"16x Bolas de Slime"},
            new String[]{"16x Slime Balls"}));
        tecnico.add(q(QuestData.QUEST_CONTROLLED_DELAY, "Retardo Controlado", "Controlled Delay",
            new String[]{"Coloca 20 repetidores."},
            new String[]{"Place 20 repeaters."},
            new String[]{"16x Repetidores"},
            new String[]{"16x Repeaters"}));
        tecnico.add(q(QuestData.QUEST_REDSTONE_BRAIN, "Cerebro de Redstone", "Redstone Brain",
            new String[]{"Coloca 20 comparadores."},
            new String[]{"Place 20 comparators."},
            new String[]{"16x Comparadores"},
            new String[]{"16x Comparators"}));
        tecnico.add(q(QuestData.QUEST_AUTO_LOGISTICS, "Logística Automatizada", "Automated Logistics",
            new String[]{"Coloca 20 tolvas."},
            new String[]{"Place 20 hoppers."},
            new String[]{"8x Tolvas"},
            new String[]{"8x Hoppers"}));
        tecnico.add(q(QuestData.QUEST_FIRE_AT_WILL, "Fuego a Discreción", "Fire at Will",
            new String[]{"Coloca 10 dispensadores."},
            new String[]{"Place 10 dispensers."},
            new String[]{"64x Flechas"},
            new String[]{"64x Arrows"}));
        tecnico.add(q(QuestData.QUEST_DROP_GOODS, "¡Suelten la Mercancía!", "Drop the Goods!",
            new String[]{"Coloca 10 soltadores."},
            new String[]{"Place 10 droppers."},
            new String[]{"4x Cofres"},
            new String[]{"4x Chests"}));
        tecnico.add(q(QuestData.QUEST_EYES_WALLS, "Ojos en las Paredes", "Eyes on the Walls",
            new String[]{"Coloca 10 observadores."},
            new String[]{"Place 10 observers."},
            new String[]{"4x Observadores"},
            new String[]{"4x Observers"}));
        tecnico.add(q(QuestData.QUEST_MANUAL_CTRL, "Control Manual", "Manual Control",
            new String[]{"Coloca 20 palancas."},
            new String[]{"Place 20 levers."},
            new String[]{"16x Palancas"},
            new String[]{"16x Levers"}));
        tecnico.add(q(QuestData.QUEST_DONT_PRESS, "¡No Toques Ese Botón!", "Don't Touch That Button!",
            new String[]{"Coloca 20 botones."},
            new String[]{"Place 20 buttons."},
            new String[]{"16x Botones de Roble"},
            new String[]{"16x Oak Buttons"}));
        tecnico.add(q(QuestData.QUEST_UNDER_FEET, "Bajo tus Pies", "Under Your Feet",
            new String[]{"Coloca 20 placas de presión."},
            new String[]{"Place 20 pressure plates."},
            new String[]{"16x Placas de Presión de Roble"},
            new String[]{"16x Oak Pressure Plates"}));
        tecnico.add(q(QuestData.QUEST_INDUSTRIAL_SAFETY, "Seguridad Industrial", "Industrial Safety",
            new String[]{"Coloca 20 trampillas de hierro."},
            new String[]{"Place 20 iron trapdoors."},
            new String[]{"8x Trampillas de Hierro"},
            new String[]{"8x Iron Trapdoors"}));
        tecnico.add(q(QuestData.QUEST_PERPETUAL_MOTION, "Movimiento Perpetuo", "Perpetual Motion",
            new String[]{"Activa un pistón 100 veces."},
            new String[]{"Activate a piston 100 times."},
            new String[]{"8x Bloques de Redstone"},
            new String[]{"8x Redstone Blocks"}));
        tecnico.add(q(QuestData.QUEST_SMART_DIST, "Distribución Inteligente", "Smart Distribution",
            new String[]{"Transporta objetos con tolvas."},
            new String[]{"Move items using hoppers."},
            new String[]{"16x Tolvas"},
            new String[]{"16x Hoppers"}));
        tecnico.add(q(QuestData.QUEST_TIC_TAC, "Tic Tac Infinito", "Infinite Tic-Tock",
            new String[]{"Construye un reloj de redstone."},
            new String[]{"Build a redstone clock."},
            new String[]{"1x Reloj"},
            new String[]{"1x Clock"}));
        tecnico.add(q(QuestData.QUEST_END_HARD_WORK, "El Fin del Trabajo Duro", "The End of Hard Work",
            new String[]{"Construye una granja automática."},
            new String[]{"Build an automatic farm."},
            new String[]{"8x Diamantes", "1x Libro Encantado"},
            new String[]{"8x Diamonds", "1x Enchanted Book"}));
        tabs.add(new Tab(english ? "Engineer" : "Técnico", tecnico));

        List<QuestEntry> logros = new ArrayList<>();
        logros.add(qi("ach_story_root", "Minecraft", "Minecraft", "Ten una mesa de trabajo en el inventario.", "Have a crafting table in the inventory."));
        logros.add(qi("ach_story_mine_stone", "Edad de Piedra", "Stone Age", "Mina piedra con tu nuevo pico.", "Mine stone with your new pickaxe."));
        logros.add(qi("ach_story_upgrade_tools", "Consiguiendo una Mejora", "Getting an Upgrade", "Construye un pico mejor.", "Construct a better pickaxe."));
        logros.add(qi("ach_story_smelt_iron", "Adquiere Ferretería", "Acquire Hardware", "Funde un lingote de hierro.", "Smelt an iron ingot."));
        logros.add(qi("ach_story_obtain_armor", "Vístete", "Suit Up", "Protégete con una pieza de armadura de hierro.", "Protect yourself with a piece of iron armor."));
        logros.add(qi("ach_story_lava_bucket", "Cosa Caliente", "Hot Stuff", "Llena un balde con lava.", "Fill a bucket with lava."));
        logros.add(qi("ach_story_iron_tools", "¿No es un Pico de Hierro?", "Isn't It Iron Pick", "Mejora tu pico.", "Upgrade your pickaxe."));
        logros.add(qi("ach_story_deflect_arrow", "Hoy No, Gracias", "Not Today, Thank You", "Desvía un proyectil con un escudo.", "Deflect a projectile with a shield."));
        logros.add(qi("ach_story_form_obsidian", "El Reto del Balde de Hielo", "Ice Bucket Challenge", "Consigue un bloque de obsidiana.", "Obtain a block of obsidian."));
        logros.add(qi("ach_story_mine_diamond", "¡Diamantes!", "Diamonds!", "Consigue diamantes.", "Acquire diamonds."));
        logros.add(qi("ach_story_enter_the_nether", "Necesitamos Ir Más Profundo", "We Need to Go Deeper", "Construye, enciende y entra a un Portal del Nether.", "Build, light and enter a Nether Portal."));
        logros.add(qi("ach_story_shiny_gear", "Cúbreme de Diamantes", "Cover Me with Diamonds", "La armadura de diamante salva vidas.", "Diamond armor saves lives."));
        logros.add(qi("ach_story_enchant_item", "Encantador", "Enchanter", "Encanta un objeto en una Mesa de Encantamientos.", "Enchant an item at an Enchanting Table."));
        logros.add(qi("ach_story_cure_zombie_villager", "Doctor Zombie", "Zombie Doctor", "Debilita y luego cura a un Aldeano Zombi.", "Weaken and then cure a Zombie Villager."));
        logros.add(qi("ach_story_follow_ender_eye", "Ojo Espía", "Eye Spy", "Sigue un Ojo de Ender hasta un Stronghold.", "Follow an Eye of Ender into a Stronghold."));
        logros.add(qi("ach_story_enter_the_end", "¿El Fin?", "The End?", "Entra al Portal del Fin.", "Enter the End Portal."));
        logros.add(qi("ach_nether_root", "Nether", "Nether", "Entra al Nether.", "Enter the Nether."));
        logros.add(qi("ach_nether_return_to_sender", "Devolver al Remitente", "Return to Sender", "Destruye un Ghast con una bola de fuego desviada.", "Destroy a Ghast with a deflected fireball."));
        logros.add(qi("ach_nether_find_bastion", "Aquellos Fueron Buenos Tiempos", "Those Were the Days", "Entra a un Bastión en Ruinas.", "Enter a Bastion Remnant."));
        logros.add(qi("ach_nether_obtain_ancient_debris", "Escondido en las Profundidades", "Hidden in the Depths", "Consigue Ancient Debris.", "Obtain Ancient Debris."));
        logros.add(qi("ach_nether_fast_travel", "Burbuja del Subespacio", "Subspace Bubble", "Usa el Nether para viajar 7 km en la Superficie.", "Use the Nether to travel 7 km in the Overworld."));
        logros.add(qi("ach_nether_find_fortress", "Una Fortaleza Terrible", "A Terrible Fortress", "Ábrete paso hasta una Fortaleza del Nether.", "Break your way into a Nether Fortress."));
        logros.add(qi("ach_nether_obtain_crying_obsidian", "¿Quién Está Cortando Cebollas?", "Who is Cutting Onions?", "Consigue Obsidiana Llorona.", "Obtain Crying Obsidian."));
        logros.add(qi("ach_nether_distract_piglin", "Oh, Brillante", "Oh Shiny", "Distrae a los Piglins con oro.", "Distract Piglins with gold."));
        logros.add(qi("ach_nether_ride_strider", "Este Bote Tiene Patas", "This Boat Has Legs", "Monta un Strider con un Hongo Distorsionado en un Palo.", "Ride a Strider with a Warped Fungus on a Stick."));
        logros.add(qi("ach_nether_uneasy_alliance", "Alianza Incómoda", "Uneasy Alliance", "Rescata a un Ghast del Nether, llévalo sano y salvo a la Superficie... y luego mátalo.", "Rescue a Ghast from the Nether, bring it safely home to the Overworld... and then kill it."));
        logros.add(qi("ach_nether_loot_bastion", "Cerdos de Guerra", "War Pigs", "Saquea un cofre en un Bastión en Ruinas.", "Loot a chest in a Bastion Remnant."));
        logros.add(qi("ach_nether_netherite_armor", "Cúbreme de Escombros", "Cover Me in Debris", "Consigue un set completo de armadura de Netherite.", "Get a full suit of Netherite armor."));
        logros.add(qi("ach_nether_get_wither_skull", "Esqueleto Espeluznante", "Spooky Scary Skeleton", "Consigue una calavera de Esqueleto Wither.", "Obtain a Wither Skeleton's skull."));
        logros.add(qi("ach_nether_obtain_blaze_rod", "Hacia el Fuego", "Into Fire", "Quítale su vara a un Blaze.", "Relieve a Blaze of its rod."));
        logros.add(qi("ach_nether_charge_respawn_anchor", "No Tan \"Nueve\" Vidas", "Not Quite \"Nine\" Lives", "Carga un Ancla de Reaparición al máximo.", "Charge a Respawn Anchor to the maximum."));
        logros.add(qi("ach_nether_ride_strider_in_overworld_lava", "Se Siente Como Casa", "Feels Like Home", "Da un paseo largo en Strider sobre un lago de lava en la Superficie.", "Take a Strider for a loooong ride on a lava lake in the Overworld."));
        logros.add(qi("ach_nether_explore_nether", "Destinos Turísticos Calientes", "Hot Tourist Destinations", "Explora todos los biomas del Nether.", "Explore all Nether biomes."));
        logros.add(qi("ach_nether_summon_wither", "Cumbres del Wither", "Withering Heights", "Invoca al Wither.", "Summon the Wither."));
        logros.add(qi("ach_nether_brew_potion", "Cervecería Local", "Local Brewery", "Prepara una poción.", "Brew a potion."));
        logros.add(qi("ach_nether_create_beacon", "Trae el Faro a Casa", "Bring Home the Beacon", "Construye y coloca un Faro.", "Construct and place a Beacon."));
        logros.add(qi("ach_nether_all_potions", "Un Cóctel Furioso", "A Furious Cocktail", "Ten todos los efectos de poción aplicados al mismo tiempo.", "Have every potion effect applied at the same time."));
        logros.add(qi("ach_nether_create_full_beacon", "El Faronator", "Beaconator", "Lleva un Faro a su máximo poder.", "Bring a Beacon to full power."));
        logros.add(qi("ach_nether_all_effects", "¿Cómo Llegamos Aquí? (oculto)", "How Did We Get Here? (oculto)", "Ten todos los efectos aplicados al mismo tiempo.", "Have every effect applied at the same time."));
        logros.add(qi("ach_end_root", "El Fin", "The End", "Entra al Fin.", "Enter the End."));
        logros.add(qi("ach_end_kill_dragon", "Libera el Fin", "Free the End", "Mata al Dragón del Fin.", "Kill the Ender Dragon."));
        logros.add(qi("ach_end_dragon_egg", "La Próxima Generación", "The Next Generation", "Sostén el Huevo de Dragón.", "Hold the Dragon Egg."));
        logros.add(qi("ach_end_enter_end_gateway", "Escapada Remota", "Remote Getaway", "Entra a un Portal del Fin (Gateway).", "Enter an End Gateway."));
        logros.add(qi("ach_end_respawn_dragon", "El Fin... Otra Vez...", "The End... Again...", "Vuelve a invocar al Dragón del Fin.", "Respawn the Ender Dragon."));
        logros.add(qi("ach_end_dragon_breath", "Necesitas una Menta", "You Need a Mint", "Recoge Aliento de Dragón en una botella de vidrio.", "Collect Dragon's Breath in a glass bottle."));
        logros.add(qi("ach_end_find_end_city", "La Ciudad al Final del Juego", "The City at the End of the Game", "Entra a una Ciudad del Fin.", "Enter an End City."));
        logros.add(qi("ach_end_elytra", "El Cielo es el Límite", "Sky's the Limit", "Encuentra unos Élitros.", "Find an Elytra."));
        logros.add(qi("ach_end_levitate", "Gran Vista Desde Aquí Arriba", "Great View From Up Here", "Levita 50 bloques por el ataque de un Shulker.", "Levitate up 50 blocks from a Shulker's attack."));
        logros.add(qi("ach_adventure_root", "Aventura", "Adventure", "Aventura, exploración y combate.", "Adventure, exploration and combat."));
        logros.add(qi("ach_adventure_voluntary_exile", "Exilio Voluntario (oculto)", "Voluntary Exile (oculto)", "Mata a un capitán de asalto.", "Kill a raid captain."));
        logros.add(qi("ach_adventure_kill_a_mob", "Cazador de Monstruos", "Monster Hunter", "Mata a cualquier monstruo hostil.", "Kill any hostile monster."));
        logros.add(qi("ach_adventure_trade", "¡Qué Buen Trato!", "What a Deal!", "Comercia exitosamente con un Aldeano.", "Successfully trade with a Villager."));
        logros.add(qi("ach_adventure_honey_block_slide", "Situación Pegajosa", "Sticky Situation", "Salta a un Bloque de Miel para amortiguar tu caída.", "Jump into a Honey Block to break your fall."));
        logros.add(qi("ach_adventure_ol_betsy", "La Vieja Betsy", "Ol' Betsy", "Dispara una Ballesta.", "Shoot a Crossbow."));
        logros.add(qi("ach_adventure_sleep_in_bed", "Dulces Sueños", "Sweet Dreams", "Duerme en una Cama para cambiar tu punto de reaparición.", "Sleep in a Bed to change your respawn point."));
        logros.add(qi("ach_adventure_hero_of_the_village", "Héroe de la Aldea (oculto)", "Hero of the Village (oculto)", "Defiende con éxito una aldea de un asalto.", "Successfully defend a village from a raid."));
        logros.add(qi("ach_adventure_throw_trident", "Un Chiste Desechable", "A Throwaway Joke", "Lanza un Tridente a algo.", "Throw a Trident at something."));
        logros.add(qi("ach_adventure_shoot_arrow", "Apunta Bien", "Take Aim", "Dispárale a algo con una Flecha.", "Shoot something with an Arrow."));
        logros.add(qi("ach_adventure_kill_all_mobs", "Monstruos Cazados", "Monsters Hunted", "Mata a cada tipo de monstruo hostil.", "Kill one of every hostile monster."));
        logros.add(qi("ach_adventure_totem_of_undying", "Postmortal", "Postmortal", "Usa un Tótem de la Inmortalidad para engañar a la muerte.", "Use a Totem of Undying to cheat death."));
        logros.add(qi("ach_adventure_summon_iron_golem", "Ayuda Contratada", "Hired Help", "Invoca un Gólem de Hierro para ayudar a defender una aldea.", "Summon an Iron Golem to help defend a village."));
        logros.add(qi("ach_adventure_two_birds_one_arrow", "Dos Pájaros de un Tiro", "Two Birds, One Arrow", "Mata a dos Phantoms con una flecha perforante.", "Kill two Phantoms with a piercing Arrow."));
        logros.add(qi("ach_adventure_whos_the_pillager_now", "¿Quién es el Saqueador Ahora?", "Who's the Pillager Now?", "Dale a un Pillager una cucharada de su propia medicina.", "Give a Pillager a taste of their own medicine."));
        logros.add(qi("ach_adventure_arbalistic", "Ballestero (oculto)", "Arbalistic (oculto)", "Mata a cinco criaturas distintas con un solo disparo de ballesta.", "Kill five unique mobs with one crossbow shot."));
        logros.add(qi("ach_adventure_adventuring_time", "Hora de Aventura", "Adventuring Time", "Descubre todos los biomas.", "Discover every biome."));
        logros.add(qi("ach_adventure_very_very_frightening", "Muy Muy Aterrador", "Very Very Frightening", "Golpea a un Aldeano con un rayo.", "Strike a Villager with lightning."));
        logros.add(qi("ach_adventure_sniper_duel", "Duelo de Francotiradores", "Sniper Duel", "Mata a un Esqueleto desde al menos 50 metros de distancia.", "Kill a Skeleton from at least 50 meters away."));
        logros.add(qi("ach_adventure_bullseye", "En el Blanco", "Bullseye", "Dale al centro de un bloque Diana desde al menos 30 metros.", "Hit the bullseye of a Target block from at least 30 meters away."));
        logros.add(qi("ach_husbandry_root", "Cría de Animales", "Husbandry", "Consume cualquier cosa que se pueda consumir, excepto pastel.", "Consume anything that can be consumed, except cake."));
        logros.add(qi("ach_husbandry_safely_harvest_honey", "Sé Nuestro Invitado", "Bee Our Guest", "Usa una Fogata para recolectar Miel de una Colmena con una Botella de Vidrio sin enojar a las Abejas.", "Use a Campfire to collect Honey from a Beehive with a Glass Bottle without angering the Bees."));
        logros.add(qi("ach_husbandry_breed_an_animal", "Los Loros y los Murciélagos", "The Parrots and the Bats", "Cría dos animales juntos.", "Breed two animals together."));
        logros.add(qi("ach_husbandry_allay_deliver_item_to_player", "Tienes un Amigo en Mí (oculto)", "You've Got a Friend in Me (oculto)", "Haz que un Allay te entregue objetos.", "Have an Allay deliver items to you."));
        logros.add(qi("ach_husbandry_ride_a_boat_with_a_goat", "¡Lo que Flote tu Cabra!", "Whatever Floats Your Goat!", "Súbete a un Bote y flota junto a una Cabra.", "Get in a Boat and float with a Goat."));
        logros.add(qi("ach_husbandry_tame_an_animal", "Mejores Amigos Para Siempre", "Best Friends Forever", "Doma un animal.", "Tame an animal."));
        logros.add(qi("ach_husbandry_make_a_sign_glow", "¡Que Brille!", "Glow and Behold!", "Haz que el texto de cualquier cartel brille.", "Make the text of any kind of sign glow."));
        logros.add(qi("ach_husbandry_fishy_business", "Asunto Turbio", "Fishy Business", "Pesca un pez.", "Catch a fish."));
        logros.add(qi("ach_husbandry_silk_touch_nest", "Mudanza Total de Abejas", "Total Beelocation", "Mueve un Nido de Abejas, con 3 abejas adentro, usando Toque de Seda.", "Move a Bee Nest, with 3 Bees inside, using Silk Touch."));
        logros.add(qi("ach_husbandry_tadpole_in_a_bucket", "Bukkit Bukkit", "Bukkit Bukkit", "Atrapa un Renacuajo en un Balde.", "Catch a Tadpole in a Bucket."));
        logros.add(qi("ach_husbandry_obtain_sniffer_egg", "Huele Interesante (oculto)", "Smells Interesting (oculto)", "Consigue un Huevo de Sniffer.", "Obtain a Sniffer Egg."));
        logros.add(qi("ach_husbandry_plant_seed", "Un Lugar de Semillas", "A Seedy Place", "Planta una semilla y obsérvala crecer.", "Plant a seed and watch it grow."));
        logros.add(qi("ach_husbandry_wax_on", "Encerar", "Wax On", "Aplica un Panal a un bloque de Cobre.", "Apply Honeycomb to a Copper block."));
        logros.add(qi("ach_husbandry_bred_all_animals", "De Dos en Dos", "Two by Two", "¡Cría a todos los animales!", "Breed all the animals!"));
        logros.add(qi("ach_husbandry_allay_deliver_cake_to_note_block", "Canción de Cumpleaños (oculto)", "Birthday Song (oculto)", "Haz que un Allay deje un Pastel en un Bloque de Notas.", "Have an Allay drop a Cake at a Note Block."));
        logros.add(qi("ach_husbandry_complete_catalogue", "Un Catálogo Completo", "A Complete Catalogue", "Doma todas las variantes de Gato.", "Tame all Cat variants."));
        logros.add(qi("ach_husbandry_tactical_fishing", "Pesca Táctica", "Tactical Fishing", "Atrapa un pez... ¡sin caña de pescar!", "Catch a fish... without a Fishing Rod!"));
        logros.add(qi("ach_husbandry_leash_all_frog_variants", "Cuando el Escuadrón Llega a la Ciudad", "When the Squad Hops into Town", "Pon una correa a cada variante de Rana.", "Get each Frog variant on a Lead."));
        logros.add(qi("ach_husbandry_feed_snifflet", "Pequeños Snifflets (oculto)", "Little Sniffs (oculto)", "Alimenta a un Snifflet.", "Feed a Snifflet."));
        logros.add(qi("ach_husbandry_balanced_diet", "Una Dieta Balanceada", "A Balanced Diet", "Come todo lo que sea comestible, aunque no te haga bien.", "Eat everything that is edible, even if it's not good for you."));
        logros.add(qi("ach_husbandry_obtain_netherite_hoe", "Dedicación Seria", "Serious Dedication", "Usa un Lingote de Netherite para mejorar una Azada.", "Use a Netherite Ingot to upgrade a Hoe."));
        logros.add(qi("ach_husbandry_wax_off", "Desencerar", "Wax Off", "Raspa la cera de un bloque de Cobre.", "Scrape Wax off of a Copper block."));
        logros.add(qi("ach_husbandry_axolotl_in_a_bucket", "El Depredador Más Tierno", "The Cutest Predator", "Atrapa un Ajolote en un Balde.", "Catch an Axolotl in a Bucket."));
        logros.add(qi("ach_husbandry_froglights", "¡Nuestros Poderes Combinados!", "With Our Powers Combined!", "Ten todos los tipos de Luz de Rana en tu inventario.", "Have all Froglights in your inventory."));
        logros.add(qi("ach_husbandry_plant_any_sniffer_seed", "Plantando el Pasado (oculto)", "Planting the Past (oculto)", "Planta cualquier semilla de Sniffer.", "Plant any Sniffer seed."));
        logros.add(qi("ach_husbandry_kill_axolotl_target", "¡El Poder Curativo de la Amistad!", "The Healing Power of Friendship!", "Haz equipo con un Ajolote y gana una pelea.", "Team up with an Axolotl and win a fight."));
        logros.add(qi("ach_husbandry_repair_wolf_armor", "Como Nueva", "Good as New", "Repara una Armadura de Lobo dañada usando Escamas de Armadillo.", "Repair a damaged Wolf Armor using Armadillo Scutes."));
        logros.add(qi("ach_husbandry_whole_pack", "La Manada Completa", "The Whole Pack", "Doma una de cada variante de Lobo.", "Tame one of each Wolf variant."));
        logros.add(qi("ach_husbandry_remove_wolf_armor", "Pura Brillantez", "Shear Brilliance", "Quítale la Armadura a un Lobo usando Tijeras.", "Remove Wolf Armor from a Wolf using Shears."));
        tabs.add(new Tab(english ? "Achievements" : "Logros", logros));

        // "Mago": pociones + encantamientos. Vacía por ahora — el diseño completo de
        // misiones llega después; mientras tanto la pestaña ya está viva (ícono, click,
        // tooltip) y muestra el mismo "Próximamente..." que cualquier lista vacía.
        List<QuestEntry> mago = new ArrayList<>();
        mago.add(q(QuestData.QUEST_MAGO_BREWING_STAND, "Alambique", "Brewing Stand",
            new String[]{"Consigue una Mesa de Pociones."},
            new String[]{"Get a Brewing Stand."},
            new String[]{"8x Botella de Vidrio"},
            new String[]{"8x Glass Bottle"}));
        mago.add(q(QuestData.QUEST_KNOWLEDGE, "El Poder del Conocimiento", "The Power of Knowledge",
            new String[]{"Fabrica una mesa de encantamientos."},
            new String[]{"Craft an enchanting table."},
            new String[]{"16x Libros"},
            new String[]{"16x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_AWKWARD, "Poción Incierta", "Awkward Potion",
            new String[]{"Prepara una Poción Incierta."},
            new String[]{"Brew an Awkward Potion."},
            new String[]{"4x Verruga del Nether"},
            new String[]{"4x Nether Wart"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_SWIFTNESS, "Poción de Velocidad", "Potion of Swiftness",
            new String[]{"Prepara una Poción de Velocidad."},
            new String[]{"Brew a Potion of Swiftness."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_POISON, "Poción de Veneno", "Potion of Poison",
            new String[]{"Prepara una Poción de Veneno."},
            new String[]{"Brew a Potion of Poison."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_WEAKNESS, "Poción de Debilidad", "Potion of Weakness",
            new String[]{"Prepara una Poción de Debilidad."},
            new String[]{"Brew a Potion of Weakness."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_SLOWNESS, "Poción de Lentitud", "Potion of Slowness",
            new String[]{"Prepara una Poción de Lentitud."},
            new String[]{"Brew a Potion of Slowness."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_FIRE_RESISTANCE, "Poción de Resistencia al Fuego", "Potion of Fire Resistance",
            new String[]{"Prepara una Poción de", "Resistencia al Fuego."},
            new String[]{"Brew a Potion of", "Fire Resistance."},
            new String[]{"2x Poción", "2x Magma Cream"},
            new String[]{"2x Potion", "2x Magma Cream"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_HEALING, "Poción de Curación", "Potion of Healing",
            new String[]{"Prepara una Poción de Curación."},
            new String[]{"Brew a Potion of Healing."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_HARMING, "Poción de Daño", "Potion of Harming",
            new String[]{"Prepara una Poción de Daño."},
            new String[]{"Brew a Potion of Harming."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_LEAPING, "Poción de Salto", "Potion of Leaping",
            new String[]{"Prepara una Poción de Salto."},
            new String[]{"Brew a Potion of Leaping."},
            new String[]{"2x Poción"},
            new String[]{"2x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_STRENGTH, "Poción de Fuerza", "Potion of Strength",
            new String[]{"Prepara una Poción de Fuerza."},
            new String[]{"Brew a Potion of Strength."},
            new String[]{"3x Poción", "4x Blaze Powder"},
            new String[]{"3x Potion", "4x Blaze Powder"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_REGENERATION, "Poción de Regeneración", "Potion of Regeneration",
            new String[]{"Prepara una Poción de Regeneración."},
            new String[]{"Brew a Potion of Regeneration."},
            new String[]{"3x Poción", "2x Lágrima de Ghast"},
            new String[]{"3x Potion", "2x Ghast Tear"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_NIGHT_VISION, "Poción de Visión Nocturna", "Potion of Night Vision",
            new String[]{"Prepara una Poción de Visión Nocturna."},
            new String[]{"Brew a Potion of Night Vision."},
            new String[]{"3x Poción"},
            new String[]{"3x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_WATER_BREATHING, "Poción de Respiración Acuática", "Potion of Water Breathing",
            new String[]{"Prepara una Poción de", "Respiración Acuática."},
            new String[]{"Brew a Potion of", "Water Breathing."},
            new String[]{"3x Poción"},
            new String[]{"3x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_INVISIBILITY, "Poción de Invisibilidad", "Potion of Invisibility",
            new String[]{"Prepara una Poción de Invisibilidad."},
            new String[]{"Brew a Potion of Invisibility."},
            new String[]{"1x Libro Encantado", "3x Poción"},
            new String[]{"1x Enchanted Book", "3x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_SLOW_FALLING, "Poción de Caída Lenta", "Potion of Slow Falling",
            new String[]{"Prepara una Poción de Caída Lenta."},
            new String[]{"Brew a Potion of Slow Falling."},
            new String[]{"1x Libro Encantado", "3x Poción"},
            new String[]{"1x Enchanted Book", "3x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_POTION_TURTLE_MASTER, "Poción del Maestro Tortuga", "Potion of the Turtle Master",
            new String[]{"Prepara una Poción del Maestro Tortuga."},
            new String[]{"Brew a Potion of the Turtle Master."},
            new String[]{"1x Libro Encantado", "4x Lapislázuli", "3x Poción"},
            new String[]{"1x Enchanted Book", "4x Lapis Lazuli", "3x Potion"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_SHARPNESS, "Encantamiento: Filo", "Enchantment: Sharpness",
            new String[]{"Encanta un objeto con Filo."},
            new String[]{"Enchant an item with Sharpness."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_SMITE, "Encantamiento: Aspecto Rabioso", "Enchantment: Smite",
            new String[]{"Encanta un objeto con Aspecto Rabioso."},
            new String[]{"Enchant an item with Smite."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_BANE_ARTHROPODS, "Encantamiento: Perjuicio", "Enchantment: Bane of Arthropods",
            new String[]{"Encanta un objeto con Perjuicio."},
            new String[]{"Enchant an item with Bane of Arthropods."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_FIRE_ASPECT, "Encantamiento: Aspecto de Fuego", "Enchantment: Fire Aspect",
            new String[]{"Encanta un objeto con Aspecto de Fuego."},
            new String[]{"Enchant an item with Fire Aspect."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_KNOCKBACK, "Encantamiento: Retroceso", "Enchantment: Knockback",
            new String[]{"Encanta un objeto con Retroceso."},
            new String[]{"Enchant an item with Knockback."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_LOOTING, "Encantamiento: Botín", "Enchantment: Looting",
            new String[]{"Encanta un objeto con Botín."},
            new String[]{"Enchant an item with Looting."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_SWEEPING_EDGE, "Encantamiento: Filo Arrasador", "Enchantment: Sweeping Edge",
            new String[]{"Encanta un objeto con Filo Arrasador."},
            new String[]{"Enchant an item with Sweeping Edge."},
            new String[]{"1x Libro Encantado", "4x Lapislázuli"},
            new String[]{"1x Enchanted Book", "4x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_BREACH, "Encantamiento: Brecha", "Enchantment: Breach",
            new String[]{"Encanta un objeto con Brecha."},
            new String[]{"Enchant an item with Breach."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_DENSITY, "Encantamiento: Densidad", "Enchantment: Density",
            new String[]{"Encanta un objeto con Densidad."},
            new String[]{"Enchant an item with Density."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_WIND_BURST, "Encantamiento: Ráfaga de Viento", "Enchantment: Wind Burst",
            new String[]{"Encanta un objeto con Ráfaga de Viento."},
            new String[]{"Enchant an item with Wind Burst."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_LUNGE, "Encantamiento: Embestida", "Enchantment: Lunge",
            new String[]{"Encanta un objeto con Embestida."},
            new String[]{"Enchant an item with Lunge."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_POWER, "Encantamiento: Poder", "Enchantment: Power",
            new String[]{"Encanta un objeto con Poder."},
            new String[]{"Enchant an item with Power."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_PUNCH, "Encantamiento: Empuje", "Enchantment: Punch",
            new String[]{"Encanta un objeto con Empuje."},
            new String[]{"Enchant an item with Punch."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_FLAME, "Encantamiento: Llama", "Enchantment: Flame",
            new String[]{"Encanta un objeto con Llama."},
            new String[]{"Enchant an item with Flame."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_INFINITY, "Encantamiento: Infinidad", "Enchantment: Infinity",
            new String[]{"Encanta un objeto con Infinidad."},
            new String[]{"Enchant an item with Infinity."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_MULTISHOT, "Encantamiento: Multidisparo", "Enchantment: Multishot",
            new String[]{"Encanta un objeto con Multidisparo."},
            new String[]{"Enchant an item with Multishot."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_PIERCING, "Encantamiento: Perforación", "Enchantment: Piercing",
            new String[]{"Encanta un objeto con Perforación."},
            new String[]{"Enchant an item with Piercing."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_QUICK_CHARGE, "Encantamiento: Carga Rápida", "Enchantment: Quick Charge",
            new String[]{"Encanta un objeto con Carga Rápida."},
            new String[]{"Enchant an item with Quick Charge."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_CHANNELING, "Encantamiento: Canalización", "Enchantment: Channeling",
            new String[]{"Encanta un objeto con Canalización."},
            new String[]{"Enchant an item with Channeling."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_IMPALING, "Encantamiento: Impaler", "Enchantment: Impaling",
            new String[]{"Encanta un objeto con Impaler."},
            new String[]{"Enchant an item with Impaling."},
            new String[]{"1x Libro Encantado", "4x Lapislázuli"},
            new String[]{"1x Enchanted Book", "4x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_LOYALTY, "Encantamiento: Lealtad", "Enchantment: Loyalty",
            new String[]{"Encanta un objeto con Lealtad."},
            new String[]{"Enchant an item with Loyalty."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_RIPTIDE, "Encantamiento: Marejada", "Enchantment: Riptide",
            new String[]{"Encanta un objeto con Marejada."},
            new String[]{"Enchant an item with Riptide."},
            new String[]{"1x Libro Encantado", "4x Lapislázuli"},
            new String[]{"1x Enchanted Book", "4x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_EFFICIENCY, "Encantamiento: Eficiencia", "Enchantment: Efficiency",
            new String[]{"Encanta un objeto con Eficiencia."},
            new String[]{"Enchant an item with Efficiency."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_FORTUNE, "Encantamiento: Fortuna", "Enchantment: Fortune",
            new String[]{"Encanta un objeto con Fortuna."},
            new String[]{"Enchant an item with Fortune."},
            new String[]{"1x Libro Encantado", "4x Lapislázuli"},
            new String[]{"1x Enchanted Book", "4x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_SILK_TOUCH, "Encantamiento: Toque de Seda", "Enchantment: Silk Touch",
            new String[]{"Encanta un objeto con Toque de Seda."},
            new String[]{"Enchant an item with Silk Touch."},
            new String[]{"1x Libro Encantado", "4x Lapislázuli"},
            new String[]{"1x Enchanted Book", "4x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_UNBREAKING, "Encantamiento: Reparación", "Enchantment: Unbreaking",
            new String[]{"Encanta un objeto con Reparación."},
            new String[]{"Enchant an item with Unbreaking."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_MENDING, "Encantamiento: Mantenimiento", "Enchantment: Mending",
            new String[]{"Encanta un objeto con Mantenimiento."},
            new String[]{"Enchant an item with Mending."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_LUCK_OF_THE_SEA, "Encantamiento: Suerte Marina", "Enchantment: Luck of the Sea",
            new String[]{"Encanta un objeto con Suerte Marina."},
            new String[]{"Enchant an item with Luck of the Sea."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_LURE, "Encantamiento: Señuelo", "Enchantment: Lure",
            new String[]{"Encanta un objeto con Señuelo."},
            new String[]{"Enchant an item with Lure."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_PROTECTION, "Encantamiento: Protección", "Enchantment: Protection",
            new String[]{"Encanta un objeto con Protección."},
            new String[]{"Enchant an item with Protection."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_FIRE_PROTECTION, "Encantamiento: Protección contra el Fuego", "Enchantment: Fire Protection",
            new String[]{"Encanta un objeto con Protección contra el Fuego."},
            new String[]{"Enchant an item with Fire Protection."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_BLAST_PROTECTION, "Encantamiento: Protección contra Explosiones", "Enchantment: Blast Protection",
            new String[]{"Encanta un objeto con Protección contra Explosiones."},
            new String[]{"Enchant an item with Blast Protection."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_PROJECTILE_PROTECTION, "Encantamiento: Protección contra Proyectiles", "Enchantment: Projectile Protection",
            new String[]{"Encanta un objeto con Protección contra Proyectiles."},
            new String[]{"Enchant an item with Projectile Protection."},
            new String[]{"2x Estanterías"},
            new String[]{"2x Bookshelves"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_FEATHER_FALLING, "Encantamiento: Plumas", "Enchantment: Feather Falling",
            new String[]{"Encanta un objeto con Plumas."},
            new String[]{"Enchant an item with Feather Falling."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_DEPTH_STRIDER, "Encantamiento: Paso Ágil", "Enchantment: Depth Strider",
            new String[]{"Encanta un objeto con Paso Ágil."},
            new String[]{"Enchant an item with Depth Strider."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_FROST_WALKER, "Encantamiento: Paso Helado", "Enchantment: Frost Walker",
            new String[]{"Encanta un objeto con Paso Helado."},
            new String[]{"Enchant an item with Frost Walker."},
            new String[]{"1x Libro Encantado", "8x Lapislázuli"},
            new String[]{"1x Enchanted Book", "8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_SOUL_SPEED, "Encantamiento: Velocidad de Alma", "Enchantment: Soul Speed",
            new String[]{"Encanta un objeto con Velocidad de Alma."},
            new String[]{"Enchant an item with Soul Speed."},
            new String[]{"1x Botas de Diamante Encantadas", "(Velocidad de Alma III)"},
            new String[]{"1x Enchanted Diamond Boots", "(Soul Speed III)"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_SWIFT_SNEAK, "Encantamiento: Sigilo Veloz", "Enchantment: Swift Sneak",
            new String[]{"Encanta un objeto con Sigilo Veloz."},
            new String[]{"Enchant an item with Swift Sneak."},
            new String[]{"1x Grebas de Diamante Encantadas", "(Sigilo Veloz III)"},
            new String[]{"1x Enchanted Diamond Leggings", "(Swift Sneak III)"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_AQUA_AFFINITY, "Encantamiento: Afinidad Acuática", "Enchantment: Aqua Affinity",
            new String[]{"Encanta un objeto con Afinidad Acuática."},
            new String[]{"Enchant an item with Aqua Affinity."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_RESPIRATION, "Encantamiento: Respiración", "Enchantment: Respiration",
            new String[]{"Encanta un objeto con Respiración."},
            new String[]{"Enchant an item with Respiration."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_ENCH_THORNS, "Encantamiento: Espinas", "Enchantment: Thorns",
            new String[]{"Encanta un objeto con Espinas."},
            new String[]{"Enchant an item with Thorns."},
            new String[]{"4x Libros"},
            new String[]{"4x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_GEAR_ARMOR_PIECE, "Encantador Novato", "Novice Enchanter",
            new String[]{"Encanta 1 pieza de armadura."},
            new String[]{"Enchant 1 piece of armor."},
            new String[]{"4x Lapislázuli", "2x Libros"},
            new String[]{"4x Lapis Lazuli", "2x Books"}));
        mago.add(q(QuestData.QUEST_MAGO_GEAR_ARMOR_FULL, "Blindaje Completo", "Full Plate",
            new String[]{"Encanta un set completo de armadura", "(casco, pecho, piernas y botas)."},
            new String[]{"Enchant a full set of armor", "(helmet, chestplate, leggings, boots)."},
            new String[]{"1x Peto de Diamante Encantado", "(Protección IV) + 8x Lapislázuli"},
            new String[]{"1x Enchanted Diamond Chestplate", "(Protection IV) + 8x Lapis Lazuli"}));
        mago.add(q(QuestData.QUEST_MAGO_GEAR_PICKAXE, "Pico Encantado", "Enchanted Pickaxe",
            new String[]{"Encanta un pico."},
            new String[]{"Enchant a pickaxe."},
            new String[]{"1x Pico de Diamante Encantado", "(Eficiencia IV)"},
            new String[]{"1x Enchanted Diamond Pickaxe", "(Efficiency IV)"}));
        mago.add(q(QuestData.QUEST_MAGO_GEAR_SWORD, "Espada Encantada", "Enchanted Sword",
            new String[]{"Encanta una espada."},
            new String[]{"Enchant a sword."},
            new String[]{"1x Espada de Diamante Encantada", "(Filo IV)"},
            new String[]{"1x Enchanted Diamond Sword", "(Sharpness IV)"}));
        mago.add(q(QuestData.QUEST_MAGO_GEAR_AXE, "Hacha Encantada", "Enchanted Axe",
            new String[]{"Encanta un hacha."},
            new String[]{"Enchant an axe."},
            new String[]{"1x Hacha de Diamante Encantada", "(Eficiencia IV)"},
            new String[]{"1x Enchanted Diamond Axe", "(Efficiency IV)"}));
        tabs.add(new Tab(english ? "Mage" : "Mago", mago));

        List<QuestEntry> guerrero = new ArrayList<>();
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_1, "Cazador de Zombie I", "Zombie Hunter I",
            new String[]{"Derrota 1 zombie."},
            new String[]{"Defeat 1 zombie."},
            new String[]{"1x Pieza de armadura de cuero (aleatoria)"},
            new String[]{"1x Random Leather Armor Piece"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_5, "Cazador de Zombie II", "Zombie Hunter II",
            new String[]{"Derrota 5 zombis."},
            new String[]{"Defeat 5 zombies."},
            new String[]{"1x Pieza de armadura de cuero (aleatoria)"},
            new String[]{"1x Random Leather Armor Piece"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_10, "Cazador de Zombie III", "Zombie Hunter III",
            new String[]{"Derrota 10 zombis."},
            new String[]{"Defeat 10 zombies."},
            new String[]{"1x Cabeza de Zombie"},
            new String[]{"1x Zombie Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SKELETON_1, "Cazador de Esqueleto I", "Skeleton Hunter I",
            new String[]{"Derrota 1 esqueleto."},
            new String[]{"Defeat 1 skeleton."},
            new String[]{"4x Huesos"},
            new String[]{"4x Bones"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SKELETON_5, "Cazador de Esqueleto II", "Skeleton Hunter II",
            new String[]{"Derrota 5 esqueletos."},
            new String[]{"Defeat 5 skeletons."},
            new String[]{"8x Flechas"},
            new String[]{"8x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SKELETON_10, "Cazador de Esqueleto III", "Skeleton Hunter III",
            new String[]{"Derrota 10 esqueletos."},
            new String[]{"Defeat 10 skeletons."},
            new String[]{"1x Cabeza de Esqueleto"},
            new String[]{"1x Skeleton Skull"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CREEPER_1, "Cazador de Creeper I", "Creeper Hunter I",
            new String[]{"Derrota 1 creeper."},
            new String[]{"Defeat 1 creeper."},
            new String[]{"2x Pólvora"},
            new String[]{"2x Gunpowder"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CREEPER_5, "Cazador de Creeper II", "Creeper Hunter II",
            new String[]{"Derrota 5 creepers."},
            new String[]{"Defeat 5 creepers."},
            new String[]{"8x Pólvora"},
            new String[]{"8x Gunpowder"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CREEPER_10, "Cazador de Creeper III", "Creeper Hunter III",
            new String[]{"Derrota 10 creepers."},
            new String[]{"Defeat 10 creepers."},
            new String[]{"1x Cabeza de Creeper"},
            new String[]{"1x Creeper Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SPIDER_1, "Cazador de Araña I", "Spider Hunter I",
            new String[]{"Derrota 1 araña."},
            new String[]{"Defeat 1 spider."},
            new String[]{"2x Hilo"},
            new String[]{"2x String"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SPIDER_5, "Cazador de Araña II", "Spider Hunter II",
            new String[]{"Derrota 5 arañas."},
            new String[]{"Defeat 5 spiders."},
            new String[]{"8x Hilo"},
            new String[]{"8x String"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SPIDER_10, "Cazador de Araña III", "Spider Hunter III",
            new String[]{"Derrota 10 arañas."},
            new String[]{"Defeat 10 spiders."},
            new String[]{"1x Cabeza de Araña"},
            new String[]{"1x Spider Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ENDERMAN_1, "Cazador de Enderman I", "Enderman Hunter I",
            new String[]{"Derrota 1 enderman."},
            new String[]{"Defeat 1 enderman."},
            new String[]{"1x Perla de Ender"},
            new String[]{"1x Ender Pearl"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ENDERMAN_5, "Cazador de Enderman II", "Enderman Hunter II",
            new String[]{"Derrota 5 enderman."},
            new String[]{"Defeat 5 endermen."},
            new String[]{"2x Perlas de Ender"},
            new String[]{"2x Ender Pearls"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ENDERMAN_10, "Cazador de Enderman III", "Enderman Hunter III",
            new String[]{"Derrota 10 enderman."},
            new String[]{"Defeat 10 endermen."},
            new String[]{"1x Cabeza de Enderman"},
            new String[]{"1x Enderman Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITCH_1, "Cazador de Bruja I", "Witch Hunter I",
            new String[]{"Derrota 1 bruja."},
            new String[]{"Defeat 1 witch."},
            new String[]{"1x Botella de cristal"},
            new String[]{"1x Glass Bottle"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITCH_5, "Cazador de Bruja II", "Witch Hunter II",
            new String[]{"Derrota 5 brujas."},
            new String[]{"Defeat 5 witches."},
            new String[]{"4x Polvo de redstone"},
            new String[]{"4x Redstone Dust"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITCH_10, "Cazador de Bruja III", "Witch Hunter III",
            new String[]{"Derrota 10 brujas."},
            new String[]{"Defeat 10 witches."},
            new String[]{"1x Cabeza de Bruja"},
            new String[]{"1x Witch Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_DROWNED_1, "Cazador de Ahogado I", "Drowned Hunter I",
            new String[]{"Derrota 1 ahogado."},
            new String[]{"Defeat 1 drowned."},
            new String[]{"2x Lingotes de cobre"},
            new String[]{"2x Copper Ingots"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_DROWNED_5, "Cazador de Ahogado II", "Drowned Hunter II",
            new String[]{"Derrota 5 ahogados."},
            new String[]{"Defeat 5 drowned."},
            new String[]{"8x Lingotes de cobre"},
            new String[]{"8x Copper Ingots"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_DROWNED_10, "Cazador de Ahogado III", "Drowned Hunter III",
            new String[]{"Derrota 10 ahogados."},
            new String[]{"Defeat 10 drowned."},
            new String[]{"1x Cabeza de Ahogado"},
            new String[]{"1x Drowned Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_HUSK_1, "Cazador de Husk I", "Husk Hunter I",
            new String[]{"Derrota 1 husk."},
            new String[]{"Defeat 1 husk."},
            new String[]{"2x Carne podrida"},
            new String[]{"2x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_HUSK_5, "Cazador de Husk II", "Husk Hunter II",
            new String[]{"Derrota 5 husks."},
            new String[]{"Defeat 5 husks."},
            new String[]{"8x Carne podrida"},
            new String[]{"8x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_HUSK_10, "Cazador de Husk III", "Husk Hunter III",
            new String[]{"Derrota 10 husks."},
            new String[]{"Defeat 10 husks."},
            new String[]{"1x Cabeza de Husk"},
            new String[]{"1x Husk Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_STRAY_1, "Cazador de Stray I", "Stray Hunter I",
            new String[]{"Derrota 1 stray."},
            new String[]{"Defeat 1 stray."},
            new String[]{"4x Flechas"},
            new String[]{"4x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_STRAY_5, "Cazador de Stray II", "Stray Hunter II",
            new String[]{"Derrota 5 strays."},
            new String[]{"Defeat 5 strays."},
            new String[]{"8x Flechas"},
            new String[]{"8x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_STRAY_10, "Cazador de Stray III", "Stray Hunter III",
            new String[]{"Derrota 10 strays."},
            new String[]{"Defeat 10 strays."},
            new String[]{"1x Cabeza de Stray"},
            new String[]{"1x Stray Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PHANTOM_1, "Cazador de Fantasma I", "Phantom Hunter I",
            new String[]{"Derrota 1 fantasma."},
            new String[]{"Defeat 1 phantom."},
            new String[]{"1x Membrana de fantasma"},
            new String[]{"1x Phantom Membrane"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PHANTOM_5, "Cazador de Fantasma II", "Phantom Hunter II",
            new String[]{"Derrota 5 fantasmas."},
            new String[]{"Defeat 5 phantoms."},
            new String[]{"2x Membranas de fantasma"},
            new String[]{"2x Phantom Membranes"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PHANTOM_10, "Cazador de Fantasma III", "Phantom Hunter III",
            new String[]{"Derrota 10 fantasmas."},
            new String[]{"Defeat 10 phantoms."},
            new String[]{"1x Cabeza de Fantasma"},
            new String[]{"1x Phantom Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SLIME_1, "Cazador de Slime I", "Slime Hunter I",
            new String[]{"Derrota 1 slime."},
            new String[]{"Defeat 1 slime."},
            new String[]{"2x Bolas de slime"},
            new String[]{"2x Slime Balls"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SLIME_5, "Cazador de Slime II", "Slime Hunter II",
            new String[]{"Derrota 5 slimes."},
            new String[]{"Defeat 5 slimes."},
            new String[]{"8x Bolas de slime"},
            new String[]{"8x Slime Balls"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SLIME_10, "Cazador de Slime III", "Slime Hunter III",
            new String[]{"Derrota 10 slimes."},
            new String[]{"Defeat 10 slimes."},
            new String[]{"1x Cabeza de Slime"},
            new String[]{"1x Slime Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_MAGMA_CUBE_1, "Cazador de Cubo de magma I", "Magma Cube Hunter I",
            new String[]{"Derrota 1 cubo de magma."},
            new String[]{"Defeat 1 magma cube."},
            new String[]{"1x Crema de magma"},
            new String[]{"1x Magma Cream"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_MAGMA_CUBE_5, "Cazador de Cubo de magma II", "Magma Cube Hunter II",
            new String[]{"Derrota 5 cubos de magma."},
            new String[]{"Defeat 5 magma cubes."},
            new String[]{"2x Cremas de magma"},
            new String[]{"2x Magma Creams"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_MAGMA_CUBE_10, "Cazador de Cubo de magma III", "Magma Cube Hunter III",
            new String[]{"Derrota 10 cubos de magma."},
            new String[]{"Defeat 10 magma cubes."},
            new String[]{"1x Cabeza de Cubo de Magma"},
            new String[]{"1x Magma Cube Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BLAZE_1, "Cazador de Blaze I", "Blaze Hunter I",
            new String[]{"Derrota 1 blaze."},
            new String[]{"Defeat 1 blaze."},
            new String[]{"1x Vara de Blaze"},
            new String[]{"1x Blaze Rod"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BLAZE_5, "Cazador de Blaze II", "Blaze Hunter II",
            new String[]{"Derrota 5 blazes."},
            new String[]{"Defeat 5 blazes."},
            new String[]{"2x Varas de Blaze"},
            new String[]{"2x Blaze Rods"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BLAZE_10, "Cazador de Blaze III", "Blaze Hunter III",
            new String[]{"Derrota 10 blazes."},
            new String[]{"Defeat 10 blazes."},
            new String[]{"1x Cabeza de Blaze"},
            new String[]{"1x Blaze Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_GHAST_1, "Cazador de Ghast I", "Ghast Hunter I",
            new String[]{"Derrota 1 ghast."},
            new String[]{"Defeat 1 ghast."},
            new String[]{"1x Lágrima de Ghast"},
            new String[]{"1x Ghast Tear"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_GHAST_5, "Cazador de Ghast II", "Ghast Hunter II",
            new String[]{"Derrota 5 ghasts."},
            new String[]{"Defeat 5 ghasts."},
            new String[]{"2x Lágrimas de Ghast"},
            new String[]{"2x Ghast Tears"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_GHAST_10, "Cazador de Ghast III", "Ghast Hunter III",
            new String[]{"Derrota 10 ghasts."},
            new String[]{"Defeat 10 ghasts."},
            new String[]{"1x Cabeza de Ghast"},
            new String[]{"1x Ghast Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PIGLIN_1, "Cazador de Piglin I", "Piglin Hunter I",
            new String[]{"Derrota 1 piglin."},
            new String[]{"Defeat 1 piglin."},
            new String[]{"4x Pepitas de oro"},
            new String[]{"4x Gold Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PIGLIN_5, "Cazador de Piglin II", "Piglin Hunter II",
            new String[]{"Derrota 5 piglins."},
            new String[]{"Defeat 5 piglins."},
            new String[]{"8x Pepitas de oro"},
            new String[]{"8x Gold Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PIGLIN_10, "Cazador de Piglin III", "Piglin Hunter III",
            new String[]{"Derrota 10 piglins."},
            new String[]{"Defeat 10 piglins."},
            new String[]{"1x Cabeza de Piglin"},
            new String[]{"1x Piglin Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_HOGLIN_1, "Cazador de Hoglin I", "Hoglin Hunter I",
            new String[]{"Derrota 1 hoglin."},
            new String[]{"Defeat 1 hoglin."},
            new String[]{"2x Chuletas de cerdo"},
            new String[]{"2x Porkchops"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_HOGLIN_5, "Cazador de Hoglin II", "Hoglin Hunter II",
            new String[]{"Derrota 5 hoglins."},
            new String[]{"Defeat 5 hoglins."},
            new String[]{"8x Chuletas de cerdo"},
            new String[]{"8x Porkchops"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_HOGLIN_10, "Cazador de Hoglin III", "Hoglin Hunter III",
            new String[]{"Derrota 10 hoglins."},
            new String[]{"Defeat 10 hoglins."},
            new String[]{"1x Cabeza de Hoglin"},
            new String[]{"1x Hoglin Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PILLAGER_1, "Cazador de Saqueador I", "Pillager Hunter I",
            new String[]{"Derrota 1 saqueador."},
            new String[]{"Defeat 1 pillager."},
            new String[]{"4x Flechas"},
            new String[]{"4x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PILLAGER_5, "Cazador de Saqueador II", "Pillager Hunter II",
            new String[]{"Derrota 5 saqueadores."},
            new String[]{"Defeat 5 pillagers."},
            new String[]{"8x Flechas"},
            new String[]{"8x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PILLAGER_10, "Cazador de Saqueador III", "Pillager Hunter III",
            new String[]{"Derrota 10 saqueadores."},
            new String[]{"Defeat 10 pillagers."},
            new String[]{"1x Cabeza de Saqueador"},
            new String[]{"1x Pillager Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_VINDICATOR_1, "Cazador de Vindicator I", "Vindicator Hunter I",
            new String[]{"Derrota 1 vindicator."},
            new String[]{"Defeat 1 vindicator."},
            new String[]{"1x Esmeralda"},
            new String[]{"1x Emerald"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_VINDICATOR_5, "Cazador de Vindicator II", "Vindicator Hunter II",
            new String[]{"Derrota 5 vindicators."},
            new String[]{"Defeat 5 vindicators."},
            new String[]{"2x Esmeraldas"},
            new String[]{"2x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_VINDICATOR_10, "Cazador de Vindicator III", "Vindicator Hunter III",
            new String[]{"Derrota 10 vindicators."},
            new String[]{"Defeat 10 vindicators."},
            new String[]{"1x Cabeza de Vindicator"},
            new String[]{"1x Vindicator Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_EVOKER_1, "Cazador de Invocador I", "Evoker Hunter I",
            new String[]{"Derrota 1 invocador."},
            new String[]{"Defeat 1 evoker."},
            new String[]{"1x Esmeralda"},
            new String[]{"1x Emerald"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_EVOKER_5, "Cazador de Invocador II", "Evoker Hunter II",
            new String[]{"Derrota 5 invocadores."},
            new String[]{"Defeat 5 evokers."},
            new String[]{"2x Esmeraldas"},
            new String[]{"2x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_EVOKER_10, "Cazador de Invocador III", "Evoker Hunter III",
            new String[]{"Derrota 10 invocadores."},
            new String[]{"Defeat 10 evokers."},
            new String[]{"1x Cabeza de Invocador"},
            new String[]{"1x Evoker Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_GUARDIAN_1, "Cazador de Guardián I", "Guardian Hunter I",
            new String[]{"Derrota 1 guardián."},
            new String[]{"Defeat 1 guardian."},
            new String[]{"2x Fragmentos de prismarina"},
            new String[]{"2x Prismarine Shards"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_GUARDIAN_5, "Cazador de Guardián II", "Guardian Hunter II",
            new String[]{"Derrota 5 guardianes."},
            new String[]{"Defeat 5 guardians."},
            new String[]{"8x Fragmentos de prismarina"},
            new String[]{"8x Prismarine Shards"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_GUARDIAN_10, "Cazador de Guardián III", "Guardian Hunter III",
            new String[]{"Derrota 10 guardianes."},
            new String[]{"Defeat 10 guardians."},
            new String[]{"1x Cabeza de Guardián"},
            new String[]{"1x Guardian Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ELDER_GUARDIAN_2, "Prueba del Guardián Anciano", "Trial of the Elder Guardian",
            new String[]{"Derrota 2 Guardianes Ancianos."},
            new String[]{"Defeat 2 Elder Guardians."},
            new String[]{"1x Cabeza de Guardián Anciano"},
            new String[]{"1x Elder Guardian Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAVAGER_2, "Prueba del Devastador", "Trial of the Ravager",
            new String[]{"Derrota 2 Devastadores."},
            new String[]{"Defeat 2 Ravagers."},
            new String[]{"1x Cabeza de Devastador"},
            new String[]{"1x Ravager Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WARDEN_1, "Desafío del Warden", "Warden Challenge",
            new String[]{"Derrota 1 Warden."},
            new String[]{"Defeat 1 Warden."},
            new String[]{"1x Cabeza de Warden"},
            new String[]{"1x Warden Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITHER_2, "El Desafío del Wither", "The Wither Challenge",
            new String[]{"Derrota 2 Withers."},
            new String[]{"Defeat 2 Withers."},
            new String[]{"1x Cabeza de Wither"},
            new String[]{"1x Wither Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_DRAGON_2, "Maestro del End", "Master of the End",
            new String[]{"Derrota 2 Ender Dragons."},
            new String[]{"Defeat 2 Ender Dragons."},
            new String[]{"1x Cabeza de Dragón del End"},
            new String[]{"1x Ender Dragon Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_FIRST, "Primera Incursión", "First Raid",
            new String[]{"Supera una incursión por primera vez."},
            new String[]{"Defeat a raid for the first time."},
            new String[]{"30x Esmeraldas"},
            new String[]{"30x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_HERO, "El Héroe de la Aldea", "Hero of the Village",
            new String[]{"Obtén el efecto Héroe de la Aldea al completar una incursión."},
            new String[]{"Obtain the Hero of the Village effect by completing a raid."},
            new String[]{"1x Botella Ominosa"},
            new String[]{"1x Ominous Bottle"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_I, "Incursión de Nivel I", "Level I Raid",
            new String[]{"Completa una incursión activada con una Botella Ominosa de nivel I."},
            new String[]{"Complete a raid started with a level I Ominous Bottle."},
            new String[]{"30x Esmeraldas"},
            new String[]{"30x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_3, "Veterano de las Incursiones", "Raid Veteran",
            new String[]{"Supera 3 incursiones en total."},
            new String[]{"Defeat 3 raids in total."},
            new String[]{"40x Esmeraldas"},
            new String[]{"40x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_III, "Incursión de Nivel III", "Level III Raid",
            new String[]{"Completa una incursión activada con una Botella Ominosa de nivel III."},
            new String[]{"Complete a raid started with a level III Ominous Bottle."},
            new String[]{"1x Botella Ominosa"},
            new String[]{"1x Ominous Bottle"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_III_3, "Veterano de Nivel III", "Level III Veteran",
            new String[]{"Supera 3 incursiones de nivel III."},
            new String[]{"Defeat 3 level III raids."},
            new String[]{"64x Esmeraldas"},
            new String[]{"64x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_V, "Incursión de Nivel V", "Level V Raid",
            new String[]{"Completa una incursión activada con una Botella Ominosa de nivel V."},
            new String[]{"Complete a raid started with a level V Ominous Bottle."},
            new String[]{"1x Tótem de la Inmortalidad"},
            new String[]{"1x Totem of Undying"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_RAID_V_3, "Maestro de las Incursiones", "Raid Master",
            new String[]{"Supera 3 incursiones de nivel V."},
            new String[]{"Defeat 3 level V raids."},
            new String[]{"1x Bloque de Esmeralda", "1x Botella Ominosa"},
            new String[]{"1x Emerald Block", "1x Ominous Bottle"}));
        // ── Agregados a pedido del usuario: mobs del Bestiario que faltaban ──
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITHER_SKELETON_1, "Cazador de Esqueleto Wither I", "Wither Skeleton Hunter I",
            new String[]{"Derrota 1 esqueleto wither."}, new String[]{"Defeat 1 wither skeleton."},
            new String[]{"2x Carbón"}, new String[]{"2x Coal"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITHER_SKELETON_5, "Cazador de Esqueleto Wither II", "Wither Skeleton Hunter II",
            new String[]{"Derrota 5 esqueletos wither."}, new String[]{"Defeat 5 wither skeletons."},
            new String[]{"8x Carbón"}, new String[]{"8x Coal"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_WITHER_SKELETON_10, "Cazador de Esqueleto Wither III", "Wither Skeleton Hunter III",
            new String[]{"Derrota 10 esqueletos wither."}, new String[]{"Defeat 10 wither skeletons."},
            new String[]{"1x Calavera de Wither"}, new String[]{"1x Wither Skeleton Skull"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_VEX_1, "Cazador de Vex I", "Vex Hunter I",
            new String[]{"Derrota 1 vex."}, new String[]{"Defeat 1 vex."},
            new String[]{"1x Esmeralda"}, new String[]{"1x Emerald"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_VEX_5, "Cazador de Vex II", "Vex Hunter II",
            new String[]{"Derrota 5 vex."}, new String[]{"Defeat 5 vexes."},
            new String[]{"2x Esmeraldas"}, new String[]{"2x Emeralds"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_VEX_10, "Cazador de Vex III", "Vex Hunter III",
            new String[]{"Derrota 10 vex."}, new String[]{"Defeat 10 vexes."},
            new String[]{"1x Cabeza de Vex"}, new String[]{"1x Vex Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CREAKING_1, "Silenciador del Creaking", "Creaking Silencer",
            new String[]{"Derrota 1 Creaking."}, new String[]{"Defeat 1 Creaking."},
            new String[]{"1x Cabeza de Creaking", "8x Ladrillo de Resina", "4x Resina"},
            new String[]{"1x Creaking Head", "8x Resin Brick", "4x Resin Clump"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOGLIN_1, "Cazador de Zoglin I", "Zoglin Hunter I",
            new String[]{"Derrota 1 zoglin."}, new String[]{"Defeat 1 zoglin."},
            new String[]{"2x Carne Podrida"}, new String[]{"2x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOGLIN_5, "Cazador de Zoglin II", "Zoglin Hunter II",
            new String[]{"Derrota 5 zoglins."}, new String[]{"Defeat 5 zoglins."},
            new String[]{"8x Carne Podrida"}, new String[]{"8x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOGLIN_10, "Cazador de Zoglin III", "Zoglin Hunter III",
            new String[]{"Derrota 10 zoglins."}, new String[]{"Defeat 10 zoglins."},
            new String[]{"1x Cabeza de Zoglin"}, new String[]{"1x Zoglin Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BREEZE_1, "Cazador de Breeze I", "Breeze Hunter I",
            new String[]{"Derrota 1 breeze."}, new String[]{"Defeat 1 breeze."},
            new String[]{"1x Vara de Breeze"}, new String[]{"1x Breeze Rod"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BREEZE_5, "Cazador de Breeze II", "Breeze Hunter II",
            new String[]{"Derrota 5 breeze."}, new String[]{"Defeat 5 breezes."},
            new String[]{"2x Varas de Breeze"}, new String[]{"2x Breeze Rods"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BREEZE_10, "Cazador de Breeze III", "Breeze Hunter III",
            new String[]{"Derrota 10 breeze."}, new String[]{"Defeat 10 breezes."},
            new String[]{"1x Cabeza de Breeze", "2x Varas de Breeze"}, new String[]{"1x Breeze Head", "2x Breeze Rods"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CAMEL_HUSK_1, "Cazador de Camello Momificado I", "Camel Husk Hunter I",
            new String[]{"Derrota 1 camello momificado."}, new String[]{"Defeat 1 camel husk."},
            new String[]{"2x Cuero"}, new String[]{"2x Leather"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CAMEL_HUSK_3, "Cazador de Camello Momificado II", "Camel Husk Hunter II",
            new String[]{"Derrota 3 camellos momificados."}, new String[]{"Defeat 3 camel husks."},
            new String[]{"1x Silla de Montar"}, new String[]{"1x Saddle"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CAMEL_HUSK_5, "Cazador de Camello Momificado III", "Camel Husk Hunter III",
            new String[]{"Derrota 5 camellos momificados."}, new String[]{"Defeat 5 camel husks."},
            new String[]{"1x Cabeza de Camello Momificado"}, new String[]{"1x Camel Husk Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_1, "Cazador de Piglin Zombie I", "Zombified Piglin Hunter I",
            new String[]{"Derrota 1 piglin zombificado."}, new String[]{"Defeat 1 zombified piglin."},
            new String[]{"4x Pepitas de Oro"}, new String[]{"4x Gold Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_5, "Cazador de Piglin Zombie II", "Zombified Piglin Hunter II",
            new String[]{"Derrota 5 piglins zombificados."}, new String[]{"Defeat 5 zombified piglins."},
            new String[]{"8x Pepitas de Oro"}, new String[]{"8x Gold Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_10, "Cazador de Piglin Zombie III", "Zombified Piglin Hunter III",
            new String[]{"Derrota 10 piglins zombificados."}, new String[]{"Defeat 10 zombified piglins."},
            new String[]{"1x Cabeza de Piglin Zombie"}, new String[]{"1x Zombified Piglin Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SILVERFISH_1, "Cazador de Lepisma I", "Silverfish Hunter I",
            new String[]{"Derrota 1 pez de plata."}, new String[]{"Defeat 1 silverfish."},
            new String[]{"4x Pepitas de Hierro"}, new String[]{"4x Iron Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SILVERFISH_5, "Cazador de Lepisma II", "Silverfish Hunter II",
            new String[]{"Derrota 5 peces de plata."}, new String[]{"Defeat 5 silverfish."},
            new String[]{"2x Lingotes de Hierro"}, new String[]{"2x Iron Ingots"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SILVERFISH_10, "Cazador de Lepisma III", "Silverfish Hunter III",
            new String[]{"Derrota 10 peces de plata."}, new String[]{"Defeat 10 silverfish."},
            new String[]{"1x Cabeza de Lepisma"}, new String[]{"1x Silverfish Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BOGGED_1, "Cazador de Bogged I", "Bogged Hunter I",
            new String[]{"Derrota 1 bogged."}, new String[]{"Defeat 1 bogged."},
            new String[]{"2x Huesos"}, new String[]{"2x Bones"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BOGGED_5, "Cazador de Bogged II", "Bogged Hunter II",
            new String[]{"Derrota 5 bogged."}, new String[]{"Defeat 5 bogged."},
            new String[]{"8x Flechas"}, new String[]{"8x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BOGGED_10, "Cazador de Bogged III", "Bogged Hunter III",
            new String[]{"Derrota 10 bogged."}, new String[]{"Defeat 10 bogged."},
            new String[]{"1x Cabeza de Bogged"}, new String[]{"1x Bogged Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_1, "Cazador de Aldeano Zombi I", "Zombie Villager Hunter I",
            new String[]{"Derrota 1 aldeano zombi."}, new String[]{"Defeat 1 zombie villager."},
            new String[]{"2x Carne Podrida"}, new String[]{"2x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_5, "Cazador de Aldeano Zombi II", "Zombie Villager Hunter II",
            new String[]{"Derrota 5 aldeanos zombis."}, new String[]{"Defeat 5 zombie villagers."},
            new String[]{"8x Zanahorias"}, new String[]{"8x Carrots"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_10, "Cazador de Aldeano Zombi III", "Zombie Villager Hunter III",
            new String[]{"Derrota 10 aldeanos zombis."}, new String[]{"Defeat 10 zombie villagers."},
            new String[]{"1x Cabeza de Aldeano Zombi", "1x Manzana Dorada"}, new String[]{"1x Zombie Villager Head", "1x Golden Apple"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ENDERMITE_1, "Cazador de Endermite I", "Endermite Hunter I",
            new String[]{"Derrota 1 endermite."}, new String[]{"Defeat 1 endermite."},
            new String[]{"1x Perla de Ender"}, new String[]{"1x Ender Pearl"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ENDERMITE_5, "Cazador de Endermite II", "Endermite Hunter II",
            new String[]{"Derrota 5 endermites."}, new String[]{"Defeat 5 endermites."},
            new String[]{"2x Perlas de Ender"}, new String[]{"2x Ender Pearls"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ENDERMITE_10, "Cazador de Endermite III", "Endermite Hunter III",
            new String[]{"Derrota 10 endermites."}, new String[]{"Defeat 10 endermites."},
            new String[]{"1x Cabeza de Endermite"}, new String[]{"1x Endermite Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_1, "Cazador de Piglin Brutal I", "Piglin Brute Hunter I",
            new String[]{"Derrota 1 piglin brutal."}, new String[]{"Defeat 1 piglin brute."},
            new String[]{"4x Pepitas de Oro"}, new String[]{"4x Gold Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_5, "Cazador de Piglin Brutal II", "Piglin Brute Hunter II",
            new String[]{"Derrota 5 piglins brutales."}, new String[]{"Defeat 5 piglin brutes."},
            new String[]{"2x Lingotes de Oro"}, new String[]{"2x Gold Ingots"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_10, "Cazador de Piglin Brutal III", "Piglin Brute Hunter III",
            new String[]{"Derrota 10 piglins brutales."}, new String[]{"Defeat 10 piglin brutes."},
            new String[]{"1x Cabeza de Piglin Brutal", "1x Hacha de Oro"}, new String[]{"1x Piglin Brute Head", "1x Golden Axe"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CAVE_SPIDER_1, "Cazador de Araña de Cueva I", "Cave Spider Hunter I",
            new String[]{"Derrota 1 araña de cueva."}, new String[]{"Defeat 1 cave spider."},
            new String[]{"2x Hilo"}, new String[]{"2x String"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CAVE_SPIDER_5, "Cazador de Araña de Cueva II", "Cave Spider Hunter II",
            new String[]{"Derrota 5 arañas de cueva."}, new String[]{"Defeat 5 cave spiders."},
            new String[]{"2x Ojos de Araña"}, new String[]{"2x Spider Eyes"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CAVE_SPIDER_10, "Cazador de Araña de Cueva III", "Cave Spider Hunter III",
            new String[]{"Derrota 10 arañas de cueva."}, new String[]{"Defeat 10 cave spiders."},
            new String[]{"1x Cabeza de Araña de Cueva"}, new String[]{"1x Cave Spider Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SHULKER_1, "Cazador de Shulker I", "Shulker Hunter I",
            new String[]{"Derrota 1 shulker."}, new String[]{"Defeat 1 shulker."},
            new String[]{"1x Perla de Ender"}, new String[]{"1x Ender Pearl"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SHULKER_5, "Cazador de Shulker II", "Shulker Hunter II",
            new String[]{"Derrota 5 shulkers."}, new String[]{"Defeat 5 shulkers."},
            new String[]{"1x Caparazón de Shulker"}, new String[]{"1x Shulker Shell"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SHULKER_10, "Cazador de Shulker III", "Shulker Hunter III",
            new String[]{"Derrota 10 shulkers."}, new String[]{"Defeat 10 shulkers."},
            new String[]{"1x Caja de Shulker"}, new String[]{"1x Shulker Box"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_1, "Cazador de Chicken Jockey I", "Chicken Jockey Hunter I",
            new String[]{"Derrota 1 Chicken Jockey."}, new String[]{"Defeat 1 Chicken Jockey."},
            new String[]{"2x Plumas"}, new String[]{"2x Feathers"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_2, "Cazador de Chicken Jockey II", "Chicken Jockey Hunter II",
            new String[]{"Derrota 2 Chicken Jockeys."}, new String[]{"Defeat 2 Chicken Jockeys."},
            new String[]{"4x Pollo Crudo"}, new String[]{"4x Raw Chicken"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_3, "Cazador de Chicken Jockey III", "Chicken Jockey Hunter III",
            new String[]{"Derrota 3 Chicken Jockeys."}, new String[]{"Defeat 3 Chicken Jockeys."},
            new String[]{"1x Disco de Música \\\"Lava Chicken\\\""}, new String[]{"1x \\\"Lava Chicken\\\" Music Disc"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BABY_ZOMBIE_1, "Cazador de Bebé Zombi I", "Baby Zombie Hunter I",
            new String[]{"Derrota 1 bebé zombi."}, new String[]{"Defeat 1 baby zombie."},
            new String[]{"2x Carne Podrida"}, new String[]{"2x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BABY_ZOMBIE_5, "Cazador de Bebé Zombi II", "Baby Zombie Hunter II",
            new String[]{"Derrota 5 bebés zombis."}, new String[]{"Defeat 5 baby zombies."},
            new String[]{"8x Zanahorias"}, new String[]{"8x Carrots"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_BABY_ZOMBIE_10, "Cazador de Bebé Zombi III", "Baby Zombie Hunter III",
            new String[]{"Derrota 10 bebés zombis."}, new String[]{"Defeat 10 baby zombies."},
            new String[]{"1x Cabeza de Bebé Zombi"}, new String[]{"1x Baby Zombie Head"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_1, "Cazador del Jinete Zombie I", "Zombie Horseman Hunter I",
            new String[]{"Derrota 1 Jinete Zombie."}, new String[]{"Defeat 1 Zombie Horseman."},
            new String[]{"2x Carne Podrida"}, new String[]{"2x Rotten Flesh"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_2, "Cazador del Jinete Zombie II", "Zombie Horseman Hunter II",
            new String[]{"Derrota 2 Jinetes Zombie."}, new String[]{"Defeat 2 Zombie Horsemen."},
            new String[]{"4x Pepitas de Hierro"}, new String[]{"4x Iron Nuggets"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_3, "Cazador del Jinete Zombie III", "Zombie Horseman Hunter III",
            new String[]{"Derrota 3 Jinetes Zombie."}, new String[]{"Defeat 3 Zombie Horsemen."},
            new String[]{"1x Cabeza de Jinete Zombie", "1x Armadura de Hierro para Caballo"},
            new String[]{"1x Zombie Horseman Head", "1x Iron Horse Armor"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_1, "Cazador del Jinete Esqueleto I", "Skeleton Horseman Hunter I",
            new String[]{"Derrota 1 Jinete Esqueleto."}, new String[]{"Defeat 1 Skeleton Horseman."},
            new String[]{"2x Huesos"}, new String[]{"2x Bones"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_2, "Cazador del Jinete Esqueleto II", "Skeleton Horseman Hunter II",
            new String[]{"Derrota 2 Jinetes Esqueleto."}, new String[]{"Defeat 2 Skeleton Horsemen."},
            new String[]{"8x Flechas"}, new String[]{"8x Arrows"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_3, "Cazador del Jinete Esqueleto III", "Skeleton Horseman Hunter III",
            new String[]{"Derrota 3 Jinetes Esqueleto."}, new String[]{"Defeat 3 Skeleton Horsemen."},
            new String[]{"1x Cabeza de Jinete Esqueleto", "1x Arco Encantado (Poder III)"},
            new String[]{"1x Skeleton Horseman Head", "1x Enchanted Bow (Power III)"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_1, "Cazador de Spider Jockey I", "Spider Jockey Hunter I",
            new String[]{"Derrota 1 Spider Jockey."}, new String[]{"Defeat 1 Spider Jockey."},
            new String[]{"2x Hilo"}, new String[]{"2x String"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_2, "Cazador de Spider Jockey II", "Spider Jockey Hunter II",
            new String[]{"Derrota 2 Spider Jockeys."}, new String[]{"Defeat 2 Spider Jockeys."},
            new String[]{"4x Huesos"}, new String[]{"4x Bones"}));
        guerrero.add(q(QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_3, "Cazador de Spider Jockey III", "Spider Jockey Hunter III",
            new String[]{"Derrota 3 Spider Jockeys."}, new String[]{"Defeat 3 Spider Jockeys."},
            new String[]{"1x Cabeza de Spider Jockey", "4x Telaraña"}, new String[]{"1x Spider Jockey Head", "4x Cobweb"}));
        tabs.add(new Tab(english ? "Warrior" : "Guerrero", guerrero));
    }

    private QuestEntry q(String id, String nameEs, String nameEn,
                          String[] descEs, String[] descEn,
                          String[] rewardsEs, String[] rewardsEn) {
        return english
            ? new QuestEntry(id, nameEn, descEn, rewardsEn, false)
            : new QuestEntry(id, nameEs, descEs, rewardsEs, false);
    }

    // Logros oficiales de Minecraft: solo de referencia (informativos), sin
    // recompensa del mod ni botón de "Obtener" — este mod no los otorga,
    // los otorga el propio juego.
    private QuestEntry qi(String id, String nameEs, String nameEn,
                          String descEs, String descEn) {
        return english
            ? new QuestEntry(id, nameEn, new String[]{ descEn }, new String[0], true)
            : new QuestEntry(id, nameEs, new String[]{ descEs }, new String[0], true);
    }

    // ── Pestaña "Completadas" (creeper) ──
    // No es una lista fija como las demás: se arma cada vez que se necesita, juntando
    // las misiones completadas de Principal/Explorador/Minero/Constructor/Técnico/Mago/
    // Guerrero (se ignora solo Logros — esas son puramente de referencia, no tienen
    // getRewardsFor ni botón de canje en ningún lado).
    // Orden pedido: las completadas y AÚN NO canjeadas van agrupadas arriba; las ya
    // canjeadas quedan abajo. Como el estado de canje puede cambiar en cualquier
    // momento (el jugador reclama una recompensa), se recalcula en cada acceso en vez
    // de guardarse una vez — es una operación barata (unas pocas docenas de misiones).
    // Índices en `tabs` que dan recompensa canjeable (todas menos Logros).
    // OJO: antes esta lista no incluía el 0 (Principal) — sus 17 misiones (Primera
    // Casa, Los Primeros Lingotes, La Mirada del Fin, etc.) SÍ tienen recompensa real
    // (getRewardsFor las cubre igual que cualquier otra), pero al no estar acá quedaban
    // "rezagadas": se completaban y quedaban reclamables, pero nunca aparecían en el
    // listado del creeper — solo entrando de nuevo a la pestaña Principal se podían
    // encontrar y canjear. Reportado por el usuario — corregido agregando el 0.
    private static final int[] REWARD_TAB_INDICES = { 0, 1, 2, 3, 4, TAB_MAGO, TAB_GUERRERO };

    private List<QuestEntry> unclaimedCompletedQuests() {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        List<QuestEntry> result = new ArrayList<>();
        if (uuid != null) {
            for (int t : REWARD_TAB_INDICES) {
                for (QuestEntry q : tabs.get(t).quests()) {
                    if (QuestData.get().isCompleted(uuid, q.id()) && !QuestData.get().isClaimed(uuid, q.id())) {
                        result.add(q);
                    }
                }
            }
        }
        return result;
    }

    private List<QuestEntry> claimedCompletedQuests() {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        List<QuestEntry> result = new ArrayList<>();
        if (uuid != null) {
            for (int t : REWARD_TAB_INDICES) {
                for (QuestEntry q : tabs.get(t).quests()) {
                    if (QuestData.get().isCompleted(uuid, q.id()) && QuestData.get().isClaimed(uuid, q.id())) {
                        result.add(q);
                    }
                }
            }
        }
        return result;
    }

    private List<QuestEntry> buildCompletedQuests() {
        List<QuestEntry> result = new ArrayList<>();
        result.addAll(unclaimedCompletedQuests());
        result.addAll(claimedCompletedQuests());
        return result;
    }

    // OJO: arranca en 0 porque "Principal" (tab 0) es una categoría de misiones
    // normal igual que Explorador/Minero/etc — antes decía "tab >= 1" y se la
    // dejaba afuera por error (arrancaba en la 1 en vez de la 0), por eso
    // Principal se quedó mostrando el indicador viejo de rango de scroll en vez
    // de la barra de progreso que ya tenían las demás. Coincide con
    // REWARD_TAB_INDICES de más arriba, que si incluye la 0 — 5 (Logros) queda
    // afuera a propósito en los dos lugares, no es una categoría de misiones común.
    private static boolean isCategoryTab(int tab) { return (tab >= 0 && tab <= 4) || tab == TAB_MAGO || tab == TAB_GUERRERO; }

    private List<QuestEntry> currentTabQuests() {
        if (selectedTab == TAB_COMPLETED) return buildCompletedQuests();
        if (selectedTab == TAB_MENU) return List.of(); // TAB_MENU no usa el listado de misiones normal
        List<QuestEntry> base = tabs.get(selectedTab).quests();
        if (!hideCompleted || !isCategoryTab(selectedTab)) return base;
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        if (uuid == null) return base;
        List<QuestEntry> filtered = new ArrayList<>();
        for (QuestEntry q : base) {
            if (!QuestData.get().isCompleted(uuid, q.id())) filtered.add(q);
        }
        return filtered;
    }

    private String currentTabLabel() {
        // Se probó "Unclaimed"/"Sin Reclamar" (la pestaña junta completadas sin
        // reclamar + ya reclamadas), pero como el botón "Reclamar todo" queda
        // visible siempre esté vacío o no, "Completadas" es más preciso para lo
        // que realmente lista esta pestaña y no genera falsas expectativas.
        if (selectedTab == TAB_COMPLETED) return english ? "Completed" : "Completadas";
        if (selectedTab == TAB_MENU) return english ? "Menu" : "Menú";
        return tabs.get(selectedTab).label();
    }

    // ── Scroll ──
    private int maxScroll() {
        if (selectedTab == TAB_MENU) {
            if ("bestiary".equals(menuSubPage)) {
                return Math.max(0, buildBestiaryRows().size() - BESTIARY_VISIBLE_ROWS);
            }
            return 0;
        }
        List<QuestEntry> quests = currentTabQuests();
        return Math.max(0, quests.size() - VISIBLE_ROWS);
    }

    /** Filas del Bestiario: solo los mobs de la categoría seleccionada (ver bestiaryCategory / las 3 pestañas). */
    private List<Bestiary.Entry> buildBestiaryRows() {
        List<Bestiary.Entry> rows = new ArrayList<>();
        rows.addAll(switch (bestiaryCategory) {
            case 1 -> Bestiary.special();
            case 2 -> Bestiary.bosses();
            default -> Bestiary.common();
        });
        return rows;
    }

    private void scrollUp()   { if (scrollOffset[selectedTab] > 0)          scrollOffset[selectedTab]--; }
    private void scrollDown() { if (scrollOffset[selectedTab] < maxScroll()) scrollOffset[selectedTab]++; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // Se resetea cada frame; drawBestiaryLootIcons lo vuelve a poner en true más
        // abajo SOLO si el mouse está justo arriba de un ícono de loot en este frame.
        bestiaryLootTooltipShown = false;

        int bx = this.width  / 2 - BW / 2;
        int by = this.height / 2 - BH / 2;

        // Fondo escalado (siempre se dibuja completo: incluye pestañas de colores, que no
        // forman parte de la animación de cambio de página)
        float scaleX = (float) BW / BG_NATIVE_W;
        float scaleY = (float) BH / BG_NATIVE_H;
        g.pose().pushMatrix();
        g.pose().translate(bx, by);
        g.pose().scale(scaleX, scaleY);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, 0, 0, 0f, 0f, BG_NATIVE_W, BG_NATIVE_H, BG_NATIVE_W, BG_NATIVE_H);
        g.pose().popMatrix();

        // ── Animación de cambio de página ──
        // Se superpone encima del fondo, alineada con el rectángulo de páginas/tapa
        // (ver constantes ANIM_DEST_*). Las pestañas de colores quedan visibles debajo
        // porque la animación no las cubre.
        long now = System.currentTimeMillis();
        boolean pageFlipping = tabAnimStart >= 0 && (now - tabAnimStart) < ANIM_TOTAL_MS;
        // Después de que termina el flip (600ms), el creeper se revela en pasos discretos
        // durante 1 segundo más — no es parte del gif de flip, así que se anima aparte.
        boolean creeperRevealing = tabAnimStart >= 0
            && (now - tabAnimStart) >= ANIM_TOTAL_MS
            && (now - tabAnimStart) < ANIM_TOTAL_MS + CREEPER_REVEAL_MS;

        if (pageFlipping) {
            int frameIdx = (int) ((now - tabAnimStart) / ANIM_FRAME_MS);
            if (frameIdx >= ANIM_FRAME_COUNT) frameIdx = ANIM_FRAME_COUNT - 1;

            float animScaleX = ANIM_DEST_W / ANIM_FRAME_W;
            float animScaleY = ANIM_DEST_H / ANIM_FRAME_H;
            g.pose().pushMatrix();
            g.pose().translate(bx, by);
            g.pose().scale(scaleX, scaleY);
            g.pose().translate(ANIM_DEST_X, ANIM_DEST_Y);
            g.pose().scale(animScaleX, animScaleY);
            g.blit(RenderPipelines.GUI_TEXTURED, ANIM_FRAMES[frameIdx], 0, 0, 0f, 0f,
                ANIM_FRAME_W, ANIM_FRAME_H, ANIM_FRAME_W, ANIM_FRAME_H);
            g.pose().popMatrix();

            // Mientras se reproduce la animación no se dibuja el contenido de la página
            // ni se dejan zonas de click activas (evita clicks fantasma sobre botones viejos).
            // El creeper tampoco se dibuja todavía — recién empieza a aparecer cuando esto termina.
            pinBtnY = 9999; claimBtnY = 9999; claimBtnQuestId = null;
            scrollUpBtnY = 9999; scrollDownBtnY = 9999;
            hideToggleY = 9999; claimAllBtnY = 9999;
            return;
        }
        if (!creeperRevealing) {
            tabAnimStart = -1L;
        }

        // Botón creeper (arriba izq., junto a la casita): pestaña "Completadas".
        // El ícono en sí (creeper.png) se dibuja siempre en su posición de descanso;
        // el resalte + tooltip solo al pasar el mouse, igual que las demás pestañas.
        // Justo después de pasar de página, se revela de a poco en 6 pasos (1 segundo
        // en total) en vez de aparecer todo de una — como si fuera saliendo de detrás
        // de la página, un pedacito más visible en cada paso.
        // NOTA: los tooltips de estos dos botones se dibujan al FINAL del método
        // (ver bloque "Tooltips diferidos" al final de extractRenderState), para que
        // queden por ENCIMA del texto/título del libro en vez de por debajo. El resto
        // (resalte + ícono) se sigue dibujando acá, en su lugar de siempre.
        boolean deferredOverCreeper, deferredOverHome, deferredOverWither;
        int deferredCrX1 = 0, deferredCrY2 = 0, deferredHX1 = 0, deferredHY2 = 0, deferredWiX1 = 0, deferredWiY2 = 0;
        String deferredCreeperTip = null, deferredHomeTip = null, deferredWitherTip = null;
        // Estos 2 (hideToggle/claimAll) viven en la fila del título de la pestaña, que
        // se dibuja bastante más abajo en el método (dentro del bloque "selectedTab !=
        // TAB_MENU") — pero como TODOS los tooltips se dibujan diferido al final, en un
        // punto que queda FUERA de ese bloque, las variables tienen que declararse acá
        // arriba (al nivel del método) para seguir existiendo en ese punto. Antes estaban
        // declaradas más abajo, dentro del bloque, y el compilador tiraba "cannot find
        // symbol" al querer leerlas ya afuera.
        boolean deferredOverHideToggle = false, deferredOverClaimAll = false;
        int deferredHideToggleTipX = 0, deferredHideToggleTipY = 0;
        int deferredClaimAllTipX = 0, deferredClaimAllTipY = 0;
        String deferredHideToggleTip = null;
        String claimAllTip = english ? "Claim all" : "Reclamar todas";
        refreshBestiaryUnlockTracking();
        {
            int crX1 = bx + TAB_CREEPER_X1, crX2 = bx + TAB_CREEPER_X2;
            int crY1 = by + TAB_CREEPER_Y1, crY2 = by + TAB_CREEPER_Y2;
            boolean overCreeper = mouseX >= crX1 && mouseX <= crX2 && mouseY >= crY1 && mouseY <= crY2;
            if (overCreeper) {
                g.fill(crX1, crY1, crX2, crY2, 0x33FFFFFF);
            }
            int revealStep = CREEPER_REVEAL_STEPS;
            if (creeperRevealing) {
                long revealElapsed = now - tabAnimStart - ANIM_TOTAL_MS;
                revealStep = (int) (revealElapsed / (CREEPER_REVEAL_MS / CREEPER_REVEAL_STEPS)) + 1;
                revealStep = Math.min(revealStep, CREEPER_REVEAL_STEPS);
            }
            drawCreeperIcon(g, bx, by, revealStep, CREEPER_REVEAL_STEPS);
            deferredOverCreeper = overCreeper;
            if (overCreeper) {
                int pending = unclaimedCompletedQuests().size();
                deferredCreeperTip = (english ? "Completed" : "Completadas")
                    + (pending > 0 ? " (" + pending + (english ? " unclaimed)" : " sin reclamar)") : "");
                deferredCrX1 = crX1; deferredCrY2 = crY2;
                // el ancho puede empujar el tooltip fuera por la izquierda si es largo;
                // como este botón está pegado al borde izquierdo del libro, se ancla
                // por la izquierda (crX1) en vez de centrarlo para no cortarlo.
            }
            int hX1 = bx + TAB_HOME_X1, hX2 = bx + TAB_HOME_X2;
            int hY1 = by + TAB_HOME_Y1, hY2 = by + TAB_HOME_Y2;
            boolean overHome = mouseX >= hX1 && mouseX <= hX2 && mouseY >= hY1 && mouseY <= hY2;
            deferredOverHome = overHome;
            if (overHome) {
                deferredHomeTip = english ? "Main" : "Principal";
                deferredHX1 = hX1; deferredHY2 = hY2;
            }

            // Atajo a Bestiario (cabeza de wither skeleton) — mismo trato visual que
            // el creeper: resalte al pasar el mouse + tooltip diferido. Además, un
            // parpadeo mientras haya un mob nuevo sin ver: se redibuja la misma
            // silueta en blanco-amarillento encima (ver WITHER_ICON_GLOW) prendiendo
            // y apagando en el tiempo, así el brillo respeta el contorno real de la
            // cabeza en vez de iluminar el cuadro completo de la pestaña.
            int wiX1 = bx + TAB_WITHER_X1, wiX2 = bx + TAB_WITHER_X2;
            int wiY1 = by + TAB_WITHER_Y1, wiY2 = by + TAB_WITHER_Y2;
            boolean overWither = mouseX >= wiX1 && mouseX <= wiX2 && mouseY >= wiY1 && mouseY <= wiY2;
            if (overWither) {
                g.fill(wiX1, wiY1, wiX2, wiY2, 0x33FFFFFF);
            }
            boolean witherGlowing = !overWither && bestiaryHasNewUnlock && Math.sin(now / 250.0) > 0;
            drawWitherIcon(g, bx, by, witherGlowing);
            deferredOverWither = overWither;
            if (overWither) {
                deferredWitherTip = english ? "Bestiary" : "Bestiario";
                deferredWiX1 = wiX1; deferredWiY2 = wiY2;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;

        // Reset botones
        pinBtnY = 9999; claimBtnY = 9999; claimBtnQuestId = null;
        hideToggleY = 9999; claimAllBtnY = 9999;

        List<QuestEntry> quests;
        int offset, visibleEnd;
        if (selectedTab == TAB_MENU) {
            quests = List.of(); offset = 0; visibleEnd = 0;
            drawMenuTabContent(g, bx, by, mouseX, mouseY);
        } else {
        quests = currentTabQuests();
        offset = scrollOffset[selectedTab];
        visibleEnd = Math.min(quests.size(), offset + VISIBLE_ROWS);

        // ══ PÁGINA IZQUIERDA ══
        int lx = bx + PAGE_L_X;
        int ly = by + PAGE_L_Y + TITLE_TOP_PAD;

        // Scissor cubre TODA el área desde el borde rojo — título incluido
        g.enableScissor(bx + PAGE_L_X, by + PAGE_L_Y,
                        bx + PAGE_L_X + PAGE_L_W, by + PAGE_L_Y + PAGE_L_H);

        // Botón "Reclamar todo" — se calcula ACÁ (antes de dibujar el título) para
        // que el título pueda achicarse si hace falta y nunca lo tape. El botón es
        // SOLO el ícono (el texto "Reclamar todo" vive únicamente en el tooltip al
        // pasar el mouse) y ahora se muestra SIEMPRE en esta pestaña, tenga o no
        // recompensas pendientes — así la gente lo puede apretar "por las dudas"
        // para asegurarse de que no le quedó nada sin reclamar, en vez de tener que
        // adivinar por la ausencia del botón. Si no hay nada pendiente, apretarlo
        // no hace nada (ver handleClick), no rompe nada.
        int claimAllIconSize = 12;
        boolean showClaimAllBtn = selectedTab == TAB_COMPLETED;
        if (showClaimAllBtn) {
            claimAllBtnH = claimAllIconSize;
            claimAllBtnW = claimAllIconSize;
            claimAllBtnX = lx + PAGE_L_W - claimAllBtnW;
            claimAllBtnY = ly;
        } else {
            claimAllBtnY = 9999;
        }

        // Título tab — centrado en el ancho de la página (antes pegado a la
        // izquierda; con nombres cortos como "Main" dejaba mucho aire vacío a la
        // derecha en vez de aprovecharlo). Si el botón "Reclamar todo" está
        // visible en esta misma fila, se recorta el centrado para que el título
        // nunca invada su zona — mejor un título corrido a la izquierda que uno
        // superpuesto con el botón. Ahora que el botón es solo un ícono de 12px
        // esto casi nunca hace falta, pero se deja como red de seguridad por si
        // el título es muy largo en algún idioma.
        String tabTitle = "§0§l" + currentTabLabel();
        int tabTitleX = centeredTextX(mc, tabTitle, lx, PAGE_L_W);
        if (showClaimAllBtn) {
            int titleW = mc.font.width(tabTitle);
            int maxX = claimAllBtnX - 6 - titleW;
            if (tabTitleX > maxX) tabTitleX = Math.max(lx, maxX);
        }
        g.text(mc.font, Component.literal(tabTitle),
            tabTitleX, ly, ARGB.opaque(0x3D1A00), false);

        // Tooltips de estos 2 botones: se calculan acá pero se DIBUJAN diferido, al
        // final de extractRenderState (ver bloque "Tooltips diferidos" y las
        // variables deferredOverHideToggle/deferredOverClaimAll declaradas arriba
        // del todo, junto a las del creeper/casita/wither) — si se dibujaban acá
        // mismo, como la lista de misiones se pinta DESPUÉS en el método, el
        // tooltip quedaba por DEBAJO de la carita del creeper y el texto de la
        // primera fila (se veía cortado a la mitad, tapado). Dibujándolo al final
        // queda por encima de absolutamente todo el contenido de la página, como
        // corresponde a un tooltip.

        // Toggle "ocultar completadas" — solo en las 4 categorías. Ícono a la derecha
        // del título; el tooltip (con el mismo helper que las pestañas) explica qué hace,
        // así no hace falta texto pegado que no entraría en el ancho de la página.
        if (isCategoryTab(selectedTab)) {
            // 1px en diagonal abajo-izquierda respecto a la esquina del marco: pegado
            // tal cual (lx + PAGE_L_W - tamaño) quedaba tocando la línea roja del borde.
            hideToggleX = lx + PAGE_L_W - hideToggleSize - 1;
            hideToggleY = ly + 1;
            boolean overToggle = mouseX >= hideToggleX && mouseX <= hideToggleX + hideToggleSize
                               && mouseY >= hideToggleY && mouseY <= hideToggleY + hideToggleSize;
            drawIconButton(g, hideToggleX, hideToggleY, hideToggleSize,
                hideCompleted ? ICON_EYE_HIDE : ICON_EYE_SHOW, overToggle, "hideToggle");
            if (overToggle) {
                deferredOverHideToggle = true;
                deferredHideToggleTip = english ? "Hide completed" : "Ocultar completadas";
                int tw = mc.font.width(deferredHideToggleTip) + 4;
                deferredHideToggleTipX = hideToggleX + hideToggleSize - tw;
                deferredHideToggleTipY = hideToggleY + hideToggleSize + 2;
            }
        } else {
            hideToggleY = 9999;
        }

        // Dibuja el botón "Reclamar todo" con la geometría ya calculada arriba.
        // Solo el ícono, siempre visible en esta pestaña; el texto vive nada más en
        // el tooltip diferido (ver arriba). La sombra usa el overload de silueta
        // (ICON_CLAIM_ALL_SHADOW) en vez de un cuadrado relleno, para que siga el
        // contorno real del cofre y no se vea como un bloque gris de fondo.
        if (showClaimAllBtn) {
            boolean overClaimAll = mouseX >= claimAllBtnX && mouseX <= claimAllBtnX + claimAllBtnW
                                 && mouseY >= claimAllBtnY && mouseY <= claimAllBtnY + claimAllBtnH;
            drawIconButton(g, claimAllBtnX, claimAllBtnY, claimAllIconSize, ICON_CLAIM_ALL, ICON_CLAIM_ALL_SHADOW, overClaimAll, "claimAll");
            if (overClaimAll) {
                deferredOverClaimAll = true;
                int tw = mc.font.width(claimAllTip) + 4;
                deferredClaimAllTipX = claimAllBtnX + claimAllBtnW - tw;
                deferredClaimAllTipY = claimAllBtnY + claimAllBtnH + 2;
            }
        }
        ly += ROW_H;

        if (quests.isEmpty()) {
            String emptyMsg = selectedTab == TAB_COMPLETED
                ? (english ? "§8No completed quests yet..." : "§8Aún no completaste misiones...")
                : (english ? "§8Coming soon..." : "§8Próximamente...");
            g.text(mc.font, Component.literal(emptyMsg), lx, ly + 20, ARGB.opaque(0x555555), false);
        } else {
            for (int i = offset; i < visibleEnd; i++) {
                QuestEntry quest = quests.get(i);
                boolean done    = uuid != null && QuestData.get().isCompleted(uuid, quest.id());
                boolean sel     = i == selectedIndex;

                if (sel) g.fill(lx - 2, ly - 1, lx + PAGE_L_W - 2, ly + 12, 0x33A0600A);

                // Ticket de estado: antes era un glifo de texto (✔/○), ahora es la
                // carita del creeper — en blanco y negro mientras no está completada,
                // a color apenas se completa (mismo lugar y tamaño que el mark viejo).
                Identifier markTex = done ? QUEST_MARK_COLOR : QUEST_MARK_GRAY;
                g.pose().pushMatrix();
                g.pose().translate(lx, ly);
                g.pose().scale(QUEST_MARK_SIZE / (float) QUEST_MARK_W, QUEST_MARK_SIZE / (float) QUEST_MARK_H);
                g.blit(RenderPipelines.GUI_TEXTURED, markTex, 0, 0, 0f, 0f, QUEST_MARK_W, QUEST_MARK_H, QUEST_MARK_W, QUEST_MARK_H);
                g.pose().popMatrix();

                String label = "§0" + truncate(mc, quest.name(), PAGE_L_W - (QUEST_MARK_SIZE + 3));
                g.text(mc.font, Component.literal(label), lx + QUEST_MARK_SIZE + 2, ly, ARGB.opaque(0x3D1A00), false);
                ly += ROW_H;
            }
        }

        // Flechas y indicador en la fila reservada del fondo (no solapan con la lista)
        int bottomRowY = by + PAGE_L_Y + PAGE_L_H - ROW_H - BOTTOM_ROW_PAD;
        scrollBtnX     = bx + PAGE_L_X + PAGE_L_W - 10;
        // La flecha de abajo queda 1px en diagonal arriba-derecha respecto a la de
        // arriba: pegada tal cual al borde rojo de la página, se lo pisaba.
        scrollDownBtnX = scrollBtnX + 1;
        scrollUpBtnY   = by + PAGE_L_Y + TITLE_TOP_PAD + ROW_H;  // justo bajo el título (con el mismo padding que el título)
        scrollDownBtnY = bottomRowY - 1;               // fila reservada

        if (offset > 0) {
            boolean overUp = mouseX >= scrollBtnX && mouseX <= scrollBtnX + 10
                           && mouseY >= scrollUpBtnY && mouseY <= scrollUpBtnY + 9;
            drawIconButton(g, scrollBtnX, scrollUpBtnY, 9, ICON_SCROLL_UP, overUp, "scrollUp");
        }
        if (offset < maxScroll()) {
            boolean overDown = mouseX >= scrollDownBtnX && mouseX <= scrollDownBtnX + 10
                             && mouseY >= scrollDownBtnY && mouseY <= scrollDownBtnY + 9;
            drawIconButton(g, scrollDownBtnX, scrollDownBtnY, 9, ICON_SCROLL_DOWN, overDown, "scrollDown");
        }

        // Indicador en la fila reservada — antes era el rango de scroll ("3-13/17",
        // que no dice nada de un vistazo); ahora es una barra de progreso con el
        // conteo de la CATEGORÍA completa (no de la lista ya filtrada por el toggle
        // de ocultar completadas, si no el número quedaría siempre igual al total
        // visible y perdería sentido). Solo aplica a pestañas de categoría — en
        // Completadas se mantiene el rango de scroll de siempre, porque ahí
        // "completadas/total" no tiene el mismo significado.
        if (isCategoryTab(selectedTab)) {
            List<QuestEntry> allInTab = tabs.get(selectedTab).quests();
            int total = allInTab.size(), done = 0;
            if (uuid != null) {
                for (QuestEntry q : allInTab) if (QuestData.get().isCompleted(uuid, q.id())) done++;
            }
            drawCategoryProgressBar(g, lx, PAGE_L_W, bottomRowY, done, total);
        } else if (quests.size() > VISIBLE_ROWS) {
            String indicator = "§8" + (offset + 1) + "-" + visibleEnd + "/" + quests.size();
            g.text(mc.font, Component.literal(indicator),
                centeredTextX(mc, indicator, lx, PAGE_L_W), bottomRowY + 1, ARGB.opaque(0x555555), false);
        }

        g.disableScissor();

        // ══ PÁGINA DERECHA ══
        g.enableScissor(bx + PAGE_R_X, by + PAGE_R_Y,
                        bx + PAGE_R_X + PAGE_R_W, by + PAGE_R_Y + PAGE_R_H);

        if (selectedIndex >= 0 && selectedIndex < quests.size()) {
            QuestEntry q    = quests.get(selectedIndex);
            boolean done    = uuid != null && QuestData.get().isCompleted(uuid, q.id());
            boolean claimed = uuid != null && QuestData.get().isClaimed(uuid, q.id());
            boolean pinned  = uuid != null && q.id().equals(QuestData.get().getPinnedQuest(uuid));

            int rx = bx + PAGE_R_X;
            int ry = by + PAGE_R_Y + TITLE_TOP_PAD;

            // Nombre — truncar si es muy largo en vez de partir en 2 líneas
            String nombre = truncate(mc, q.name(), PAGE_R_W);
            String nombre2 = null;
            if (mc.font.width("§0§l" + q.name()) > PAGE_R_W) {
                int split = q.name().lastIndexOf(' ', q.name().length() / 2 + 6);
                if (split > 0) {
                    nombre  = q.name().substring(0, split);
                    nombre2 = q.name().substring(split + 1);
                }
            }
            g.text(mc.font, Component.literal("§0§l" + nombre), rx, ry, ARGB.opaque(0x3D1A00), false);
            ry += 10;
            if (nombre2 != null) {
                g.text(mc.font, Component.literal("§0§l" + nombre2), rx, ry, ARGB.opaque(0x3D1A00), false);
                ry += 10;
            }

            // Estado — los logros de referencia no los rastrea este mod (los
            // otorga el propio Minecraft), así que se muestra una etiqueta
            // informativa en vez de "En progreso/Completada".
            String status = q.isReference()
                ? (english ? "§9§oOfficial Minecraft Achievement" : "§9§oLogro oficial de Minecraft")
                : (english
                    ? ((done && claimed) ? "§7Claimed" : done ? "§2Completed!" : "§6In progress...")
                    : ((done && claimed) ? "§7Reclamada" : done ? "§2¡Completada!" : "§6En progreso..."));
            g.text(mc.font, Component.literal(status), rx, ry, ARGB.opaque(0x3D1A00), false);
            ry += 12;

            // Descripción — se une todo el texto y se reparte con salto de línea real,
            // usando el ancho completo permitido por el marco rojo (PAGE_R_W).
            // Antes cada línea predefinida se truncaba con "..." si no entraba; ahora
            // el texto completo siempre es legible, sin perder palabras.
            String fullDesc = String.join(" ", q.desc());
            for (String line : wrapText(mc, fullDesc, PAGE_R_W)) {
                g.text(mc.font, Component.literal("§8" + line),
                    rx, ry, ARGB.opaque(0x5C3317), false);
                ry += 10;
            }
            ry += 4;

            // Los logros de referencia terminan acá: no tienen recompensa de
            // este mod ni botones de Obtener/Fijar (Minecraft ya los rastrea
            // en su propia pantalla de logros, con la tecla L).
            if (!q.isReference()) {
                // Recompensa
                g.text(mc.font, Component.literal(english ? "§0§lReward:" : "§0§lRecompensa:"), rx, ry, ARGB.opaque(0x3D1A00), false);
                ry += 10;
                // Íconos reales de ítem junto a cada línea de recompensa, mismo criterio
                // que ya usa el Bestiario para su loot (Bestiary.itemIconTexture) —
                // reutiliza getRewardsFor(), la MISMA fuente que usa el servidor para
                // entregar la recompensa de verdad, así que nunca se puede desincronizar
                // mostrando un ícono que no es lo que en realidad se entrega.
                // Se dibuja solo si la cantidad de ItemStacks coincide 1 a 1 con la
                // cantidad de líneas de texto — si no coinciden (algún caso viejo con
                // texto escrito a mano que no calza con los stacks reales), se prefiere
                // no mostrar ningún ícono antes que mostrar uno en la línea equivocada.
                ItemStack[] rewardStacks = mc.level != null
                    ? HomeQuestMod.getRewardsFor(q.id(), mc.level.registryAccess())
                    : new ItemStack[0];
                boolean iconsMatch = rewardStacks.length == q.rewards().length;
                int iconPad = iconsMatch ? REWARD_ICON_PX + 3 : 0;
                for (int ri = 0; ri < q.rewards().length; ri++) {
                    String r = q.rewards()[ri];
                    if (iconsMatch && !rewardStacks[ri].isEmpty()) {
                        String itemId = BuiltInRegistries.ITEM.getKey(rewardStacks[ri].getItem()).getPath();
                        Identifier tex = Bestiary.itemIconTexture(itemId);
                        // blit no escala solo con width/height si no coincide con el
                        // tamaño nativo de la textura (recortaría en vez de reducir) —
                        // por eso se escala con la matriz, igual que ya hace
                        // drawIconButton en otro lado de este mismo archivo.
                        g.pose().pushMatrix();
                        g.pose().translate(rx, ry - 1);
                        g.pose().scale(REWARD_ICON_PX / (float) LOOT_ICON_PX, REWARD_ICON_PX / (float) LOOT_ICON_PX);
                        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f,
                            LOOT_ICON_PX, LOOT_ICON_PX, LOOT_ICON_PX, LOOT_ICON_PX);
                        g.pose().popMatrix();
                    }
                    g.text(mc.font, Component.literal("§8• " + truncate(mc, r, PAGE_R_W - 6 - iconPad)),
                        rx + iconPad, ry, ARGB.opaque(0x5C3317), false);
                    ry += 10;
                }
                ry += 4;

                // Botón Obtener
                if (done && !claimed) {
                    claimBtnX = rx; claimBtnY = ry; claimBtnQuestId = q.id();
                    g.fill(rx, ry, rx + claimBtnW, ry + claimBtnH, 0xCC1A4A8B);
                    g.fill(rx, ry, rx + claimBtnW, ry + 1, 0xFFFFFFFF);
                    String claimTxt = english ? "§f§l[ Claim ]" : "§f§l[ Obtener ]";
                    g.text(mc.font, Component.literal(claimTxt),
                        rx + (claimBtnW - mc.font.width(claimTxt)) / 2,
                        ry + 3, ARGB.opaque(0xFFFFFF), false);
                    ry += claimBtnH + 4;
                }

                // Botón Fijar
                if (!done) {
                    pinBtnX = rx; pinBtnY = ry;
                    g.fill(rx, ry, rx + pinBtnW, ry + pinBtnH, pinned ? 0xCC6B2200 : 0xCC1A5C1A);
                    g.fill(rx, ry, rx + pinBtnW, ry + 1, 0xFFFFFFFF);
                    String btnTxt = english
                        ? (pinned ? "[ Unpin from HUD ]" : "[ Pin to HUD ]")
                        : (pinned ? "[ Desfijar HUD ]" : "[ Fijar en HUD ]");
                    g.text(mc.font, Component.literal("§f" + btnTxt),
                        rx + (pinBtnW - mc.font.width("§f" + btnTxt)) / 2,
                        ry + 3, ARGB.opaque(0xFFFFFF), false);
                }
            }
        } else if (!quests.isEmpty()) {
            int rx = bx + PAGE_R_X;
            int ry = by + PAGE_R_Y + 30;
            if (english) {
                g.text(mc.font, Component.literal("§8<- Select"), rx, ry, ARGB.opaque(0x5C3317), false);
                g.text(mc.font, Component.literal("§8a quest"),    rx, ry + 11, ARGB.opaque(0x5C3317), false);
            } else {
                g.text(mc.font, Component.literal("§8<- Selecciona"), rx, ry, ARGB.opaque(0x5C3317), false);
                g.text(mc.font, Component.literal("§8una misión"),    rx, ry + 11, ARGB.opaque(0x5C3317), false);
            }
        }

        g.disableScissor();
        } // fin del else (selectedTab != TAB_MENU)

        // Botón de opciones (⚙) — esquina inferior derecha de la página derecha,
        // simétrico al contador de páginas de abajo a la izquierda. Coordenadas
        // ancladas a PAGE_R_X/PAGE_R_W/PAGE_R_Y/PAGE_R_H para no salirse nunca
        // del marco rojo de la página (mismo límite que usa el resto del texto
        // de esta página, ver PAGE_R_W más arriba).
        optionsBtnX = bx + PAGE_R_X + PAGE_R_W - optionsBtnW;
        optionsBtnY = by + PAGE_R_Y + PAGE_R_H - optionsBtnH;
        boolean overOptions = mouseX >= optionsBtnX && mouseX <= optionsBtnX + optionsBtnW
                            && mouseY >= optionsBtnY && mouseY <= optionsBtnY + optionsBtnH;
        drawIconButton(g, optionsBtnX + (optionsBtnW - optionsBtnH) / 2, optionsBtnY, optionsBtnH,
            ICON_GEAR, overOptions, "gear");
        if (overOptions) {
            drawTooltip(g, optionsBtnX, optionsBtnY - 12, english ? "Settings" : "Configuración");
        }

        // Botón de Menú (≡) — mismo estilo que ⚙, pegado a su izquierda. Selecciona
        // TAB_MENU, EXACTAMENTE igual que cualquier otro ícono de pestaña (dispara la
        // misma animación de vuelta de página, ver handleClick) — no abre una ventana.
        menuBtnY = optionsBtnY;
        menuBtnX = optionsBtnX - 2 - menuBtnW;
        boolean overMenu = mouseX >= menuBtnX && mouseX <= menuBtnX + menuBtnW
                         && mouseY >= menuBtnY && mouseY <= menuBtnY + menuBtnH;
        if (selectedTab == TAB_MENU) {
            // Página de Menú activa: el botón queda "hundido" de forma permanente
            // (no solo durante el bounce del click) para marcar el estado activo,
            // el mismo lenguaje visual que el resaltado de pestaña seleccionada.
            g.fill(menuBtnX, menuBtnY, menuBtnX + menuBtnW, menuBtnY + menuBtnH, 0x33000000);
        }
        drawIconButton(g, menuBtnX + (menuBtnW - menuBtnH) / 2, menuBtnY, menuBtnH,
            ICON_MENU, overMenu, "menu");
        if (overMenu && selectedTab != TAB_MENU) {
            drawTooltip(g, menuBtnX, menuBtnY - 12, english ? "Menu" : "Menú");
        }

        // Íconos animados de las 4 pestañas de categoría (Explorador/Minero/Constructor/Técnico).
        // Mismo resalte sutil de hover/selección que la pestaña de Logros, para que las 7
        // pestañas de la derecha se sientan consistentes entre sí.
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 0, 1, tabs.get(1).label(),
            COMPASS_FRAMES, COMPASS_FRAME_COUNT, COMPASS_FRAME_W, COMPASS_FRAME_H, COMPASS_FRAME_MS, 12);
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 1, 2, tabs.get(2).label(),
            PICKAXE_FRAMES, PICKAXE_FRAME_COUNT, PICKAXE_FRAME_W, PICKAXE_FRAME_H, PICKAXE_FRAME_MS, 12);
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 2, 3, tabs.get(3).label(),
            BLOCK_FRAMES, BLOCK_FRAME_COUNT, BLOCK_FRAME_W, BLOCK_FRAME_H, BLOCK_FRAME_MS, 12);
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 3, 4, tabs.get(4).label(),
            REDSTONE_FRAMES, REDSTONE_FRAME_COUNT, REDSTONE_FRAME_W, REDSTONE_FRAME_H, REDSTONE_FRAME_MS, 12);
        // Mago (libro encantado) — slot 4, pestaña real TAB_MAGO(6).
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 4, TAB_MAGO, tabs.get(TAB_MAGO).label(),
            ENCH_BOOK_FRAMES, ENCH_BOOK_FRAME_COUNT, ENCH_BOOK_FRAME_W, ENCH_BOOK_FRAME_H, ENCH_BOOK_FRAME_MS, 12);
        // Guerrero (hacha) — slot 6, pestaña real TAB_GUERRERO(7). Con el arte nuevo su
        // forma YA viene horneada en el fondo igual que las demás — antes NO estaba y
        // se dibujaba aparte con drawGuerreroTab()/GUERRERO_TAB_BG, que ya no hacen
        // falta y se eliminaron. GUERRERO_AXE es una imagen estática (no animada), así
        // que se pasa como array de un solo frame — drawAnimatedTabIcon lo soporta
        // igual (frameCount=1 → siempre dibuja ese único frame).
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 6, TAB_GUERRERO, tabs.get(TAB_GUERRERO).label(),
            new Identifier[]{ GUERRERO_AXE }, 1, GUERRERO_AXE_W, GUERRERO_AXE_H, 1000, 12);

        // Pestaña "Logros" — slot 5, la gris. La captura de referencia (build vieja)
        // confirma que esta pestaña SÍ mostraba la manzana animada (LOGO_FRAMES); en
        // algún momento se reemplazó por un tilde simple asumiendo por error que
        // LOGO_FRAMES era exclusivo de la fila "Main" del menú de Progreso. Se
        // restaura acá usando el mismo drawAnimatedTabIcon() que las otras 6 pestañas
        // para que el resaltado de hover/selección y el centrado del ícono sean
        // idénticos entre todas.
        drawAnimatedTabIcon(g, bx, by, mouseX, mouseY, 5, 5, tabs.get(5).label(),
            LOGO_FRAMES, LOGO_FRAME_COUNT, LOGO_FRAME_W, LOGO_FRAME_H, LOGO_FRAME_MS, 12);

        // ── Tooltips diferidos ──
        // Se dibujan acá, al final de todo, para quedar por ENCIMA del título/texto
        // del libro (antes se dibujaban apenas se pintaba el ícono, mucho antes que el
        // texto de la página, así que el texto terminaba tapándolos).
        if (deferredOverCreeper) {
            drawTooltip(g, deferredCrX1, deferredCrY2 + 2, deferredCreeperTip);
        }
        if (deferredOverHome) {
            drawTooltip(g, deferredHX1, deferredHY2 + 2, deferredHomeTip);
        }
        if (deferredOverWither) {
            drawTooltip(g, deferredWiX1, deferredWiY2 + 2, deferredWitherTip);
        }
        if (deferredOverHideToggle) {
            drawTooltip(g, deferredHideToggleTipX, deferredHideToggleTipY, deferredHideToggleTip);
        }
        if (deferredOverClaimAll) {
            drawTooltip(g, deferredClaimAllTipX, deferredClaimAllTipY, claimAllTip);
        }
        if (bestiaryLootTooltipShown) {
            drawItemTooltip(g, bestiaryLootTooltipX, bestiaryLootTooltipY, bestiaryLootTooltipName, bestiaryLootTooltipNote);
        }

        // Tutorial de primer uso — se dibuja último, encima de absolutamente todo
        // (incluidos tooltips), y handleClick() ya se encarga de que ningún click de
        // atrás llegue mientras esté activo.
        if (tutorialActive) {
            drawTutorialOverlay(g, bx, by);
        }
    }

    /**
     * 4 pasos fijos: bienvenida general, pestañas de categoría, Completadas +
     * atajo a Bestiario, y Menú/Configuración. Cada paso resalta con un marco
     * claro la zona de la que habla (o ninguno, para la bienvenida): el oscurecido
     * se dibuja en franjas alrededor del recuadro, así esa zona queda con su brillo
     * real en vez de solo tener un marco encima de un fondo igual de oscuro.
     */
    private void drawTutorialOverlay(GuiGraphicsExtractor g, int bx, int by) {
        Minecraft mc = Minecraft.getInstance();

        int hx1 = 0, hy1 = 0, hx2 = 0, hy2 = 0; // zona resaltada; todo en 0 = sin resaltado (bienvenida)
        String title, body;
        switch (tutorialStep) {
            case 1 -> {
                hx1 = bx + TAB_X1 - 2; hy1 = by + 6; hx2 = bx + TAB_X2 + 2; hy2 = by + BH - 26;
                title = english ? "Category tabs" : "Pestañas de categoría";
                body = english
                    ? "Each tab on the right is a set of quests — Explorer, Miner, Builder, and more."
                    : "Cada pestaña de la derecha es un grupo de misiones — Explorador, Minero, Constructor, y más.";
            }
            case 2 -> {
                hx1 = bx + TAB_CREEPER_X1 - 2; hy1 = by + TAB_CREEPER_Y1;
                hx2 = bx + TAB_WITHER_X2 + 2;  hy2 = by + TAB_CREEPER_Y2;
                title = english ? "Completed & Bestiary" : "Completadas y Bestiario";
                body = english
                    ? "The creeper shows your completed quests. The wither head is a shortcut to the Bestiary."
                    : "El creeper muestra tus misiones completadas. La cabeza de wither es un atajo al Bestiario.";
            }
            case 3 -> {
                hx1 = menuBtnX - 2; hy1 = menuBtnY - 2; hx2 = optionsBtnX + optionsBtnW + 2; hy2 = menuBtnY + menuBtnH + 2;
                title = english ? "Menu & Settings" : "Menú y Configuración";
                body = english
                    ? "From here: overall progress, the Bestiary, game stats, and language settings."
                    : "Desde aquí: progreso general, Bestiario, datos de la partida, y configuración de idioma.";
            }
            default -> {
                title = english ? "Welcome!" : "¡Bienvenido!";
                body = english
                    ? "This is your Quest Book. It tracks your objectives and lets you claim rewards."
                    : "Este es tu Libro de Misiones. Aquí sigues tus objetivos y reclamas recompensas.";
            }
        }

        // El oscurecido NO se dibuja como un solo rectángulo de pantalla completa:
        // pintar encima con alpha 0 no "borra" nada (blending normal, no-op), así
        // que la zona marcada seguía quedando igual de oscura que el resto con solo
        // el marco amarillo encima. En cambio, se dibuja el oscurecido en las 4
        // franjas ALREDEDOR del recuadro — así esa zona nunca se toca y se ve con
        // su brillo real.
        if (hx2 > hx1) {
            g.fill(0, 0, this.width, hy1, 0x99000000);             // arriba
            g.fill(0, hy2, this.width, this.height, 0x99000000);   // abajo
            g.fill(0, hy1, hx1, hy2, 0x99000000);                  // izquierda
            g.fill(hx2, hy1, this.width, hy2, 0x99000000);         // derecha
            g.fill(hx1, hy1, hx2, hy1 + 1, 0xFFFFE082);
            g.fill(hx1, hy2 - 1, hx2, hy2, 0xFFFFE082);
            g.fill(hx1, hy1, hx1 + 1, hy2, 0xFFFFE082);
            g.fill(hx2 - 1, hy1, hx2, hy2, 0xFFFFE082);
        } else {
            g.fill(0, 0, this.width, this.height, 0x99000000);
        }

        // Panel de texto, centrado en pantalla. Preferimos bien abajo del libro para
        // no tapar nunca la zona resaltada, pero SIEMPRE con clamp contra el alto de
        // pantalla real — "by + BH + 10" sin límite se iba abajo del todo en
        // resoluciones más chicas o con GUI Scale alto, dejando el panel (con los
        // botones Siguiente/Saltar adentro) fuera de la pantalla por completo.
        int panelW = 220, panelH = 54;
        int px = Math.max(4, Math.min(this.width / 2 - panelW / 2, this.width - panelW - 4));
        int py = Math.min(by + BH + 10, this.height - panelH - 4);
        g.fill(px, py, px + panelW, py + panelH, 0xEE1F1710);
        g.fill(px, py, px + panelW, py + 1, 0xFF8B7355);
        g.fill(px, py + panelH - 1, px + panelW, py + panelH, 0xFF8B7355);
        g.fill(px, py, px + 1, py + panelH, 0xFF8B7355);
        g.fill(px + panelW - 1, py, px + panelW, py + panelH, 0xFF8B7355);

        g.text(mc.font, Component.literal("§f§l" + title), px + 8, py + 6, ARGB.opaque(0xFFFFFF), false);
        int ty = py + 18;
        for (String line : wrapText(mc, body, panelW - 16)) {
            g.text(mc.font, Component.literal("§7" + line), px + 8, ty, ARGB.opaque(0xCCCCCC), false);
            ty += mc.font.lineHeight + 1;
        }

        String stepLabel = (tutorialStep + 1) + "/" + TUTORIAL_STEPS;
        g.text(mc.font, Component.literal("§8" + stepLabel), px + 8, py + panelH - 10, ARGB.opaque(0x8A8A8A), false);

        String skipLabel = english ? "Skip" : "Saltar";
        String nextLabel = tutorialStep >= TUTORIAL_STEPS - 1 ? (english ? "Got it!" : "¡Listo!") : (english ? "Next >" : "Siguiente >");
        tutorialSkipW = mc.font.width(skipLabel);
        tutorialSkipX = px + panelW - tutorialSkipW - 8 - mc.font.width(nextLabel) - 12;
        tutorialSkipY = py + panelH - 10;
        tutorialNextW = mc.font.width(nextLabel);
        tutorialNextX = px + panelW - tutorialNextW - 8;
        tutorialNextY = tutorialSkipY;
        g.text(mc.font, Component.literal("§7" + skipLabel), tutorialSkipX, tutorialSkipY, ARGB.opaque(0xAAAAAA), false);
        g.text(mc.font, Component.literal("§a" + nextLabel), tutorialNextX, tutorialNextY, ARGB.opaque(0x55FF55), false);
    }

    private int tutorialSkipX, tutorialSkipY, tutorialSkipW, tutorialNextX, tutorialNextY, tutorialNextW;

    private void handleTutorialClick(double mx, double my) {
        int rowH = Minecraft.getInstance().font.lineHeight;
        if (mx >= tutorialNextX - 2 && mx <= tutorialNextX + tutorialNextW + 2
                && my >= tutorialNextY - 2 && my <= tutorialNextY + rowH + 2) {
            playClickSound();
            if (tutorialStep >= TUTORIAL_STEPS - 1) {
                finishTutorial();
            } else {
                tutorialStep++;
            }
            return;
        }
        if (mx >= tutorialSkipX - 2 && mx <= tutorialSkipX + tutorialSkipW + 2
                && my >= tutorialSkipY - 2 && my <= tutorialSkipY + rowH + 2) {
            playClickSound();
            finishTutorial();
        }
    }

    private void finishTutorial() {
        tutorialActive = false;
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        if (uuid != null) QuestData.get().setTutorialSeen(uuid, true);
        ClientPlayNetworking.send(new TutorialSeenPayload());
    }

    /**
     * Contenido de TAB_MENU: usa las MISMAS páginas del libro que cualquier otra
     * pestaña (PAGE_L/PAGE_R, mismo scissor, mismo texto tinta-sobre-pergamino, sin
     * fondo propio) — no es una ventana aparte. menuSubPage decide qué se ve:
     * null = listado de secciones, "progress" = Progreso, "bestiary" = Bestiario.
     */
    private void drawMenuTabContent(GuiGraphicsExtractor g, int bx, int by, int mouseX, int mouseY) {
        if (menuSubPage == null) {
            drawMenuChooser(g, bx, by, mouseX, mouseY);
        } else if (menuSubPage.equals("progress")) {
            drawProgressSubpage(g, bx, by);
        } else if (menuSubPage.equals("bestiary")) {
            drawBestiarySubpage(g, bx, by, mouseX, mouseY);
        } else if (menuSubPage.equals("stats")) {
            drawStatsSubpage(g, bx, by);
        }
    }

    /** Listado inicial de TAB_MENU: "Progreso" / "Bestiario" / lugar para lo que se sume. */
    private void drawMenuChooser(GuiGraphicsExtractor g, int bx, int by, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int lx = bx + PAGE_L_X, ly = by + PAGE_L_Y + TITLE_TOP_PAD;

        g.enableScissor(bx + PAGE_L_X, by + PAGE_L_Y, bx + PAGE_L_X + PAGE_L_W, by + PAGE_L_Y + PAGE_L_H);
        String menuTitle = "§0§l" + (english ? "Menu" : "Menú");
        g.text(mc.font, Component.literal(menuTitle), centeredTextX(mc, menuTitle, lx, PAGE_L_W), ly, ARGB.opaque(0x3D1A00), false);
        ly += ROW_H;

        menuRowX = lx; menuRowW = PAGE_L_W;
        menuRowProgressY = ly;
        drawMenuChooserRow(g, lx, menuRowProgressY, PAGE_L_W, menuRowH, english ? "Progress" : "Progreso", mouseX, mouseY);
        ly += menuRowH + 3;

        menuRowBestiaryY = ly;
        drawMenuChooserRow(g, lx, menuRowBestiaryY, PAGE_L_W, menuRowH, english ? "Bestiary" : "Bestiario", mouseX, mouseY);
        ly += menuRowH + 3;

        menuRowStatsY = ly;
        drawMenuChooserRow(g, lx, menuRowStatsY, PAGE_L_W, menuRowH, english ? "Game Stats" : "Datos de la Partida", mouseX, mouseY);
        ly += menuRowH + 10;

        g.text(mc.font, Component.literal("§8" + (english ? "More coming soon..." : "Más próximamente...")),
            lx, ly, ARGB.opaque(0x8A6A4A), false);
        g.disableScissor();

        g.enableScissor(bx + PAGE_R_X, by + PAGE_R_Y, bx + PAGE_R_X + PAGE_R_W, by + PAGE_R_Y + PAGE_R_H);
        int rx = bx + PAGE_R_X, ry = by + PAGE_R_Y + 30;
        for (String line : wrapText(mc, english ? "Choose a section" : "Elegí una sección", PAGE_R_W)) {
            g.text(mc.font, Component.literal("§8" + line), rx, ry, ARGB.opaque(0x5C3317), false);
            ry += 10;
        }
        g.disableScissor();
    }

    /** Una fila clickeable del listado — mismo resalte que una fila de misión seleccionada. */
    private void drawMenuChooserRow(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean over = mouseX >= x - 2 && mouseX <= x + w - 2 && mouseY >= y - 1 && mouseY <= y + h;
        if (over) g.fill(x - 2, y - 1, x + w - 2, y + h, 0x33A0600A);
        g.text(mc.font, Component.literal((over ? "§0§l" : "§0") + "▸ " + label), x, y, ARGB.opaque(0x3D1A00), false);
    }

    /** Link "< Menú" para volver del sub-contenido al listado, arriba de la página izquierda. */
    private void drawBackLink(GuiGraphicsExtractor g, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        String label = "§0« " + (english ? "Menu" : "Menú");
        menuBackX = x; menuBackY = y; menuBackW = mc.font.width(label);
        g.text(mc.font, Component.literal(label), x, y, ARGB.opaque(0x5C3317), false);
    }

    /**
     * Progreso: una fila por categoría — ícono + nombre en su color, la barra debajo
     * ocupando el ancho, con el % a su derecha. 4 categorías en cada página. Reusa
     * tab.quests() y QuestData.get().isCompleted, los mismos datos que ya alimentan
     * el contador "3/8" de cada pestaña, así que el número siempre coincide.
     */
    private void drawProgressSubpage(GuiGraphicsExtractor g, int bx, int by) {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        // Antes usaba TITLE_TOP_PAD (7px) también acá, pero ese padding grande es para
        // texto en NEGRITA — el link "« Menú" es texto normal y queda con más aire del
        // pedido. Usa BACK_LINK_TOP_PAD (bien pegado arriba, como en el mockup) y el
        // título en negrita de abajo ya queda con sobra de espacio (BACK_LINK_TOP_PAD +
        // ROW_H ≈ 14px) sin necesitar su propio padding extra.
        int lx = bx + PAGE_L_X, ly = by + PAGE_L_Y + BACK_LINK_TOP_PAD;

        g.enableScissor(bx + PAGE_L_X, by + PAGE_L_Y, bx + PAGE_L_X + PAGE_L_W, by + PAGE_L_Y + PAGE_L_H);
        drawBackLink(g, lx, ly);
        ly += ROW_H;
        String progressTitle = "§0§l" + (english ? "Progress" : "Progreso");
        g.text(mc.font, Component.literal(progressTitle), centeredTextX(mc, progressTitle, lx, PAGE_L_W), ly, ARGB.opaque(0x3D1A00), false);
        ly += ROW_H + 4;

        // Orden de exhibición: igual que las pestañas, pero con Logros al final (no
        // en su posición real en `tabs`, índice 5) — así lo pidieron explícitamente.
        int[] order = { 0, 1, 2, 3, 4, TAB_MAGO, TAB_GUERRERO, 5 };
        for (int i = 0; i < 4; i++) {
            ly = drawProgressRow(g, lx, ly, PAGE_L_W, order[i], uuid);
        }
        g.disableScissor();

        g.enableScissor(bx + PAGE_R_X, by + PAGE_R_Y, bx + PAGE_R_X + PAGE_R_W, by + PAGE_R_Y + PAGE_R_H);
        int rx = bx + PAGE_R_X, ry = by + PAGE_R_Y + TITLE_TOP_PAD;
        for (int i = 4; i < 8; i++) {
            ry = drawProgressRow(g, rx, ry, PAGE_R_W, order[i], uuid);
        }
        g.disableScissor();
    }

    /**
     * "Datos de la Partida": monstruos eliminados / bloques destruidos / bloques
     * colocados. Los primeros 2 son estadísticas propias de Minecraft (no un
     * contador del mod) leídas bajo demanda en el servidor — ver
     * RequestGameStatsPayload/GameStatsPayload — así que acá solo se muestra lo
     * último que llegó en GameStatsClient. El tercero (bloques colocados) sí es un
     * contador propio, porque Minecraft no trackea un total de eso.
     */
    private void drawStatsSubpage(GuiGraphicsExtractor g, int bx, int by) {
        Minecraft mc = Minecraft.getInstance();
        int lx = bx + PAGE_L_X, ly = by + PAGE_L_Y + BACK_LINK_TOP_PAD;

        g.enableScissor(bx + PAGE_L_X, by + PAGE_L_Y, bx + PAGE_L_X + PAGE_L_W, by + PAGE_L_Y + PAGE_L_H);
        drawBackLink(g, lx, ly);
        ly += ROW_H;
        String title = "§0§l" + (english ? "Game Stats" : "Datos de la Partida");
        g.text(mc.font, Component.literal(title), centeredTextX(mc, title, lx, PAGE_L_W), ly, ARGB.opaque(0x3D1A00), false);
        ly += ROW_H + 8;

        String loading = english ? "Loading..." : "Cargando...";
        String mobsLabel  = english ? "Monsters killed" : "Monstruos eliminados";
        String minedLabel = english ? "Blocks broken"   : "Bloques destruidos";
        String placedLabel = english ? "Blocks placed"  : "Bloques colocados";

        if (!GameStatsClient.hasData()) {
            g.text(mc.font, Component.literal("§8" + loading), lx, ly, ARGB.opaque(0x8A6A4A), false);
        } else {
            ly = drawStatRow(g, lx, ly, PAGE_L_W, mobsLabel, GameStatsClient.mobKills());
            ly = drawStatRow(g, lx, ly, PAGE_L_W, minedLabel, GameStatsClient.blocksMined());
            ly = drawStatRow(g, lx, ly, PAGE_L_W, placedLabel, GameStatsClient.blocksPlaced());
        }
        g.disableScissor();
    }

    /** Una fila de "Datos de la Partida": nombre a la izquierda, número a la derecha. Devuelve el próximo Y libre. */
    private int drawStatRow(GuiGraphicsExtractor g, int x, int y, int w, String label, long value) {
        Minecraft mc = Minecraft.getInstance();
        g.text(mc.font, Component.literal("§0" + label), x, y, ARGB.opaque(0x3D1A00), false);
        String valStr = String.format("%,d", value);
        g.text(mc.font, Component.literal("§0§l" + valStr), x + w - mc.font.width(valStr), y, ARGB.opaque(0x1F1710), false);
        return y + ROW_H + 4;
    }

    /** Color de exhibición de cada categoría en la página de Progreso (índice en `tabs`, o 5=Logros). */
    private int progressColor(int tabIndex) {
        return switch (tabIndex) {
            case 0 -> 0xFFE04B4B; // Principal — rojo
            case 1 -> 0xFF4CAF50; // Explorador — verde
            case 2 -> 0xFFD9A441; // Minero — dorado
            case 3 -> 0xFF9B59B6; // Constructor — violeta
            case 4 -> 0xFF36C6D9; // Técnico — cyan
            case 5 -> 0xFFAAAAAA; // Logros — gris
            case 6 -> 0xFFE066B0; // Mago — rosa
            case 7 -> 0xFF3B6FB0; // Guerrero — azul (igual que la pestaña nueva)
            default -> 0xFFFFFFFF;
        };
    }

    /** Una fila de la página de Progreso: ícono + nombre en color, y la barra con el % debajo. Devuelve el próximo Y libre. */
    private int drawProgressRow(GuiGraphicsExtractor g, int x, int y, int w, int tabIndex, UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        List<QuestEntry> qs = tabs.get(tabIndex).quests();
        int total = qs.size(), done = 0;
        if (uuid != null) for (QuestEntry q : qs) if (QuestData.get().isCompleted(uuid, q.id())) done++;

        int iconBox = 14;
        int color = progressColor(tabIndex);
        String label = tabIndex == 5 ? (english ? "Minecraft Achievements" : "Logros de Minecraft") : tabs.get(tabIndex).label();

        drawProgressRowIcon(g, x, y, iconBox, tabIndex);
        g.text(mc.font, Component.literal("§l" + truncate(mc, label, w - iconBox - 3)),
            x + iconBox + 3, y + 2, ARGB.opaque(color), false);
        // Antes avanzaba solo 11px, pero el ícono mide 14px de alto → los últimos 3px
        // del ícono quedaban pisando la barra de progreso de abajo. Se avanza el alto
        // real del ícono (+1px de aire) para que la barra empiece justo debajo.
        y += iconBox + 1;

        String pct = (total == 0 ? 0 : Math.round(100f * done / total)) + "%";
        int pctW = mc.font.width(pct);
        int barW = w - pctW - 4;
        drawProgressBar(g, x, y, barW, 6, total == 0 ? 0f : done / (float) total);
        g.text(mc.font, Component.literal("§7" + pct), x + barW + 4, y - 1, ARGB.opaque(0xAAAAAA), false);
        y += 12;
        return y;
    }

    /** Ícono animado (el mismo gif de la pestaña real) al lado del nombre de cada categoría. */
    private void drawProgressRowIcon(GuiGraphicsExtractor g, int x, int y, int box, int tabIndex) {
        switch (tabIndex) {
            // Antes acá (Principal) se dibujaba LOGO_FRAMES (la manzana dorada) —
            // ese es el logo de LOGROS (ver el comentario en su declaración: "para la
            // pestaña Logros"), copiado/pegado al caso equivocado. Principal no tiene
            // ícono animado propio (no vive en la barra lateral, ver drawAnimatedTabIcon
            // más arriba — no hay slot para el tab 0), así que usa el mismo recurso
            // liviano que ya usa Logros más abajo: un glifo de texto, en este caso una
            // casita (§ Unicode U+2302), mismo patrón que el tilde "✓" de Logros.
            case 0 -> {
                Minecraft mc = Minecraft.getInstance();
                g.text(mc.font, Component.literal("§c⌂"), x + box / 2 - 3, y + box / 2 - 4, ARGB.opaque(0xB05A2B), false);
            }
            case 1 -> drawSimpleIcon(g, x, y, box, COMPASS_FRAMES, COMPASS_FRAME_COUNT, COMPASS_FRAME_W, COMPASS_FRAME_H, COMPASS_FRAME_MS);
            case 2 -> drawSimpleIcon(g, x, y, box, PICKAXE_FRAMES, PICKAXE_FRAME_COUNT, PICKAXE_FRAME_W, PICKAXE_FRAME_H, PICKAXE_FRAME_MS);
            case 3 -> drawSimpleIcon(g, x, y, box, BLOCK_FRAMES, BLOCK_FRAME_COUNT, BLOCK_FRAME_W, BLOCK_FRAME_H, BLOCK_FRAME_MS);
            case 4 -> drawSimpleIcon(g, x, y, box, REDSTONE_FRAMES, REDSTONE_FRAME_COUNT, REDSTONE_FRAME_W, REDSTONE_FRAME_H, REDSTONE_FRAME_MS);
            case 6 -> drawSimpleIcon(g, x, y, box, ENCH_BOOK_FRAMES, ENCH_BOOK_FRAME_COUNT, ENCH_BOOK_FRAME_W, ENCH_BOOK_FRAME_H, ENCH_BOOK_FRAME_MS);
            case 7 -> drawSimpleIcon(g, x, y, box, new Identifier[]{ GUERRERO_AXE }, 1, GUERRERO_AXE_W, GUERRERO_AXE_H, 1000);
            case 5 -> // Logros SÍ tiene ícono propio: la manzana dorada animada
                      // (LOGO_FRAMES) — antes estaba mal puesta en el caso 0 (Principal)
                      // por error de copy-paste; acá es donde realmente corresponde.
                drawSimpleIcon(g, x, y, box, LOGO_FRAMES, LOGO_FRAME_COUNT, LOGO_FRAME_W, LOGO_FRAME_H, LOGO_FRAME_MS);
        }
    }

    /** Ícono chico animado (o estático si frameCount==1) sin lógica de hover — usado en Progreso. */
    private void drawSimpleIcon(GuiGraphicsExtractor g, int x, int y, int iconH,
            Identifier[] frames, int frameCount, int frameW, int frameH, int frameMs) {
        int idx = frameCount <= 1 ? 0 : (int) ((System.currentTimeMillis() / frameMs) % frameCount);
        Identifier tex = frames[idx];
        int iconW = Math.round(iconH * (frameW / (float) frameH));
        g.pose().pushMatrix();
        g.pose().translate(x + (iconH - iconW) / 2f, y);
        g.pose().scale(iconW / (float) frameW, iconH / (float) frameH);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, frameW, frameH, frameW, frameH);
        g.pose().popMatrix();
    }

    /** Barra de progreso simple: fondo gris + relleno verde proporcional a `frac` (0-1). Siempre verde, sin variante dorada. */
    private void drawProgressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac) {
        frac = Math.max(0f, Math.min(1f, frac));
        g.fill(x, y, x + w, y + h, 0x992B2B2B);
        int fillW = (int) (w * frac);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, 0xFF4CAF50);
        }
    }

    /**
     * Bestiario: página izquierda = LISTA scrolleable (mismo sistema que la lista de
     * misiones: scrollOffset[TAB_MENU], VISIBLE_ROWS, flechas ▲▼) con "Enemigos" y
     * "Jefes" como encabezados dentro de la lista; página derecha = detalle del mob
     * seleccionado, igual que el detalle de una misión. El estado "matado" se lee del
     * mismo contador de kills que ya usan las quests de Guerrero (QuestSavedData#mobKills,
     * sincronizado al cliente), no de las estadísticas vanilla — así se revela apenas
     * se mata UN mob, sin depender de completar ninguna quest. Sin matar: silueta negra + "????". Matado: gif animado a color + nombre
     * real — en ambas páginas. Mobs sin arte todavía (Bestiary.Entry.hasArt()==false)
     * caen en un contorno con "?", ver drawBestiaryIcon.
     */
    private void drawBestiarySubpage(GuiGraphicsExtractor g, int bx, int by, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        UUID uuid = player != null ? player.getUUID() : null;
        int lx = bx + PAGE_L_X, ly = by + PAGE_L_Y + BACK_LINK_TOP_PAD;

        g.enableScissor(bx + PAGE_L_X, by + PAGE_L_Y, bx + PAGE_L_X + PAGE_L_W, by + PAGE_L_Y + PAGE_L_H);
        drawBackLink(g, lx, ly);
        ly += ROW_H;
        String bestiaryTitle = "§0§l" + (english ? "Bestiary" : "Bestiario");
        g.text(mc.font, Component.literal(bestiaryTitle), centeredTextX(mc, bestiaryTitle, lx, PAGE_L_W), ly, ARGB.opaque(0x3D1A00), false);
        ly += ROW_H;

        // ── 3 pestañas clickeables: Comunes / Especiales / Jefes ──
        // Cambian qué lista se ve, DENTRO de esta misma página — no son pestañas del
        // libro, son internas del Bestiario.
        // Los 2 intentos anteriores (padding fijo, después 0.9x fijo) seguían sin
        // entrar porque el ancho real de "mc.font.width(...)" nunca se midió — se
        // adivinó a ojo. Ahora se mide el ancho real con la fuente del juego y se
        // calcula la escala NECESARIA para que las 3 quepan en PAGE_L_W, en vez de
        // adivinar un número fijo — así no vuelve a pasar en otro idioma o si cambian
        // las etiquetas.
        String[] catLabels = english
            ? new String[]{ "Common", "Special", "Bosses" }
            : new String[]{ "Comunes", "Especiales", "Jefes" };
        int pad = 4, gap = 2;
        int rawTotal = pad * catLabels.length + gap * (catLabels.length - 1);
        // OJO: medir con "§l" (negrita) para las 3, no con el texto plano. La pestaña
        // seleccionada se dibuja en negrita más abajo (sel ? "§0§l" : "§8") y la
        // negrita es más ancha — medir en plano subestimaba el ancho real de la
        // seleccionada, así que la escala calculada no achicaba lo suficiente y esa
        // pestaña (la resaltada, la que en la práctica casi siempre importa) se salía
        // igual ("Bosses" cortado en el screenshot). Midiendo negrita para las 3 se
        // cubre el peor caso sea cual sea la que esté seleccionada en cada momento.
        for (String lbl : catLabels) rawTotal += mc.font.width("§l" + lbl);
        float tabScale = rawTotal > PAGE_L_W ? (float) PAGE_L_W / rawTotal : 1.0f;
        bestiaryTabY = ly;
        int tabX = lx;
        for (int c = 0; c < 3; c++) {
            String lbl = catLabels[c];
            int w = Math.round(mc.font.width(lbl) * tabScale) + pad;
            boolean sel = bestiaryCategory == c;
            if (sel) g.fill(tabX, bestiaryTabY - 1, tabX + w, bestiaryTabY + 9, 0x55A0600A);
            g.pose().pushMatrix();
            g.pose().translate(tabX + 2, bestiaryTabY);
            g.pose().scale(tabScale, tabScale);
            g.text(mc.font, Component.literal((sel ? "§0§l" : "§8") + lbl), 0, 0, ARGB.opaque(sel ? 0x3D1A00 : 0x8A6A4A), false);
            g.pose().popMatrix();
            bestiaryTabX[c] = tabX;
            bestiaryTabW[c] = w;
            tabX += w + gap;
        }
        ly += 12;


        List<Bestiary.Entry> rows;
        if (bestiaryOrderBuiltForCategory == bestiaryCategory) {
            rows = bestiaryOrder; // ya está armada para esta categoría, no hay que tocar nada
        } else {
            rows = buildBestiaryRows();
            bestiaryOrder.clear();
            bestiaryOrder.addAll(rows);
            bestiaryOrderBuiltForCategory = bestiaryCategory;
        }

        int offset = scrollOffset[TAB_MENU];
        int visibleEnd = Math.min(rows.size(), offset + BESTIARY_VISIBLE_ROWS);
        // índice dentro de bestiaryOrder (== rows, ya no hay headers que saltar) del
        // primer mob visible
        int entryIdx = offset;

        int rowY = ly;
        int iconSize = BESTIARY_ICON_SIZE;
        for (int i = offset; i < visibleEnd; i++) {
            Bestiary.Entry e = rows.get(i);
            boolean killed = Bestiary.isKilled(e, uuid);
            boolean sel = entryIdx == selectedBestiaryIndex;
            if (sel) g.fill(lx - 2, rowY - 1, lx + PAGE_L_W - 2, rowY + BESTIARY_ROW_H - 2, 0x33A0600A);

            drawBestiaryIcon(g, lx, rowY, iconSize, BESTIARY_ICON_COL_W, e, killed);
            String label = killed ? e.name(english) : "????";
            // Antes truncate(...) cortaba a "Elder Gu..." — la columna de texto (51px,
            // PAGE_L_W - BESTIARY_ICON_COL_W - 4) es angosta porque el ícono es grande
            // a propósito, pero la fila es bien alta (BESTIARY_ROW_H=63px), así que
            // sobra lugar de sobra para 2 líneas en vez de perder texto con "...".
            List<String> nameLines = wrapText(mc, label, PAGE_L_W - BESTIARY_ICON_COL_W - 4);
            int nameLineH = 10;
            int nameTextY = rowY + BESTIARY_ROW_H / 2 - (nameLines.size() * nameLineH) / 2;
            for (String line : nameLines) {
                g.text(mc.font, Component.literal("§0" + line),
                    lx + BESTIARY_ICON_COL_W + 4, nameTextY, ARGB.opaque(0x3D1A00), false);
                nameTextY += nameLineH;
            }
            entryIdx++;
            rowY += BESTIARY_ROW_H;
        }

        // Flechas de scroll — mismo patrón que la lista de misiones, PERO ancladas
        // justo después de la última fila realmente dibujada (rowY), no a una posición
        // fija basada en PAGE_L_H. Con la posición fija, como las filas del bestiario
        // son más altas (26px) que las de misiones, sobraba un hueco grande y vacío
        // entre la última fila visible y la flecha/el borde inferior — "muy comprimido,
        // no llega hasta abajo". Ahora la flecha ▼ sigue al contenido real.
        scrollBtnX     = bx + PAGE_L_X + PAGE_L_W - 10;
        scrollDownBtnX = scrollBtnX + 1;
        // Antes esto coincidía exactamente con la fila de pestañas Comunes/Especiales/
        // Jefes (bestiaryTabY), así que la flecha ▲ quedaba dibujada encima de "Jefes"
        // tapándolo. Se sube a la fila del título "Bestiario" en cambio, que tiene
        // todo el lado derecho libre (sin ese riesgo de choque con las 3 pestañas).
        scrollUpBtnY   = by + PAGE_L_Y + BACK_LINK_TOP_PAD + ROW_H;
        scrollDownBtnY = Math.min(rowY + 2, by + PAGE_L_Y + PAGE_L_H - ROW_H - BOTTOM_ROW_PAD) - 1;
        if (offset > 0) {
            boolean overUp = mouseX >= scrollBtnX && mouseX <= scrollBtnX + 10
                           && mouseY >= scrollUpBtnY && mouseY <= scrollUpBtnY + 9;
            drawIconButton(g, scrollBtnX, scrollUpBtnY, 9, ICON_SCROLL_UP, overUp, "scrollUp");
        }
        if (offset < Math.max(0, rows.size() - BESTIARY_VISIBLE_ROWS)) {
            boolean overDown = mouseX >= scrollDownBtnX && mouseX <= scrollDownBtnX + 10
                             && mouseY >= scrollDownBtnY && mouseY <= scrollDownBtnY + 9;
            drawIconButton(g, scrollDownBtnX, scrollDownBtnY, 9, ICON_SCROLL_DOWN, overDown, "scrollDown");
        }
        g.disableScissor();

        // ── Página derecha: detalle del mob seleccionado ──
        // OJO: la ilustración (useIllustration) se dibuja con PAGE_R_BORDER (sin
        // padding, calzada al marco real — ver drawBestiaryIllustratedDetail) que es
        // más grande que PAGE_R normal. Si el scissor se queda en PAGE_R (chico), corta
        // el costado derecho y la parte de abajo del dibujo. Se decide el scissor ANTES
        // de saber si hay ilustración, así que se calcula esa condición primero.
        boolean willUseIllustration = false;
        if (selectedBestiaryIndex >= 0 && selectedBestiaryIndex < bestiaryOrder.size()) {
            Bestiary.Entry pe = bestiaryOrder.get(selectedBestiaryIndex);
            willUseIllustration = Bestiary.isKilled(pe, uuid) && Bestiary.hasDetailImage(pe);
        }
        if (willUseIllustration) {
            g.enableScissor(bx + PAGE_R_BORDER_X, by + PAGE_R_BORDER_Y,
                bx + PAGE_R_BORDER_X + PAGE_R_BORDER_W, by + PAGE_R_BORDER_Y + PAGE_R_BORDER_H);
        } else {
            g.enableScissor(bx + PAGE_R_X, by + PAGE_R_Y, bx + PAGE_R_X + PAGE_R_W, by + PAGE_R_Y + PAGE_R_H);
        }
        int rx = bx + PAGE_R_X, ry = by + PAGE_R_Y + TITLE_TOP_PAD;
        if (selectedBestiaryIndex >= 0 && selectedBestiaryIndex < bestiaryOrder.size()) {
            Bestiary.Entry e = bestiaryOrder.get(selectedBestiaryIndex);
            boolean killed = Bestiary.isKilled(e, uuid);
            boolean useIllustration = killed && Bestiary.hasDetailImage(e);

            if (useIllustration) {
                drawBestiaryIllustratedDetail(g, bx, by, e, mouseX, mouseY);
            } else {
                // Sin ilustración propia todavía: el ícono grande animado de siempre,
                // arriba a la derecha, con el nombre y la fecha angostos a su izquierda.
                int iconBig = 34;
                drawBestiaryIcon(g, rx + PAGE_R_W - iconBig, ry, iconBig, e, killed);
                int textColW = PAGE_R_W - iconBig - 4;
                String nameLine = killed ? e.name(english) : "????";
                g.text(mc.font, Component.literal("§0§l" + nameLine), rx, ry, ARGB.opaque(0x3D1A00), false);
                ry += 10;
                if (killed) {
                    for (String line : wrapText(mc, (english ? "First appearance: " : "Primera Aparición: ") + e.addedVersion(), textColW)) {
                        g.text(mc.font, Component.literal("§8" + line), rx, ry, ARGB.opaque(0x5C3317), false);
                        ry += 10;
                    }
                }
                ry = Math.max(ry, by + PAGE_R_Y + iconBig) + 4;

                if (killed) {
                    for (String line : wrapText(mc, e.desc(english), PAGE_R_W)) {
                        g.text(mc.font, Component.literal("§8" + line), rx, ry, ARGB.opaque(0x5C3317), false);
                        ry += 10;
                    }
                    ry += 4;
                    for (String line : wrapText(mc, e.trivia(english), PAGE_R_W)) {
                        g.text(mc.font, Component.literal("§6" + line), rx, ry, ARGB.opaque(0xC9A227), false);
                        ry += 10;
                    }
                    String tip = Bestiary.tip(e, english);
                    if (!tip.isEmpty()) {
                        ry += 4;
                        g.text(mc.font, Component.literal("§2§l" + (english ? "Tip:" : "Consejo:")), rx, ry, ARGB.opaque(0x2E6B1F), false);
                        ry += 10;
                        for (String line : wrapText(mc, tip, PAGE_R_W)) {
                            g.text(mc.font, Component.literal("§2" + line), rx, ry, ARGB.opaque(0x2E6B1F), false);
                            ry += 10;
                        }
                    }
                } else {
                    String hint = english ? "??? — not defeated yet" : "??? — todavía no lo derrotaste";
                    for (String line : wrapText(mc, hint, PAGE_R_W)) {
                        g.text(mc.font, Component.literal("§8" + line), rx, ry, ARGB.opaque(0x5C3317), false);
                        ry += 10;
                    }
                }
            }
        } else {
            ry += 30;
            String hint = english ? "<- Select a mob" : "<- Selecciona un mob";
            for (String line : wrapText(mc, hint, PAGE_R_W)) {
                g.text(mc.font, Component.literal("§8" + line), rx, ry, ARGB.opaque(0x5C3317), false);
                ry += 10;
            }
        }
        g.disableScissor();
    }

    /**
     * Detalle de un mob CON página ilustrada propia (zombie, esqueleto, enderman por
     * ahora): la imagen (dibujo + íconos de recompensa) reemplaza el pergamino normal
     * y ocupa TODA la página — son fijos, ya vienen recortados exactos al tamaño de
     * PAGE_R. El texto (nombre, Primera Aparición, descripción, curiosidad, consejo)
     * se dibuja ENCIMA, en la mitad izquierda que el dibujo deja libre a propósito, y
     * es lo único que se puede scrollear (con las flechas ▲▼), ya que puede no entrar
     * completo. No confundir con drawBestiaryIcon (el ícono chico animado que usan
     * los mobs SIN página ilustrada todavía).
     */
    // ── Layout nuevo de la página de detalle del Bestiario ─────────────────────
    // A pedido del usuario (mandó una imagen de referencia): columna de texto alta
    // a la izquierda, dos recuadros a la derecha (Foto 1 = personaje arriba, Foto 2
    // = escena abajo, tamaño parecido entre sí), franja de ítems abajo del todo.
    // Todo definido como fracción de PAGE_R_BORDER_* (el área completa dentro del
    // marco rojo) para no depender de números fijos si el marco cambia de tamaño.
    // Antes 0.02/0.02: prácticamente pegado a la esquina del marco rojo real
    // (PAGE_R_BORDER, que no tiene padding interno), así que el título en negrita
    // (más ancho que el texto normal) quedaba tocando/pisando el borde en vez de
    // quedar separado como el resto del libro. Se sube el margen a ~6-7% para que
    // quede un padding parejo con el resto de las páginas.
    // La columna de texto quedaba muy angosta (57px reales) — palabras de largo medio
    // en negrita como "Zombified" (título) directamente NO entraban ni siquiera solas,
    // y el corte letra-por-letra de wrapText (la red de seguridad para nunca pisar el
    // borde) terminaba partiéndolas a la mitad ("Zombifie" + "d"). Se corre el límite
    // X1 varios px a la derecha (0.52→0.60) para que la gran mayoría de las palabras
    // entren enteras en una línea. La foto del personaje se angosta un poco para
    // compensar (X0 0.55→0.62) y sigue dejando el mismo margen chico entre ambas.
    private static final float DETAIL_TEXT_X0 = 0.07f, DETAIL_TEXT_Y0 = 0.055f, DETAIL_TEXT_X1 = 0.60f, DETAIL_TEXT_Y1 = 0.88f;
    // Foto 1 (personaje, arriba a la derecha): X1/Y0 quedaban a solo ~2% del borde real
    // del marco (PAGE_R_BORDER) — muy poco margen para una imagen con bordes rectos
    // (a diferencia del texto, una imagen sí puede tocar visualmente incluso con pocos
    // px). Se sube a ~3.5-4% en los 3 lados que dan al marco (arriba/derecha) para que
    // nunca quede pegada a la línea roja.
    private static final float DETAIL_FOTO1_X0 = 0.62f, DETAIL_FOTO1_Y0 = 0.035f, DETAIL_FOTO1_X1 = 0.96f, DETAIL_FOTO1_Y1 = 0.43f;
    private static final float DETAIL_FOTO2_X0 = 0.55f, DETAIL_FOTO2_Y0 = 0.47f, DETAIL_FOTO2_X1 = 0.98f, DETAIL_FOTO2_Y1 = 0.88f;
    // Angosta a propósito (llegaba hasta ITEMS_X1=0.98 antes, se metía debajo de los
    // botones ☰/☀ del menú y quedaba tapada) — ahora los ítems quedan agrupados más a
    // la izquierda, bien lejos de esos botones.
    // Tira de ítems (abajo a la izquierda): Y1 quedaba a solo ~1% del borde inferior
    // real del marco — se sube a ~3% para que los íconos no toquen la línea roja de abajo.
    private static final float DETAIL_ITEMS_X0 = 0.02f, DETAIL_ITEMS_Y0 = 0.90f, DETAIL_ITEMS_X1 = 0.62f, DETAIL_ITEMS_Y1 = 0.97f;

    /**
     * Blitea una textura ESCALADA A CONTENER dentro de un recuadro (preserva
     * proporciones, centrada, sin recortar ni estirar) — usado para meter char.png/
     * scene.png (tamaño natural distinto en cada mob) dentro de Foto 1 / Foto 2, que
     * son recuadros de tamaño fijo.
     */
    private void blitContain(GuiGraphicsExtractor g, Identifier tex, int nw, int nh, int boxX, int boxY, int boxW, int boxH) {
        float scale = Math.min(boxW / (float) nw, boxH / (float) nh);
        int drawW = Math.round(nw * scale), drawH = Math.round(nh * scale);
        int drawX = boxX + (boxW - drawW) / 2, drawY = boxY + (boxH - drawH) / 2;
        g.pose().pushMatrix();
        g.pose().translate(drawX, drawY);
        g.pose().scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, nw, nh, nw, nh);
        g.pose().popMatrix();
    }

    private void drawBestiaryIllustratedDetail(GuiGraphicsExtractor g, int bx, int by, Bestiary.Entry e, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int frameX = bx + PAGE_R_BORDER_X, frameY = by + PAGE_R_BORDER_Y;
        int frameW = PAGE_R_BORDER_W, frameH = PAGE_R_BORDER_H;

        // Foto 1 (personaje, arriba a la derecha)
        int f1x = frameX + Math.round(DETAIL_FOTO1_X0 * frameW), f1y = frameY + Math.round(DETAIL_FOTO1_Y0 * frameH);
        int f1w = Math.round((DETAIL_FOTO1_X1 - DETAIL_FOTO1_X0) * frameW), f1h = Math.round((DETAIL_FOTO1_Y1 - DETAIL_FOTO1_Y0) * frameH);
        int[] charSize = Bestiary.characterImageSize(e);
        Identifier charTex = Bestiary.characterImage(e);
        if (charTex != null) blitContain(g, charTex, charSize[0], charSize[1], f1x, f1y, f1w, f1h);

        // Foto 2 (escena, abajo a la derecha)
        int f2x = frameX + Math.round(DETAIL_FOTO2_X0 * frameW), f2y = frameY + Math.round(DETAIL_FOTO2_Y0 * frameH);
        int f2w = Math.round((DETAIL_FOTO2_X1 - DETAIL_FOTO2_X0) * frameW), f2h = Math.round((DETAIL_FOTO2_Y1 - DETAIL_FOTO2_Y0) * frameH);
        int[] sceneSize = Bestiary.sceneImageSize(e);
        Identifier sceneTex = Bestiary.sceneImage(e);
        if (sceneTex != null) blitContain(g, sceneTex, sceneSize[0], sceneSize[1], f2x, f2y, f2w, f2h);

        // Franja de ítems (loot real, abajo del todo, ancho completo)
        if (Bestiary.hasLootIcons(e)) {
            int itx = frameX + Math.round(DETAIL_ITEMS_X0 * frameW), ity = frameY + Math.round(DETAIL_ITEMS_Y0 * frameH);
            int itw = Math.round((DETAIL_ITEMS_X1 - DETAIL_ITEMS_X0) * frameW), ith = Math.round((DETAIL_ITEMS_Y1 - DETAIL_ITEMS_Y0) * frameH);
            drawBestiaryLootIcons(g, itx, ity, itw, ith, e, mouseX, mouseY);
        }

        // Columna de texto, alta, a la izquierda.
        int textX = frameX + Math.round(DETAIL_TEXT_X0 * frameW), textY = frameY + Math.round(DETAIL_TEXT_Y0 * frameH);
        int textW = Math.round((DETAIL_TEXT_X1 - DETAIL_TEXT_X0) * frameW), textH = Math.round((DETAIL_TEXT_Y1 - DETAIL_TEXT_Y0) * frameH);
        int wrapW = textW - 9; // deja lugar a las flechas ▲▼ sin que el texto las tape

        List<String> lines = new ArrayList<>();
        // El título se mide y se envuelve como negrita (true) — ver wrapText(...,
        // bold) más abajo — así ninguna línea se pasa del ancho real aunque el
        // nombre sea largo (antes se medía en modo normal y la negrita, más ancha,
        // hacía que la última palabra se saliera del recuadro y quedara cortada).
        for (String l : wrapText(mc, e.name(english), wrapW, true)) lines.add("§0§l" + l);
        for (String l : wrapText(mc, (english ? "First appearance: " : "Primera Aparición: ") + e.addedVersion(), wrapW)) lines.add("§8" + l);
        lines.add("");
        for (String l : wrapText(mc, e.desc(english), wrapW)) lines.add("§8" + l);
        lines.add("");
        for (String l : wrapText(mc, e.trivia(english), wrapW)) lines.add("§6" + l);
        String tip = Bestiary.tip(e, english);
        if (!tip.isEmpty()) {
            lines.add("");
            lines.add("§2§l" + (english ? "Tip:" : "Consejo:"));
            for (String l : wrapText(mc, tip, wrapW)) lines.add("§2" + l);
        }

        int lineH = 10;
        int visibleLines = Math.max(1, textH / lineH);
        bestiaryDetailMaxScroll = Math.max(0, lines.size() - visibleLines);
        bestiaryDetailScroll = Math.min(bestiaryDetailScroll, bestiaryDetailMaxScroll);

        g.enableScissor(textX, textY, textX + textW, textY + textH);
        int ty = textY;
        int end = Math.min(lines.size(), bestiaryDetailScroll + visibleLines);
        for (int i = bestiaryDetailScroll; i < end; i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                g.text(mc.font, Component.literal(line), textX, ty, ARGB.opaque(0xFFFFFF), false);
            }
            ty += lineH;
        }
        g.disableScissor();

        bestiaryDetailScrollBtnX = textX + textW - 8;
        bestiaryDetailScrollUpY = textY;
        bestiaryDetailScrollDownY = textY + textH - 9;
        if (bestiaryDetailScroll > 0) {
            boolean overUp = mouseX >= bestiaryDetailScrollBtnX && mouseX <= bestiaryDetailScrollBtnX + 8
                           && mouseY >= bestiaryDetailScrollUpY && mouseY <= bestiaryDetailScrollUpY + 9;
            drawIconButton(g, bestiaryDetailScrollBtnX, bestiaryDetailScrollUpY, 8, ICON_SCROLL_UP, overUp, "bestiaryDetailScrollUp");
        }
        if (bestiaryDetailScroll < bestiaryDetailMaxScroll) {
            boolean overDown = mouseX >= bestiaryDetailScrollBtnX && mouseX <= bestiaryDetailScrollBtnX + 8
                             && mouseY >= bestiaryDetailScrollDownY && mouseY <= bestiaryDetailScrollDownY + 9;
            drawIconButton(g, bestiaryDetailScrollBtnX, bestiaryDetailScrollDownY, 8, ICON_SCROLL_DOWN, overDown, "bestiaryDetailScrollDown");
        }
    }

    private static final int LOOT_ICON_PX = 16; // tamaño nativo de una textura de ítem vanilla (minecraft:textures/item/*.png)
    private static final int REWARD_ICON_PX = 10; // más chico que LOOT_ICON_PX: acá comparte línea con texto, no una franja propia

    /**
     * Dibuja hasta 3 ítems reales (textura del juego) repartidos parejo dentro de la
     * franja de ítems (ver DETAIL_ITEMS_*), y muestra su nombre + una nota corta
     * (probabilidad/condición del drop) como tooltip al pasar el mouse por encima.
     */
    private void drawBestiaryLootIcons(GuiGraphicsExtractor g, int stripX, int stripY, int stripW, int stripH, Bestiary.Entry e, int mouseX, int mouseY) {
        Bestiary.LootIcon[] icons = Bestiary.lootIcons(e);
        if (icons == null || icons.length == 0) return;

        int n = icons.length;
        int slotW = stripW / n;
        int cy = stripY + stripH / 2;

        for (int i = 0; i < n; i++) {
            Bestiary.LootIcon li = icons[i];
            Identifier tex = Bestiary.lootIconTexture(li);
            int cx = stripX + slotW * i + slotW / 2;
            int drawX = cx - LOOT_ICON_PX / 2, drawY = cy - LOOT_ICON_PX / 2;

            g.blit(RenderPipelines.GUI_TEXTURED, tex, drawX, drawY, 0f, 0f, LOOT_ICON_PX, LOOT_ICON_PX, LOOT_ICON_PX, LOOT_ICON_PX);

            if (mouseX >= drawX && mouseX < drawX + LOOT_ICON_PX && mouseY >= drawY && mouseY < drawY + LOOT_ICON_PX) {
                // No se dibuja acá directo: quedaría recortado por el scissor de la
                // página derecha que todavía está activo en este punto (ver
                // drawBestiarySubpage). Se guarda y se dibuja diferido, al final de
                // extractRenderState, ya sin ningún scissor activo.
                bestiaryLootTooltipShown = true;
                bestiaryLootTooltipX = mouseX + 6;
                bestiaryLootTooltipY = mouseY + 6;
                bestiaryLootTooltipName = english ? li.nameEn() : li.nameEs();
                bestiaryLootTooltipNote = english ? li.noteEn() : li.noteEs();
            }
        }
    }

    /**
     * Tooltip de 2 líneas (nombre del ítem + nota corta de probabilidad/condición) para
     * los íconos de loot real del Bestiario — ver drawBestiaryLootIcons. Mismo estilo
     * visual que drawTooltip (fondo negro semitransparente), pero con una línea más.
     */
    private void drawItemTooltip(GuiGraphicsExtractor g, int x, int y, String line1, String line2) {
        Minecraft mc = Minecraft.getInstance();
        int pad = 2;
        int w = Math.max(mc.font.width(line1), mc.font.width(line2)) + pad * 2;
        int lineH = mc.font.lineHeight;
        int h = lineH * 2 + pad;
        g.fill(x, y, x + w, y + h, 0xEE000000);
        g.text(mc.font, Component.literal("§f" + line1), x + pad, y + pad / 2, ARGB.opaque(0xFFFFFF), false);
        g.text(mc.font, Component.literal("§7" + line2), x + pad, y + pad / 2 + lineH, ARGB.opaque(0xFFFFFF), false);
    }


    /**
     * Ícono de una entrada del Bestiario, tamaño `size` x `size`.
     * - Sin arte (Bestiary.Entry.hasArt()==false): contorno con "?" (placeholder viejo,
     *   hasta que se sumen más gifs).
     * - Con arte, sin matar: silueta negra estática (shadow.png).
     * - Con arte, matado: gif animado a color (frame_N.png), ciclando con el reloj del
     *   sistema — mismo criterio de velocidad que drawAnimatedTabIcon (~80ms/frame).
     */
    private void drawBestiaryIcon(GuiGraphicsExtractor g, int x, int y, int size, Bestiary.Entry e, boolean killed) {
        drawBestiaryIcon(g, x, y, size, Integer.MAX_VALUE, e, killed);
    }

    /** @param maxW ancho máximo del ícono (además del alto `size`) — el que sea más
     *  chico manda la escala. Usar Integer.MAX_VALUE para el comportamiento de siempre
     *  (escalar solo por alto, sin límite de ancho). */
    private void drawBestiaryIcon(GuiGraphicsExtractor g, int x, int y, int size, int maxW, Bestiary.Entry e, boolean killed) {
        if (!e.hasArt()) {
            g.outline(x, y, size, size, 0xFF5C3317);
            Minecraft mc = Minecraft.getInstance();
            g.text(mc.font, Component.literal("§8?"), x + size / 2 - 2, y + size / 2 - 4, ARGB.opaque(0x5C3317), false);
            return;
        }
        Identifier tex;
        if (killed && e.frameCount() > 1) {
            Identifier[] frames = Bestiary.frames(e);
            int frameMs = 80;
            int frameIdx = (int) ((System.currentTimeMillis() / frameMs) % e.frameCount());
            tex = frames[frameIdx];
        } else if (killed) {
            tex = Bestiary.frames(e)[0];
        } else {
            tex = Bestiary.shadow(e);
        }
        // Los PNG no son cuadrados (cada mob tiene su propia proporción real). Se
        // escala por ALTURA fija (size), pero si el resultado se pasa de `maxW` (mobs
        // anchos como Vex o Ravager), se re-escala por ancho en su lugar — el que sea
        // más chico manda, para no invadir la columna de texto de al lado.
        int fw = e.frameW(), fh = e.frameH();
        float scale = size / (float) fh;
        if (fw * scale > maxW) scale = maxW / (float) fw;
        int iconW = Math.max(1, Math.round(fw * scale));
        int iconH = Math.max(1, Math.round(fh * scale));
        int colW = Math.min(size, maxW == Integer.MAX_VALUE ? size : maxW);
        int drawX = x + (colW - iconW) / 2;       // centrado horizontalmente en la columna
        int drawY = y + (size - iconH) / 2;        // centrado verticalmente en la fila
        g.pose().pushMatrix();
        g.pose().translate(drawX, drawY);
        g.pose().scale(iconW / (float) fw, iconH / (float) fh);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, fw, fh, fw, fh);
        g.pose().popMatrix();
    }

    /**
     * Dibuja el ícono del creeper, centrado horizontalmente sobre el botón (TAB_CREEPER_*)
     * y anclado por ARRIBA a donde el lomo (tapa) se vuelve sólido (CREEPER_TOP_Y) en su
     * posición de descanso final. revealStep/totalSteps controla cuánto se ve, recortado
     * con scissor ANCLADO POR ABAJO — es decir, crece hacia ARRIBA (como si fuera
     * saliendo/deslizándose desde abajo de la página hasta llegar a su lugar): revealStep
     * == totalSteps muestra el ícono completo; valores menores sólo muestran la porción
     * inferior (la más cercana a la página), no la superior — usado durante el revelado
     * escalonado al pasar de página.
     */
    private void drawCreeperIcon(GuiGraphicsExtractor g, int bx, int by, int revealStep, int totalSteps) {
        if (revealStep <= 0) return;
        int h = CREEPER_ICON_DISPLAY_H;
        int w = Math.round(h * (CREEPER_ICON_W / (float) CREEPER_ICON_H));
        int x1 = bx + TAB_CREEPER_X1, x2 = bx + TAB_CREEPER_X2;
        int ix = x1 + ((x2 - x1) - w) / 2;
        int iy = by + CREEPER_TOP_Y;
        int visibleH = revealStep >= totalSteps ? h : Math.round(h * (revealStep / (float) totalSteps));
        if (visibleH <= 0) return;
        // Ventana de recorte anclada al borde INFERIOR (iy+h) y creciendo hacia arriba,
        // en vez de anclada arriba creciendo hacia abajo.
        g.enableScissor(ix, iy + h - visibleH, ix + w, iy + h);
        g.pose().pushMatrix();
        g.pose().translate(ix, iy);
        g.pose().scale(w / (float) CREEPER_ICON_W, h / (float) CREEPER_ICON_H);
        g.blit(RenderPipelines.GUI_TEXTURED, CREEPER_ICON, 0, 0, 0f, 0f, CREEPER_ICON_W, CREEPER_ICON_H, CREEPER_ICON_W, CREEPER_ICON_H);
        g.pose().popMatrix();
        g.disableScissor();
    }

    /**
     * Ícono del atajo a Bestiario (cabeza de wither skeleton) — mismo tamaño de
     * pantalla y misma altura (CREEPER_TOP_Y) que drawCreeperIcon, para que quede
     * perfectamente alineado al lado del creeper. A diferencia de este último, no
     * tiene animación de "revelado": se dibuja entero siempre.
     */
    private void drawWitherIcon(GuiGraphicsExtractor g, int bx, int by, boolean glowing) {
        int h = CREEPER_ICON_DISPLAY_H;
        int w = Math.round(h * (WITHER_ICON_W / (float) WITHER_ICON_H));
        int x1 = bx + TAB_WITHER_X1;
        // A diferencia del creeper (centrado en su caja), acá se pega el ícono contra
        // el borde izquierdo con un margen chico fijo — centrarlo en una caja de 21px
        // de ancho dejaba ~6-7px de aire de cada lado, y eso es lo que hacía ver la
        // cabeza de wither mucho más lejos del creeper que el creeper de la casa.
        int ix = x1 + 2;
        int iy = by + CREEPER_TOP_Y;
        g.pose().pushMatrix();
        g.pose().translate(ix, iy);
        g.pose().scale(w / (float) WITHER_ICON_W, h / (float) WITHER_ICON_H);
        g.blit(RenderPipelines.GUI_TEXTURED, WITHER_ICON, 0, 0, 0f, 0f, WITHER_ICON_W, WITHER_ICON_H, WITHER_ICON_W, WITHER_ICON_H);
        if (glowing) {
            g.blit(RenderPipelines.GUI_TEXTURED, WITHER_ICON_GLOW, 0, 0, 0f, 0f, WITHER_ICON_W, WITHER_ICON_H, WITHER_ICON_W, WITHER_ICON_H);
        }
        g.pose().popMatrix();
    }

    /**
     * Tooltip simple (fondo + texto) para pestañas y botones nuevos. (x,y) es la
     * esquina superior izquierda de la caja del tooltip — el llamador decide dónde
     * ubicarla según de qué lado hay espacio.
     */
    /**
     * Caja de tooltip genérica — la usan los 8 lugares que muestran texto al pasar
     * el mouse (creeper/casita/wither, configuración, menú, ocultar completadas,
     * reclamar todo, e íconos de loot del bestiario). Antes dibujaba en el (x,y)
     * pedido sin fijarse si el texto entraba en pantalla — un texto largo (como
     * "Reclamar todas las recompensas pendientes") o una ventana angosta podían
     * mandar la caja fuera del borde izquierdo/derecho y se veía cortada. Ahora se
     * recorta para quedar siempre dentro de this.width/this.height, mismo patrón
     * que ya se usaba para el panel del tutorial (ver px/py más abajo en el archivo).
     */
    private void drawTooltip(GuiGraphicsExtractor g, int x, int y, String text) {
        Minecraft mc = Minecraft.getInstance();
        int pad = 2;
        int w = mc.font.width(text) + pad * 2;
        int h = mc.font.lineHeight + pad;
        x = Math.max(2, Math.min(x, this.width - w - 2));
        y = Math.max(2, Math.min(y, this.height - h - 2));
        g.fill(x, y, x + w, y + h, 0xEE000000);
        g.text(mc.font, Component.literal("§f" + text), x + pad, y + pad / 2, ARGB.opaque(0xFFFFFF), false);
    }

    /** "3/8" — completadas/total de una pestaña de categoría (índice en `tabs`). */
    private String progressString(int tabIndex) {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        List<QuestEntry> qs = tabs.get(tabIndex).quests();
        int total = qs.size(), done = 0;
        if (uuid != null) {
            for (QuestEntry q : qs) if (QuestData.get().isCompleted(uuid, q.id())) done++;
        }
        return done + "/" + total;
    }

    /**
     * Barra de progreso de la categoría actual, en la fila reservada del fondo de la
     * lista — reemplaza al viejo indicador de rango de scroll ("3-13/17"), que
     * mostraba qué filas estaban visibles pero no decía nada del progreso real.
     * boxX/boxW: caja de la página izquierda completa, para centrar barra+texto igual
     * que antes se centraba el texto solo.
     */
    private void drawCategoryProgressBar(GuiGraphicsExtractor g, int boxX, int boxW, int y, int done, int total) {
        Minecraft mc = Minecraft.getInstance();
        String label = done + "/" + total;
        int barW = 44, barH = 5;
        int gap = 4;
        int labelW = mc.font.width(label);
        int totalW = barW + gap + labelW;
        int x = boxX + (boxW - totalW) / 2;
        int barY = y + (ROW_H - barH) / 2;

        g.fill(x, barY, x + barW, barY + barH, 0xFF3A2E22);           // marco oscuro (madera)
        g.fill(x + 1, barY + 1, x + barW - 1, barY + barH - 1, 0xFF1F1710); // fondo vacío
        int fillW = total > 0 ? Math.round((barW - 2) * (done / (float) total)) : 0;
        if (fillW > 0) {
            int fillColor = done >= total ? 0xFF4C8C3C : 0xFF8C7A3C; // verde si está completa, ámbar si va a mitad
            g.fill(x + 1, barY + 1, x + 1 + fillW, barY + barH - 1, fillColor);
        }
        g.text(mc.font, Component.literal("§8" + label), x + barW + gap, y + 1, ARGB.opaque(0x555555), false);
    }

    /**
     * Dibuja un ícono animado (loop de N frames) centrado sobre una de las 5 pestañas
     * de la derecha, con resalte sutil al pasar el mouse o si está seleccionada.
     * tabArrayIndex = índice dentro de TAB_Y1/TAB_Y2 (0-4). targetTab = valor de
     * selectedTab al que corresponde esa pestaña (1=Explorador ... 5=Logros).
     */
    /** true si la categoría tiene al menos una misión completada y aún sin reclamar. */
    private boolean tabHasUnclaimed(int tabIndex) {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.player != null ? mc.player.getUUID() : null;
        if (uuid == null) return false;
        for (QuestEntry q : tabs.get(tabIndex).quests()) {
            if (QuestData.get().isCompleted(uuid, q.id()) && !QuestData.get().isClaimed(uuid, q.id())) return true;
        }
        return false;
    }

    private void drawAnimatedTabIcon(GuiGraphicsExtractor g, int bx, int by, int mouseX, int mouseY,
            int tabArrayIndex, int targetTab, String label,
            Identifier[] frames, int frameCount, int frameW, int frameH, int frameMs, int iconH) {
        int tX1 = bx + TAB_X1, tX2 = bx + TAB_X2;
        int tY1 = by + TAB_Y1[tabArrayIndex], tY2 = by + TAB_Y2[tabArrayIndex];
        boolean selected = selectedTab == targetTab;
        boolean over = mouseX >= tX1 && mouseX <= tX2 && mouseY >= tY1 && mouseY <= tY2;
        if (selected || over) {
            g.fill(tX1, tY1, tX2, tY2, selected ? 0x40FFFFFF : 0x22FFFFFF);
        } else if (tabHasUnclaimed(targetTab)) {
            // Mismo lenguaje visual que el parpadeo de la cabeza de wither: avisa que
            // hay algo pendiente (acá, una recompensa sin reclamar) sin necesitar que
            // el jugador entre a cada pestaña a revisar. Se apaga solo al reclamarla
            // (con "Reclamar todo" o una por una) — no hace falta un estado de "ya lo
            // vi" aparte, la condición deja de cumplirse sola.
            float alpha = 0.18f + 0.18f * (float) Math.sin(System.currentTimeMillis() / 250.0);
            g.fill(tX1, tY1, tX2, tY2, (Math.round(alpha * 255) << 24) | 0xFFFFFF);
        }
        int frameIdx = (int) ((System.currentTimeMillis() / frameMs) % frameCount);
        int iconW = Math.round(iconH * (frameW / (float) frameH));
        int iconX = tX1 + (TAB_X2 - TAB_X1 - iconW) / 2;
        int iconY = tY1 + (tY2 - tY1 - iconH) / 2;
        g.pose().pushMatrix();
        g.pose().translate(iconX, iconY);
        g.pose().scale(iconW / (float) frameW, iconH / (float) frameH);
        g.blit(RenderPipelines.GUI_TEXTURED, frames[frameIdx], 0, 0, 0f, 0f, frameW, frameH, frameW, frameH);
        g.pose().popMatrix();
        if (over) {
            String tip = label + " (" + progressString(targetTab) + ")";
            int tw = Minecraft.getInstance().font.width(tip) + 4;
            drawTooltip(g, tX1 - tw - 3, tY1 + (tY2 - tY1 - (Minecraft.getInstance().font.lineHeight + 2)) / 2, tip);
        }
    }

    /**
     * Progreso 0..1 del "bounce" de click de un botón, en forma de campana (sube y
     * vuelve a bajar): 0 en reposo, pico a mitad de BTN_PRESS_MS, 0 de nuevo al
     * terminar. Usar sin() en vez de una simple rampa lineal hace que el botón
     * "vuelva" solo, con una desaceleración natural, sin necesidad de trackear
     * mouseReleased ni un estado "held" — un solo timestamp por botón alcanza.
     */
    private float pressT(String key) {
        Long at = btnPressAt.get(key);
        if (at == null) return 0f;
        long elapsed = System.currentTimeMillis() - at;
        if (elapsed < 0 || elapsed >= BTN_PRESS_MS) return 0f;
        return (float) Math.sin(Math.PI * elapsed / BTN_PRESS_MS);
    }

    /** Arranca la animación de click de un botón. Llamar en el mismo click que ejecuta su acción. */
    private void triggerPress(String key) {
        btnPressAt.put(key, System.currentTimeMillis());
    }

    /**
     * Dibuja un botón cuadrado tipo "ficha" con bisel gris + ícono transparente encima,
     * con profundidad real y animación de click:
     *  - Sombra dura (sin blur, un solo rectángulo offset, en línea con el resto del
     *    HUD que usa fills planos) por detrás de todo el botón — se acerca al botón
     *    cuando está presionado, en vez de quedarse fija "flotando" siempre igual.
     *  - Bisel de 1px: borde claro arriba/izquierda + oscuro abajo/derecha en reposo
     *    (relieve hacia afuera); se INVIERTE al presionar (oscuro arriba/izquierda,
     *    claro abajo/derecha) para que se lea como hundido, no solo más chico.
     *  - El ícono (ya recortado sin fondo ni marco, ver icons_clean/) se dibuja con
     *    margen adentro del bisel — no pegado al borde — así se ve "cómodo" en vez
     *    de forzado a llenar todo el botón.
     * x,y,size: hitbox del botón en píxeles de pantalla (cuadrado).
     */
    /**
     * Dibuja el botón usando la textura del usuario TAL CUAL (madera + pergamino +
     * corchetes rojos + ícono, ya recortada al pixel exacto del borde) — sin recolorear
     * ni reconstruir nada. La profundidad se agrega solo con una sombra dura por detrás
     * (un rectángulo sólido con offset, sin blur, igual que el resto del HUD) y un
     * pequeño desplazamiento hacia abajo mientras dura el click, para que se lea como
     * que el botón se hunde 1px en la página en vez de quedar flotando siempre igual.
     * x,y,size: hitbox cuadrada en píxeles de pantalla.
     */
    private void drawIconButton(GuiGraphicsExtractor g, int x, int y, int size, Identifier tex,
            boolean hovered, String pressKey) {
        float t = pressT(pressKey);
        int sinkY = t > 0.5f ? 1 : 0;
        int shadowOff = 2 - sinkY; // sombra pegada al botón cuando está hundido, separada en reposo

        g.fill(x + shadowOff, y + shadowOff, x + shadowOff + size, y + shadowOff + size, 0x80000000);

        g.pose().pushMatrix();
        g.pose().translate(x, y + sinkY);
        g.pose().scale(size / (float) ICON_NATIVE, size / (float) ICON_NATIVE);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, ICON_NATIVE, ICON_NATIVE, ICON_NATIVE, ICON_NATIVE);
        g.pose().popMatrix();

        if (hovered && t <= 0.01f) {
            g.fill(x, y + sinkY, x + size, y + sinkY + size, 0x22FFFFFF); // leve resalte al pasar el mouse, sin cambiar el tamaño
        }
    }

    /**
     * Variante para íconos con recorte irregular (no llenan todo el cuadrado, como
     * claim_all desde que se le sacó el fondo tipo medallón): en vez de una sombra
     * cuadrada de relleno sólido, blitea una segunda copia de la MISMA silueta
     * (shadowTex — negro semitransparente, recortado igual que el ícono real) con
     * un offset chico en diagonal. Así la sombra sigue el contorno real del dibujo
     * (cofre + brotes) en vez de verse como un cuadrado gris de fondo sin relación
     * con la forma del ícono. El offset es fijo y chico (1px, se pierde al hundirse
     * con el click) — sombra paralela sutil, no un bloque separado.
     */
    private void drawIconButton(GuiGraphicsExtractor g, int x, int y, int size, Identifier tex,
            Identifier shadowTex, boolean hovered, String pressKey) {
        float t = pressT(pressKey);
        int sinkY = t > 0.5f ? 1 : 0;
        int shadowOff = t > 0.5f ? 0 : 1; // sombra chica en reposo, pegada del todo cuando se hunde

        g.pose().pushMatrix();
        g.pose().translate(x + shadowOff, y + sinkY + shadowOff);
        g.pose().scale(size / (float) ICON_NATIVE, size / (float) ICON_NATIVE);
        g.blit(RenderPipelines.GUI_TEXTURED, shadowTex, 0, 0, 0f, 0f, ICON_NATIVE, ICON_NATIVE, ICON_NATIVE, ICON_NATIVE);
        g.pose().popMatrix();

        g.pose().pushMatrix();
        g.pose().translate(x, y + sinkY);
        g.pose().scale(size / (float) ICON_NATIVE, size / (float) ICON_NATIVE);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, ICON_NATIVE, ICON_NATIVE, ICON_NATIVE, ICON_NATIVE);
        g.pose().popMatrix();

        if (hovered && t <= 0.01f) {
            g.fill(x, y + sinkY, x + size, y + sinkY + size, 0x22FFFFFF);
        }
    }

    /** Trunca el texto si es más ancho que maxWidth px */
    private static String truncate(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) return text;
        while (text.length() > 1 && mc.font.width(text + "...") > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    /**
     * Reparte el texto en líneas que quepan en maxWidth px, cortando por palabra completa
     * (nunca a la mitad de una palabra). A diferencia de truncate(), no pierde contenido:
     * si una línea no entra, la palabra sobrante pasa a la siguiente línea.
     */
    private static List<String> wrapText(Minecraft mc, String text, int maxWidth) {
        return wrapText(mc, text, maxWidth, false);
    }

    // Variante que mide el ancho como texto EN NEGRITA. mc.font.width(String) mide en
    // modo normal — la negrita (§l) dibuja cada glifo ~1px más ancho, así que para el
    // título (que se pinta con §l) el ancho medido siempre se quedaba corto y la línea
    // terminaba pasándose del recuadro/scissor → seguía viéndose cortada aunque ya
    // estuviera "envuelta". Ahora se mide con el mismo estilo con el que se dibuja.
    // También corta a la fuerza, letra por letra, cualquier palabra suelta más ancha
    // que maxWidth (p. ej. un nombre largo sin espacios), como red de seguridad para
    // que ninguna línea pueda salirse del recuadro sin importar el idioma o el nombre.
    private static List<String> wrapText(Minecraft mc, String text, int maxWidth, boolean bold) {
        List<String> words = new ArrayList<>();
        for (String w : text.split(" ")) {
            if (w.isEmpty()) continue;
            if (widthOf(mc, w, bold) <= maxWidth) { words.add(w); continue; }
            StringBuilder piece = new StringBuilder();
            List<String> pieces = new ArrayList<>();
            for (int i = 0; i < w.length(); i++) {
                String next = piece.toString() + w.charAt(i);
                if (widthOf(mc, next, bold) > maxWidth && !piece.isEmpty()) {
                    pieces.add(piece.toString());
                    piece = new StringBuilder().append(w.charAt(i));
                } else {
                    piece.append(w.charAt(i));
                }
            }
            if (!piece.isEmpty()) pieces.add(piece.toString());
            // Evita que quede una letra sola huérfana en el último pedazo (p. ej.
            // "Zombificad" + "o") — se ve mal y quedó reportado como bug. Si el
            // último trozo tiene 1 sola letra y hay un trozo anterior para robarle,
            // se le pasa 1 letra de ahí: "Zombifica" + "do" en vez de "...cad"+"o".
            if (pieces.size() >= 2 && pieces.get(pieces.size() - 1).length() == 1
                    && pieces.get(pieces.size() - 2).length() > 1) {
                int last = pieces.size() - 1;
                String prev = pieces.get(last - 1);
                pieces.set(last - 1, prev.substring(0, prev.length() - 1));
                pieces.set(last, prev.charAt(prev.length() - 1) + pieces.get(last));
            }
            words.addAll(pieces);
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(mc, candidate, bold) <= maxWidth || current.isEmpty()) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private static int widthOf(Minecraft mc, String s, boolean bold) {
        return bold ? mc.font.width(Component.literal(s).withStyle(ChatFormatting.BOLD)) : mc.font.width(s);
    }

    // Reproduce el sonido de pasar página completo, sin recortar, apenas empieza la animación.
    // No importa que dure más o menos que la animación visual (600ms): se deja sonar entero.
    private void playPageFlipSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(ModSounds.PAGE_FLIP, 1.0f)
        );
    }

    // Sonido de reclamar recompensa — el "chime de logro" reemplazado por el clip
    // que trajo el usuario para esto específicamente.
    private void playClaimSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(ModSounds.CLAIM_REWARD, 1.0f)
        );
    }

    // Click corto y discreto para fijar/desfijar y para el toggle de "ocultar completadas".
    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
        );
    }

    private void handleClick(double mx, double my) {
        // Mientras el tutorial está activo, absorbe TODOS los clicks — ni pestañas,
        // ni botones, nada del libro real responde hasta que se termine o se salte.
        if (tutorialActive) { handleTutorialClick(mx, my); return; }

        // Ignorar clicks mientras se reproduce la animación de cambio de página
        if (tabAnimStart >= 0 && (System.currentTimeMillis() - tabAnimStart) < ANIM_TOTAL_MS) return;

        // Botón de opciones (⚙): reabre la ventana de elegir idioma, por si alguien
        // se equivocó la primera vez. Se chequea primero porque es fijo en toda pestaña.
        if (mx >= optionsBtnX && mx <= optionsBtnX + optionsBtnW
                && my >= optionsBtnY && my <= optionsBtnY + optionsBtnH) {
            triggerPress("gear");
            playClickSound();
            Minecraft.getInstance().gui.setScreen(new LanguageSelectScreen());
            return;
        }

        // Botón de Menú (≡): selecciona TAB_MENU, EXACTAMENTE igual que clickear
        // cualquier otro ícono de pestaña más abajo — dispara la misma animación de
        // vuelta de página. Un click estando ya en TAB_MENU vuelve al listado.
        if (mx >= menuBtnX && mx <= menuBtnX + menuBtnW
                && my >= menuBtnY && my <= menuBtnY + menuBtnH) {
            triggerPress("menu");
            if (selectedTab != TAB_MENU) {
                tabAnimStart = System.currentTimeMillis();
                playPageFlipSound();
            }
            selectedTab = TAB_MENU; selectedIndex = -1;
            menuSubPage = null; selectedBestiaryIndex = -1;
            playClickSound();
            return;
        }

        // Flechas de scroll (▲▼): se chequean ACÁ, antes que cualquier contenido de la
        // pestaña (filas de misiones, filas del bestiario, pestañas internas), porque
        // con las filas grandes del bestiario la flecha ▼ queda flotando encima de la
        // última fila (no hay espacio libre debajo, ver BESTIARY_ROW_H) — si el chequeo
        // de la fila corriera primero, un click en la flecha se interpretaba como click
        // en la fila de abajo y nunca scrolleaba. Poniéndolo primero, la flecha siempre
        // gana el click así se superponga con lo que sea.
        if (mx >= scrollBtnX && mx <= scrollBtnX + 10
                && my >= scrollUpBtnY && my <= scrollUpBtnY + 9) {
            triggerPress("scrollUp"); playClickSound(); scrollUp(); return;
        }
        if (mx >= scrollDownBtnX && mx <= scrollDownBtnX + 10
                && my >= scrollDownBtnY && my <= scrollDownBtnY + 9) {
            triggerPress("scrollDown"); playClickSound(); scrollDown(); return;
        }

        // Clicks propios del contenido de TAB_MENU (filas del listado, link de volver,
        // celdas del bestiario). Si el click no cae en ninguno de estos, sigue el flujo
        // normal de más abajo — así clickear el ícono de OTRA pestaña para salir de acá
        // sigue funcionando igual que siempre, sin necesidad de "cerrar" nada primero.
        if (selectedTab == TAB_MENU) {
            if (menuSubPage == null) {
                if (mx >= menuRowX && mx <= menuRowX + menuRowW
                        && my >= menuRowProgressY - 1 && my <= menuRowProgressY + menuRowH) {
                    menuSubPage = "progress";
                    playClickSound();
                    return;
                }
                if (mx >= menuRowX && mx <= menuRowX + menuRowW
                        && my >= menuRowBestiaryY - 1 && my <= menuRowBestiaryY + menuRowH) {
                    enterBestiary();
                    playClickSound();
                    return;
                }
                if (mx >= menuRowX && mx <= menuRowX + menuRowW
                        && my >= menuRowStatsY - 1 && my <= menuRowStatsY + menuRowH) {
                    menuSubPage = "stats";
                    ClientPlayNetworking.send(new RequestGameStatsPayload());
                    playClickSound();
                    return;
                }
            } else {
                if (mx >= menuBackX && mx <= menuBackX + menuBackW
                        && my >= menuBackY && my <= menuBackY + 10) {
                    menuSubPage = null;
                    selectedBestiaryIndex = -1;
                    playClickSound();
                    return;
                }
                if (menuSubPage.equals("bestiary")) {
                    // Flechas ▲▼ del texto sobre la página ilustrada (si hay una
                    // seleccionada) — se chequean primero, tienen prioridad.
                    if (mx >= bestiaryDetailScrollBtnX && mx <= bestiaryDetailScrollBtnX + 8) {
                        if (my >= bestiaryDetailScrollUpY && my <= bestiaryDetailScrollUpY + 9 && bestiaryDetailScroll > 0) {
                            triggerPress("bestiaryDetailScrollUp");
                            bestiaryDetailScroll--;
                            playClickSound();
                            return;
                        }
                        if (my >= bestiaryDetailScrollDownY && my <= bestiaryDetailScrollDownY + 9 && bestiaryDetailScroll < bestiaryDetailMaxScroll) {
                            triggerPress("bestiaryDetailScrollDown");
                            bestiaryDetailScroll++;
                            playClickSound();
                            return;
                        }
                    }

                    // Pestañas de categoría (Comunes/Especiales/Jefes) — se chequean
                    // primero, usando los rects que ya calculó el render de este mismo
                    // frame (bestiaryTabX/W/Y).
                    for (int c = 0; c < 3; c++) {
                        if (mx >= bestiaryTabX[c] && mx <= bestiaryTabX[c] + bestiaryTabW[c]
                                && my >= bestiaryTabY - 1 && my <= bestiaryTabY + 9) {
                            if (bestiaryCategory != c) {
                                bestiaryCategory = c;
                                scrollOffset[TAB_MENU] = 0;
                                selectedBestiaryIndex = -1;
                                playClickSound();
                            }
                            return;
                        }
                    }

                    List<Bestiary.Entry> rows = buildBestiaryRows();
                    int off = scrollOffset[TAB_MENU];
                    int end = Math.min(rows.size(), off + BESTIARY_VISIBLE_ROWS);
                    int entryIdx = off;

                    int rowBx = this.width  / 2 - BW / 2;
                    int rowBy = this.height / 2 - BH / 2;
                    int rowLx = rowBx + PAGE_L_X;
                    // Debe coincidir con drawBestiarySubpage: ly arranca en PAGE_L_Y +
                    // BACK_LINK_TOP_PAD (el link "« Menú", no el título — por eso NO es
                    // TITLE_TOP_PAD acá), +ROW_H (back link) +ROW_H (título) +12
                    // (pestañas Comunes/Especiales/Jefes) antes de la primera fila de mobs.
                    int rowY  = rowBy + PAGE_L_Y + BACK_LINK_TOP_PAD + ROW_H * 2 + 12; // +12: la fila de pestañas de categoría
                    for (int i = off; i < end; i++) {
                        if (mx >= rowLx - 2 && mx <= rowLx + PAGE_L_W - 2
                                && my >= rowY - 1 && my <= rowY + BESTIARY_ROW_H - 2) {
                            selectedBestiaryIndex = (selectedBestiaryIndex == entryIdx) ? -1 : entryIdx;
                            bestiaryDetailScroll = 0;
                            playClickSound();
                            return;
                        }
                        entryIdx++;
                        rowY += BESTIARY_ROW_H;
                    }
                }
            }
        }

        int bx = this.width  / 2 - BW / 2;
        int by = this.height / 2 - BH / 2;

        double rx = mx - bx, ry = my - by;

        // Click en pestaña Principal (icono casita arriba izquierda)
        if (rx >= TAB_HOME_X1 && rx <= TAB_HOME_X2
                && ry >= TAB_HOME_Y1 && ry <= TAB_HOME_Y2) {
            if (selectedTab != 0) { tabAnimStart = System.currentTimeMillis(); playPageFlipSound(); }
            selectedTab = 0; selectedIndex = -1; return;
        }

        // Click en botón creeper (arriba izquierda, junto a la casita): pestaña
        // "Completadas" — misiones completadas de las 4 categorías, sin canjear
        // primero, canjeadas después (ver buildCompletedQuests()).
        if (rx >= TAB_CREEPER_X1 && rx <= TAB_CREEPER_X2
                && ry >= TAB_CREEPER_Y1 && ry <= TAB_CREEPER_Y2) {
            if (selectedTab != TAB_COMPLETED) { tabAnimStart = System.currentTimeMillis(); playPageFlipSound(); }
            selectedTab = TAB_COMPLETED; selectedIndex = -1; return;
        }

        // Click en la cabeza de wither (atajo a Bestiario): entra directo a
        // TAB_MENU con menuSubPage="bestiary", como si hubiera venido del listado
        // de Menú — el Bestiario en sí sigue viviendo solo ahí, esto es solo un acceso rápido.
        if (rx >= TAB_WITHER_X1 && rx <= TAB_WITHER_X2
                && ry >= TAB_WITHER_Y1 && ry <= TAB_WITHER_Y2) {
            if (selectedTab != TAB_MENU) { tabAnimStart = System.currentTimeMillis(); playPageFlipSound(); }
            selectedTab = TAB_MENU;
            enterBestiary();
            playClickSound();
            return;
        }

        // Click en pestañas derecha: Explorador/Minero/Constructor/Técnico/Mago/Logros/Guerrero.
        // Ya no es un simple "+1" — los últimos tres slots están invertidos respecto al
        // número de pestaña (Mago vive en el slot 4 pero es la pestaña 6, Logros vive
        // en el slot 5 pero sigue siendo la pestaña 5, Guerrero vive en el slot 6 y es
        // la pestaña 7).
        if (rx >= TAB_X1 && rx <= TAB_X2) {
            for (int i = 0; i < TAB_Y1.length; i++) {
                if (ry >= TAB_Y1[i] && ry <= TAB_Y2[i]) {
                    int newTab = TAB_SLOT_TO_SELECTED[i];
                    if (selectedTab != newTab) { tabAnimStart = System.currentTimeMillis(); playPageFlipSound(); }
                    selectedTab = newTab;
                    selectedIndex = -1;
                    return;
                }
            }
        }



        // Click en toggle "ocultar completadas"
        if (my >= hideToggleY && my <= hideToggleY + hideToggleSize
                && mx >= hideToggleX && mx <= hideToggleX + hideToggleSize) {
            triggerPress("hideToggle");
            hideCompleted = !hideCompleted;
            selectedIndex = -1; // el índice seleccionado ya no vale con la lista filtrada distinta
            playClickSound();
            return;
        }

        // Click en "Reclamar todo" (Completadas)
        if (my >= claimAllBtnY && my <= claimAllBtnY + claimAllBtnH
                && mx >= claimAllBtnX && mx <= claimAllBtnX + claimAllBtnW) {
            triggerPress("claimAll");
            for (QuestEntry q : unclaimedCompletedQuests()) {
                ClientPlayNetworking.send(new ClaimRewardPayload(q.id()));
            }
            playClaimSound();
            return;
        }

        // Click en lista de misiones
        List<QuestEntry> quests = currentTabQuests();
        int lx  = bx + PAGE_L_X;
        // Antes sin TITLE_TOP_PAD: quedaba 4px más arriba que las filas realmente
        // dibujadas (ver drawTabContent), así que clickear una fila visible a veces
        // seleccionaba la de arriba. Debe coincidir siempre con el mismo cálculo que usa
        // el render (por eso queda como TITLE_TOP_PAD + ROW_H, igual que allá).
        int ly  = by + PAGE_L_Y + TITLE_TOP_PAD + ROW_H;
        int off = scrollOffset[selectedTab];
        int end = Math.min(quests.size(), off + VISIBLE_ROWS);
        for (int i = off; i < end; i++) {
            if (mx >= lx - 2 && mx <= lx + PAGE_L_W - 2 && my >= ly - 1 && my <= ly + 12) {
                selectedIndex = (selectedIndex == i) ? -1 : i;
                return;
            }
            ly += ROW_H;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || selectedIndex < 0 || selectedIndex >= quests.size()) return;
        UUID uuid = mc.player.getUUID();
        QuestEntry q = quests.get(selectedIndex);

        // Botón Obtener
        if (claimBtnQuestId != null
                && my >= claimBtnY && my <= claimBtnY + claimBtnH
                && mx >= claimBtnX && mx <= claimBtnX + claimBtnW) {
            ClientPlayNetworking.send(new ClaimRewardPayload(claimBtnQuestId));
            playClaimSound();
            return;
        }

        // Botón Fijar
        if (my >= pinBtnY && my <= pinBtnY + pinBtnH
                && mx >= pinBtnX && mx <= pinBtnX + pinBtnW) {
            if (!QuestData.get().isCompleted(uuid, q.id())) {
                if (q.id().equals(QuestData.get().getPinnedQuest(uuid)))
                    QuestData.get().unpinQuest(uuid);
                else
                    QuestData.get().pinQuest(uuid, q.id());
                playClickSound();
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) handleClick(click.x(), click.y());
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        // Válvula de seguridad: si el tutorial queda atascado por lo que sea (como
        // el bug de posición que lo mandó fuera de pantalla), ESC siempre tiene que
        // poder sacar a alguien de ahí. Lo marca como visto y listo — no vuelve a
        // trabar en la próxima apertura del libro.
        if (tutorialActive && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            finishTutorial();
            return true;
        }
        // Flechas ↑/↓ del teclado: mismo scroll que la rueda del mouse o las flechas
        // ▲▼ en pantalla — reutiliza scrollUp()/scrollDown(), así que funciona igual
        // en la lista de misiones y en el bestiario sin lógica aparte.
        if (input.key() == GLFW.GLFW_KEY_UP)   { scrollUp();   return true; }
        if (input.key() == GLFW.GLFW_KEY_DOWN) { scrollDown(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scroll con rueda del ratón cuando el cursor está sobre la página izquierda
        int bx = this.width  / 2 - BW / 2;
        int by = this.height / 2 - BH / 2;
        boolean overLeft = mouseX >= bx + PAGE_L_X - 2
                        && mouseX <= bx + PAGE_L_X + PAGE_L_W
                        && mouseY >= by + PAGE_L_Y
                        && mouseY <= by + PAGE_L_Y + PAGE_L_H;
        if (overLeft) {
            if (verticalAmount > 0) scrollUp();
            else if (verticalAmount < 0) scrollDown();
            return true;
        }
        boolean overRight = mouseX >= bx + PAGE_R_X - 2
                         && mouseX <= bx + PAGE_R_X + PAGE_R_W
                         && mouseY >= by + PAGE_R_Y
                         && mouseY <= by + PAGE_R_Y + PAGE_R_H;
        if (overRight && selectedTab == TAB_MENU && "bestiary".equals(menuSubPage)) {
            if (verticalAmount > 0 && bestiaryDetailScroll > 0) bestiaryDetailScroll--;
            else if (verticalAmount < 0 && bestiaryDetailScroll < bestiaryDetailMaxScroll) bestiaryDetailScroll++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean isPauseScreen() { return false; }
}
