package com.homequest;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos estáticos del Bestiario (sin lógica de renderizado — eso vive en
 * QuestBookScreen#drawBestiarySubpage). La base es la misma lista de mobs que ya
 * existe en HomeQuestMod#WARRIOR_MOB_KEY del lado servidor, para que "matar a X para
 * el Guerrero" y "descubrir a X en el Bestiario" sean el mismo bicho — pero el
 * Bestiario puede tener ALGUNOS mobs de más que el Guerrero no rastrea (por ahora,
 * Wither Skeleton, agregado junto con su arte). No hace falta que coincidan 1 a 1.
 *
 * ARTE: iconId != null significa que hay assets reales en
 * textures/gui/bestiary/<iconId>/ (frame_0.png..frame_N.png + shadow.png), generados
 * a partir de los gifs/imágenes que pasó el usuario (sacados de es.minecraft.wiki).
 * iconId == null (la mayoría, todavía) usa el placeholder de contorno en
 * QuestBookScreen mientras no lleguen más assets. Para agregar un mob nuevo con arte:
 * 1) generar textures/gui/bestiary/<id>/frame_0..N.png + shadow.png (silueta negra
 *    sólida, mismo tamaño que los frames), 2) pasarle iconId/frameCount acá.
 */
public final class Bestiary {

    public record Entry(
        EntityType<?> type,
        String nameEs, String nameEn,
        boolean boss,
        String addedVersion,          // ej. "Alpha 1.0.2 (2010)"
        String descEs, String descEn,
        String triviaEs, String triviaEn,
        String iconId,                // carpeta bajo textures/gui/bestiary/, o null si no hay arte todavía
        int frameCount,                // 1 = imagen estática, >1 = animado (frame_0..frameCount-1)
        int frameW, int frameH         // tamaño real en px de cada frame/shadow (no son cuadrados)
    ) {
        // Constructor compacto para las entradas SIN arte todavía — no hace falta
        // tocar las que ya existen al sumar el sistema de íconos.
        public Entry(EntityType<?> type, String nameEs, String nameEn, boolean boss, String addedVersion,
                String descEs, String descEn, String triviaEs, String triviaEn) {
            this(type, nameEs, nameEn, boss, addedVersion, descEs, descEn, triviaEs, triviaEn, null, 0, 0, 0);
        }

        public String name(boolean english) { return english ? nameEn : nameEs; }
        public String desc(boolean english) { return english ? descEn : descEs; }
        public String trivia(boolean english) { return english ? triviaEn : triviaEs; }
        public boolean hasArt() { return iconId != null; }
    }

    private static final Map<String, Identifier[]> FRAME_CACHE = new HashMap<>();
    private static final Map<String, Identifier> SHADOW_CACHE = new HashMap<>();

    // Misma clave que HomeQuestMod#WARRIOR_MOB_KEY del lado servidor (deben coincidir
    // los strings exactamente, ojo con "dragon" en vez de "ender_dragon"), usada para
    // leer QuestData.get().getMobKills(uuid, mobKey) — así el Bestiario sabe si ya se
    // mató un mob aunque todavía no tenga arte propio (iconId null).
    // Antes esto era Map<EntityType,String>, pero eso se rompe para mobs "compuestos"
    // que comparten EntityType con otra entrada ya existente — un Zombie Bebé sigue
    // siendo EntityTypes.ZOMBIE, iguial que el Zombi normal, así que ambos calzarían
    // con la misma clave y uno taparía al otro. Se cambia a Map<String,String> por
    // iconId (que sí es único por entrada) → clave real usada en el servidor (ver
    // HomeQuestMod#WARRIOR_MOB_KEY). OJO: casi siempre coincide con el iconId, salvo
    // "ender_dragon" (iconId) → "dragon" (clave real, ya guardada así en partidas
    // viejas) — no unificar esos dos strings o se pierde el progreso ya guardado.
    private static final Map<String, String> MOB_KEY = Map.ofEntries(
        Map.entry("zombie", "zombie"),
        Map.entry("skeleton", "skeleton"),
        Map.entry("wither_skeleton", "wither_skeleton"),
        Map.entry("creeper", "creeper"),
        Map.entry("spider", "spider"),
        Map.entry("enderman", "enderman"),
        Map.entry("witch", "witch"),
        Map.entry("drowned", "drowned"),
        Map.entry("husk", "husk"),
        Map.entry("stray", "stray"),
        Map.entry("phantom", "phantom"),
        Map.entry("slime", "slime"),
        Map.entry("magma_cube", "magma_cube"),
        Map.entry("blaze", "blaze"),
        Map.entry("ghast", "ghast"),
        Map.entry("piglin", "piglin"),
        Map.entry("hoglin", "hoglin"),
        Map.entry("pillager", "pillager"),
        Map.entry("vindicator", "vindicator"),
        Map.entry("evoker", "evoker"),
        Map.entry("vex", "vex"),
        Map.entry("guardian", "guardian"),
        Map.entry("elder_guardian", "elder_guardian"),
        Map.entry("ravager", "ravager"),
        Map.entry("warden", "warden"),
        Map.entry("wither", "wither"),
        Map.entry("ender_dragon", "dragon"),
        Map.entry("creaking", "creaking"),
        Map.entry("zoglin", "zoglin"),
        Map.entry("breeze", "breeze"),
        Map.entry("camel_husk", "camel_husk"),
        // Variantes compuestas: comparten EntityType con su forma "base" (zombie/
        // skeleton), así que del lado servidor NO se registran vía el mapa genérico
        // WARRIOR_MOB_KEY — el hook de muerte las detecta aparte mirando isBaby()/
        // getVehicle() y llama a onWarriorKill() directo con esta clave (ver
        // HomeQuestMod, AFTER_DEATH). Acá solo hace falta la clave para poder leer
        // el contador de vuelta.
        Map.entry("zombie_horseman", "zombie_horseman"),
        Map.entry("baby_zombie", "baby_zombie"),
        Map.entry("chicken_jockey", "chicken_jockey"),
        Map.entry("skeleton_horseman", "skeleton_horseman"),
        Map.entry("spider_jockey", "spider_jockey"),
        Map.entry("zombified_piglin", "zombified_piglin"),
        Map.entry("silverfish", "silverfish"),
        Map.entry("bogged", "bogged"),
        Map.entry("zombie_villager", "zombie_villager"),
        Map.entry("endermite", "endermite"),
        Map.entry("piglin_brute", "piglin_brute"),
        Map.entry("cave_spider", "cave_spider"),
        Map.entry("shulker", "shulker")
    );

    /** true si el jugador ya mató al menos una vez a este mob (según el servidor). */
    public static boolean isKilled(Entry e, java.util.UUID uuid) {
        if (uuid == null || e.iconId() == null) return false;
        String key = MOB_KEY.get(e.iconId());
        if (key == null) return false; // no debería pasar si el mob está bien cargado en MOB_KEY
        return com.homequest.data.QuestData.get().getMobKills(uuid, key) > 0;
    }

    private record Tip(String es, String en) {}

