package com.homequest.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistencia en disco de todo el estado de quests por jugador.
 * Formato del archivo (una línea por jugador):
 *   UUID|started|completed:q1,q2|coal:N|blocks:N|biomes:b1;b2|chestX,Y,Z|pinned:questId
 *
 * Todos los datos están ligados al UUID del jugador y al mundo (worldFolder).
 * Jugadores distintos tienen entradas distintas — sin acoplamientos.
 */
public class QuestSavedData {

    private static final String DATA_NAME = "homequest_players.dat";

    // Por jugador: todos los campos necesarios
    private final Map<String, PlayerRecord> records = new LinkedHashMap<>();
    private final File dataFile;
    private volatile boolean dirty = false;

    // ---- Cache: una instancia por servidor, cargada UNA sola vez desde disco.
    // WeakHashMap para no retener el MinecraftServer si el mundo se descarga
    // (relevante sobre todo en singleplayer, donde el "servidor integrado" se recrea
    // cada vez que se abre un mundo).
    private static final Map<MinecraftServer, QuestSavedData> CACHE = new WeakHashMap<>();

    // ---- Record interno ----
    static class PlayerRecord {
        boolean receivedChest   = false;
        boolean receivedBook    = false;
        boolean questsStarted   = false;
        boolean crownGiven      = false; // corona por completar el libro al 100%, se entrega una sola vez
        Set<String> completed   = new HashSet<>();
        Set<String> claimed     = new HashSet<>(); // recompensas ya entregadas al cofre
        int coalCount           = 0;
        int blocksPlaced        = 0;
        // A diferencia de blocksPlaced (que se usa para la quest "Trabajo Honesto" y
        // por eso deja de sumar antes de empezar quests o después de terminarlas
        // todas), este es un total de por vida sin condiciones — para la página
        // "Datos de la Partida", que quiere el número real, no uno pausado a mitad.
        long blocksPlacedTotal  = 0;
        Set<String> biomes      = new HashSet<>();
        String chestPos         = "";
        String pinnedQuest      = "";
        String spawnBiome       = "";
        String language         = "es";   // "es" o "en" — default español (idioma original del mod)
        boolean languageChosen  = false;  // true despues de elegir en la ventana de bienvenida
        boolean tutorialSeen    = false;  // true despues de terminar o saltar el tutorial del libro
        // Nuevos campos para misiones Principal
        boolean visitedNether   = false;
        boolean visitedEnd      = false;
        boolean killedDragon    = false;
        boolean summonedWither  = false;
        boolean foundStronghold = false;
        boolean foundStrongholdRuins = false; // entrada REAL al Stronghold (para QUEST_FORGOTTEN_FORTRESS, distinto del heurístico de ojos de ender de foundStronghold)
        // Nuevos campos para misiones Explorador (estructuras)
        boolean foundVillage    = false;
        boolean foundDesertTemple = false;
        boolean foundJungleTemple = false;
        boolean foundIgloo      = false;
        boolean foundShipwreck  = false;
        boolean foundOceanRuins = false;
        boolean foundMonument   = false;
        boolean foundOutpost    = false;
        boolean foundMansion    = false;
        boolean foundNetherFortress = false;
        boolean foundBastion    = false;
        boolean foundEndCity    = false;
        boolean foundAncientCity = false;
        boolean foundTrialChamber = false;
        // Nuevos campos para misiones MINERO (conteo de bloques minados)
        int coalMined = 0;
        int ironMined = 0;
        int copperMined = 0;
        int goldMined = 0;
        int redstoneMined = 0;
        int lapisMined = 0;
        int diamondMined = 0;
        int ancientDebrisMined = 0;
        int emeraldMined = 0;
        // Campos CONSTRUCTOR
        int stairsPlaced    = 0;
        int slabsPlaced     = 0;
        int trapdoorsPlaced = 0;
        int doorsPlaced     = 0;
        int glassPlaced     = 0;
        int fencesPlaced    = 0;
        int lanternsPlaced  = 0;
        int seaLanternsPlaced = 0;
        int decorativePlaced = 0;
        int chiseledPlaced  = 0;
        int brickPlaced     = 0;
        int quartzPlaced    = 0;
        int concretePlaced  = 0;
        int honeyPlaced     = 0;
        boolean placedBell   = false;
        boolean placedBeacon = false;
        // Campos TÉCNICO
        boolean craftedPiston       = false;
        boolean craftedStickyPiston = false;
        boolean craftedRepeater     = false;
        boolean craftedComparator   = false;
        int pistonsPlaced           = 0;
        int stickyPistonsPlaced     = 0;
        int repeatersPlaced         = 0;
        int comparatorsPlaced       = 0;
        int hoppersPlaced           = 0;
        int dispensersPlaced        = 0;
        int droppersPlaced          = 0;
        int observersPlaced         = 0;
        int leversPlaced            = 0;
        int buttonsPlaced           = 0;
        int pressurePlatesPlaced    = 0;
        int ironTrapdoorsPlaced     = 0;
        int pistonActivations       = 0;
        boolean builtHopperChain    = false;
        boolean builtRedstoneClock  = false;
        boolean builtAutoFarm       = false;
        // Campos GUERRERO — conteo genérico de mobs derrotados por tipo (25 tipos
        // distintos entre las quests de "Cazador de X"; un mapa clave→cantidad es
        // muchísimo más manejable que 25 campos individuales como los de arriba).
        Map<String, Integer> mobKills = new HashMap<>();
        int raidsWonTotal   = 0; // cualquier nivel
        int raidsWonLevel3  = 0;
        int raidsWonLevel5  = 0;
        boolean hadHeroEffect = false; // para detectar el flanco de "recién ganó una incursión"
    }

    private QuestSavedData(File dataFile) {
        this.dataFile = dataFile;
    }

    // ---- Punto de entrada: una instancia por servidor, en memoria.
    // Se carga de disco solo la PRIMERA vez que se pide (arranque del server / mundo);
    // las siguientes llamadas devuelven la misma instancia ya cargada, sin tocar el disco.
    public static synchronized QuestSavedData get(MinecraftServer server) {
        return CACHE.computeIfAbsent(server, s -> {
            File worldFolder = s.getWorldPath(LevelResource.ROOT).toFile();
            File dataFile = new File(worldFolder, DATA_NAME);
            QuestSavedData data = new QuestSavedData(dataFile);
            data.load();
            return data;
        });
    }

