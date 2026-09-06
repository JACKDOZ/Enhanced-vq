package com.homequest;

/**
 * Última respuesta de GameStatsPayload guardada en RAM del cliente, para la
 * página "Datos de la Partida" del libro. Se pide de nuevo (RequestGameStatsPayload)
 * cada vez que se abre esa página, así que este holder solo necesita guardar el
 * último valor recibido — no hace falta persistirlo ni compararlo con nada.
 */
public final class GameStatsClient {
    private GameStatsClient() {}

    private static long mobKills = -1;
    private static long blocksMined = -1;
    private static long blocksPlaced = -1;

    public static void update(long newMobKills, long newBlocksMined, long newBlocksPlaced) {
        mobKills = newMobKills;
        blocksMined = newBlocksMined;
        blocksPlaced = newBlocksPlaced;
    }

    /** true si todavía no llegó ninguna respuesta desde que se abrió el mundo (para mostrar "..."). */
    public static boolean hasData() { return mobKills >= 0; }

    public static long mobKills() { return mobKills; }
    public static long blocksMined() { return blocksMined; }
    public static long blocksPlaced() { return blocksPlaced; }
}