    // Consejos para vencer a cada mob más fácil — tabla externa (igual que MOB_KEY),
    // keyeada por iconId (no por EntityType): varias entradas comparten EntityType con
    // otra (p. ej. Bebé Zombi, Jinete Zombie y Chicken Jockey son las 3 EntityTypes.ZOMBIE)
    // y cada una necesita su propio consejo — con EntityType como clave, Map.ofEntries
    // directamente no compilaba por claves duplicadas.
    private static final Map<String, Tip> TIPS = Map.ofEntries(
        Map.entry("zombie", new Tip(
            "Golpéalo y retrocede entre ataques para evitar recibir daño; si juegas en Difícil, refuerza las entradas de tu base.",
            "Hit it and back off between attacks to avoid taking damage; on Hard, reinforce your base's entrances.")),
        Map.entry("skeleton", new Tip(
            "Acércate rápido en zigzag, o bloquea sus flechas con un escudo mientras te acercas.",
            "Close the distance quickly in a zigzag, or block its arrows with a shield as you approach.")),
        Map.entry("wither_skeleton", new Tip(
            "No intercambies golpes con él: elimínalo rápido antes de que el efecto Wither desgaste toda tu vida.",
            "Don't trade hits with it: kill it fast before the Wither effect wears down your health.")),
        Map.entry("creeper", new Tip(
            "Golpéalo y retrocede enseguida, o usa flechas a distancia. Nunca lo dejes acercarse en espacios pequeños.",
            "Hit it and back off right away, or use arrows from range. Never let it get close in tight spaces.")),
        Map.entry("spider", new Tip(
            "Aprovecha la altura a tu favor: desde un saliente o tejado le costará mucho más alcanzarte.",
            "Use height to your advantage: from a ledge or rooftop it'll have a much harder time reaching you.")),
        Map.entry("enderman", new Tip(
            "Evita mirarlo directamente si no quieres pelear; un techo de dos bloques limita muchísimo sus movimientos.",
            "Avoid looking straight at it unless you want to fight; if you do attack it, do it under a low ceiling to limit its teleporting.")),
        Map.entry("witch", new Tip(
            "Atácala sin darle tiempo a beber pociones; cuanto más corta sea la pelea, menos oportunidades tendrá de curarse.",
            "Attack her before she has time to drink a healing potion; a splash Harming potion deals her heavy damage.")),
        Map.entry("drowned", new Tip(
            "Si puedes, oblígalo a pelear en tierra firme: fuera del agua pierde gran parte de su ventaja.",
            "If you can, force it to fight on dry land: out of the water it loses most of its edge.")),
        Map.entry("husk", new Tip(
            "Nunca confíes en que el amanecer lo eliminará; trátalo como un enemigo activo tanto de día como de noche.",
            "Never assume sunrise will finish it off; treat it as an active threat day and night.")),
        Map.entry("stray", new Tip(
            "No intercambies flechas con él: acércate rápido usando cobertura antes de que la Lentitud te deje en desventaja.",
            "Don't trade arrows with it: close the distance quickly using cover before Slowness puts you at a disadvantage.")),
        Map.entry("phantom", new Tip(
            "Mira al cielo de vez en cuando durante la noche y duerme regularmente para evitar que comiencen a aparecer.",
            "Glance at the sky now and then at night, and sleep regularly to keep them from spawning.")),
        Map.entry("slime", new Tip(
            "Antes de matar uno grande, asegúrate de tener espacio para moverte, porque terminarás rodeado por sus crías.",
            "Before killing a big one, make sure you have room to move, because you'll end up surrounded by its offspring.")),
        Map.entry("magma_cube", new Tip(
            "Espera el instante después de su salto para atacar, cuando queda expuesto durante un breve momento.",
            "Wait for the moment right after its jump to attack, when it's briefly exposed.")),
        Map.entry("blaze", new Tip(
            "Usa columnas o paredes como cobertura y acércate entre cada ráfaga en lugar de correr de frente.",
            "Use pillars or walls as cover and close in between volleys instead of running straight at it.")),
        Map.entry("ghast", new Tip(
            "Espera hasta el último instante y devuelve la bola de fuego con la espada para dañarlo con su propio ataque.",
            "Wait until the last moment and knock the fireball back with your sword to damage it with its own attack.")),
        Map.entry("piglin", new Tip(
            "Antes de explorar un bastión o un bosque carmesí, equípate con algo de oro y evita abrir cofres frente a ellos.",
            "Before exploring a bastion or crimson forest, gear up with some gold and avoid opening chests in front of them.")),
        Map.entry("hoglin", new Tip(
            "Pelea desde obstáculos o usa fuego de alma para mantenerlo alejado mientras atacas con seguridad.",
            "Fight from behind obstacles or use soul fire to keep it at bay while you attack safely.")),
        Map.entry("pillager", new Tip(
            "Si tiene la ballesta cargada, usa cobertura hasta que dispare y aprovecha su recarga para atacar.",
            "If its crossbow is loaded, use cover until it fires and take advantage of its reload to attack.")),
        Map.entry("vindicator", new Tip(
            "Nunca lo subestimes: su enorme daño hace que unos pocos golpes basten para acabar con jugadores mal protegidos.",
            "Never underestimate it: its huge damage means just a few hits can finish off a poorly-armored player.")),
        Map.entry("evoker", new Tip(
            "Conviértelo siempre en tu prioridad durante una incursión; mientras siga vivo continuará llenando el campo de vex.",
            "Always make it your priority during a raid; while it's alive it'll keep filling the field with vexes.")),
        Map.entry("vex", new Tip(
            "Si hay un evocador cerca, ignora a los vex el tiempo suficiente para eliminar primero a quien sigue invocándolos.",
            "If there's an evoker nearby, ignore the vexes long enough to take out the one still summoning them.")),
        Map.entry("guardian", new Tip(
            "Rompe la línea de visión escondiéndote detrás de bloques para interrumpir su rayo antes de que cargue por completo.",
            "Break its line of sight by hiding behind blocks to interrupt its laser before it fully charges.")),
        Map.entry("elder_guardian", new Tip(
            "Explora primero el monumento y derrótalo antes de intentar minar sus paredes o buscar sus tesoros.",
            "Scout the monument first and defeat it before trying to mine its walls or look for its treasure.")),
        Map.entry("ravager", new Tip(
            "Muévete hacia los lados cuando cargue: esquivar su embestida es mucho más efectivo que intentar bloquearla.",
            "Sidestep when it charges: dodging its rush is far more effective than trying to block it.")),
        Map.entry("warden", new Tip(
            "Tu objetivo no es derrotarlo, sino evitarlo; agáchate, usa lana para reducir las vibraciones y no hagas ruido innecesario.",
            "Your goal isn't to beat it, but to avoid it; sneak, use wool to muffle vibrations, and don't make unnecessary noise.")),
        Map.entry("wither", new Tip(
            "Prepara el terreno antes de invocarlo: una arena controlada vale mucho más que intentar improvisar durante la pelea.",
            "Prepare the arena before summoning it: a controlled space is worth far more than improvising mid-fight.")),
        Map.entry("ender_dragon", new Tip(
            "Destruye primero todos los cristales de los pilares; mientras existan, seguirá recuperando vida constantemente.",
            "Destroy all the crystals on the pillars first; while they exist, the dragon keeps healing nonstop.")),
        Map.entry("creaking", new Tip(
            "Mantenlo a la vista para inmovilizarlo y busca rápidamente el Corazón del Creaking; destruirlo es la clave para vencerlo.",
            "Keep it in view to freeze it in place and quickly find the Creaking Heart; destroying it is the key to beating it.")),
        Map.entry("zoglin", new Tip(
            "Mantén la distancia y aprovecha obstáculos estrechos para evitar sus brutales embestidas.",
            "Keep your distance and use narrow chokepoints to avoid its brutal charges.")),
        Map.entry("breeze", new Tip(
            "Nunca pelees al borde de un precipicio; su mayor peligro suele ser empujarte, no el daño directo.",
            "Never fight it at the edge of a drop; its biggest danger is usually the knockback, not the direct damage.")),
        Map.entry("camel_husk", new Tip(
            "Concéntrate primero en los jinetes; eliminar al husk y al parched convierte una emboscada peligrosa en una recompensa muy útil.",
            "Focus on the riders first; clearing the husk and the parched turns a dangerous ambush into a very useful reward.")),
        Map.entry("zombie_horseman", new Tip(
            "Derriba primero al jinete y evita permanecer frente a su carga; la movilidad del caballo es su mayor ventaja.",
            "Knock the rider off first and avoid standing in front of its charge; the horse's mobility is its biggest edge.")),
        Map.entry("baby_zombie", new Tip(
            "No ataques desesperadamente: espera el momento en que se acerque y usa un arma con buen alcance para golpearlo con seguridad.",
            "Don't attack wildly; wait for the moment it closes in and use a weapon with good reach to hit it safely.")),
        Map.entry("chicken_jockey", new Tip(
            "Intenta eliminar primero al bebé zombi; dejar la gallina viva hace mucho más sencillo controlar el combate.",
            "Try to kill the baby zombie first; leaving the chicken alive makes the fight much easier to control.")),
        Map.entry("skeleton_horseman", new Tip(
            "Sepáralos usando el terreno; enfrentar a los cuatro arqueros al mismo tiempo suele ser mucho más peligroso que dividir la pelea.",
            "Split them up using the terrain; taking on all four archers at once is usually far more dangerous than dividing the fight.")),
        Map.entry("spider_jockey", new Tip(
            "Busca terreno abierto y elimina primero al esqueleto; sin el arquero, la araña vuelve a ser una amenaza mucho menor.",
            "Look for open ground and take out the skeleton first; without the archer, the spider becomes a much smaller threat.")),
        Map.entry("zombified_piglin", new Tip(
            "Nunca ataques a uno por accidente cuando haya un grupo cerca; si necesitas pelear, aleja primero a los demás.",
            "Never attack one by accident when a group is nearby; if you must fight, lure the others away first.")),
        Map.entry("silverfish", new Tip(
            "Si encuentras muchos juntos, evita romper piedra al azar y elimina primero los que ya hayan salido de los bloques.",
            "If you find a bunch of them, avoid mining stone at random and take out the ones already out of the blocks first.")),
        Map.entry("bogged", new Tip(
            "Lleva leche o evita recibir disparos innecesarios; el veneno puede desgastarte incluso después de terminar el combate.",
            "Bring milk or avoid taking unnecessary hits; the poison can wear you down even after the fight is over.")),
        Map.entry("zombie_villager", new Tip(
            "Si encuentras un buen comerciante zombificado, vale mucho más la pena curarlo que eliminarlo.",
            "If you find a good trader gone zombie, curing it is worth far more than killing it.")),
        Map.entry("endermite", new Tip(
            "Si aparece durante un combate, elimínala rápido antes de que se convierta en una molestia adicional entre varios endermen.",
            "If it shows up mid-fight, kill it quickly before it becomes an extra hassle among several endermen.")),
        Map.entry("piglin_brute", new Tip(
            "En un bastión, identifica primero dónde está; entrar sin localizarlo puede terminar en una emboscada devastadora.",
            "In a bastion, locate it first; walking in blind can end in a devastating ambush.")),
        Map.entry("cave_spider", new Tip(
            "Destruye primero el generador si es posible; luchar contra oleadas infinitas siempre será peor que eliminar su origen.",
            "Destroy the spawner if you can — fighting endless waves is always worse than removing the source.")),
        Map.entry("shulker", new Tip(
            "Nunca ignores la levitación: lleva cubos de agua o Perlas de Ender para evitar una caída mortal cuando termine el efecto.",
            "Never ignore the levitation; bring water buckets or Ender Pearls to avoid a deadly fall once it wears off."))
    );

    public static String tip(Entry e, boolean english) {
        Tip t = TIPS.get(e.iconId());
        if (t == null) return "";
        return english ? t.en() : t.es();
    }