    // ---- Volcar a disco SOLO si hubo cambios desde el ultimo guardado.
    // Hay que llamarlo periodicamente (ver HomeQuestMod#onServerTick) y, sobre todo,
    // al desconectar un jugador y al apagar el servidor, para no perder progreso.
    public synchronized void flushIfDirty() {
        if (!dirty) return;
        writeToDisk();
        dirty = false;
    }

    // ---- Llamar al apagar el servidor / descargar el mundo: guarda lo pendiente
    // y saca la instancia del cache.
    public static synchronized void unload(MinecraftServer server) {
        QuestSavedData data = CACHE.remove(server);
        if (data != null) data.flushIfDirty();
    }

    // ---- Obtener o crear record ----
    private PlayerRecord record(UUID player) {
        return records.computeIfAbsent(player.toString(), k -> new PlayerRecord());
    }

    /**
     * Resetea TODO el progreso de un jugador a estado inicial (como si
     * recién hubiera entrado al mundo): misiones completadas, reclamadas,
     * contadores, estructuras encontradas, cofre colocado, quest fijada —
     * todo vuelve a su valor por defecto. La elección de idioma NO se
     * resetea (es una preferencia de interfaz, no progreso de misiones).
     */
    public synchronized void resetPlayer(UUID p) {
        PlayerRecord old = records.get(p.toString());
        String lang = old != null ? old.language : "es";
        boolean langChosen = old != null && old.languageChosen;
        records.remove(p.toString());
        PlayerRecord fresh = record(p);
        fresh.language = lang;
        fresh.languageChosen = langChosen;
        save();
    }

    /** UUIDs de todos los jugadores con datos guardados, estén online o no. */
    public java.util.Set<UUID> getAllKnownPlayers() {
        java.util.Set<UUID> result = new java.util.HashSet<>();
        for (String key : records.keySet()) {
            try {
                result.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // línea corrupta o con otro formato — se ignora, no debe romper el reset de los demás
            }
        }
        return result;
    }

    // ======== API pública ========

    // -- Cofre inicial --
    public boolean hasReceivedChest(UUID p) { return record(p).receivedChest; }
    public void markReceivedChest(UUID p)   { record(p).receivedChest = true; save(); }

    // -- Libro inicial --
    public boolean hasReceivedBook(UUID p)  { return record(p).receivedBook; }
    public void markReceivedBook(UUID p)    { record(p).receivedBook = true; save(); }

    // -- Quests iniciadas (flag que se activa cuando el jugador toma el libro) --
    public boolean hasStartedQuests(UUID p)  { return record(p).questsStarted; }
    public void markStartedQuests(UUID p)    { record(p).questsStarted = true; save(); }

    // -- Corona por 100% --
    public boolean hasCrown(UUID p) { return record(p).crownGiven; }
    public void markCrownGiven(UUID p) { record(p).crownGiven = true; save(); }

    // -- Quests completadas --
    public boolean isCompleted(UUID p, String questId) { return record(p).completed.contains(questId); }
    public void markCompleted(UUID p, String questId)  { record(p).completed.add(questId); save(); }
    public Set<String> getCompletedQuests(UUID p)      { return Collections.unmodifiableSet(record(p).completed); }

    /**
     * true si este jugador ya completó absolutamente todas las quests del mod.
     * Se usa para dejar de escanear/actualizar progreso (inventario, bloques
     * colocados, biomas, estructuras, etc.) en el tick y en los eventos de
     * bloque: una vez que no queda nada por completar, seguir recalculando y
     * reenviando ese estado al cliente en cada tick/bloque es trabajo sin
     * ningún efecto — solo gasta CPU y ancho de banda de red.
     * O(1): compara tamaños de sets en vez de recorrer ALL_QUEST_IDS.
     */
    public boolean hasCompletedAllQuests(UUID p) {
        return record(p).completed.size() >= QuestData.ALL_QUEST_IDS.size();
    }

    // -- Recompensas reclamadas (entregadas al cofre) --
    public boolean isClaimed(UUID p, String questId)   { return record(p).claimed.contains(questId); }
    public void markClaimed(UUID p, String questId)    { record(p).claimed.add(questId); save(); }
    public Set<String> getClaimedRewards(UUID p)       { return Collections.unmodifiableSet(record(p).claimed); }

    // -- Progreso carbón: ahora refleja cuanto tiene el jugador AHORA en su
    // inventario (chequeado periodicamente), no cuantos bloques de mena rompio.
    public int getCoalCount(UUID p)        { return record(p).coalCount; }
    public void setCoal(UUID p, int n)     { record(p).coalCount = n; save(); }

    // -- Progreso bloques --
    public int getBlocksPlaced(UUID p)     { return record(p).blocksPlaced; }
    public void addBlockPlaced(UUID p)     { record(p).blocksPlaced++; save(); }
    public long getBlocksPlacedTotal(UUID p) { return record(p).blocksPlacedTotal; }
    public void addBlockPlacedTotal(UUID p)  { record(p).blocksPlacedTotal++; save(); }

    // -- Biomas descubiertos --
    public Set<String> getDiscoveredBiomes(UUID p) { return Collections.unmodifiableSet(record(p).biomes); }
    public void addDiscoveredBiome(UUID p, String b) { record(p).biomes.add(b); save(); }

    // -- Posición del cofre --
    public boolean hasChestPlaced(UUID p)  { return !record(p).chestPos.isEmpty(); }
    public void setChestPos(UUID p, net.minecraft.core.BlockPos pos) {
        record(p).chestPos = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        save();
    }
    public void removeChestPos(UUID p)     { record(p).chestPos = ""; save(); }
    public net.minecraft.core.BlockPos getChestPos(UUID p) {
        String s = record(p).chestPos;
        if (s.isEmpty()) return null;
        String[] parts = s.split(",");
        return new net.minecraft.core.BlockPos(
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        );
    }

