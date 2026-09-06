package com.homequest;

/**
 * Puente de comunicación entre el sourceset main y el client.
 * HomeQuestClient (client) registra el callback; QuestBookItem (main) lo activa.
 */
public class ClientBridge {

    private static Runnable openBookCallback = null;

    // Llamado desde HomeQuestClient al inicializar
    public static void setOpenBookCallback(Runnable callback) {
        openBookCallback = callback;
    }

    // Llamado desde QuestBookItem.use()
    public static void requestOpenBook() {
        if (openBookCallback != null) {
            openBookCallback.run();
        }
    }
}