    // Imagen de detalle ilustrada a mano (dibujo completo, no un ícono) para la página
    // derecha del Bestiario. Solo unos pocos mobs la tienen por ahora — el resto sigue
    // usando el ícono grande animado como antes (ver QuestBookScreen#drawBestiarySubpage).
    // Map.ofEntries (no Map.of): Map.of solo tiene overloads hasta 10 pares — con 11
    // ilustraciones ya no entra.
    private static final Map<String, String> DETAIL_IMAGE = Map.ofEntries(
        Map.entry("zombie", "zombie"),
        Map.entry("enderman", "enderman"),
        Map.entry("skeleton", "skeleton"),
        Map.entry("ender_dragon", "ender_dragon"),
        Map.entry("warden", "warden"),
        Map.entry("guardian", "guardian"),
        Map.entry("zombified_piglin", "zombified_piglin"),
        Map.entry("creaking", "creaking"),
        Map.entry("ghast", "ghast"),
        Map.entry("elder_guardian", "elder_guardian"),
        Map.entry("cave_spider", "cave_spider"),
        Map.entry("piglin", "piglin"),
        Map.entry("wither", "wither"),
        Map.entry("slime", "slime"),
        Map.entry("piglin_brute", "piglin_brute"),
        Map.entry("creeper", "creeper"),
        Map.entry("evoker", "evoker"),
        Map.entry("vindicator", "vindicator"),
        Map.entry("shulker", "shulker"),
        Map.entry("vex", "vex"),
        Map.entry("zombie_villager", "zombie_villager"),
        Map.entry("witch", "witch"),
        Map.entry("phantom", "phantom"),
        Map.entry("chicken_jockey", "chicken_jockey"),
        Map.entry("baby_zombie", "baby_zombie"),
        Map.entry("zoglin", "zoglin"),
        Map.entry("pillager", "pillager"),
        Map.entry("wither_skeleton", "wither_skeleton"),
        Map.entry("spider", "spider"),
        Map.entry("drowned", "drowned"),
        Map.entry("blaze", "blaze"),
        Map.entry("hoglin", "hoglin"),
        Map.entry("husk", "husk"),
        Map.entry("silverfish", "silverfish"),
        Map.entry("ravager", "ravager"),
        Map.entry("spider_jockey", "spider_jockey"),
        Map.entry("endermite", "endermite"),
        Map.entry("bogged", "bogged"),
        Map.entry("skeleton_horseman", "skeleton_horseman"),
        Map.entry("camel_husk", "camel_husk"),
        Map.entry("stray", "stray"),
        Map.entry("zombie_horseman", "zombie_horseman"),
        Map.entry("breeze", "breeze"),
        Map.entry("magma_cube", "magma_cube")
    );

    public static boolean hasDetailImage(Entry e) { return e.iconId() != null && DETAIL_IMAGE.containsKey(e.iconId()); }

    // NOTA: antes había acá un detailImage()/detailImageSize() + un mapa
    // DETAIL_IMAGE_SIZE con el tamaño real de cada detail.png — quedaron sin uso
    // (código muerto) desde que TODOS los mobs se migraron a char.png+scene.png
    // (ver characterImage()/sceneImage() más abajo); hasDetailImage() se sigue
    // usando como chequeo de "¿este mob tiene arte?" pero nada vuelve a pedir el
    // detail.png viejo en sí. Se eliminaron ambos junto con el mapa de tamaños
    // (Sep 2026) — si hace falta recuperarlos, están en el historial de versiones.

    // ── Layout nuevo: personaje y escena como imágenes SEPARADAS ──────────────
    // Antes cada detail.png traía el personaje (arriba) y la escena (abajo) en UNA
    // sola imagen con el fondo de pergamino de por medio, y el layout viejo la
    // bliteaba entera en un solo hueco grande. A pedido del usuario (ver imagen de
    // referencia que mandó) ahora van en dos recuadros separados de tamaño parecido
    // (columna de texto a la izquierda, Foto 1 = personaje arriba a la derecha,
    // Foto 2 = escena abajo a la derecha, franja de ítems abajo del todo).
    //
    // char.png / scene.png son RECORTES AJUSTADOS solo al dibujo (fondo de
    // pergamino descartado al máximo posible, ver historial: detección de
    // contenido por HSV — oscuro o saturado y con matiz fuera del rango
    // pergamino/rojo — con el borde rojo y las esquinas decorativas excluidos por
    // margen). Se generaron automáticamente para los 44 mobs partiendo de sus
    // detail.png ya existentes; el propio detail.png se deja en el disco sin usar
    // por si hace falta volver a cortar alguno a mano.
    //
    // Igual que con DETAIL_IMAGE_SIZE: estos tamaños tienen que coincidir con el
    // archivo real en disco o la imagen sale distorsionada al blitear.
    private static final Map<String, int[]> CHARACTER_IMAGE_SIZE = Map.ofEntries(
        Map.entry("zombie", new int[]{297, 500}),
        Map.entry("enderman", new int[]{315, 525}),
        Map.entry("skeleton", new int[]{313, 382}),
        Map.entry("ender_dragon", new int[]{288, 348}),
        Map.entry("warden", new int[]{294, 384}),
        Map.entry("guardian", new int[]{280, 284}),
        Map.entry("zombified_piglin", new int[]{302, 438}),
        Map.entry("creaking", new int[]{174, 423}),
        Map.entry("ghast", new int[]{237, 349}),
        Map.entry("elder_guardian", new int[]{295, 335}),
        Map.entry("cave_spider", new int[]{358, 360}),
        Map.entry("piglin", new int[]{326, 469}),
        Map.entry("wither", new int[]{325, 384}),
        Map.entry("slime", new int[]{305, 387}),
        Map.entry("piglin_brute", new int[]{324, 469}),
        Map.entry("creeper", new int[]{203, 379}),
        Map.entry("evoker", new int[]{204, 375}),
        Map.entry("vindicator", new int[]{275, 386}),
        Map.entry("shulker", new int[]{254, 318}),
        Map.entry("vex", new int[]{358, 362}),
        Map.entry("zombie_villager", new int[]{239, 427}),
        Map.entry("witch", new int[]{327, 424}),
        Map.entry("phantom", new int[]{393, 316}),
        Map.entry("chicken_jockey", new int[]{529, 419}),
        Map.entry("baby_zombie", new int[]{186, 409}),
        Map.entry("zoglin", new int[]{382, 358}),
        Map.entry("pillager", new int[]{265, 364}),
        Map.entry("wither_skeleton", new int[]{308, 474}),
        Map.entry("spider", new int[]{497, 356}),
        Map.entry("drowned", new int[]{271, 431}),
        Map.entry("blaze", new int[]{217, 352}),
        Map.entry("hoglin", new int[]{413, 383}),
        Map.entry("husk", new int[]{242, 398}),
        Map.entry("silverfish", new int[]{252, 159}),
        Map.entry("ravager", new int[]{416, 399}),
        Map.entry("spider_jockey", new int[]{321, 345}),
        Map.entry("endermite", new int[]{251, 203}),
        Map.entry("bogged", new int[]{276, 416}),
        Map.entry("skeleton_horseman", new int[]{321, 454}),
        Map.entry("camel_husk", new int[]{313, 474}),
        Map.entry("stray", new int[]{262, 430}),
        Map.entry("zombie_horseman", new int[]{364, 465}),
        Map.entry("breeze", new int[]{377, 478}),
        Map.entry("magma_cube", new int[]{370, 440})
    );

    private static final Map<String, int[]> SCENE_IMAGE_SIZE = Map.ofEntries(
        Map.entry("zombie", new int[]{274, 289}),
        Map.entry("enderman", new int[]{347, 301}),
        Map.entry("skeleton", new int[]{360, 422}),
        Map.entry("ender_dragon", new int[]{283, 257}),
        Map.entry("warden", new int[]{281, 267}),
        Map.entry("guardian", new int[]{363, 452}),
        Map.entry("zombified_piglin", new int[]{316, 363}),
        Map.entry("creaking", new int[]{267, 281}),
        Map.entry("ghast", new int[]{315, 365}),
        Map.entry("elder_guardian", new int[]{301, 328}),
        Map.entry("cave_spider", new int[]{320, 433}),
        Map.entry("piglin", new int[]{302, 332}),
        Map.entry("wither", new int[]{301, 358}),
        Map.entry("slime", new int[]{303, 365}),
        Map.entry("piglin_brute", new int[]{302, 333}),
        Map.entry("creeper", new int[]{321, 459}),
        Map.entry("evoker", new int[]{551, 464}),
        Map.entry("vindicator", new int[]{320, 432}),
        Map.entry("shulker", new int[]{344, 451}),
        Map.entry("vex", new int[]{312, 380}),
        Map.entry("zombie_villager", new int[]{288, 372}),
        Map.entry("witch", new int[]{279, 275}),
        Map.entry("phantom", new int[]{302, 452}),
        Map.entry("chicken_jockey", new int[]{472, 420}),
        Map.entry("baby_zombie", new int[]{298, 358}),
        Map.entry("zoglin", new int[]{435, 447}),
        Map.entry("pillager", new int[]{365, 475}),
        Map.entry("wither_skeleton", new int[]{303, 326}),
        Map.entry("spider", new int[]{578, 423}),
        Map.entry("drowned", new int[]{291, 381}),
        Map.entry("blaze", new int[]{551, 481}),
        Map.entry("hoglin", new int[]{306, 355}),
        Map.entry("husk", new int[]{402, 423}),
        Map.entry("silverfish", new int[]{367, 499}),
        Map.entry("ravager", new int[]{356, 422}),
        Map.entry("spider_jockey", new int[]{405, 417}),
        Map.entry("endermite", new int[]{280, 359}),
        Map.entry("bogged", new int[]{400, 408}),
        Map.entry("skeleton_horseman", new int[]{333, 385}),
        Map.entry("camel_husk", new int[]{370, 293}),
        Map.entry("stray", new int[]{279, 284}),
        Map.entry("zombie_horseman", new int[]{368, 328}),
        Map.entry("breeze", new int[]{369, 323}),
        Map.entry("magma_cube", new int[]{375, 395})
    );

    public static Identifier characterImage(Entry e) {
        if (e.iconId() == null || !CHARACTER_IMAGE_SIZE.containsKey(e.iconId())) return null;
        return Identifier.fromNamespaceAndPath("homequest", "textures/gui/bestiary/" + e.iconId() + "/char.png");
    }
    public static int[] characterImageSize(Entry e) { return e.iconId() == null ? null : CHARACTER_IMAGE_SIZE.get(e.iconId()); }

    public static Identifier sceneImage(Entry e) {
        if (e.iconId() == null || !SCENE_IMAGE_SIZE.containsKey(e.iconId())) return null;
        return Identifier.fromNamespaceAndPath("homequest", "textures/gui/bestiary/" + e.iconId() + "/scene.png");
    }
    public static int[] sceneImageSize(Entry e) { return e.iconId() == null ? null : SCENE_IMAGE_SIZE.get(e.iconId()); }