    /**
     * Busca si la posición dada ya está registrada como el cofre de quest de
     * ALGÚN jugador, y devuelve su UUID (o null si nadie tiene un cofre ahí
     * todavía). Se usa para no "robarle" el cofre a otro jugador: antes,
     * cualquiera que abriera un cofre de quest ajeno quedaba automáticamente
     * vinculado a esa posición, pisando su propio cofre registrado.
     */
    public UUID findChestOwner(net.minecraft.core.BlockPos pos) {
        String posStr = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        for (Map.Entry<String, PlayerRecord> entry : records.entrySet()) {
            if (posStr.equals(entry.getValue().chestPos)) {
                try {
                    return UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException ignored) {
                    // clave corrupta/no-UUID: la ignoramos y seguimos buscando
                }
            }
        }
        return null;
    }


    // -- Quest fijada en HUD --
    public String getPinnedQuest(UUID p)           { return record(p).pinnedQuest.isEmpty() ? null : record(p).pinnedQuest; }
    public void pinQuest(UUID p, String questId)   { record(p).pinnedQuest = questId; save(); }
    public void unpinQuest(UUID p)                 { record(p).pinnedQuest = ""; save(); }

    // -- Bioma de spawn (para detectar "nuevo bioma" real) --
    public String getSpawnBiome(UUID p)            { return record(p).spawnBiome; }
    public void setSpawnBiome(UUID p, String b)    { record(p).spawnBiome = b; save(); }

    // -- Idioma elegido por el jugador (ventana de bienvenida del libro) --
    public String getLanguage(UUID p)              { return record(p).language; }
    public boolean hasChosenLanguage(UUID p)        { return record(p).languageChosen; }
    public void setLanguage(UUID p, String lang) {
        PlayerRecord rec = record(p);
        rec.language = "en".equals(lang) ? "en" : "es";
        rec.languageChosen = true;
        save();
    }

    // -- Tutorial del libro (primer uso) --
    public boolean hasSeenTutorial(UUID p)  { return record(p).tutorialSeen; }
    public void markTutorialSeen(UUID p)    { record(p).tutorialSeen = true; save(); }

    // -- Nuevos campos para misiones Principal --
    public boolean hasVisitedNether(UUID p)        { return record(p).visitedNether; }
    public void markVisitedNether(UUID p)          { record(p).visitedNether = true; save(); }

    public boolean hasVisitedEnd(UUID p)           { return record(p).visitedEnd; }
    public void markVisitedEnd(UUID p)             { record(p).visitedEnd = true; save(); }

    public boolean hasKilledDragon(UUID p)         { return record(p).killedDragon; }
    public void markKilledDragon(UUID p)           { record(p).killedDragon = true; save(); }

    public boolean hasSummonedWither(UUID p)      { return record(p).summonedWither; }
    public void markSummonedWither(UUID p)         { record(p).summonedWither = true; save(); }

    public boolean hasFoundStronghold(UUID p)      { return record(p).foundStronghold; }
    public void markFoundStronghold(UUID p)        { record(p).foundStronghold = true; save(); }
    public boolean hasFoundStrongholdRuins(UUID p)  { return record(p).foundStrongholdRuins; }
    public void markFoundStrongholdRuins(UUID p)    { record(p).foundStrongholdRuins = true; save(); }

    // -- Nuevos campos para misiones Explorador (estructuras) --
    public boolean hasFoundVillage(UUID p)         { return record(p).foundVillage; }
    public void markFoundVillage(UUID p)           { record(p).foundVillage = true; save(); }

    public boolean hasFoundDesertTemple(UUID p)     { return record(p).foundDesertTemple; }
    public void markFoundDesertTemple(UUID p)       { record(p).foundDesertTemple = true; save(); }

    public boolean hasFoundJungleTemple(UUID p)     { return record(p).foundJungleTemple; }
    public void markFoundJungleTemple(UUID p)       { record(p).foundJungleTemple = true; save(); }

    public boolean hasFoundIgloo(UUID p)            { return record(p).foundIgloo; }
    public void markFoundIgloo(UUID p)              { record(p).foundIgloo = true; save(); }

    public boolean hasFoundShipwreck(UUID p)        { return record(p).foundShipwreck; }
    public void markFoundShipwreck(UUID p)          { record(p).foundShipwreck = true; save(); }

    public boolean hasFoundOceanRuins(UUID p)       { return record(p).foundOceanRuins; }
    public void markFoundOceanRuins(UUID p)         { record(p).foundOceanRuins = true; save(); }

    public boolean hasFoundMonument(UUID p)         { return record(p).foundMonument; }
    public void markFoundMonument(UUID p)           { record(p).foundMonument = true; save(); }

    public boolean hasFoundOutpost(UUID p)          { return record(p).foundOutpost; }
    public void markFoundOutpost(UUID p)            { record(p).foundOutpost = true; save(); }

    public boolean hasFoundMansion(UUID p)          { return record(p).foundMansion; }
    public void markFoundMansion(UUID p)            { record(p).foundMansion = true; save(); }

    public boolean hasFoundNetherFortress(UUID p)   { return record(p).foundNetherFortress; }
    public void markFoundNetherFortress(UUID p)     { record(p).foundNetherFortress = true; save(); }

    public boolean hasFoundBastion(UUID p)          { return record(p).foundBastion; }
    public void markFoundBastion(UUID p)            { record(p).foundBastion = true; save(); }

    public boolean hasFoundEndCity(UUID p)           { return record(p).foundEndCity; }
    public void markFoundEndCity(UUID p)             { record(p).foundEndCity = true; save(); }

    public boolean hasFoundAncientCity(UUID p)      { return record(p).foundAncientCity; }
    public void markFoundAncientCity(UUID p)        { record(p).foundAncientCity = true; save(); }

    public boolean hasFoundTrialChamber(UUID p)     { return record(p).foundTrialChamber; }
    public void markFoundTrialChamber(UUID p)       { record(p).foundTrialChamber = true; save(); }