    /**
     * Loot real que muestra la página en vez de los íconos dibujados a mano (esos venían
     * mal en varios mobs — items que ese mob ni siquiera suelta). Reemplazo: se dibuja
     * la textura de ítem REAL del juego (16x16, la misma que usa el inventario) en el
     * mismo hueco de la franja de abajo que antes ocupaban los íconos dibujados — ver
     * QuestBookScreen#drawBestiaryLootIcons — y al pasar el mouse por encima aparece su
     * nombre + una nota corta de probabilidad/condición.
     *
     * OJO — por qué esto usa `itemId` (string) y nombres a mano en vez de Item/ItemStack:
     * la primera versión llamaba a g.renderItem(...)/g.renderItemDecorations(...), que
     * no existen en GuiGraphicsExtractor de este proyecto (error de compilación real,
     * ver historial). Esta versión en cambio dibuja la textura del ítem con g.blit(...),
     * EXACTAMENTE el mismo método que ya usa este archivo para el resto de los íconos
     * (drawBestiaryIcon, la ilustración grande, etc.) — no depende de ningún método
     * nuevo/no probado. Las texturas de ítems vanilla viven en
     * minecraft:textures/item/<id>.png (14/16px reales, se ven nítidas a este tamaño;
     * son las mismas PNG que arma el atlas, existen sueltas igual y blitean bien).
     *
     * Solo tiene entrada acá un mob si a su detail.png YA se le sacó la franja de
     * íconos dibujados (recortada con la técnica de "restaurar bracket rojo", ver
     * historial); si un mob todavía tiene los íconos viejos horneados en la imagen, NO
     * agregar acá o quedarían doble.
     */
    public record LootIcon(String itemId, String nameEs, String nameEn, String noteEs, String noteEn) {}