    // -- Progreso de recursos "Consigue X de <mineral>": reflejan cuanto tiene
    // el jugador AHORA MISMO en su inventario (chequeado periodicamente contra
    // countItem()), no cuantos bloques de mena rompio. Asi Fortuna suma bien
    // (mas items = mas progreso) y Toque de Seda no cuenta de mas (te da el
    // bloque de mena, no el recurso, asi que tu conteo de items no sube).
    public int getCoalMined(UUID p)                { return record(p).coalMined; }
    public void setCoalMined(UUID p, int n)         { record(p).coalMined = n; save(); }

    public int getIronMined(UUID p)                { return record(p).ironMined; }
    public void setIronMined(UUID p, int n)         { record(p).ironMined = n; save(); }

    public int getCopperMined(UUID p)              { return record(p).copperMined; }
    public void setCopperMined(UUID p, int n)       { record(p).copperMined = n; save(); }

    public int getGoldMined(UUID p)                { return record(p).goldMined; }
    public void setGoldMined(UUID p, int n)         { record(p).goldMined = n; save(); }

    public int getRedstoneMined(UUID p)             { return record(p).redstoneMined; }
    public void setRedstoneMined(UUID p, int n)    { record(p).redstoneMined = n; save(); }

    public int getLapisMined(UUID p)               { return record(p).lapisMined; }
    public void setLapisMined(UUID p, int n)        { record(p).lapisMined = n; save(); }

    public int getDiamondMined(UUID p)             { return record(p).diamondMined; }
    public void setDiamondMined(UUID p, int n)      { record(p).diamondMined = n; save(); }

    public int getAncientDebrisMined(UUID p)        { return record(p).ancientDebrisMined; }
    public void setAncientDebrisMined(UUID p, int n) { record(p).ancientDebrisMined = n; save(); }

    public int getEmeraldMined(UUID p)              { return record(p).emeraldMined; }
    public void setEmeraldMined(UUID p, int n)       { record(p).emeraldMined = n; save(); }

    // -- CONSTRUCTOR --
    public int getStairsPlaced(UUID p)            { return record(p).stairsPlaced; }
    public void addStairsPlaced(UUID p)           { record(p).stairsPlaced++; save(); }

    public int getSlabsPlaced(UUID p)             { return record(p).slabsPlaced; }
    public void addSlabsPlaced(UUID p)            { record(p).slabsPlaced++; save(); }

    public int getTrapdoorsPlaced(UUID p)         { return record(p).trapdoorsPlaced; }
    public void addTrapdoorsPlaced(UUID p)        { record(p).trapdoorsPlaced++; save(); }

    public int getDoorsPlaced(UUID p)             { return record(p).doorsPlaced; }
    public void addDoorsPlaced(UUID p)            { record(p).doorsPlaced++; save(); }

    public int getGlassPlaced(UUID p)             { return record(p).glassPlaced; }
    public void addGlassPlaced(UUID p)            { record(p).glassPlaced++; save(); }

    public int getFencesPlaced(UUID p)            { return record(p).fencesPlaced; }
    public void addFencesPlaced(UUID p)           { record(p).fencesPlaced++; save(); }

    public int getLanternsPlaced(UUID p)          { return record(p).lanternsPlaced; }
    public void addLanternsPlaced(UUID p)         { record(p).lanternsPlaced++; save(); }

    public int getSeaLanternsPlaced(UUID p)       { return record(p).seaLanternsPlaced; }
    public void addSeaLanternsPlaced(UUID p)      { record(p).seaLanternsPlaced++; save(); }

    public int getDecorativePlaced(UUID p)        { return record(p).decorativePlaced; }
    public void addDecorativePlaced(UUID p)       { record(p).decorativePlaced++; save(); }

    public int getChiseledPlaced(UUID p)          { return record(p).chiseledPlaced; }
    public void addChiseledPlaced(UUID p)         { record(p).chiseledPlaced++; save(); }

    public int getBrickPlaced(UUID p)             { return record(p).brickPlaced; }
    public void addBrickPlaced(UUID p)            { record(p).brickPlaced++; save(); }

    public int getQuartzPlaced(UUID p)            { return record(p).quartzPlaced; }
    public void addQuartzPlaced(UUID p)           { record(p).quartzPlaced++; save(); }

    public int getConcretePlaced(UUID p)          { return record(p).concretePlaced; }
    public void addConcretePlaced(UUID p)         { record(p).concretePlaced++; save(); }

    public int getHoneyPlaced(UUID p)             { return record(p).honeyPlaced; }
    public void addHoneyPlaced(UUID p)            { record(p).honeyPlaced++; save(); }

    public boolean hasPlacedBell(UUID p)          { return record(p).placedBell; }
    public void markPlacedBell(UUID p)            { record(p).placedBell = true; save(); }

    public boolean hasPlacedBeacon(UUID p)        { return record(p).placedBeacon; }
    public void markPlacedBeacon(UUID p)          { record(p).placedBeacon = true; save(); }

    // -- TÉCNICO --
    public boolean hasCraftedPiston(UUID p)       { return record(p).craftedPiston; }
    public void markCraftedPiston(UUID p)         { record(p).craftedPiston = true; save(); }

    public boolean hasCraftedStickyPiston(UUID p) { return record(p).craftedStickyPiston; }
    public void markCraftedStickyPiston(UUID p)   { record(p).craftedStickyPiston = true; save(); }

    public boolean hasCraftedRepeater(UUID p)     { return record(p).craftedRepeater; }
    public void markCraftedRepeater(UUID p)       { record(p).craftedRepeater = true; save(); }

    public boolean hasCraftedComparator(UUID p)   { return record(p).craftedComparator; }
    public void markCraftedComparator(UUID p)     { record(p).craftedComparator = true; save(); }

    public int getPistonsPlaced(UUID p)           { return record(p).pistonsPlaced; }
    public void addPistonPlaced(UUID p)           { record(p).pistonsPlaced++; save(); }

    public int getStickyPistonsPlaced(UUID p)     { return record(p).stickyPistonsPlaced; }
    public void addStickyPistonPlaced(UUID p)     { record(p).stickyPistonsPlaced++; save(); }

    public int getRepeatersPlaced(UUID p)         { return record(p).repeatersPlaced; }
    public void addRepeaterPlaced(UUID p)         { record(p).repeatersPlaced++; save(); }

    public int getComparatorsPlaced(UUID p)       { return record(p).comparatorsPlaced; }
    public void addComparatorPlaced(UUID p)       { record(p).comparatorsPlaced++; save(); }

    public int getHoppersPlaced(UUID p)           { return record(p).hoppersPlaced; }
    public void addHopperPlaced(UUID p)           { record(p).hoppersPlaced++; save(); }

    public int getDispensersPlaced(UUID p)        { return record(p).dispensersPlaced; }
    public void addDispenserPlaced(UUID p)        { record(p).dispensersPlaced++; save(); }

    public int getDroppersPlaced(UUID p)          { return record(p).droppersPlaced; }
    public void addDropperPlaced(UUID p)          { record(p).droppersPlaced++; save(); }

    public int getObserversPlaced(UUID p)         { return record(p).observersPlaced; }
    public void addObserverPlaced(UUID p)         { record(p).observersPlaced++; save(); }

    public int getLeversPlaced(UUID p)            { return record(p).leversPlaced; }
    public void addLeverPlaced(UUID p)            { record(p).leversPlaced++; save(); }

    public int getButtonsPlaced(UUID p)           { return record(p).buttonsPlaced; }
    public void addButtonPlaced(UUID p)           { record(p).buttonsPlaced++; save(); }

    public int getPressurePlatesPlaced(UUID p)    { return record(p).pressurePlatesPlaced; }
    public void addPressurePlatePlaced(UUID p)    { record(p).pressurePlatesPlaced++; save(); }

    public int getIronTrapdoorsPlaced(UUID p)     { return record(p).ironTrapdoorsPlaced; }
    public void addIronTrapdoorPlaced(UUID p)     { record(p).ironTrapdoorsPlaced++; save(); }

    public int getPistonActivations(UUID p)       { return record(p).pistonActivations; }
    public void addPistonActivation(UUID p)       { record(p).pistonActivations++; save(); }

    public boolean hasBuiltHopperChain(UUID p)    { return record(p).builtHopperChain; }
    public void markBuiltHopperChain(UUID p)      { record(p).builtHopperChain = true; save(); }

    public boolean hasBuiltRedstoneClock(UUID p)  { return record(p).builtRedstoneClock; }
    public void markBuiltRedstoneClock(UUID p)    { record(p).builtRedstoneClock = true; save(); }

    public boolean hasBuiltAutoFarm(UUID p)       { return record(p).builtAutoFarm; }
    public void markBuiltAutoFarm(UUID p)         { record(p).builtAutoFarm = true; save(); }

    // -- GUERRERO: mobs derrotados por tipo --
    public int getMobKills(UUID p, String mobKey) { return record(p).mobKills.getOrDefault(mobKey, 0); }
    /** Todo el mapa de kills por mob de este jugador — usado para sincronizar al cliente (Bestiario). */
    public Map<String, Integer> getAllMobKills(UUID p) { return record(p).mobKills; }
    public int addMobKill(UUID p, String mobKey) {
        int updated = record(p).mobKills.merge(mobKey, 1, Integer::sum);
        save();
        return updated;
    }
    /**
     * Solo para pruebas (comando "/homequest bestiary unlockall"): sube el contador de
     * CADA mob en {@code mobKeys} a al menos {@code min}, sin bajarlo si el jugador ya
     * tenía más kills registrados. Sirve para revisar rápido que todas las páginas del
     * Bestiario (arte, textos, animación) se vean bien sin tener que matar cada mob a
     * mano. No toca quests de "Cazador de X" ya reclamadas ni ningún otro progreso.
     */
    public void setAllMobKillsAtLeast(UUID p, java.util.Collection<String> mobKeys, int min) {
        Map<String, Integer> kills = record(p).mobKills;
        for (String key : mobKeys) {
            if (kills.getOrDefault(key, 0) < min) kills.put(key, min);
        }
        save();
    }

    // -- GUERRERO: incursiones --
    public int getRaidsWonTotal(UUID p)   { return record(p).raidsWonTotal; }
    public int getRaidsWonLevel3(UUID p)  { return record(p).raidsWonLevel3; }
    public int getRaidsWonLevel5(UUID p)  { return record(p).raidsWonLevel5; }
    public void addRaidWon(UUID p)        { record(p).raidsWonTotal++; save(); }
    public void addRaidWonLevel3(UUID p)  { record(p).raidsWonLevel3++; save(); }
    public void addRaidWonLevel5(UUID p)  { record(p).raidsWonLevel5++; save(); }
    // No se persiste a disco (ver campo en PlayerRecord) — es solo para detectar,
    // tick a tick, el flanco de "recién apareció el efecto Héroe de la Aldea".
    public boolean hadHeroEffect(UUID p)          { return record(p).hadHeroEffect; }
    public void setHadHeroEffect(UUID p, boolean v) { record(p).hadHeroEffect = v; }

        // ======== Persistencia ========