    private static final Map<String, LootIcon[]> LOOT_ICONS = Map.ofEntries(
        Map.entry("piglin", new LootIcon[]{
            new LootIcon("gold_nugget", "Pepita de Oro", "Gold Nugget", "0-2 al morir", "0-2 on death")
        }),
        Map.entry("piglin_brute", new LootIcon[]{
            new LootIcon("golden_axe", "Hacha de Oro", "Golden Axe", "Su hacha equipada, si tú das el golpe final", "Its equipped axe, if you get the kill")
        }),
        Map.entry("wither", new LootIcon[]{
            new LootIcon("nether_star", "Estrella del Nether", "Nether Star", "Siempre suelta 1", "Always drops 1")
        }),
        Map.entry("slime", new LootIcon[]{
            new LootIcon("slime_ball", "Bola de Slime", "Slimeball", "0-2, solo los de tamaño chico", "0-2, small size only")
        }),
        Map.entry("cave_spider", new LootIcon[]{
            new LootIcon("string", "Hilo", "String", "0-2 al morir", "0-2 on death"),
            new LootIcon("spider_eye", "Ojo de Araña", "Spider Eye", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("creeper", new LootIcon[]{
            new LootIcon("gunpowder", "Pólvora", "Gunpowder", "0-2 al morir", "0-2 on death"),
            new LootIcon("music_disc_13", "Disco de Música", "Music Disc", "Si lo mata un esqueleto/stray", "If a skeleton/stray lands the kill")
        }),
        Map.entry("evoker", new LootIcon[]{
            new LootIcon("totem_of_undying", "Tótem de la Inmortalidad", "Totem of Undying", "Siempre suelta 1", "Always drops 1")
        }),
        Map.entry("vindicator", new LootIcon[]{
            new LootIcon("emerald", "Esmeralda", "Emerald", "0-1, solo si viene de una incursión", "0-1, raid spawns only"),
            new LootIcon("iron_axe", "Hacha de Hierro", "Iron Axe", "8.5% si nació con una equipada", "8.5% if it spawned with one equipped")
        }),
        Map.entry("shulker", new LootIcon[]{
            new LootIcon("shulker_shell", "Caparazón de Shulker", "Shulker Shell", "50% de probabilidad", "50% chance")
        }),
        Map.entry("zombie_villager", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 al morir", "0-2 on death"),
            new LootIcon("carrot", "Zanahoria", "Carrot", "2.5% en total (o papa/hierro)", "2.5% total (or potato/iron ingot)")
        }),
        Map.entry("witch", new LootIcon[]{
            new LootIcon("glowstone_dust", "Polvo de Piedra Luminosa", "Glowstone Dust", "12.5% de probabilidad", "12.5% chance"),
            new LootIcon("redstone", "Redstone", "Redstone", "12.5% de probabilidad", "12.5% chance"),
            new LootIcon("gunpowder", "Pólvora", "Gunpowder", "12.5% de probabilidad", "12.5% chance")
        }),
        Map.entry("phantom", new LootIcon[]{
            new LootIcon("phantom_membrane", "Membrana de Phantom", "Phantom Membrane", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("chicken_jockey", new LootIcon[]{
            new LootIcon("feather", "Pluma", "Feather", "0-2 (del pollo)", "0-2 (from the chicken)"),
            new LootIcon("chicken", "Pollo Crudo", "Raw Chicken", "1 (del pollo)", "1 (from the chicken)"),
            new LootIcon("carrot", "Zanahoria", "Carrot", "Rara, del bebé zombi (o papa/hierro)", "Rare, from the baby zombie (or potato/iron)")
        }),
        Map.entry("baby_zombie", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 al morir", "0-2 on death"),
            new LootIcon("carrot", "Zanahoria", "Carrot", "2.5% en total (o papa/hierro)", "2.5% total (or potato/iron ingot)")
        }),
        Map.entry("zoglin", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "1-3 al morir", "1-3 on death")
        }),
        // Estos 10 son los mobs "originales" (los que ya venían en el proyecto antes
        // de esta sesión): tenían íconos dibujados a mano horneados en el detail.png,
        // igual que el resto, así que también se les sacó esa franja y quedan acá.
        Map.entry("zombie", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 al morir", "0-2 on death"),
            new LootIcon("carrot", "Zanahoria", "Carrot", "2.5% en total (o papa/hierro)", "2.5% total (or potato/iron ingot)")
        }),
        Map.entry("enderman", new LootIcon[]{
            new LootIcon("ender_pearl", "Perla de Ender", "Ender Pearl", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("skeleton", new LootIcon[]{
            new LootIcon("bone", "Hueso", "Bone", "0-2 al morir", "0-2 on death"),
            new LootIcon("arrow", "Flecha", "Arrow", "0-2 al morir", "0-2 on death"),
            new LootIcon("bow", "Arco", "Bow", "8.5% si nació con uno equipado", "8.5% if it spawned with one equipped")
        }),
        Map.entry("ender_dragon", new LootIcon[]{
            new LootIcon("dragon_egg", "Huevo de Dragón", "Dragon Egg", "Solo la primera vez que lo derrotas", "Only on the first kill ever")
        }),
        Map.entry("warden", new LootIcon[]{
            new LootIcon("sculk_catalyst", "Catalizador de Sculk", "Sculk Catalyst", "Siempre suelta 1", "Always drops 1")
        }),
        Map.entry("guardian", new LootIcon[]{
            new LootIcon("prismarine_shard", "Fragmento de Prismarine", "Prismarine Shard", "0-2 al morir", "0-2 on death"),
            new LootIcon("prismarine_crystals", "Cristales de Prismarine", "Prismarine Crystals", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("zombified_piglin", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-1 al morir", "0-1 on death"),
            new LootIcon("gold_nugget", "Pepita de Oro", "Gold Nugget", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("ghast", new LootIcon[]{
            new LootIcon("gunpowder", "Pólvora", "Gunpowder", "0-2 al morir", "0-2 on death"),
            new LootIcon("ghast_tear", "Lágrima de Ghast", "Ghast Tear", "0-1, probabilidad baja", "0-1, low chance")
        }),
        Map.entry("elder_guardian", new LootIcon[]{
            new LootIcon("sponge", "Esponja", "Sponge", "Siempre suelta 1", "Always drops 1"),
            new LootIcon("prismarine_shard", "Fragmento de Prismarine", "Prismarine Shard", "0-2 al morir", "0-2 on death")
        }),
        Map.entry("pillager", new LootIcon[]{
            new LootIcon("crossbow", "Ballesta", "Crossbow", "8.5% si tú das el golpe final", "8.5% if you get the kill")
        }),
        Map.entry("wither_skeleton", new LootIcon[]{
            new LootIcon("bone", "Hueso", "Bone", "0-2 al morir", "0-2 on death"),
            new LootIcon("coal", "Carbón", "Coal", "0-2 al morir", "0-2 on death")
            // El cráneo (2.5%, el drop más icónico) NO se puede mostrar como ícono:
            // los cráneos/cabezas en Minecraft se renderizan con un modelo 3D
            // especial (igual que las cabezas de jugador), no con una textura plana
            // — un blit directo a "textures/item/wither_skeleton_skull.png" sale
            // como textura rota (el cuadriculado magenta/negro que se vio en el
            // juego). Se deja afuera en vez de mostrar algo roto.
        }),
        Map.entry("spider", new LootIcon[]{
            new LootIcon("string", "Hilo", "String", "0-2 al morir", "0-2 on death"),
            new LootIcon("spider_eye", "Ojo de Araña", "Spider Eye", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("drowned", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 al morir", "0-2 on death"),
            new LootIcon("trident", "Tridente", "Trident", "6.25%, si nació con uno", "6.25%, if it spawned holding one"),
            new LootIcon("copper_ingot", "Lingote de Cobre", "Copper Ingot", "11% de probabilidad", "11% chance")
        }),
        Map.entry("blaze", new LootIcon[]{
            new LootIcon("blaze_rod", "Vara de Blaze", "Blaze Rod", "50% si tú das el golpe final", "50% if you get the kill")
        }),
        Map.entry("hoglin", new LootIcon[]{
            new LootIcon("porkchop", "Chuleta de Cerdo Cruda", "Raw Porkchop", "2-4 al morir", "2-4 on death"),
            new LootIcon("leather", "Cuero", "Leather", "0-1 al morir", "0-1 on death")
        }),
        Map.entry("husk", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 al morir", "0-2 on death"),
            new LootIcon("carrot", "Zanahoria", "Carrot", "2.5% en total (o papa/hierro)", "2.5% total (or potato/iron ingot)")
        }),
        Map.entry("ravager", new LootIcon[]{
            new LootIcon("saddle", "Silla de Montar", "Saddle", "Siempre suelta 1", "Always drops 1")
        }),
        Map.entry("spider_jockey", new LootIcon[]{
            new LootIcon("bone", "Hueso", "Bone", "0-2 (del esqueleto)", "0-2 (from the skeleton)"),
            new LootIcon("arrow", "Flecha", "Arrow", "0-2 (del esqueleto)", "0-2 (from the skeleton)"),
            new LootIcon("string", "Hilo", "String", "0-2 (de la araña)", "0-2 (from the spider)")
        }),
        Map.entry("bogged", new LootIcon[]{
            new LootIcon("bone", "Hueso", "Bone", "0-2 al morir", "0-2 on death"),
            new LootIcon("arrow", "Flecha", "Arrow", "0-2 al morir", "0-2 on death"),
            // "tipped_arrow" (el ítem real de Flecha de Veneno) usa una textura de
            // dos capas con tinte de color dinámico según la poción — no es un
            // archivo plano bliteable directo, sale como textura rota. Se usa la
            // flecha normal como ícono; la nota de abajo ya aclara que es de veneno.
            new LootIcon("arrow", "Flecha de Veneno", "Arrow of Poison", "50% de probabilidad", "50% chance")
        }),
        Map.entry("skeleton_horseman", new LootIcon[]{
            new LootIcon("bone", "Hueso", "Bone", "0-2 al morir", "0-2 on death"),
            new LootIcon("arrow", "Flecha", "Arrow", "0-2 al morir", "0-2 on death"),
            new LootIcon("bow", "Arco", "Bow", "8.5% si tú das el golpe final", "8.5% if you get the kill")
        }),
        Map.entry("camel_husk", new LootIcon[]{
            // Mob propio del pack (no es vanilla), monta un husk + un "parched" — no
            // hay tabla de drops "oficial" para copiar, así que se armó una razonable
            // combinando el loot típico de cada jinete (husk = carne podrida, el
            // arquero "parched" = flechas).
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 (del husk)", "0-2 (from the husk)"),
            new LootIcon("arrow", "Flecha", "Arrow", "0-2 (del \"parched\")", "0-2 (from the \"parched\")")
        }),
        Map.entry("stray", new LootIcon[]{
            new LootIcon("bone", "Hueso", "Bone", "0-2 al morir", "0-2 on death"),
            new LootIcon("arrow", "Flecha", "Arrow", "0-2 al morir", "0-2 on death"),
            // mismo caso que en "bogged": la Flecha de Lentitud es una textura con
            // tinte dinámico (no bliteable directo), se usa flecha normal.
            new LootIcon("arrow", "Flecha de Lentitud", "Arrow of Slowness", "Hasta 50%, si tú o tu lobo dan el golpe final", "Up to 50%, if you or your wolf get the kill")
        }),
        Map.entry("zombie_horseman", new LootIcon[]{
            new LootIcon("rotten_flesh", "Carne Podrida", "Rotten Flesh", "0-2 al morir", "0-2 on death"),
            new LootIcon("carrot", "Zanahoria", "Carrot", "2.5% en total (o papa/hierro)", "2.5% total (or potato/iron ingot)")
        }),
        Map.entry("breeze", new LootIcon[]{
            new LootIcon("breeze_rod", "Vara de Breeze", "Breeze Rod", "Siempre suelta 1", "Always drops 1")
        }),
        Map.entry("magma_cube", new LootIcon[]{
            new LootIcon("magma_cream", "Crema de Magma", "Magma Cream", "Probabilidad baja (mucho más con Looting)", "Low chance (much higher with Looting)")
        })
        // creaking, vex, silverfish y endermite: sin entrada a propósito — ninguno de
        // los cuatro suelta nada al morir. Sus páginas no muestran íconos de loot, que
        // es lo correcto.
    );

    public static boolean hasLootIcons(Entry e) { return e.iconId() != null && LOOT_ICONS.containsKey(e.iconId()); }
    public static LootIcon[] lootIcons(Entry e) { return e.iconId() == null ? null : LOOT_ICONS.get(e.iconId()); }

    private static final Map<String, Identifier> LOOT_ICON_TEX_CACHE = new HashMap<>();

    // Verificado contra el jar real del cliente (minecraft-clientOnly-26.2): estos 4
    // itemId NO tienen archivo en textures/item/<id>.png, así que blitear esa ruta
    // devolvía la textura "missing" (el cuadriculado rosa/negro) — el ítem "no
    // aparecía" como tal en el ícono aunque el tooltip (que es solo texto a mano,
    // nameEs/nameEn) seguía mostrando el nombre bien.
    // - crossbow: no existe un "crossbow.png" plano, solo variantes de animación de
    //   carga (crossbow_standby/_pulling_N/_arrow/_firework) — se usa la de reposo
    //   (sin cargar), que es el ícono que se ve siempre en inventario. Es un ítem
    //   normal (textura plana vanilla real), así que sigue viviendo en minecraft:.
    // - sponge: bloque con una sola textura pareja en las 6 caras — la cara plana
    //   de textures/block/ se ve igual que el ícono real, así que sigue en minecraft:.
    // - dragon_egg / sculk_catalyst: NO tienen una sola textura plana que se parezca
    //   al ícono real de inventario (que es un render isométrico del bloque, no una
    //   cara plana) — usar la textura de bloque tal cual se veía mal/distinto al
    //   ícono real (confirmado con capturas + referencias del usuario). Se generaron
    //   íconos propios de 16x16 a partir de esas referencias (recortados a su bounding
    //   box y reescalados) y se empaquetan como recurso del mod en
    //   assets/homequest/textures/item/, de ahí el namespace "homequest:" en vez de
    //   "minecraft:" para estos dos.
    private static final Map<String, Identifier> LOOT_ICON_PATH_OVERRIDE = Map.of(
        "crossbow", Identifier.fromNamespaceAndPath("minecraft", "textures/item/crossbow_standby.png"),
        "sponge", Identifier.fromNamespaceAndPath("minecraft", "textures/block/sponge.png"),
        // TNT es un bloque de 3 caras distintas (arriba/costado/abajo) — no hay un
        // "tnt.png" único, ni como ítem ni como bloque uniforme. "tnt_side.png" es
        // la cara que mejor se lee sola como ícono chico.
        "tnt", Identifier.fromNamespaceAndPath("minecraft", "textures/block/tnt_side.png"),
        "dragon_egg", Identifier.fromNamespaceAndPath("homequest", "textures/item/dragon_egg_icon.png"),
        "sculk_catalyst", Identifier.fromNamespaceAndPath("homequest", "textures/item/sculk_catalyst_icon.png"),
        // Estas 2 SÍ tienen ícono real — no genérico — porque este mismo mod ya
        // trae un recorte de cabeza propio para la pestaña del wither y para el
        // creeper (ver TAB_WITHER/TAB_CREEPER en QuestBookScreen). Reusarlos acá
        // es gratis y da el ícono correcto de verdad, no una aproximación.
        "wither_skeleton_skull", Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/wither.png"),
        "creeper_head", Identifier.fromNamespaceAndPath("homequest", "textures/gui/tab_icons/creeper.png")
    );

    // Bloques de textura uniforme (misma cara en los 6 lados) que en vanilla NO
    // tienen un PNG propio en textures/item/ — su modelo de ítem apunta directo a
    // textures/block/<id>.png. Lista a mano y no "todo BlockItem" a propósito:
    // la primera versión de esto usaba `item instanceof BlockItem` para decidir,
    // y rompió cosas que SÍ tenían ícono de ítem propio (zanahoria, papa, mesa de
    // encantamientos — las tres son BlockItem, pero cada una tiene su propio PNG
    // en textures/item/ que la regla general estaba pisando por error). Esta
    // lista es más corta de mantener, pero nunca rompe algo que ya andaba bien.
    private static final java.util.Set<String> UNIFORM_BLOCK_ITEMS = java.util.Set.of(
        "coal_block", "diamond_block", "emerald_block", "gold_block", "redstone_block",
        "glass", "honey_block", "sea_lantern", "bricks", "chiseled_stone_bricks",
        "stone", "oak_planks", "resin_bricks", "torch", "redstone_torch", "cobweb",
        "prismarine"
    );

    // CAUSA DE FONDO de losas/escaleras/trampillas/cofres/pistones/etc.: en vanilla,
    // el ícono de inventario de un bloque con FORMA (no un cubo liso) no es un PNG
    // plano — es un render 3D en vivo del modelo del bloque, hecho con el motor de
    // renderizado de ítems de Minecraft (GuiGraphics#renderItem). Ese método no
    // existe en el GuiGraphicsExtractor de este proyecto (se intentó y no compiló,
    // ver el comentario de lootIconTexture más abajo) — por eso acá solo se puede
    // blitear PNGs sueltos, y para estos bloques NO HAY un PNG suelto que mostrar,
    // ni como ítem ni como bloque. No es un id mal escrito ni una excepción que
    // faltó agregar: es una limitación real de qué se puede dibujar acá.
    //
    // Mientras tanto, se usa una textura "de la familia" como aproximación visual
    // (mismo material, forma distinta) — mejor que el error rosa, pero no es el
    // ícono real. Si en algún momento se consigue un PNG de ícono propio para
    // alguno de estos (como ya se hizo para dragon_egg/sculk_catalyst), va a
    // LOOT_ICON_PATH_OVERRIDE de arriba y sale de esta lista.
    private static final Map<String, Identifier> SHAPED_BLOCK_APPROXIMATION = Map.ofEntries(
        Map.entry("oak_slab", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("oak_stairs", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("oak_trapdoor", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("oak_fence", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("oak_door", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("oak_button", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("oak_pressure_plate", Identifier.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png")),
        Map.entry("iron_trapdoor", Identifier.fromNamespaceAndPath("minecraft", "textures/block/iron_block.png"))
    );

    // Cabezas sin ícono propio conseguido todavía (a diferencia de wither/creeper,
    // que ya están arriba con su ícono real) — mismo problema de fondo que
    // SHAPED_BLOCK_APPROXIMATION: no hay PNG plano de una cabeza en vanilla, ni
    // siquiera para player_head. Usan el ícono de un libro como placeholder
    // neutro (mejor eso que el error rosa) hasta tener un ícono de verdad para
    // cada una — ver la respuesta sobre qué mandar para estas 4.
    private static final java.util.Set<String> HEAD_ITEMS_NEEDING_ICON = java.util.Set.of(
        "player_head", "dragon_head", "skeleton_skull", "zombie_head", "piglin_head"
    );

    /** Textura de un ítem por su id (sin el "minecraft:"), aplicando las mismas
     *  excepciones que lootIconTexture (ver LOOT_ICON_PATH_OVERRIDE) — expuesto
     *  público para que QuestBookScreen lo reuse también para los íconos de
     *  recompensa de misión, no solo para el loot del Bestiario. Mismo criterio,
     *  un solo lugar donde arreglar una textura que se vea mal.
     *
     * Ver los comentarios de UNIFORM_BLOCK_ITEMS, SHAPED_BLOCK_APPROXIMATION y
     * HEAD_ITEMS_NEEDING_ICON arriba para el porqué de cada excepción — a
     * propósito NO se intenta adivinar con una regla general (item instanceof
     * BlockItem), eso fue lo que rompió zanahoria/papa/mesa de encantamientos en
     * el intento anterior. */
    public static Identifier itemIconTexture(String itemId) {
        Identifier override = LOOT_ICON_PATH_OVERRIDE.get(itemId);
        if (override != null) return override;
        Identifier approx = SHAPED_BLOCK_APPROXIMATION.get(itemId);
        if (approx != null) return approx;
        if (HEAD_ITEMS_NEEDING_ICON.contains(itemId)) {
            return Identifier.fromNamespaceAndPath("minecraft", "textures/item/book.png");
        }
        if (UNIFORM_BLOCK_ITEMS.contains(itemId)) {
            return Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + itemId + ".png");
        }
        return Identifier.fromNamespaceAndPath("minecraft", "textures/item/" + itemId + ".png");
    }

    /** Textura 16x16 real del ítem (la misma PNG que usa el inventario de Minecraft,
     *  salvo un par de excepciones con ícono propio — ver LOOT_ICON_PATH_OVERRIDE). */
    public static Identifier lootIconTexture(LootIcon li) {
        return LOOT_ICON_TEX_CACHE.computeIfAbsent(li.itemId(), Bestiary::itemIconTexture);
    }

    /** Frames a color (revelados, mob ya matado). null si la entrada no tiene arte. */
    public static Identifier[] frames(Entry e) {
        if (!e.hasArt()) return null;
        return FRAME_CACHE.computeIfAbsent(e.iconId(), id -> {
            Identifier[] arr = new Identifier[e.frameCount()];
            for (int i = 0; i < e.frameCount(); i++) {
                arr[i] = Identifier.fromNamespaceAndPath("homequest", "textures/gui/bestiary/" + id + "/frame_" + i + ".png");
            }
            return arr;
        });
    }

    /** Silueta negra (sin matar todavía). null si la entrada no tiene arte. */
    public static Identifier shadow(Entry e) {
        if (!e.hasArt()) return null;
        return SHADOW_CACHE.computeIfAbsent(e.iconId(), id ->
            Identifier.fromNamespaceAndPath("homequest", "textures/gui/bestiary/" + id + "/shadow.png"));
    }

    public static final List<Entry> ENTRIES = List.of(
        new Entry(EntityTypes.ZOMBIE, "Zombi", "Zombie", false, "Classic (2009)",
            "El no-muerto más común de Minecraft. Persigue a los jugadores cuerpo a cuerpo y arde con la luz del sol.",
            "The most common undead in Minecraft. Chases players in melee and burns in sunlight.",
            "En dificultad Difícil puede romper puertas de madera y sorprenderte incluso dentro de tu propia casa.",
            "On Hard difficulty it can break down wooden doors and surprise you even inside your own house.",
            "zombie", 24, 69, 140),
        new Entry(EntityTypes.SKELETON, "Esqueleto", "Skeleton", false, "Classic (2009)",
            "No-muerto que dispara flechas a distancia.",
            "Undead mob that shoots arrows from range.",
            "Si un Esqueleto mata por casualidad a un Creeper, puede que te deje caer una pequeña sorpresa.",
            "If a Skeleton accidentally kills a Creeper, it might drop you a little surprise.",
            "skeleton", 24, 67, 140),
        new Entry(EntityTypes.WITHER_SKELETON, "Esqueleto Wither", "Wither Skeleton", false, "Beta 1.9-pre (2011)",
            "Una oscura variante del esqueleto que habita las fortalezas del Nether y aplica Wither con cada golpe.",
            "A dark variant of the skeleton that lives in Nether fortresses and inflicts Wither with every hit.",
            "Es el único mob capaz de soltar calaveras de Wither, necesarias para invocar al poderoso Wither.",
            "It's the only mob that can drop Wither Skulls, needed to summon the powerful Wither.",
            "wither_skeleton", 1, 103, 140),
        new Entry(EntityTypes.CREEPER, "Creeper", "Creeper", false, "Alpha (2009)",
            "Se acerca en silencio y explota. No tiene brazos.",
            "Sneaks up in silence and explodes. It has no arms.",
            "Nació de un error de código: Notch quiso programar un cerdo y confundió el alto con el largo del modelo.",
            "It was born from a coding mistake: Notch meant to make a pig and mixed up the model's height and length.",
            "creeper", 23, 70, 140),
        new Entry(EntityTypes.SPIDER, "Araña", "Spider", false, "Alpha (2010)",
            "Una criatura ágil capaz de trepar paredes y perseguirte por lugares donde otros mobs no pueden llegar.",
            "An agile creature that can climb walls and chase you through places other mobs can't reach.",
            "Durante el día se vuelve neutral si no la provocas, convirtiéndose en uno de los pocos enemigos con ese comportamiento.",
            "During the day it turns neutral if you don't provoke it, making it one of the few enemies that behaves that way.",
            "spider", 24, 172, 140),
        new Entry(EntityTypes.ENDERMAN, "Enderman", "Enderman", false, "Beta 1.8 (2011)",
            "Un misterioso habitante del End que puede teletransportarse y recoger bloques del mundo.",
            "A mysterious End dweller that can teleport and pick up blocks from the world.",
            "Puede llevarse bloques como tierra, arena o césped, siendo responsable de muchos paisajes ligeramente modificados.",
            "It can carry off blocks like dirt, sand, or grass, and is responsible for many slightly altered landscapes.",
            "enderman", 24, 47, 140),
        new Entry(EntityTypes.WITCH, "Bruja", "Witch", false, "1.4.2 (2012)",
            "Una enemiga especializada en pociones que alterna entre ataques venenosos y bebidas para curarse.",
            "A potion-slinging enemy that alternates between poison attacks and healing potions for herself.",
            "Un aldeano alcanzado por un rayo puede transformarse en una bruja en lugar de morir.",
            "A villager struck by lightning can turn into a witch instead of dying.",
            "witch", 24, 63, 140),
        new Entry(EntityTypes.DROWNED, "Ahogado", "Drowned", false, "1.13 (2018)",
            "La versión acuática del zombi, mucho más peligrosa gracias a sus ataques con tridente.",
            "The underwater version of the zombie, much more dangerous thanks to its trident attacks.",
            "Es uno de los pocos mobs capaces de soltar un tridente, una de las armas más difíciles de conseguir de forma natural.",
            "It's one of the few mobs that can drop a trident, one of the hardest weapons to get naturally.",
            "drowned", 1, 73, 140),
        new Entry(EntityTypes.HUSK, "Husk", "Husk", false, "1.9 (2016)",
            "Un zombi adaptado al desierto que resiste la luz solar y provoca Hambre con sus ataques.",
            "A desert-adapted zombie that resists sunlight and inflicts Hunger with its attacks.",
            "Si permanece sumergido durante suficiente tiempo, termina convirtiéndose en un zombi normal.",
            "If it stays underwater long enough, it eventually turns into a regular zombie.",
            "husk", 24, 80, 140),
        new Entry(EntityTypes.STRAY, "Esqueleto Glacial", "Stray", false, "1.10 (2016)",
            "Un esqueleto de los biomas helados cuyas flechas ralentizan a cualquier jugador que logren alcanzar.",
            "A skeleton from icy biomes whose arrows slow down any player they hit.",
            "Puede aparecer cuando un esqueleto permanece atrapado en nieve polvo durante bastante tiempo.",
            "It can appear when a skeleton gets trapped in powder snow for long enough.",
            "stray", 1, 82, 140),
        new Entry(EntityTypes.PHANTOM, "Fantasma", "Phantom", false, "1.14 (2019)",
            "Una criatura nocturna que acecha desde el cielo a quienes llevan demasiado tiempo sin dormir.",
            "A nocturnal creature that swoops down from the sky at those who've gone too long without sleep.",
            "Su aparición no depende únicamente de la oscuridad: también depende de cuánto tiempo llevas ignorando la cama.",
            "Its spawning doesn't just depend on darkness — it also depends on how long you've been ignoring your bed.",
            "phantom", 24, 162, 140),
        new Entry(EntityTypes.SLIME, "Slime", "Slime", false, "Alpha (2010)",
            "Una masa gelatinosa que se divide en versiones cada vez más pequeñas al ser derrotada.",
            "A gelatinous blob that splits into smaller versions of itself when defeated.",
            "Los slimes no aparecen en cualquier lugar: existen chunks específicos donde pueden generarse constantemente.",
            "Slimes don't spawn just anywhere — there are specific chunks where they can spawn constantly.",
            "slime", 24, 85, 140),
        new Entry(EntityTypes.MAGMA_CUBE, "Cubo de Magma", "Magma Cube", false, "Beta 1.9-pre (2011)",
            "El equivalente del slime en el Nether, resistente al fuego y capaz de sobrevivir entre la lava.",
            "The Nether's equivalent of the slime, fire-resistant and able to survive in lava.",
            "A diferencia de los slimes, no necesita pantanos ni condiciones especiales de la luna para aparecer: pertenece completamente al Nether.",
            "Unlike slimes, it doesn't need swamps or special moon phases to spawn — it belongs entirely to the Nether.",
            "magma_cube", 24, 51, 140),
        new Entry(EntityTypes.BLAZE, "Blaze", "Blaze", false, "Beta 1.9-pre (2011)",
            "Una criatura flotante rodeada de fuego que protege las fortalezas del Nether disparando ráfagas incendiarias.",
            "A floating creature wreathed in fire that guards Nether fortresses by shooting fireball volleys.",
            "Sus varas son imprescindibles para fabricar polvo de blaze y avanzar hasta el End.",
            "Its rods are essential for crafting blaze powder and progressing to the End.",
            "blaze", 24, 70, 140),
        new Entry(EntityTypes.GHAST, "Ghast", "Ghast", false, "Alpha (2011)",
            "Un gigantesco espectro del Nether que bombardea a los jugadores con enormes bolas de fuego.",
            "A giant Nether specter that bombards players with huge fireballs.",
            "Sus propios proyectiles pueden ser devueltos con un golpe preciso y convertirse en su peor enemigo.",
            "Its own projectiles can be knocked back with a well-timed hit and become its own worst enemy.",
            "ghast", 24, 112, 140),
        new Entry(EntityTypes.PIGLIN, "Piglin", "Piglin", false, "1.16 (2020)",
            "Un habitante inteligente del Nether obsesionado con el oro y dispuesto a intercambiar objetos por él.",
            "A clever Nether dweller obsessed with gold and willing to trade items for it.",
            "Llevar una sola pieza de armadura dorada es suficiente para que los piglins comunes te toleren.",
            "Wearing just a single piece of gold armor is enough for regular piglins to tolerate you.",
            "piglin", 24, 112, 140),
        new Entry(EntityTypes.HOGLIN, "Hoglin", "Hoglin", false, "1.16 (2020)",
            "Una enorme bestia del Nether que embiste con fuerza y sirve como una importante fuente de comida de esa dimensión.",
            "A huge Nether beast that charges with brute force and serves as an important food source in that dimension.",
            "Es uno de los pocos mobs hostiles que siente miedo del fuego de alma y evita acercarse a él.",
            "It's one of the few hostile mobs afraid of soul fire and avoids getting near it.",
            "hoglin", 24, 185, 140),
        new Entry(EntityTypes.PILLAGER, "Saqueador", "Pillager", false, "1.14 (2019)",
            "Un ilagero armado con ballesta que suele patrullar o proteger puestos de avanzada.",
            "An illager armed with a crossbow that usually patrols or guards outposts.",
            "No todos provocan un mal presagio: únicamente el capitán que lleva la bandera desencadena ese efecto.",
            "Not all of them trigger Bad Omen — only the captain carrying the banner causes that effect.",
            "pillager", 16, 85, 140),
        new Entry(EntityTypes.VINDICATOR, "Vindicador", "Vindicator", false, "1.11 (2016)",
            "Un feroz ilagero que combate con hacha y representa una de las mayores amenazas de las incursiones.",
            "A fierce axe-wielding illager and one of the biggest threats during raids.",
            "Existe una variante extremadamente rara llamada Johnny capaz de atacar prácticamente cualquier criatura cercana.",
            "There's an extremely rare variant called Johnny that will attack almost any nearby creature.",
            "vindicator", 24, 112, 140),
        new Entry(EntityTypes.EVOKER, "Evocador", "Evoker", false, "1.11 (2016)",
            "El hechicero de los ilageros, capaz de invocar vex y hacer surgir letales colmillos desde el suelo.",
            "The illagers' spellcaster, able to summon vexes and conjure deadly fangs from the ground.",
            "Es el único mob que garantiza un objeto único: el Tótem de la Inmortalidad.",
            "It's the only mob guaranteed to drop a unique item: the Totem of Undying.",
            "evoker", 24, 71, 140),
        new Entry(EntityTypes.VEX, "Vex", "Vex", false, "1.11 (2016)",
            "Un pequeño espíritu armado con espada que atraviesa paredes y persigue sin descanso a su objetivo.",
            "A small sword-wielding spirit that phases through walls and relentlessly chases its target.",
            "No permanece para siempre: los vex invocados terminan desapareciendo después de un tiempo.",
            "It doesn't last forever — summoned vexes eventually disappear after a while.",
            "vex", 24, 239, 140),
        new Entry(EntityTypes.GUARDIAN, "Guardián", "Guardian", false, "1.8 (2014)",
            "Un protector de los monumentos oceánicos que dispara un poderoso rayo de energía bajo el agua.",
            "A guardian of ocean monuments that fires a powerful energy beam underwater.",
            "Sus espinas se extienden cuando se siente amenazado, dañando a quienes lo atacan cuerpo a cuerpo.",
            "Its spikes extend when it feels threatened, damaging anyone who attacks it in melee.",
            "guardian", 24, 192, 140),
        new Entry(EntityTypes.ELDER_GUARDIAN, "Guardián Ancestral", "Elder Guardian", true, "1.8 (2014)",
            "El enorme líder de los guardianes y verdadero protector de los monumentos oceánicos.",
            "The huge leader of the guardians and true protector of ocean monuments.",
            "Cada cierto tiempo envía una onda que aplica Fatiga Minera incluso a jugadores que ni siquiera lo han visto.",
            "Every so often it sends out a pulse that inflicts Mining Fatigue even on players who haven't even seen it yet.",
            "elder_guardian", 24, 192, 140),
        new Entry(EntityTypes.RAVAGER, "Devastador", "Ravager", false, "1.14 (2019)",
            "Una gigantesca bestia de guerra utilizada por los ilageros durante las incursiones contra las aldeas.",
            "A gigantic war beast used by illagers during raids against villages.",
            "Su rugido puede lanzar por los aires a entidades cercanas sin necesidad de golpearlas directamente.",
            "Its roar can launch nearby entities into the air without even hitting them directly.",
            "ravager", 24, 186, 140),
        new Entry(EntityTypes.WARDEN, "Warden", "Warden", true, "1.19 (2022)",
            "El depredador definitivo de las profundidades, completamente ciego pero con un oído extraordinario.",
            "The ultimate predator of the deep, completely blind but with extraordinary hearing.",
            "No necesita verte para encontrarte: detecta vibraciones, sonidos y hasta puede rastrearte mediante el olfato.",
            "It doesn't need to see you to find you — it detects vibrations, sounds, and can even track you by smell.",
            "warden", 24, 75, 140),
        new Entry(EntityTypes.WITHER, "Wither", "Wither", true, "1.4.2 (2012)",
            "Un devastador jefe invocado artificialmente capaz de destruir bloques y lanzar cráneos explosivos.",
            "A devastating artificially-summoned boss able to destroy blocks and launch explosive skulls.",
            "Al aparecer genera una enorme explosión mientras permanece invulnerable durante unos segundos.",
            "When it spawns it triggers a huge explosion while staying invulnerable for a few seconds.",
            "wither", 24, 151, 140),
        new Entry(EntityTypes.ENDER_DRAGON, "Dragón del Ender", "Ender Dragon", true, "Beta 1.9 (2011)",
            "La soberana del End y jefa final de la aventura principal de Minecraft.",
            "The ruler of the End and the final boss of Minecraft's main adventure.",
            "Su primera derrota activa el portal de salida y desbloquea la secuencia de créditos del juego.",
            "Defeating it for the first time opens the exit portal and unlocks the game's end credits.",
            "ender_dragon", 24, 140, 140),
        // ── Agregados a pedido del usuario (imágenes de referencia + arte generado) ──
        new Entry(EntityTypes.CREAKING, "Creaking", "Creaking", true, "1.21.4 (2024)",
            "Un inquietante guardián de madera que cobra vida durante la noche en el Bosque Pálido.",
            "A creepy wooden guardian that comes to life at night in the Pale Garden.",
            "Golpear su cuerpo no sirve de nada: la verdadera fuente de su vida es el Corazón del Creaking oculto entre los árboles.",
            "Hitting its body does nothing — its true source of life is the Creaking Heart hidden among the trees.",
            "creaking", 20, 86, 140),
        new Entry(EntityTypes.ZOGLIN, "Zoglin", "Zoglin", false, "1.16 (2020)",
            "Un hoglin corrompido por permanecer demasiado tiempo fuera del Nether que pierde toda neutralidad y ataca a casi cualquier criatura.",
            "A hoglin corrupted by staying too long outside the Nether, losing all neutrality and attacking almost anything.",
            "A diferencia de su forma original, ya no siente miedo del fuego de alma ni convive pacíficamente con otros hoglins.",
            "Unlike its original form, it's no longer afraid of soul fire and doesn't peacefully coexist with other hoglins.",
            "zoglin", 20, 186, 140),
        new Entry(EntityTypes.BREEZE, "Breeze", "Breeze", false, "1.21 (2024)",
            "Una criatura elemental de las Trial Chambers que combate lanzando explosivas ráfagas de viento.",
            "An elemental creature from Trial Chambers that fights by launching explosive gusts of wind.",
            "Sus ataques no solo dañan: también pueden activar botones, palancas y otros mecanismos a distancia.",
            "Its attacks don't just deal damage — they can also trigger buttons, levers, and other mechanisms at a distance.",
            "breeze", 24, 133, 140),
        new Entry(EntityTypes.CAMEL_HUSK, "Camello Momificado", "Camel Husk", false, "Mounts of Mayhem",
            "Una variante no-muerta del camello que aparece en el desierto acompañada por dos peligrosos jinetes.",
            "An undead variant of the camel that appears in the desert accompanied by two dangerous riders.",
            "Cuando derrotas a ambos jinetes, el Camel Husk puede convertirse en tu propia montura y conservar su resistencia al sol.",
            "Once you defeat both riders, the Camel Husk can become your own mount and keeps its immunity to sunlight.",
            "camel_husk", 16, 133, 140),
        // ── Variantes "jockey"/montadas: comparten EntityType con su forma base
        // (zombie o skeleton), así que el conteo de muertes NO pasa por el mapa
        // genérico WARRIOR_MOB_KEY — HomeQuestMod las detecta aparte mirando
        // isBaby()/getVehicle() en el momento de la muerte (ver AFTER_DEATH). El
        // "Zombie Horseman" es real y vanilla (llegó con Mounts of Mayhem, no
        // confundir con datapacks de comunidad que agregan uno similar).
        new Entry(EntityTypes.ZOMBIE, "Jinete Zombie", "Zombie Horseman", false, "Mounts of Mayhem",
            "Un guerrero no-muerto montado sobre un veloz caballo zombi y armado con una lanza.",
            "An undead warrior mounted on a swift zombie horse and armed with an iron spear.",
            "Tras derrotar al jinete, el caballo puede ser domesticado y utilizado como una montura poco común.",
            "Once the rider is defeated, the horse can be tamed and used as an uncommon mount.",
            "zombie_horseman", 1, 97, 140),
        new Entry(EntityTypes.ZOMBIE, "Bebé Zombi", "Baby Zombie", false, "Beta 1.9 (2011)",
            "Una diminuta variante del zombi, mucho más rápida y difícil de golpear que su versión adulta.",
            "A tiny variant of the regular zombie, just as dangerous but faster and harder to hit.",
            "Aunque parezca inofensivo por su tamaño, conserva prácticamente todo el daño de un zombi normal mientras corre mucho más rápido.",
            "Even though it looks harmless due to its size, it keeps nearly all of a regular zombie's damage while running much faster.",
            "baby_zombie", 16, 93, 140),
        new Entry(EntityTypes.ZOMBIE, "Chicken Jockey", "Chicken Jockey", false, "1.0 (2011)",
            "Una combinación extremadamente rara donde un bebé zombi utiliza una gallina como montura.",
            "An extremely rare combo where a baby zombie rides a chicken as its mount.",
            "La gallina convierte al zombi en uno de los enemigos más rápidos y extraños que puedes encontrar durante una partida.",
            "The chicken turns the zombie into one of the fastest, strangest enemies you can run into during a playthrough.",
            "chicken_jockey", 16, 84, 140),
        new Entry(EntityTypes.SKELETON, "Jinete Esqueleto", "Skeleton Horseman", false, "1.9 (2016)",
            "Un peligroso grupo de esqueletos montados sobre caballos esqueleto que aparece durante una trampa de relámpagos.",
            "A dangerous group of 4 skeletons mounted on skeleton horses that spawns during a lightning trap event.",
            "Una vez derrotados todos los jinetes, los caballos permanecen y pueden convertirse en excelentes monturas.",
            "Once every rider is defeated, the horses remain and can become excellent mounts.",
            "skeleton_horseman", 20, 110, 140),
        new Entry(EntityTypes.SKELETON, "Spider Jockey", "Spider Jockey", false, "1.4.2 (2012)",
            "Una rarísima combinación entre la movilidad de una araña y la precisión de un esqueleto arquero.",
            "An extremely rare combo mixing a spider's mobility with a skeleton archer's precision.",
            "Mientras la araña escala paredes, el esqueleto continúa disparando flechas sin perder su capacidad de ataque.",
            "While the spider climbs walls, the skeleton keeps firing arrows without losing any of its attack power.",
            "spider_jockey", 20, 132, 140),
        // ── Segunda tanda agregada a pedido del usuario ──
        // OJO: Ghast y Elder Guardian NO van acá — ya existían desde la lista original
        // de 27 con su propio Entry() más arriba; lo único que hacía falta para esos
        // dos era agregarles el detail.png ilustrado nuevo a su carpeta de assets, ver
        // DETAIL_IMAGE. Agregar un segundo Entry con el mismo EntityType/iconId acá
        // rompía el arranque del mod (Map.ofEntries no permite claves duplicadas).
        new Entry(EntityTypes.ZOMBIFIED_PIGLIN, "Piglin Zombie", "Zombified Piglin", false, "Beta 1.9 (2011)",
            "Un antiguo habitante del Nether convertido en no-muerto que permanece neutral hasta ser provocado.",
            "A former Nether dweller turned undead that stays neutral until provoked.",
            "Golpear a uno puede enfurecer instantáneamente a toda la manada cercana, incluso si los demás nunca te habían visto.",
            "Hitting one can instantly enrage the whole nearby pack, even ones that had never seen you before.",
            "zombified_piglin", 20, 90, 140),
        new Entry(EntityTypes.SILVERFISH, "Lepisma", "Silverfish", false, "Beta 1.4 (2011)",
            "Un pequeño insecto que se esconde dentro de bloques infestados esperando el momento de sorprenderte.",
            "A tiny bug that hides inside infested blocks, waiting for the moment to ambush you.",
            "Al recibir daño puede despertar a otros lepismas ocultos en los bloques cercanos y convertir una pelea en una emboscada.",
            "Taking damage can wake up other silverfish hidden in nearby blocks, turning a fight into an ambush.",
            "silverfish", 14, 222, 140),
        new Entry(EntityTypes.BOGGED, "Bogged", "Bogged", false, "1.21 (2024)",
            "Una variante pantanosa del esqueleto cuyas flechas envenenan lentamente a sus víctimas.",
            "A swamp-dwelling skeleton variant whose arrows slowly poison their victims.",
            "Además de los pantanos, también puede encontrarse protegiendo las Trial Chambers junto a otros enemigos especiales.",
            "Besides swamps, it can also be found guarding Trial Chambers alongside other special enemies.",
            "bogged", 1, 71, 140),
        new Entry(EntityTypes.ZOMBIE_VILLAGER, "Aldeano Zombi", "Zombie Villager", false, "1.0 (2011)",
            "Un aldeano infectado que todavía puede recuperar su humanidad mediante una cura adecuada.",
            "An infected villager that can still regain its humanity through the right cure.",
            "Curarlo no solo lo salva: también puede ofrecer grandes descuentos en sus intercambios como recompensa permanente.",
            "Curing it doesn't just save it — it can also offer great discounts on its trades as a lasting reward.",
            "zombie_villager", 6, 79, 140),
        new Entry(EntityTypes.ENDERMITE, "Endermite", "Endermite", false, "1.8 (2014)",
            "Una diminuta criatura púrpura que puede aparecer de forma excepcional al utilizar una Perla de Ender.",
            "A tiny purple creature that can rarely spawn when you use an Ender Pearl.",
            "Los endermen sienten una extraña hostilidad hacia ella y suelen atacarla apenas la detectan.",
            "Endermen feel a strange hostility toward it and tend to attack it the moment they spot it.",
            "endermite", 16, 173, 140),
        new Entry(EntityTypes.PIGLIN_BRUTE, "Piglin Brutal", "Piglin Brute", false, "1.16 (2020)",
            "El guardián de élite de los bastiones, armado con un hacha y completamente inmune a los sobornos.",
            "The elite guardian of bastions, armed with an axe and completely immune to bribery.",
            "Ni siquiera una armadura dorada cambia su comportamiento: siempre considerará al jugador un intruso.",
            "Not even gold armor changes its behavior — it will always see the player as an intruder.",
            "piglin_brute", 20, 93, 140),
        new Entry(EntityTypes.CAVE_SPIDER, "Araña de Cueva", "Cave Spider", false, "Beta 1.7 (2011)",
            "Una pequeña araña venenosa que habita los generadores de las minas abandonadas.",
            "A small venomous spider that lives in abandoned mineshaft spawners.",
            "Su reducido tamaño le permite atravesar espacios donde una araña normal jamás podría entrar.",
            "Its small size lets it squeeze through spaces a regular spider could never fit into.",
            "cave_spider", 20, 245, 140),
        new Entry(EntityTypes.SHULKER, "Shulker", "Shulker", false, "1.9 (2016)",
            "Una extraña criatura con forma de bloque que protege las ciudades del End disparando proyectiles de levitación.",
            "A strange block-shaped creature that guards End Cities by firing levitation projectiles.",
            "Su caparazón puede convertirse en una de las cajas de almacenamiento más útiles de todo Minecraft.",
            "Its shell can be turned into one of the most useful storage items in all of Minecraft.",
            "shulker", 16, 131, 140)
    );

    // Enemigos especiales: los raros/de incursión — el resto de los no-boss son
    // "comunes". Pedido explícito: brutos (Vindicator), invocadores (Evoker), los
    // bichos que espamean los invocadores (Vex), el "rinoceronte" (Ravager) y la bruja
    // (Witch).
    // String (iconId), no EntityType: varias de las nuevas comparten EntityType con
    // una entrada YA existente (ver comentario en MOB_KEY) — si esto siguiera siendo
    // Set<EntityType>, agregar ZOMBIE acá también marcaría como "especial" al Zombi
    // normal.
    private static final java.util.Set<String> SPECIAL = java.util.Set.of(
        "vindicator", "evoker", "vex", "ravager", "witch",
        "zoglin", "breeze", "camel_husk",
        "zombie_horseman", "baby_zombie", "chicken_jockey", "skeleton_horseman", "spider_jockey",
        "bogged", "endermite", "piglin_brute", "zombie_villager", "cave_spider", "shulker"
    );
    public static boolean isSpecial(Entry e) { return e.iconId() != null && SPECIAL.contains(e.iconId()); }

    // ENTRIES es fija en runtime, así que filtrar por categoría UNA sola vez acá (al
    // cargar la clase) y devolver la misma lista cacheada es gratis; antes common()/
    // special()/bosses() hacían un stream().filter().toList() nuevo en CADA llamada, y
    // se llamaban varias veces por frame dibujado (ver QuestBookScreen#buildBestiaryRows
    // y #maxScroll) — con 27 entradas no era el cuello de botella real del lag, pero es
    // trabajo repetido sin motivo.
    private static final List<Entry> COMMON = ENTRIES.stream().filter(e -> !e.boss() && !isSpecial(e)).toList();
    private static final List<Entry> SPECIAL_LIST = ENTRIES.stream().filter(e -> !e.boss() && isSpecial(e)).toList();
    private static final List<Entry> BOSSES = ENTRIES.stream().filter(Entry::boss).toList();

    public static List<Entry> common() { return COMMON; }

    public static List<Entry> special() { return SPECIAL_LIST; }

    public static List<Entry> bosses() { return BOSSES; }

    private Bestiary() {}
}