    public void load() {
        if (!dataFile.exists()) return;
        try (BufferedReader r = Files.newBufferedReader(dataFile.toPath())) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length < 1) continue;
                String uuid = fields[0];
                PlayerRecord rec = records.computeIfAbsent(uuid, k -> new PlayerRecord());
                for (int i = 1; i < fields.length; i++) {
                    String f = fields[i];
                    if (f.equals("chest"))          rec.receivedChest = true;
                    else if (f.equals("book"))      rec.receivedBook  = true;
                    else if (f.equals("started"))   rec.questsStarted = true;
                    else if (f.equals("crown"))     rec.crownGiven    = true;
                    else if (f.startsWith("done:")) {
                        String val = f.substring(5);
                        if (!val.isEmpty())
                            Collections.addAll(rec.completed, val.split(","));
                    }
                    else if (f.startsWith("claimed:")) {
                        String val = f.substring(8);
                        if (!val.isEmpty())
                            Collections.addAll(rec.claimed, val.split(","));
                    }
                    else if (f.startsWith("coal:"))   rec.coalCount   = parseInt(f.substring(5));
                    else if (f.startsWith("blocks:")) rec.blocksPlaced = parseInt(f.substring(7));
                    else if (f.startsWith("blocksTotal:")) rec.blocksPlacedTotal = Long.parseLong(f.substring(12));
                    else if (f.startsWith("biomes:")) {
                        String val = f.substring(7);
                        if (!val.isEmpty())
                            Collections.addAll(rec.biomes, val.split(";"));
                    }
                    else if (f.startsWith("spawn:")) rec.spawnBiome  = f.substring(6);
                    else if (f.startsWith("lang:")) rec.language = f.substring(5);
                    else if (f.equals("lang_chosen")) rec.languageChosen = true;
                    else if (f.equals("tutorial_seen")) rec.tutorialSeen = true;
                    else if (f.startsWith("cpos:"))  rec.chestPos    = f.substring(5);
                    else if (f.startsWith("pin:"))   rec.pinnedQuest = f.substring(4);
                    else if (f.equals("nether"))     rec.visitedNether = true;
                    else if (f.equals("end"))        rec.visitedEnd = true;
                    else if (f.equals("dragon"))     rec.killedDragon = true;
                    else if (f.equals("wither"))     rec.summonedWither = true;
                    else if (f.equals("stronghold")) rec.foundStronghold = true;
                    else if (f.equals("stronghold_ruins")) rec.foundStrongholdRuins = true;
                    else if (f.equals("village"))    rec.foundVillage = true;
                    else if (f.equals("desert_temple")) rec.foundDesertTemple = true;
                    else if (f.equals("jungle_temple")) rec.foundJungleTemple = true;
                    else if (f.equals("igloo"))      rec.foundIgloo = true;
                    else if (f.equals("shipwreck"))  rec.foundShipwreck = true;
                    else if (f.equals("ocean_ruins")) rec.foundOceanRuins = true;
                    else if (f.equals("monument"))   rec.foundMonument = true;
                    else if (f.equals("outpost"))    rec.foundOutpost = true;
                    else if (f.equals("mansion"))    rec.foundMansion = true;
                    else if (f.equals("nether_fortress")) rec.foundNetherFortress = true;
                    else if (f.equals("bastion"))    rec.foundBastion = true;
                    else if (f.equals("end_city"))   rec.foundEndCity = true;
                    else if (f.equals("ancient_city")) rec.foundAncientCity = true;
                    else if (f.equals("trial_chamber")) rec.foundTrialChamber = true;
                    else if (f.startsWith("coal_mined:")) rec.coalMined = parseInt(f.substring(11));
                    else if (f.startsWith("iron_mined:")) rec.ironMined = parseInt(f.substring(11));
                    else if (f.startsWith("copper_mined:")) rec.copperMined = parseInt(f.substring(13));
                    else if (f.startsWith("gold_mined:")) rec.goldMined = parseInt(f.substring(11));
                    else if (f.startsWith("redstone_mined:")) rec.redstoneMined = parseInt(f.substring(15));
                    else if (f.startsWith("lapis_mined:")) rec.lapisMined = parseInt(f.substring(12));
                    else if (f.startsWith("diamond_mined:")) rec.diamondMined = parseInt(f.substring(14));
                    else if (f.startsWith("ancient_debris_mined:")) rec.ancientDebrisMined = parseInt(f.substring(20));
                    else if (f.startsWith("stairs_placed:"))      rec.stairsPlaced     = parseInt(f.substring(14));
                    else if (f.startsWith("slabs_placed:"))       rec.slabsPlaced      = parseInt(f.substring(13));
                    else if (f.startsWith("trapdoors_placed:"))   rec.trapdoorsPlaced  = parseInt(f.substring(17));
                    else if (f.startsWith("doors_placed:"))       rec.doorsPlaced      = parseInt(f.substring(13));
                    else if (f.startsWith("glass_placed:"))       rec.glassPlaced      = parseInt(f.substring(13));
                    else if (f.startsWith("fences_placed:"))      rec.fencesPlaced     = parseInt(f.substring(14));
                    else if (f.startsWith("lanterns_placed:"))    rec.lanternsPlaced   = parseInt(f.substring(16));
                    else if (f.startsWith("sealanterns_placed:")) rec.seaLanternsPlaced = parseInt(f.substring(19));
                    else if (f.startsWith("decorative_placed:")) rec.decorativePlaced  = parseInt(f.substring(18));
                    else if (f.startsWith("chiseled_placed:"))   rec.chiseledPlaced    = parseInt(f.substring(16));
                    else if (f.startsWith("brick_placed:"))      rec.brickPlaced       = parseInt(f.substring(13));
                    else if (f.startsWith("quartz_placed:"))     rec.quartzPlaced      = parseInt(f.substring(14));
                    else if (f.startsWith("concrete_placed:"))   rec.concretePlaced    = parseInt(f.substring(16));
                    else if (f.startsWith("honey_placed:"))      rec.honeyPlaced       = parseInt(f.substring(13));
                    else if (f.equals("placed_bell"))            rec.placedBell        = true;
                    else if (f.equals("placed_beacon"))          rec.placedBeacon      = true;
                    else if (f.equals("crafted_piston"))         rec.craftedPiston     = true;
                    else if (f.equals("crafted_sticky_piston"))  rec.craftedStickyPiston = true;
                    else if (f.equals("crafted_repeater"))       rec.craftedRepeater   = true;
                    else if (f.equals("crafted_comparator"))     rec.craftedComparator = true;
                    else if (f.startsWith("pistons_placed:"))    rec.pistonsPlaced     = parseInt(f.substring(15));
                    else if (f.startsWith("sticky_pistons_placed:")) rec.stickyPistonsPlaced = parseInt(f.substring(21));
                    else if (f.startsWith("repeaters_placed:"))  rec.repeatersPlaced   = parseInt(f.substring(17));
                    else if (f.startsWith("comparators_placed:")) rec.comparatorsPlaced = parseInt(f.substring(19));
                    else if (f.startsWith("hoppers_placed:"))    rec.hoppersPlaced     = parseInt(f.substring(15));
                    else if (f.startsWith("dispensers_placed:")) rec.dispensersPlaced  = parseInt(f.substring(18));
                    else if (f.startsWith("droppers_placed:"))   rec.droppersPlaced    = parseInt(f.substring(16));
                    else if (f.startsWith("observers_placed:"))  rec.observersPlaced   = parseInt(f.substring(17));
                    else if (f.startsWith("levers_placed:"))     rec.leversPlaced      = parseInt(f.substring(14));
                    else if (f.startsWith("buttons_placed:"))    rec.buttonsPlaced     = parseInt(f.substring(15));
                    else if (f.startsWith("pressure_plates_placed:")) rec.pressurePlatesPlaced = parseInt(f.substring(23));
                    else if (f.startsWith("iron_trapdoors_placed:")) rec.ironTrapdoorsPlaced = parseInt(f.substring(21));
                    else if (f.startsWith("piston_activations:")) rec.pistonActivations = parseInt(f.substring(19));
                    else if (f.equals("built_hopper_chain"))     rec.builtHopperChain  = true;
                    else if (f.equals("built_redstone_clock"))   rec.builtRedstoneClock = true;
                    else if (f.equals("built_auto_farm"))        rec.builtAutoFarm     = true;
                    else if (f.startsWith("emerald_mined:")) rec.emeraldMined = parseInt(f.substring(14));
                    else if (f.startsWith("mobkills:")) {
                        for (String pair : f.substring(9).split(",")) {
                            if (pair.isEmpty()) continue;
                            int eq = pair.indexOf('=');
                            if (eq < 0) continue;
                            rec.mobKills.put(pair.substring(0, eq), parseInt(pair.substring(eq + 1)));
                        }
                    }
                    else if (f.startsWith("raids_total:")) rec.raidsWonTotal  = parseInt(f.substring(12));
                    else if (f.startsWith("raids_lvl3:"))  rec.raidsWonLevel3 = parseInt(f.substring(11));
                    else if (f.startsWith("raids_lvl5:"))  rec.raidsWonLevel5 = parseInt(f.substring(11));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Antes este metodo escribia el archivo entero a disco de forma SINCRONA en cada
    // llamada (cada bloque roto/colocado, por cada jugador). Ahora solo marca que hay
    // cambios pendientes; el volcado real a disco lo hace writeToDisk(), disparado
    // periodicamente por flushIfDirty() (ver onServerTick en HomeQuestMod) y al
    // desconectar jugadores / apagar el servidor. Esto no cambia el comportamiento
    // para el jugador: sus datos siguen aislados por UUID y se siguen guardando,
    // solo que el disco se toca cada pocos segundos en vez de en cada bloque.
    public void save() {
        dirty = true;
    }

    private void writeToDisk() {
        try {
            dataFile.getParentFile().mkdirs();
            Path finalPath = dataFile.toPath();
            Path tmpPath = finalPath.resolveSibling(dataFile.getName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmpPath)) {
                w.write("# Enhanced-vq player data — do not edit manually");
                w.newLine();
                for (Map.Entry<String, PlayerRecord> entry : records.entrySet()) {
                    PlayerRecord rec = entry.getValue();
                    StringBuilder sb = new StringBuilder(entry.getKey());
                    if (rec.receivedChest) sb.append("|chest");
                    if (rec.receivedBook)  sb.append("|book");
                    if (rec.questsStarted) sb.append("|started");
                    if (rec.crownGiven)    sb.append("|crown");
                    if (!rec.completed.isEmpty()) sb.append("|done:").append(String.join(",", rec.completed));
                    if (!rec.claimed.isEmpty())   sb.append("|claimed:").append(String.join(",", rec.claimed));
                    if (rec.coalCount > 0)        sb.append("|coal:").append(rec.coalCount);
                    if (rec.blocksPlaced > 0)     sb.append("|blocks:").append(rec.blocksPlaced);
                    if (rec.blocksPlacedTotal > 0) sb.append("|blocksTotal:").append(rec.blocksPlacedTotal);
                    if (!rec.biomes.isEmpty())    sb.append("|biomes:").append(String.join(";", rec.biomes));
                    if (!rec.spawnBiome.isEmpty()) sb.append("|spawn:").append(rec.spawnBiome);
                    if (!"es".equals(rec.language)) sb.append("|lang:").append(rec.language);
                    if (rec.languageChosen) sb.append("|lang_chosen");
                    if (rec.tutorialSeen) sb.append("|tutorial_seen");
                    if (!rec.chestPos.isEmpty())  sb.append("|cpos:").append(rec.chestPos);
                    if (!rec.pinnedQuest.isEmpty()) sb.append("|pin:").append(rec.pinnedQuest);
                    if (rec.visitedNether)        sb.append("|nether");
                    if (rec.visitedEnd)           sb.append("|end");
                    if (rec.killedDragon)         sb.append("|dragon");
                    if (rec.summonedWither)       sb.append("|wither");
                    if (rec.foundStronghold)      sb.append("|stronghold");
                    if (rec.foundStrongholdRuins) sb.append("|stronghold_ruins");
                    if (rec.foundVillage)         sb.append("|village");
                    if (rec.foundDesertTemple)    sb.append("|desert_temple");
                    if (rec.foundJungleTemple)    sb.append("|jungle_temple");
                    if (rec.foundIgloo)           sb.append("|igloo");
                    if (rec.foundShipwreck)       sb.append("|shipwreck");
                    if (rec.foundOceanRuins)      sb.append("|ocean_ruins");
                    if (rec.foundMonument)        sb.append("|monument");
                    if (rec.foundOutpost)         sb.append("|outpost");
                    if (rec.foundMansion)         sb.append("|mansion");
                    if (rec.foundNetherFortress)  sb.append("|nether_fortress");
                    if (rec.foundBastion)         sb.append("|bastion");
                    if (rec.foundEndCity)         sb.append("|end_city");
                    if (rec.foundAncientCity)     sb.append("|ancient_city");
                    if (rec.foundTrialChamber)    sb.append("|trial_chamber");
                    if (rec.coalMined > 0)        sb.append("|coal_mined:").append(rec.coalMined);
                    if (rec.ironMined > 0)        sb.append("|iron_mined:").append(rec.ironMined);
                    if (rec.copperMined > 0)       sb.append("|copper_mined:").append(rec.copperMined);
                    if (rec.goldMined > 0)        sb.append("|gold_mined:").append(rec.goldMined);
                    if (rec.redstoneMined > 0)    sb.append("|redstone_mined:").append(rec.redstoneMined);
                    if (rec.lapisMined > 0)        sb.append("|lapis_mined:").append(rec.lapisMined);
                    if (rec.diamondMined > 0)      sb.append("|diamond_mined:").append(rec.diamondMined);
                    if (rec.ancientDebrisMined > 0) sb.append("|ancient_debris_mined:").append(rec.ancientDebrisMined);
                    if (rec.emeraldMined > 0)      sb.append("|emerald_mined:").append(rec.emeraldMined);
                    if (rec.stairsPlaced > 0)      sb.append("|stairs_placed:").append(rec.stairsPlaced);
                    if (rec.slabsPlaced > 0)       sb.append("|slabs_placed:").append(rec.slabsPlaced);
                    if (rec.trapdoorsPlaced > 0)   sb.append("|trapdoors_placed:").append(rec.trapdoorsPlaced);
                    if (rec.doorsPlaced > 0)       sb.append("|doors_placed:").append(rec.doorsPlaced);
                    if (rec.glassPlaced > 0)       sb.append("|glass_placed:").append(rec.glassPlaced);
                    if (rec.fencesPlaced > 0)      sb.append("|fences_placed:").append(rec.fencesPlaced);
                    if (rec.lanternsPlaced > 0)    sb.append("|lanterns_placed:").append(rec.lanternsPlaced);
                    if (rec.seaLanternsPlaced > 0) sb.append("|sealanterns_placed:").append(rec.seaLanternsPlaced);
                    if (rec.decorativePlaced > 0)  sb.append("|decorative_placed:").append(rec.decorativePlaced);
                    if (rec.chiseledPlaced > 0)    sb.append("|chiseled_placed:").append(rec.chiseledPlaced);
                    if (rec.brickPlaced > 0)       sb.append("|brick_placed:").append(rec.brickPlaced);
                    if (rec.quartzPlaced > 0)      sb.append("|quartz_placed:").append(rec.quartzPlaced);
                    if (rec.concretePlaced > 0)    sb.append("|concrete_placed:").append(rec.concretePlaced);
                    if (rec.honeyPlaced > 0)       sb.append("|honey_placed:").append(rec.honeyPlaced);
                    if (rec.placedBell)            sb.append("|placed_bell");
                    if (rec.placedBeacon)          sb.append("|placed_beacon");
                    if (rec.craftedPiston)         sb.append("|crafted_piston");
                    if (rec.craftedStickyPiston)   sb.append("|crafted_sticky_piston");
                    if (rec.craftedRepeater)       sb.append("|crafted_repeater");
                    if (rec.craftedComparator)     sb.append("|crafted_comparator");
                    if (rec.pistonsPlaced > 0)     sb.append("|pistons_placed:").append(rec.pistonsPlaced);
                    if (rec.stickyPistonsPlaced > 0) sb.append("|sticky_pistons_placed:").append(rec.stickyPistonsPlaced);
                    if (rec.repeatersPlaced > 0)   sb.append("|repeaters_placed:").append(rec.repeatersPlaced);
                    if (rec.comparatorsPlaced > 0) sb.append("|comparators_placed:").append(rec.comparatorsPlaced);
                    if (rec.hoppersPlaced > 0)     sb.append("|hoppers_placed:").append(rec.hoppersPlaced);
                    if (rec.dispensersPlaced > 0)  sb.append("|dispensers_placed:").append(rec.dispensersPlaced);
                    if (rec.droppersPlaced > 0)    sb.append("|droppers_placed:").append(rec.droppersPlaced);
                    if (rec.observersPlaced > 0)   sb.append("|observers_placed:").append(rec.observersPlaced);
                    if (rec.leversPlaced > 0)      sb.append("|levers_placed:").append(rec.leversPlaced);
                    if (rec.buttonsPlaced > 0)     sb.append("|buttons_placed:").append(rec.buttonsPlaced);
                    if (rec.pressurePlatesPlaced > 0) sb.append("|pressure_plates_placed:").append(rec.pressurePlatesPlaced);
                    if (rec.ironTrapdoorsPlaced > 0) sb.append("|iron_trapdoors_placed:").append(rec.ironTrapdoorsPlaced);
                    if (rec.pistonActivations > 0) sb.append("|piston_activations:").append(rec.pistonActivations);
                    if (rec.builtHopperChain)      sb.append("|built_hopper_chain");
                    if (rec.builtRedstoneClock)    sb.append("|built_redstone_clock");
                    if (rec.builtAutoFarm)         sb.append("|built_auto_farm");
                    if (!rec.mobKills.isEmpty()) {
                        StringBuilder mk = new StringBuilder();
                        for (Map.Entry<String,Integer> e : rec.mobKills.entrySet()) {
                            if (mk.length() > 0) mk.append(',');
                            mk.append(e.getKey()).append('=').append(e.getValue());
                        }
                        sb.append("|mobkills:").append(mk);
                    }
                    if (rec.raidsWonTotal > 0)  sb.append("|raids_total:").append(rec.raidsWonTotal);
                    if (rec.raidsWonLevel3 > 0) sb.append("|raids_lvl3:").append(rec.raidsWonLevel3);
                    if (rec.raidsWonLevel5 > 0) sb.append("|raids_lvl5:").append(rec.raidsWonLevel5);
                    w.write(sb.toString());
                    w.newLine();
                }
            }
            // Escritura atomica: primero se escribe todo en el .tmp y recien al final
            // se reemplaza el archivo real de un solo golpe. Asi, si el servidor se
            // cae o se corta la luz a mitad de una escritura, el archivo de TODOS
            // los jugadores queda intacto (con los datos de la version anterior)
            // en vez de quedar corrupto a medio escribir.
            try {
                Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
