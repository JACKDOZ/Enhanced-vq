package com.homequest;

import com.homequest.block.ModBlockEntities;
import com.homequest.block.ModBlocks;
import com.homequest.block.QuestChestBlock;
import com.homequest.data.QuestData;
import com.homequest.data.QuestSavedData;
import com.homequest.item.ModItems;
import com.homequest.network.ClaimRewardPayload;
import com.homequest.network.FindChestPayload;
import com.homequest.network.GameStatsPayload;
import com.homequest.network.QuestSyncPayload;
import com.homequest.network.RequestGameStatsPayload;
import com.homequest.network.SetLanguagePayload;
import com.homequest.network.TutorialSeenPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.stats.Stats;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HomeQuestMod implements ModInitializer {

    public static final String MOD_ID = "homequest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private int tickCounter = 0;
    // Dentro de onServerTick, un mismo jugador puede completar varias quests en la
    // MISMA pasada (ej. cruza de bioma + termina de minar hierro en el mismo chequeo)
    // y cada bloque de quest llamaba a syncToClient() por su cuenta — eso significaba
    // reconstruir el snapshot completo (~30 campos leídos de QuestSavedData) y mandar
    // el paquete de red más de una vez para el mismo jugador en el mismo tick. Este set
    // se vacía al principio de cada pasada de onServerTick y syncToClient() lo consulta
    // para no repetir el trabajo si ya se sincronizó a ese jugador en esta misma pasada.
    private static final java.util.Set<UUID> syncedThisTick = new java.util.HashSet<>();
    private static final int CHECK_INTERVAL = 40;

    // ── GUERRERO: tablas de mob → quest usadas por el handler de AFTER_DEATH ──
    private static final Map<net.minecraft.world.entity.EntityType<?>, String> WARRIOR_MOB_KEY = new java.util.HashMap<>();
    private static final Map<String, WarriorTier[]> WARRIOR_TIERS = new java.util.HashMap<>();
    // Todas las claves de mob que el Bestiario puede llegar a mostrar: los valores de
    // WARRIOR_MOB_KEY de arriba, más las variantes "compuestas" (comparten EntityType
    // con zombie/skeleton, se detectan aparte en el hook de AFTER_DEATH — ver más abajo).
    // Se usa SOLO para el comando de prueba "/homequest bestiary unlockall" (ver
    // registerCommands): no participa del tracking normal de kills.
    private static final java.util.Set<String> ALL_BESTIARY_MOB_KEYS = new java.util.HashSet<>(java.util.List.of(
        "baby_zombie", "chicken_jockey", "zombie_horseman", "skeleton_horseman", "spider_jockey"
    ));
    private record WarriorTier(int threshold, String questId, String subtitle) {}
    static {
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ZOMBIE, "zombie");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.SKELETON, "skeleton");
        // Solo para el Bestiario (no tiene tiers de Guerrero, ver onWarriorKill): cuenta
        // igual las muertes para poder revelarlo, sin generar una quest nueva.
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.WITHER_SKELETON, "wither_skeleton");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.CREEPER, "creeper");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.SPIDER, "spider");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ENDERMAN, "enderman");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.WITCH, "witch");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.DROWNED, "drowned");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.HUSK, "husk");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.STRAY, "stray");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.PHANTOM, "phantom");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.SLIME, "slime");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.MAGMA_CUBE, "magma_cube");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.BLAZE, "blaze");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.GHAST, "ghast");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.PIGLIN, "piglin");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.HOGLIN, "hoglin");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.PILLAGER, "pillager");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.VINDICATOR, "vindicator");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.EVOKER, "evoker");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.GUARDIAN, "guardian");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN, "elder_guardian");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.RAVAGER, "ravager");
        // Solo para el Bestiario (no tiene tiers de Guerrero), igual que wither_skeleton.
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.VEX, "vex");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.WARDEN, "warden");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.WITHER, "wither");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ENDER_DRAGON, "dragon");
        // Solo para el Bestiario (sin tiers de Guerrero), agregados a pedido del
        // usuario junto con su arte — mismo patrón que wither_skeleton/vex arriba.
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.CREAKING, "creaking");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ZOGLIN, "zoglin");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.BREEZE, "breeze");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.CAMEL_HUSK, "camel_husk");
        // Segunda tanda del Bestiario (sin tiers de Guerrero), mismo patrón que
        // creaking/zoglin/breeze/camel_husk arriba — faltaban acá, por eso no se
        // revelaban en el Bestiario al matarlos aunque ya tuvieran Entry() y arte.
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ZOMBIFIED_PIGLIN, "zombified_piglin");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.SILVERFISH, "silverfish");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.BOGGED, "bogged");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER, "zombie_villager");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.ENDERMITE, "endermite");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE, "piglin_brute");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.CAVE_SPIDER, "cave_spider");
        WARRIOR_MOB_KEY.put(net.minecraft.world.entity.EntityTypes.SHULKER, "shulker");
        // zombie_horseman/baby_zombie/chicken_jockey/skeleton_horseman/spider_jockey NO
        // van acá: comparten EntityType con zombie/skeleton normales, así que se
        // detectan aparte más abajo, en el propio hook de AFTER_DEATH (ver comentario
        // ahí) en vez de por este mapa genérico tipo→clave.
        ALL_BESTIARY_MOB_KEYS.addAll(WARRIOR_MOB_KEY.values());

        WARRIOR_TIERS.put("zombie", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_ZOMBIE_1, "§e¡Cazador de Zombie I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_ZOMBIE_5, "§e¡Cazador de Zombie II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_ZOMBIE_10, "§e¡Cazador de Zombie III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("skeleton", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_SKELETON_1, "§e¡Cazador de Esqueleto I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_SKELETON_5, "§e¡Cazador de Esqueleto II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_SKELETON_10, "§e¡Cazador de Esqueleto III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("creeper", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_CREEPER_1, "§e¡Cazador de Creeper I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_CREEPER_5, "§e¡Cazador de Creeper II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_CREEPER_10, "§e¡Cazador de Creeper III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("spider", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_SPIDER_1, "§e¡Cazador de Araña I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_SPIDER_5, "§e¡Cazador de Araña II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_SPIDER_10, "§e¡Cazador de Araña III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("enderman", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_ENDERMAN_1, "§e¡Cazador de Enderman I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_ENDERMAN_5, "§e¡Cazador de Enderman II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_ENDERMAN_10, "§e¡Cazador de Enderman III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("witch", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_WITCH_1, "§e¡Cazador de Bruja I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_WITCH_5, "§e¡Cazador de Bruja II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_WITCH_10, "§e¡Cazador de Bruja III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("drowned", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_DROWNED_1, "§e¡Cazador de Ahogado I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_DROWNED_5, "§e¡Cazador de Ahogado II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_DROWNED_10, "§e¡Cazador de Ahogado III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("husk", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_HUSK_1, "§e¡Cazador de Husk I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_HUSK_5, "§e¡Cazador de Husk II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_HUSK_10, "§e¡Cazador de Husk III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("stray", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_STRAY_1, "§e¡Cazador de Stray I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_STRAY_5, "§e¡Cazador de Stray II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_STRAY_10, "§e¡Cazador de Stray III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("phantom", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_PHANTOM_1, "§e¡Cazador de Fantasma I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_PHANTOM_5, "§e¡Cazador de Fantasma II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_PHANTOM_10, "§e¡Cazador de Fantasma III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("slime", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_SLIME_1, "§e¡Cazador de Slime I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_SLIME_5, "§e¡Cazador de Slime II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_SLIME_10, "§e¡Cazador de Slime III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("magma_cube", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_MAGMA_CUBE_1, "§e¡Cazador de Cubo de magma I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_MAGMA_CUBE_5, "§e¡Cazador de Cubo de magma II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_MAGMA_CUBE_10, "§e¡Cazador de Cubo de magma III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("blaze", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_BLAZE_1, "§e¡Cazador de Blaze I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_BLAZE_5, "§e¡Cazador de Blaze II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_BLAZE_10, "§e¡Cazador de Blaze III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("ghast", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_GHAST_1, "§e¡Cazador de Ghast I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_GHAST_5, "§e¡Cazador de Ghast II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_GHAST_10, "§e¡Cazador de Ghast III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("piglin", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_PIGLIN_1, "§e¡Cazador de Piglin I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_PIGLIN_5, "§e¡Cazador de Piglin II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_PIGLIN_10, "§e¡Cazador de Piglin III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("hoglin", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_HOGLIN_1, "§e¡Cazador de Hoglin I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_HOGLIN_5, "§e¡Cazador de Hoglin II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_HOGLIN_10, "§e¡Cazador de Hoglin III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("pillager", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_PILLAGER_1, "§e¡Cazador de Saqueador I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_PILLAGER_5, "§e¡Cazador de Saqueador II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_PILLAGER_10, "§e¡Cazador de Saqueador III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("vindicator", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_VINDICATOR_1, "§e¡Cazador de Vindicator I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_VINDICATOR_5, "§e¡Cazador de Vindicator II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_VINDICATOR_10, "§e¡Cazador de Vindicator III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("evoker", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_EVOKER_1, "§e¡Cazador de Invocador I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_EVOKER_5, "§e¡Cazador de Invocador II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_EVOKER_10, "§e¡Cazador de Invocador III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("guardian", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_GUARDIAN_1, "§e¡Cazador de Guardián I! §7— Abre el libro para reclamar"), new WarriorTier(5, QuestData.QUEST_WARRIOR_GUARDIAN_5, "§e¡Cazador de Guardián II! §7— Abre el libro para reclamar"), new WarriorTier(10, QuestData.QUEST_WARRIOR_GUARDIAN_10, "§e¡Cazador de Guardián III! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("elder_guardian", new WarriorTier[]{ new WarriorTier(2, QuestData.QUEST_WARRIOR_ELDER_GUARDIAN_2, "§e¡Prueba del Guardián Anciano! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("ravager", new WarriorTier[]{ new WarriorTier(2, QuestData.QUEST_WARRIOR_RAVAGER_2, "§e¡Prueba del Devastador! §7— Abre el libro para reclamar") });
        // El Warden pide solo 1 (no 2 como el resto de los jefes): a diferencia de
        // Elder Guardian/Ravager/Wither/Dragon, que se pueden re-pelear para farmear
        // loot, el Warden está diseñado para EVITARSE — pedir 2 lo hacía prácticamente
        // inalcanzable para la mayoría de los jugadores. Con 1 alcanza.
        WARRIOR_TIERS.put("warden", new WarriorTier[]{ new WarriorTier(1, QuestData.QUEST_WARRIOR_WARDEN_1, "§e¡Desafío del Warden! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("wither", new WarriorTier[]{ new WarriorTier(2, QuestData.QUEST_WARRIOR_WITHER_2, "§e¡El Desafío del Wither! §7— Abre el libro para reclamar") });
        WARRIOR_TIERS.put("dragon", new WarriorTier[]{ new WarriorTier(2, QuestData.QUEST_WARRIOR_DRAGON_2, "§e¡Maestro del End! §7— Abre el libro para reclamar") });

        // ── Agregados a pedido del usuario: mobs del Bestiario que ya se contaban
        // para revelarlo (WARRIOR_MOB_KEY) pero no tenían quest/recompensa de
        // Guerrero propia. Mismo patrón 1/5/10 para los mobs comunes; 1/2/3 o 1/3/5
        // para los realmente raros (jockeys, jinetes, camello momificado — pedirles
        // 10 los volvía casi inalcanzables); tier único para el Creaking, que es
        // boss del Bestiario (mismo criterio que Elder Guardian/Ravager/Warden).
        WARRIOR_TIERS.put("wither_skeleton", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_WITHER_SKELETON_1, "§e¡Cazador de Esqueleto Wither I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_WITHER_SKELETON_5, "§e¡Cazador de Esqueleto Wither II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_WITHER_SKELETON_10, "§e¡Cazador de Esqueleto Wither III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("vex", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_VEX_1, "§e¡Cazador de Vex I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_VEX_5, "§e¡Cazador de Vex II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_VEX_10, "§e¡Cazador de Vex III! §7— Abre el libro para reclamar")
        });
        // Creaking: invulnerable salvo con su Corazón expuesto — se pide solo 1,
        // igual que el Warden, por la misma razón (no está pensado para farmear).
        WARRIOR_TIERS.put("creaking", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_CREAKING_1, "§e¡Silenciador del Creaking! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("zoglin", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_ZOGLIN_1, "§e¡Cazador de Zoglin I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_ZOGLIN_5, "§e¡Cazador de Zoglin II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_ZOGLIN_10, "§e¡Cazador de Zoglin III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("breeze", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_BREEZE_1, "§e¡Cazador de Breeze I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_BREEZE_5, "§e¡Cazador de Breeze II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_BREEZE_10, "§e¡Cazador de Breeze III! §7— Abre el libro para reclamar")
        });
        // Camello Momificado: mob raro (Mounts of Mayhem) — 1/3/5 en vez de 1/5/10.
        WARRIOR_TIERS.put("camel_husk", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_CAMEL_HUSK_1, "§e¡Cazador de Camello Momificado I! §7— Abre el libro para reclamar"),
            new WarriorTier(3, QuestData.QUEST_WARRIOR_CAMEL_HUSK_3, "§e¡Cazador de Camello Momificado II! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_CAMEL_HUSK_5, "§e¡Cazador de Camello Momificado III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("zombified_piglin", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_1, "§e¡Cazador de Piglin Zombie I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_5, "§e¡Cazador de Piglin Zombie II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_10, "§e¡Cazador de Piglin Zombie III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("silverfish", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_SILVERFISH_1, "§e¡Cazador de Lepisma I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_SILVERFISH_5, "§e¡Cazador de Lepisma II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_SILVERFISH_10, "§e¡Cazador de Lepisma III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("bogged", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_BOGGED_1, "§e¡Cazador de Bogged I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_BOGGED_5, "§e¡Cazador de Bogged II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_BOGGED_10, "§e¡Cazador de Bogged III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("zombie_villager", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_1, "§e¡Cazador de Aldeano Zombi I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_5, "§e¡Cazador de Aldeano Zombi II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_10, "§e¡Cazador de Aldeano Zombi III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("endermite", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_ENDERMITE_1, "§e¡Cazador de Endermite I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_ENDERMITE_5, "§e¡Cazador de Endermite II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_ENDERMITE_10, "§e¡Cazador de Endermite III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("piglin_brute", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_1, "§e¡Cazador de Piglin Brutal I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_5, "§e¡Cazador de Piglin Brutal II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_10, "§e¡Cazador de Piglin Brutal III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("cave_spider", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_CAVE_SPIDER_1, "§e¡Cazador de Araña de Cueva I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_CAVE_SPIDER_5, "§e¡Cazador de Araña de Cueva II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_CAVE_SPIDER_10, "§e¡Cazador de Araña de Cueva III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("shulker", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_SHULKER_1, "§e¡Cazador de Shulker I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_SHULKER_5, "§e¡Cazador de Shulker II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_SHULKER_10, "§e¡Cazador de Shulker III! §7— Abre el libro para reclamar")
        });
        // Variantes "jockey"/montadas: rarísimas en el juego real (spawn <1%), así
        // que se piden solo 1/2/3 en vez de 1/5/10 — farmear 10 sería casi imposible.
        WARRIOR_TIERS.put("chicken_jockey", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_1, "§e¡Cazador de Chicken Jockey I! §7— Abre el libro para reclamar"),
            new WarriorTier(2, QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_2, "§e¡Cazador de Chicken Jockey II! §7— Abre el libro para reclamar"),
            new WarriorTier(3, QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_3, "§e¡Cazador de Chicken Jockey III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("baby_zombie", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_BABY_ZOMBIE_1, "§e¡Cazador de Bebé Zombi I! §7— Abre el libro para reclamar"),
            new WarriorTier(5, QuestData.QUEST_WARRIOR_BABY_ZOMBIE_5, "§e¡Cazador de Bebé Zombi II! §7— Abre el libro para reclamar"),
            new WarriorTier(10, QuestData.QUEST_WARRIOR_BABY_ZOMBIE_10, "§e¡Cazador de Bebé Zombi III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("zombie_horseman", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_1, "§e¡Cazador del Jinete Zombie I! §7— Abre el libro para reclamar"),
            new WarriorTier(2, QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_2, "§e¡Cazador del Jinete Zombie II! §7— Abre el libro para reclamar"),
            new WarriorTier(3, QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_3, "§e¡Cazador del Jinete Zombie III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("skeleton_horseman", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_1, "§e¡Cazador del Jinete Esqueleto I! §7— Abre el libro para reclamar"),
            new WarriorTier(2, QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_2, "§e¡Cazador del Jinete Esqueleto II! §7— Abre el libro para reclamar"),
            new WarriorTier(3, QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_3, "§e¡Cazador del Jinete Esqueleto III! §7— Abre el libro para reclamar")
        });
        WARRIOR_TIERS.put("spider_jockey", new WarriorTier[]{
            new WarriorTier(1, QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_1, "§e¡Cazador de Spider Jockey I! §7— Abre el libro para reclamar"),
            new WarriorTier(2, QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_2, "§e¡Cazador de Spider Jockey II! §7— Abre el libro para reclamar"),
            new WarriorTier(3, QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_3, "§e¡Cazador de Spider Jockey III! §7— Abre el libro para reclamar")
        });
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[Enhanced-vq] Iniciado para MC 26.2!");

        // Registrar payloads de red
        PayloadTypeRegistry.clientboundPlay().register(QuestSyncPayload.TYPE, QuestSyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClaimRewardPayload.TYPE, ClaimRewardPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetLanguagePayload.TYPE, SetLanguagePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FindChestPayload.TYPE, FindChestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestGameStatsPayload.TYPE, RequestGameStatsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GameStatsPayload.TYPE, GameStatsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TutorialSeenPayload.TYPE, TutorialSeenPayload.CODEC);

        // Handler C2S: jugador pulsó "Obtener" en el libro
        ServerPlayNetworking.registerGlobalReceiver(ClaimRewardPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String questId = payload.questId();
            MinecraftServer server = context.server();
            QuestSavedData saved = QuestSavedData.get(server);
            UUID uuid = player.getUUID();

            // Validar: quest debe estar completada y no reclamada
            if (!saved.isCompleted(uuid, questId) || saved.isClaimed(uuid, questId)) return;

            // Buscar el cofre en el mundo
            BlockPos chestPos = saved.getChestPos(uuid);
            if (chestPos == null) {
                player.sendSystemMessage(Component.literal(
                    "§c[Enhanced-vq] Por favor coloca el cofre para obtener la recompensa."));
                return;
            }
            // Verificar que el bloque sigue siendo un QuestChestBlock
            ServerLevel world = (ServerLevel) player.level();
            if (!(world.getBlockState(chestPos).getBlock() instanceof QuestChestBlock)) {
                saved.removeChestPos(uuid);
                player.sendSystemMessage(Component.literal(
                    "§c[Enhanced-vq] Por favor coloca el cofre para obtener la recompensa."));
                return;
            }
            com.homequest.block.QuestChestBlockEntity chest =
                (com.homequest.block.QuestChestBlockEntity) world.getBlockEntity(chestPos);
            if (chest == null) {
                player.sendSystemMessage(Component.literal(
                    "§c[Enhanced-vq] Por favor coloca el cofre para obtener la recompensa."));
                return;
            }

            // Entregar recompensa según quest
            ItemStack[] rewards = getRewardsFor(questId, world.registryAccess());
            for (ItemStack item : rewards) {
                // Buscar slot vacío en el cofre
                boolean placed = false;
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    if (chest.getItem(i).isEmpty()) {
                        chest.setItem(i, item.copy());
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    // Cofre lleno: dar al inventario
                    player.getInventory().add(item.copy());
                }
            }

            saved.markClaimed(uuid, questId);
            syncToClient(player, saved);
            player.sendSystemMessage(Component.literal(
                "§a[Enhanced-vq] §r¡Recompensa entregada al Cofre de Quest!"));
        });

        // Handler C2S: jugador eligió idioma en la ventana de bienvenida del libro
        ServerPlayNetworking.registerGlobalReceiver(SetLanguagePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            QuestSavedData saved = QuestSavedData.get(server);
            saved.setLanguage(player.getUUID(), payload.language());
            syncToClient(player, saved);
        });

        // Handler C2S: jugador pulsó "Buscar Cofre" en las opciones del libro.
        // Busca la posición guardada en TODAS las dimensiones cargadas (no solo
        // la actual del jugador, por si colocó el cofre en otro mundo/dimensión
        // y después viajó). Si no existe más (se rompió, hizo despawn con el
        // jugador al morir, nunca tuvo uno, etc.), le limpia el vínculo viejo
        // y le entrega un Cofre de Quest nuevo directo al inventario.
        ServerPlayNetworking.registerGlobalReceiver(FindChestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            QuestSavedData saved = QuestSavedData.get(server);
            UUID uuid = player.getUUID();

            BlockPos chestPos = saved.getChestPos(uuid);
            ServerLevel foundLevel = null;
            if (chestPos != null) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    if (lvl.getBlockState(chestPos).getBlock() instanceof QuestChestBlock
                            && uuid.equals(saved.findChestOwner(chestPos))) {
                        foundLevel = lvl;
                        break;
                    }
                }
            }

            if (foundLevel != null) {
                player.sendSystemMessage(Component.literal(
                    "§a[Enhanced-vq] §rTu Cofre de Quest está en "
                    + foundLevel.dimension().identifier() + " — "
                    + chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ() + "."));
            } else {
                if (chestPos != null) saved.removeChestPos(uuid);
                giveQuestChest(player);
                player.sendSystemMessage(Component.literal(
                    "§e[Enhanced-vq] §rNo encontré tu Cofre de Quest — te di uno nuevo. "
                    + "Colócalo y ábrelo para vincularlo."));
            }
            syncToClient(player, saved);
        });

        // Handler C2S: jugador abrió "Datos de la Partida" — lee las estadísticas
        // propias de Minecraft (no un contador del mod) y responde con los 3
        // números. mobKills sale directo de Stats.MOB_KILLS (ya es un total).
        // blocksMined no tiene un total vanilla propio, así que se suma
        // Stats.BLOCK_MINED sobre TODO el registro de bloques — por eso este
        // cálculo se hace bajo demanda acá y no en cada QuestSyncPayload.
        ServerPlayNetworking.registerGlobalReceiver(RequestGameStatsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            QuestSavedData saved = QuestSavedData.get(server);
            UUID uuid = player.getUUID();

            long mobKills = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            long blocksMined = 0;
            for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
                blocksMined += player.getStats().getValue(Stats.BLOCK_MINED.get(block));
            }
            long blocksPlaced = saved.getBlocksPlacedTotal(uuid);

            ServerPlayNetworking.send(player, new GameStatsPayload(mobKills, blocksMined, blocksPlaced));
        });

        // Handler C2S: terminó o saltó el tutorial del libro — no hace falta
        // volver a sincronizar todo el estado, el cliente ya sabe que terminó.
        ServerPlayNetworking.registerGlobalReceiver(TutorialSeenPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            QuestSavedData saved = QuestSavedData.get(context.server());
            saved.markTutorialSeen(player.getUUID());
        });
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        com.homequest.sound.ModSounds.initialize();

        // Al entrar al servidor: dar cofre si es primera vez + sync estado al cliente
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            QuestSavedData savedData = QuestSavedData.get(server);
            if (!savedData.hasReceivedChest(player.getUUID())) {
                giveQuestChest(player);
                savedData.markReceivedChest(player.getUUID());
                player.sendSystemMessage(Component.literal(
                    "§6§l[Enhanced-vq] §r§eBienvenido. Coloca el §6§lCofre de Quest§e en tu base y haz clic derecho en él."));
            }
            // Sincronizar estado de quests al cliente (restaura progreso de sesiones previas)
            syncToClient(player, savedData);
        });

        // Al desconectar un jugador: volcar a disco lo pendiente de ESE servidor.
        // Red de seguridad extra ademas del guardado periodico de onServerTick,
        // para que el progreso quede escrito ni bien alguien se va.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            QuestSavedData.get(server).flushIfDirty();
        });

        // El Libro de Quests sobrevive a la muerte
        // Paso 1: Antes de morir, quitar el libro del inventario para que Minecraft no lo dropee
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register(
            (entity, damageSource, damageAmount) -> {
                if (entity instanceof ServerPlayer sp) {
                    for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                        ItemStack stack = sp.getInventory().getItem(i);
                        if (!stack.isEmpty() && stack.getItem() instanceof com.homequest.item.QuestBookItem) {
                            // Quitar del inventario antes de que Minecraft procese los drops de muerte
                            sp.getInventory().setItem(i, ItemStack.EMPTY);
                            break;
                        }
                    }
                }
                return true; // No cancelar la muerte, solo quitar el libro
            }
        );

        // GUERRERO: contabilizar mobs derrotados por el jugador para las quests de
        // "Cazador de X". AFTER_DEATH se dispara para cualquier LivingEntity que
        // muere en el server; solo nos interesan los mobs de la tabla WARRIOR_MOB_KEY,
        // y solo si el responsable directo del daño fue un jugador (así una araña que
        // muere quemada por el sol, o un piglin que se ahoga, no cuenta).
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
            (entity, damageSource) -> {
                if (!(damageSource.getEntity() instanceof ServerPlayer sp)) return;

                // Variantes "montadas"/bebé: comparten EntityType con la forma normal
                // (un bebé zombi sigue siendo EntityTypes.ZOMBIE), así que hay que
                // mirar el estado del entity (isBaby()/getVehicle()) ANTES de ir al
                // mapa genérico de abajo — si no, todas caerían indistintamente en
                // "zombie"/"skeleton" y nunca se revelarían aparte en el Bestiario.
                // El juego mismo hace exactamente esto para el disco "Lava Chicken"
                // (solo dropea si el zombi que muere es bebé Y va montado en una
                // gallina) — mismo patrón acá, ampliado a las otras combinaciones.
                var vehicle = entity.getVehicle();
                if (entity.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE) {
                    boolean baby = entity instanceof net.minecraft.world.entity.Mob m && m.isBaby();
                    if (baby && vehicle != null && vehicle.getType() == net.minecraft.world.entity.EntityTypes.CHICKEN) {
                        onWarriorKill(sp, "chicken_jockey");
                        onWarriorKill(sp, "zombie"); // sigue contando como zombi normal también
                        return;
                    }
                    if (baby) {
                        onWarriorKill(sp, "baby_zombie");
                        onWarriorKill(sp, "zombie");
                        return;
                    }
                    if (vehicle != null && vehicle.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE_HORSE) {
                        onWarriorKill(sp, "zombie_horseman");
                        onWarriorKill(sp, "zombie");
                        return;
                    }
                } else if (entity.getType() == net.minecraft.world.entity.EntityTypes.SKELETON && vehicle != null) {
                    if (vehicle.getType() == net.minecraft.world.entity.EntityTypes.SKELETON_HORSE) {
                        onWarriorKill(sp, "skeleton_horseman");
                        onWarriorKill(sp, "skeleton");
                        return;
                    }
                    if (vehicle.getType() == net.minecraft.world.entity.EntityTypes.SPIDER) {
                        onWarriorKill(sp, "spider_jockey");
                        onWarriorKill(sp, "skeleton");
                        return;
                    }
                }

                String mobKey = WARRIOR_MOB_KEY.get(entity.getType());
                if (mobKey == null) return;
                onWarriorKill(sp, mobKey);
            }
        );

        // Paso 2: Al hacer respawn, restaurar el libro (lo quitamos en el paso 1 así que
        // COPY_FROM copia un inventario sin libro — usamos AFTER_RESPAWN en su lugar)
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) -> {
                if (alive) return; // Solo al morir
                // El libro fue quitado antes de morir, darlo de vuelta al respawn
                if (!hasQuestBook(newPlayer)) {
                    newPlayer.getInventory().add(new ItemStack(com.homequest.item.ModItems.QUEST_BOOK));
                }
            }
        );

        // (Eliminar el COPY_FROM antiguo ya que AFTER_RESPAWN lo reemplaza)

        // NOTA: la deteccion de "Consigue X de <mineral>" para las misiones de
        // Minero ya NO se hace contando bloques de mena rotos (ver checkResourceGoals,
        // llamado desde onServerTick). Antes, romper 10 bloques de Carbón marcaba
        // "Consigue 10 de carbón" como cumplida aunque el jugador tuviera Toque de
        // Seda (que da el bloque de mena, no el carbón) o perdiera después ese
        // carbón — no coincidía con lo que el texto de la misión promete.

        // Detectar bloques colocados — Constructor y misiones generales
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            UUID uuid = sp.getUUID();
            QuestSavedData saved = QuestSavedData.get(sp.level().getServer());

            // Total de "Datos de la Partida" — de por vida, sin las condiciones de
            // abajo (que son solo para la lógica de quests). Antes del gate de
            // hasStartedQuests/hasCompletedAllQuests a propósito, para que la
            // página de estadísticas no se quede pausada apenas se termina el mod.
            ItemStack heldForTotal = player.getItemInHand(hand);
            if (!heldForTotal.isEmpty() && heldForTotal.getItem() instanceof net.minecraft.world.item.BlockItem) {
                saved.addBlockPlacedTotal(uuid);
            }

            if (!saved.hasStartedQuests(uuid)) return net.minecraft.world.InteractionResult.PASS;
            // Ya completó TODO el mod: colocar bloques no puede completar nada más,
            // así que no hace falta seguir contando ni reenviando el estado al
            // cliente en cada bloque — cortamos acá antes de tocar nada.
            if (saved.hasCompletedAllQuests(uuid)) return net.minecraft.world.InteractionResult.PASS;

            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty() || !(held.getItem() instanceof net.minecraft.world.item.BlockItem bi))
                return net.minecraft.world.InteractionResult.PASS;

            net.minecraft.world.level.block.Block placedBlock = bi.getBlock();
            net.minecraft.world.level.block.state.BlockState placedState = placedBlock.defaultBlockState();

            // ── Contador genérico de bloques (Constructor) ──
            saved.addBlockPlaced(uuid);
            int totalPlaced = saved.getBlocksPlaced(uuid);
            if (!saved.isCompleted(uuid, QuestData.QUEST_HONEST_WORK) && totalPlaced >= 50) {
                completeQuest(sp, (ServerLevel) world, QuestData.QUEST_HONEST_WORK,
                    "§e¡Es Trabajo Honesto! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_HONEST_WORK);
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_FIRM_FOUNDATIONS) && totalPlaced >= 250) {
                completeQuest(sp, (ServerLevel) world, QuestData.QUEST_FIRM_FOUNDATIONS,
                    "§e¡Bases Sólidas! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_FIRM_FOUNDATIONS);
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_MASON_HANDS) && totalPlaced >= 1000) {
                completeQuest(sp, (ServerLevel) world, QuestData.QUEST_MASON_HANDS,
                    "§e¡Manos de Albañil! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_MASON_HANDS);
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_GREAT_ARCHITECT) && totalPlaced >= 5000) {
                completeQuest(sp, (ServerLevel) world, QuestData.QUEST_GREAT_ARCHITECT,
                    "§e¡Gran Arquitecto! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_GREAT_ARCHITECT);
            }

            // ── Escaleras ──
            if (placedState.is(BlockTags.STAIRS)) {
                saved.addStairsPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_STEP_BY_STEP) && saved.getStairsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_STEP_BY_STEP,
                        "§e¡Paso a Paso! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_STEP_BY_STEP);
                }
            }
            // ── Losas ──
            if (placedState.is(BlockTags.SLABS)) {
                saved.addSlabsPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_HALF_BLOCK) && saved.getSlabsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_HALF_BLOCK,
                        "§e¡Medio Bloque! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_HALF_BLOCK);
                }
            }
            // ── Trampillas ──
            if (placedState.is(BlockTags.TRAPDOORS)) {
                saved.addTrapdoorsPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_ESCAPE_HATCH) && saved.getTrapdoorsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_ESCAPE_HATCH,
                        "§e¡Escotilla de Escape! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_ESCAPE_HATCH);
                }
            }
            // ── Puertas ──
            if (placedState.is(BlockTags.DOORS)) {
                saved.addDoorsPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_KNOCK_BEFORE) && saved.getDoorsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_KNOCK_BEFORE,
                        "§e¡Llama Antes de Entrar! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_KNOCK_BEFORE);
                }
            }
            // ── Cristal / vidrio ──
            if (isGlassBlock(placedBlock)) {
                saved.addGlassPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_FRAGILE_TRANSP) && saved.getGlassPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_FRAGILE_TRANSP,
                        "§e¡Frágil y Transparente! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_FRAGILE_TRANSP);
                }
            }
            // ── Vallas ──
            if (placedState.is(BlockTags.FENCES) || placedState.is(BlockTags.FENCE_GATES)) {
                saved.addFencesPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_NO_PASS) && saved.getFencesPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_NO_PASS,
                        "§e¡No Pasarás! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_NO_PASS);
                }
            }
            // ── Faroles (lantern) ──
            if (placedState.is(BlockTags.LANTERNS) && placedBlock != Blocks.SOUL_LANTERN) {
                saved.addLanternsPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_SAFE_NIGHTS) && saved.getLanternsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_SAFE_NIGHTS,
                        "§e¡Noches Seguras! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_SAFE_NIGHTS);
                }
            }
            // ── Linternas marinas (sea lantern) ──
            if (placedBlock == Blocks.SEA_LANTERN) {
                saved.addSeaLanternsPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_MARINE_LIGHT) && saved.getSeaLanternsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_MARINE_LIGHT,
                        "§e¡Luz Marina! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_MARINE_LIGHT);
                }
            }
            // ── Decorativos (macetas, banners, marcos, alfombras) ──
            if (placedState.is(BlockTags.FLOWER_POTS) || placedState.is(BlockTags.BANNERS)
             || placedState.is(BlockTags.WOOL_CARPETS)) {
                saved.addDecorativePlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_DETAILS_MATTER) && saved.getDecorativePlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_DETAILS_MATTER,
                        "§e¡Los Detalles Importan! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_DETAILS_MATTER);
                }
            }
            // ── Bloques tallados (chiseled) ──
            if (isChiseledBlock(placedBlock)) {
                saved.addChiseledPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_CHISEL_STONE) && saved.getChiseledPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_CHISEL_STONE,
                        "§e¡Cincel y Piedra! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_CHISEL_STONE);
                }
            }
            // ── Ladrillos ──
            if (placedBlock == Blocks.BRICKS || placedBlock == Blocks.BRICK_STAIRS
             || placedBlock == Blocks.BRICK_SLAB || placedBlock == Blocks.BRICK_WALL
             || placedBlock == Blocks.MUD_BRICKS || placedBlock == Blocks.MUD_BRICK_STAIRS
             || placedBlock == Blocks.MUD_BRICK_SLAB || placedBlock == Blocks.MUD_BRICK_WALL) {
                saved.addBrickPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_PIG_HOUSE) && saved.getBrickPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_PIG_HOUSE,
                        "§e¡Casa de Ladrillo! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_PIG_HOUSE);
                }
            }
            // ── Cuarzo ──
            if (placedBlock == Blocks.QUARTZ_BLOCK || placedBlock == Blocks.QUARTZ_PILLAR
             || placedBlock == Blocks.QUARTZ_BRICKS || placedBlock == Blocks.QUARTZ_STAIRS
             || placedBlock == Blocks.QUARTZ_SLAB || placedBlock == Blocks.CHISELED_QUARTZ_BLOCK
             || placedBlock == Blocks.SMOOTH_QUARTZ || placedBlock == Blocks.SMOOTH_QUARTZ_STAIRS
             || placedBlock == Blocks.SMOOTH_QUARTZ_SLAB) {
                saved.addQuartzPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_CLASSIC_TEMPLE) && saved.getQuartzPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_CLASSIC_TEMPLE,
                        "§e¡Templo Clásico! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_CLASSIC_TEMPLE);
                }
            }
            // ── Hormigón ──
            if (isConcreteOrPowder(placedBlock)) {
                saved.addConcretePlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_COLOR_PALETTE) && saved.getConcretePlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_COLOR_PALETTE,
                        "§e¡Paleta de Colores! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_COLOR_PALETTE);
                }
            }
            // ── Miel ──
            if (placedBlock == Blocks.HONEY_BLOCK || placedBlock == Blocks.HONEYCOMB_BLOCK) {
                saved.addHoneyPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_STICKY_STRUCT) && saved.getHoneyPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_STICKY_STRUCT,
                        "§e¡Estructura Pegajosa! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_STICKY_STRUCT);
                }
            }
            // ── Campana ──
            if (placedBlock == Blocks.BELL && !saved.isCompleted(uuid, QuestData.QUEST_PLAZA_MEETING)) {
                saved.markPlacedBell(uuid);
                completeQuest(sp, (ServerLevel) world, QuestData.QUEST_PLAZA_MEETING,
                    "§e¡Plaza de Reunión! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_PLAZA_MEETING);
            }
            // ── Baliza ──
            if (placedBlock == Blocks.BEACON && !saved.isCompleted(uuid, QuestData.QUEST_BEACON_LIGHT)) {
                saved.markPlacedBeacon(uuid);
                completeQuest(sp, (ServerLevel) world, QuestData.QUEST_BEACON_LIGHT,
                    "§e¡Luz de Baliza! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_BEACON_LIGHT);
            }
            // ── TÉCNICO: bloques colocados ──
            if (placedBlock == Blocks.PISTON) {
                saved.addPistonPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_BRUTE_FORCE) && saved.getPistonsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_BRUTE_FORCE,
                        "§e¡Fuerza Bruta! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_BRUTE_FORCE);
                }
            }
            if (placedBlock == Blocks.STICKY_PISTON) {
                saved.addStickyPistonPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_BACK_FORTH) && saved.getStickyPistonsPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_BACK_FORTH,
                        "§e¡Ida y Vuelta! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_BACK_FORTH);
                }
            }
            if (placedBlock == Blocks.REPEATER) {
                saved.addRepeaterPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_CONTROLLED_DELAY) && saved.getRepeatersPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_CONTROLLED_DELAY,
                        "§e¡Delay Controlado! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_CONTROLLED_DELAY);
                }
            }
            if (placedBlock == Blocks.COMPARATOR) {
                saved.addComparatorPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_REDSTONE_BRAIN) && saved.getComparatorsPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_REDSTONE_BRAIN,
                        "§e¡Cerebro de Redstone! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_REDSTONE_BRAIN);
                }
            }
            if (placedBlock == Blocks.HOPPER) {
                saved.addHopperPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_AUTO_LOGISTICS) && saved.getHoppersPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_AUTO_LOGISTICS,
                        "§e¡Logística Automática! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_AUTO_LOGISTICS);
                }
                // Detección simple de cadena de tolvas: 3+ tolvas adyacentes
                if (!saved.hasBuiltHopperChain(uuid) && !saved.isCompleted(uuid, QuestData.QUEST_SMART_DIST)) {
                    net.minecraft.core.BlockPos hpos = hitResult.getBlockPos().relative(hitResult.getDirection());
                    int adjHoppers = 0;
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        if (((ServerLevel) world).getBlockState(hpos.relative(dir)).is(Blocks.HOPPER)) adjHoppers++;
                    }
                    if (adjHoppers >= 2) {
                        saved.markBuiltHopperChain(uuid);
                        completeQuest(sp, (ServerLevel) world, QuestData.QUEST_SMART_DIST,
                            "§e¡Distribución Inteligente! §7— Abre el libro para reclamar");
                        saved.markCompleted(uuid, QuestData.QUEST_SMART_DIST);
                    }
                }
            }
            if (placedBlock == Blocks.DISPENSER) {
                saved.addDispenserPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_FIRE_AT_WILL) && saved.getDispensersPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_FIRE_AT_WILL,
                        "§e¡Fuego a Voluntad! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_FIRE_AT_WILL);
                }
            }
            if (placedBlock == Blocks.DROPPER) {
                saved.addDropperPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_DROP_GOODS) && saved.getDroppersPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_DROP_GOODS,
                        "§e¡Suelta la Mercancía! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_DROP_GOODS);
                }
            }
            if (placedBlock == Blocks.OBSERVER) {
                saved.addObserverPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_EYES_WALLS) && saved.getObserversPlaced(uuid) >= 10) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_EYES_WALLS,
                        "§e¡Ojos en las Paredes! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_EYES_WALLS);
                }
                // Reloj de redstone: observer adyacente a otro observer
                if (!saved.hasBuiltRedstoneClock(uuid) && !saved.isCompleted(uuid, QuestData.QUEST_TIC_TAC)) {
                    net.minecraft.core.BlockPos opos = hitResult.getBlockPos().relative(hitResult.getDirection());
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        if (((ServerLevel) world).getBlockState(opos.relative(dir)).is(Blocks.OBSERVER)) {
                            saved.markBuiltRedstoneClock(uuid);
                            completeQuest(sp, (ServerLevel) world, QuestData.QUEST_TIC_TAC,
                                "§e¡Tic Tac! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_TIC_TAC);
                            break;
                        }
                    }
                }
            }
            if (placedBlock == Blocks.LEVER) {
                saved.addLeverPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_MANUAL_CTRL) && saved.getLeversPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_MANUAL_CTRL,
                        "§e¡Control Manual! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_MANUAL_CTRL);
                }
            }
            if (placedState.is(BlockTags.BUTTONS)) {
                saved.addButtonPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_DONT_PRESS) && saved.getButtonsPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_DONT_PRESS,
                        "§e¡No Lo Presiones! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_DONT_PRESS);
                }
            }
            if (placedState.is(BlockTags.PRESSURE_PLATES)) {
                saved.addPressurePlatePlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_UNDER_FEET) && saved.getPressurePlatesPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_UNDER_FEET,
                        "§e¡Bajo tus Pies! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_UNDER_FEET);
                }
            }
            if (placedBlock == Blocks.IRON_TRAPDOOR) {
                saved.addIronTrapdoorPlaced(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_INDUSTRIAL_SAFETY) && saved.getIronTrapdoorsPlaced(uuid) >= 20) {
                    completeQuest(sp, (ServerLevel) world, QuestData.QUEST_INDUSTRIAL_SAFETY,
                        "§e¡Seguridad Industrial! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_INDUSTRIAL_SAFETY);
                }
            }

            syncToClient(sp, saved);
            return net.minecraft.world.InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Al apagar el servidor (o cerrar el mundo en singleplayer): volcar a disco
        // lo que quedó pendiente y liberar el cache. Sin esto, un cierre podría
        // perder hasta CHECK_INTERVAL ticks de progreso sin guardar.
        ServerLifecycleEvents.SERVER_STOPPING.register(QuestSavedData::unload);

        registerCommands();

        LOGGER.info("[Enhanced-vq] Eventos registrados.");
    }

    /**
     * Comandos de administración (requieren nivel de permiso 2, como /gamemode):
     *   /homequest reset <jugador>   — resetea el progreso de un jugador puntual.
     *   /homequest resetall confirm — resetea el progreso de TODOS los jugadores
     *                                 con datos guardados (online u offline).
     * En ambos casos: se borran las misiones completadas/reclamadas y todos los
     * contadores, se retira (si sigue colocado) el Cofre de Quest del mundo, se
     * limpian copias del libro/cofre del inventario de jugadores online, y se
     * les entrega un Cofre de Quest nuevo — igual que al entrar por primera vez.
     * La elección de idioma NO se toca.
     * <p>
     * Límite conocido: el cofre colocado solo se rastrea en el Overworld (el mod
     * no guarda en qué dimensión lo pusiste), y copias sueltas que un jugador
     * haya movido a otro cofre o tirado al suelo en el mundo no se persiguen
     * automáticamente — esto solo limpia el inventario de jugadores conectados
     * y el cofre que el propio mod rastrea por jugador.
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("homequest")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reset")
                    .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
                            MinecraftServer server = ctx.getSource().getServer();
                            QuestSavedData saved = QuestSavedData.get(server);
                            int chests = resetOnePlayer(server, saved, target.getUUID());
                            saved.flushIfDirty();
                            String targetName = target.getName().getString();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a[Enhanced-vq] Progreso de " + targetName + " reiniciado. "
                                    + chests + " cofre(s) retirado(s) del mundo."), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("resetall")
                    .executes(ctx -> {
                        ctx.getSource().sendFailure(Component.literal(
                            "§c[Enhanced-vq] Esto borra el progreso de TODOS los jugadores (online y offline) "
                                + "y no se puede deshacer. Escribe §f/homequest resetall confirm§c para continuar."));
                        return 0;
                    })
                    .then(Commands.literal("confirm")
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            QuestSavedData saved = QuestSavedData.get(server);
                            int players = 0, chests = 0;
                            for (UUID uuid : saved.getAllKnownPlayers()) {
                                chests += resetOnePlayer(server, saved, uuid);
                                players++;
                            }
                            saved.flushIfDirty();
                            int fp = players, fc = chests;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a[Enhanced-vq] Progreso reiniciado para " + fp + " jugador(es). "
                                    + fc + " cofre(s) retirado(s) del mundo."), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("bestiary")
                    .then(Commands.literal("unlockall")
                        // Sin argumento: desbloquea para quien ejecuta el comando (debe ser un jugador).
                        .executes(ctx -> unlockBestiaryTest(ctx, ctx.getSource().getPlayerOrException()))
                        // Con argumento: un game master puede desbloquearlo para otro jugador online.
                        .then(Commands.argument("jugador", EntityArgument.player())
                            .executes(ctx -> unlockBestiaryTest(ctx, EntityArgument.getPlayer(ctx, "jugador")))
                        )
                    )
                )
            );
        });
    }

    /**
     * Solo para pruebas: sube a 1 el contador de kills de TODOS los mobs que reconoce
     * el Bestiario, así se puede revisar rápido que cada página (arte, textos,
     * animación) se vea bien sin tener que ir a matar cada mob a mano. No completa
     * quests de "Cazador de X" (esas requieren varios kills — 5, 10 — que este comando
     * no otorga) ni toca ningún otro progreso del jugador.
     */
    private int unlockBestiaryTest(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            ServerPlayer target) {
        MinecraftServer server = ctx.getSource().getServer();
        QuestSavedData saved = QuestSavedData.get(server);
        saved.setAllMobKillsAtLeast(target.getUUID(), ALL_BESTIARY_MOB_KEYS, 1);
        saved.flushIfDirty();
        syncToClient(target, saved);
        String targetName = target.getName().getString();
        int total = ALL_BESTIARY_MOB_KEYS.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§a[Enhanced-vq] Bestiario de " + targetName + " desbloqueado por completo ("
                + total + " mobs) — solo para pruebas."), true);
        target.sendSystemMessage(Component.literal(
            "§6§l[Enhanced-vq] §r§eSe desbloqueó todo el Bestiario para pruebas."));
        return 1;
    }

    /**
     * Resetea a un jugador puntual (online u offline): borra su progreso
     * guardado, y si está conectado además retira el cofre del mundo (si
     * sigue en pie), limpia copias del libro/cofre de su inventario y le
     * entrega un Cofre de Quest nuevo. Devuelve 1 si se retiró un cofre del
     * mundo, 0 si no había ninguno (o ya no estaba).
     */
    private int resetOnePlayer(MinecraftServer server, QuestSavedData saved, UUID uuid) {
        int chestsRemoved = 0;
        if (saved.hasChestPlaced(uuid)) {
            BlockPos pos = saved.getChestPos(uuid);
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null && overworld.isLoaded(pos)
                    && overworld.getBlockState(pos).is(ModBlocks.QUEST_CHEST_BLOCK)) {
                overworld.removeBlock(pos, false);
                chestsRemoved = 1;
            }
        }

        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            removeQuestItemsFromInventory(online);
        }

        saved.resetPlayer(uuid);

        if (online != null) {
            giveQuestChest(online);
            saved.markReceivedChest(uuid);
            syncToClient(online, saved);
            online.sendSystemMessage(Component.literal(
                "§6§l[Enhanced-vq] §r§eTu progreso de misiones fue reiniciado. Recibiste un nuevo §6§lCofre de Quest§e."));
        }
        return chestsRemoved;
    }

    /** Quita cualquier copia del Libro o Cofre de Quest del inventario principal del jugador. */
    private void removeQuestItemsFromInventory(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && (stack.getItem() == ModItems.QUEST_BOOK || stack.getItem() == ModItems.QUEST_CHEST)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;
        syncedThisTick.clear();

        // Cargar saved data UNA vez por tick, no una vez por jugador
        QuestSavedData saved = QuestSavedData.get(server);

        for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : world.players()) {
                UUID uuid = player.getUUID();

                // Quest 1: Primera Casa (también requiere questsStarted, ya verificado arriba)
                if (!saved.isCompleted(uuid, QuestData.QUEST_FIRST_HOME)
                    && saved.hasChestPlaced(uuid)) {
                    BlockPos chestPos = saved.getChestPos(uuid);
                    if (chestPos == null || !(world.getBlockState(chestPos).getBlock() instanceof QuestChestBlock)) {
                        saved.removeChestPos(uuid);
                    } else if (checkFirstHome(world, chestPos)) {
                        completeFirstHome(player, world, chestPos, saved);
                        saved.markCompleted(uuid, QuestData.QUEST_FIRST_HOME);
                        syncToClient(player, saved); // sync DESPUÉS de markCompleted
                    }
                }

                // Solo procesar quests si el jugador ya tomó el libro
                if (!saved.hasStartedQuests(uuid)) continue;

                // Ya completó TODO el mod: no hace falta seguir escaneando inventario,
                // bloques colocados, biomas, estructuras, etc. para este jugador — nada
                // de esto puede completar ninguna quest nueva, así que se saltea entero.
                if (!saved.hasCompletedAllQuests(uuid)) {
                    // "Consigue X de <mineral>": se revisa cuanto tiene el jugador
                    // AHORA en su inventario, no cuanto minero historicamente.
                    checkResourceGoals(player, world, saved);

                    // GUERRERO: incursiones. No hay un evento vanilla de "incursión
                    // ganada" a mano en Fabric API, así que se detecta por el flanco
                    // del efecto Héroe de la Aldea (vanilla se lo da automáticamente
                    // a los jugadores cerca al ganar una incursión) — de false a true
                    // significa "recién ganó una". checkRaidVictory() ya se encarga de
                    // no hacer nada si el jugador no tiene ese cambio de estado.
                    checkRaidVictory(player, saved);

                    // Quest 2: Un Nuevo Mundo — detectar bioma distinto al de spawn
                    if (!saved.isCompleted(uuid, QuestData.QUEST_NEW_WORLD)) {
                        String spawnBiome = saved.getSpawnBiome(uuid);
                        // Si aún no hay bioma de spawn registrado, esperar a que el jugador
                        // abra el cofre (se registra en QuestChestBlock.useWithoutItem)
                        if (!spawnBiome.isEmpty()) {
                            var biomeHolder = world.getBiome(player.blockPosition());
                            var biomeKeyOpt = biomeHolder.unwrapKey();
                            if (biomeKeyOpt.isPresent()) {
                                String biomeStr = biomeKeyOpt.get().toString();
                                boolean isNewBiome = !biomeStr.equals(spawnBiome)
                                                  && !saved.getDiscoveredBiomes(uuid).contains(biomeStr);
                                if (isNewBiome) {
                                    saved.addDiscoveredBiome(uuid, biomeStr);
                                    completeQuest(player, world, QuestData.QUEST_NEW_WORLD,
                                        "§e¡Un Nuevo Mundo! §7— Abre el libro para reclamar");
                                    saved.markCompleted(uuid, QuestData.QUEST_NEW_WORLD);
                                    syncToClient(player, saved);
                                }
                            }
                        }
                    }

                    // Detectar biomas para misiones Explorador (compartido con QUEST_NEW_WORLD)
                    var biomeHolder = world.getBiome(player.blockPosition());
                    var biomeKeyOpt = biomeHolder.unwrapKey();
                    if (biomeKeyOpt.isPresent()) {
                        String biomeStr = biomeKeyOpt.get().toString();
                        if (!saved.getDiscoveredBiomes(uuid).contains(biomeStr)) {
                            saved.addDiscoveredBiome(uuid, biomeStr);
                            int biomeCount = saved.getDiscoveredBiomes(uuid).size();

                            // Quest: Más Allá del Horizonte — 3 biomas
                            if (!saved.isCompleted(uuid, QuestData.QUEST_BEYOND_HORIZON) && biomeCount >= 3) {
                                completeQuest(player, world, QuestData.QUEST_BEYOND_HORIZON,
                                    "§e¡Más Allá del Horizonte! §7— Abre el libro para reclamar");
                                saved.markCompleted(uuid, QuestData.QUEST_BEYOND_HORIZON);
                                syncToClient(player, saved);
                            }

                            // Quest: Sin Fronteras — 10 biomas
                            if (!saved.isCompleted(uuid, QuestData.QUEST_NO_BORDERS) && biomeCount >= 10) {
                                completeQuest(player, world, QuestData.QUEST_NO_BORDERS,
                                    "§e¡Sin Fronteras! §7— Abre el libro para reclamar");
                                saved.markCompleted(uuid, QuestData.QUEST_NO_BORDERS);
                                syncToClient(player, saved);
                            }

                            // Quest: No Queda Nada por Ver — 50 biomas (aproximación de "todos")
                            if (!saved.isCompleted(uuid, QuestData.QUEST_ALL_SEEN) && biomeCount >= 50) {
                                completeQuest(player, world, QuestData.QUEST_ALL_SEEN,
                                    "§e¡No Queda Nada por Ver! §7— Abre el libro para reclamar");
                                saved.markCompleted(uuid, QuestData.QUEST_ALL_SEEN);
                                syncToClient(player, saved);
                            }
                        }
                    }

                    // Quest: Hacia lo Desconocido — entrar al Nether
                    if (!saved.isCompleted(uuid, QuestData.QUEST_INTO_UNKNOWN) && !saved.hasVisitedNether(uuid)) {
                        if (world.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
                            saved.markVisitedNether(uuid);
                            completeQuest(player, world, QuestData.QUEST_INTO_UNKNOWN,
                                "§e¡Hacia lo Desconocido! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_INTO_UNKNOWN);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Un Mundo Extraño — llegar al End (Explorador, pero detectamos aquí)
                    if (!saved.isCompleted(uuid, QuestData.QUEST_STRANGE_WORLD) && !saved.hasVisitedEnd(uuid)) {
                        if (world.dimension().equals(net.minecraft.world.level.Level.END)) {
                            saved.markVisitedEnd(uuid);
                            completeQuest(player, world, QuestData.QUEST_STRANGE_WORLD,
                                "§e¡Un Mundo Extraño! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_STRANGE_WORLD);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Los Primeros Lingotes — 10 hierro
                    if (!saved.isCompleted(uuid, QuestData.QUEST_FIRST_INGOTS)) {
                        if (countItem(player, Items.IRON_INGOT) >= 10) {
                            completeQuest(player, world, QuestData.QUEST_FIRST_INGOTS,
                                "§e¡Los Primeros Lingotes! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_FIRST_INGOTS);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Bien Protegido — armadura completa de hierro
                    if (!saved.isCompleted(uuid, QuestData.QUEST_WELL_PROTECTED)) {
                        if (hasFullArmor(player, Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                                         Items.IRON_LEGGINGS, Items.IRON_BOOTS)) {
                            completeQuest(player, world, QuestData.QUEST_WELL_PROTECTED,
                                "§e¡Bien Protegido! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_WELL_PROTECTED);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: La Mejor Defensa — escudo
                    if (!saved.isCompleted(uuid, QuestData.QUEST_BEST_DEFENSE)) {
                        if (hasItem(player, Items.SHIELD)) {
                            completeQuest(player, world, QuestData.QUEST_BEST_DEFENSE,
                                "§e¡La Mejor Defensa! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_BEST_DEFENSE);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: ¡Lo Encontré! — 1 diamante
                    if (!saved.isCompleted(uuid, QuestData.QUEST_FOUND_IT)) {
                        if (hasItem(player, Items.DIAMOND)) {
                            completeQuest(player, world, QuestData.QUEST_FOUND_IT,
                                "§e¡Lo Encontré! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_FOUND_IT);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Jugando con Fuego — 5 blaze rods
                    if (!saved.isCompleted(uuid, QuestData.QUEST_PLAYING_FIRE)) {
                        if (countItem(player, Items.BLAZE_ROD) >= 5) {
                            completeQuest(player, world, QuestData.QUEST_PLAYING_FIRE,
                                "§e¡Jugando con Fuego! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_PLAYING_FIRE);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Entre Dimensiones — 8 ender pearls
                    if (!saved.isCompleted(uuid, QuestData.QUEST_BETWEEN_DIMS)) {
                        if (countItem(player, Items.ENDER_PEARL) >= 8) {
                            completeQuest(player, world, QuestData.QUEST_BETWEEN_DIMS,
                                "§e¡Entre Dimensiones! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_BETWEEN_DIMS);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Alza el Vuelo — elytra
                    if (!saved.isCompleted(uuid, QuestData.QUEST_TAKE_FLIGHT)) {
                        if (hasItem(player, Items.ELYTRA)) {
                            completeQuest(player, world, QuestData.QUEST_TAKE_FLIGHT,
                                "§e¡Alza el Vuelo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_TAKE_FLIGHT);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Una Nueva Esperanza — nether star
                    if (!saved.isCompleted(uuid, QuestData.QUEST_NEW_HOPE)) {
                        if (hasItem(player, Items.NETHER_STAR)) {
                            completeQuest(player, world, QuestData.QUEST_NEW_HOPE,
                                "§e¡Una Nueva Esperanza! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_NEW_HOPE);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Un Nuevo Comienzo — mesa de crafteo
                    if (!saved.isCompleted(uuid, QuestData.QUEST_NEW_BEGINNING)) {
                        if (hasItem(player, Items.CRAFTING_TABLE)) {
                            completeQuest(player, world, QuestData.QUEST_NEW_BEGINNING,
                                "§e¡Un Nuevo Comienzo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_NEW_BEGINNING);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Manos a la Obra — herramientas de piedra (pico, hacha, pala, azada)
                    if (!saved.isCompleted(uuid, QuestData.QUEST_HANDS_ON)) {
                        boolean hasStonePickaxe = hasItem(player, Items.STONE_PICKAXE);
                        boolean hasStoneAxe = hasItem(player, Items.STONE_AXE);
                        boolean hasStoneShovel = hasItem(player, Items.STONE_SHOVEL);
                        boolean hasStoneHoe = hasItem(player, Items.STONE_HOE);
                        if (hasStonePickaxe && hasStoneAxe && hasStoneShovel && hasStoneHoe) {
                            completeQuest(player, world, QuestData.QUEST_HANDS_ON,
                                "§e¡Manos a la Obra! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_HANDS_ON);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Listo para Todo — pico de diamante
                    if (!saved.isCompleted(uuid, QuestData.QUEST_READY_FOR_ALL)) {
                        if (hasItem(player, Items.DIAMOND_PICKAXE)) {
                            completeQuest(player, world, QuestData.QUEST_READY_FOR_ALL,
                                "§e¡Listo para Todo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_READY_FOR_ALL);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: El Poder del Conocimiento — mesa de encantamientos
                    if (!saved.isCompleted(uuid, QuestData.QUEST_KNOWLEDGE)) {
                        if (hasItem(player, Items.ENCHANTING_TABLE)) {
                            completeQuest(player, world, QuestData.QUEST_KNOWLEDGE,
                                "§e¡El Poder del Conocimiento! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_KNOWLEDGE);
                            syncToClient(player, saved);
                        }
                    }

                    // ── MAGO (pociones + encantamientos) ──
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_BREWING_STAND)) {
                        if (hasItem(player, Items.BREWING_STAND)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_BREWING_STAND,
                                "§e¡Alambique! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_BREWING_STAND);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_AWKWARD)) {
                        if (hasPotionBase(player, "awkward")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_AWKWARD,
                                "§e¡Poción Incierta! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_AWKWARD);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_SWIFTNESS)) {
                        if (hasPotionBase(player, "swiftness")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_SWIFTNESS,
                                "§e¡Poción de Velocidad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_SWIFTNESS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_POISON)) {
                        if (hasPotionBase(player, "poison")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_POISON,
                                "§e¡Poción de Veneno! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_POISON);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_WEAKNESS)) {
                        if (hasPotionBase(player, "weakness")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_WEAKNESS,
                                "§e¡Poción de Debilidad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_WEAKNESS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_SLOWNESS)) {
                        if (hasPotionBase(player, "slowness")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_SLOWNESS,
                                "§e¡Poción de Lentitud! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_SLOWNESS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_FIRE_RESISTANCE)) {
                        if (hasPotionBase(player, "fire_resistance")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_FIRE_RESISTANCE,
                                "§e¡Poción de Resistencia al Fuego! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_FIRE_RESISTANCE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_HEALING)) {
                        if (hasPotionBase(player, "healing")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_HEALING,
                                "§e¡Poción de Curación! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_HEALING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_HARMING)) {
                        if (hasPotionBase(player, "harming")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_HARMING,
                                "§e¡Poción de Daño! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_HARMING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_LEAPING)) {
                        if (hasPotionBase(player, "leaping")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_LEAPING,
                                "§e¡Poción de Salto! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_LEAPING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_STRENGTH)) {
                        if (hasPotionBase(player, "strength")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_STRENGTH,
                                "§e¡Poción de Fuerza! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_STRENGTH);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_REGENERATION)) {
                        if (hasPotionBase(player, "regeneration")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_REGENERATION,
                                "§e¡Poción de Regeneración! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_REGENERATION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_NIGHT_VISION)) {
                        if (hasPotionBase(player, "night_vision")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_NIGHT_VISION,
                                "§e¡Poción de Visión Nocturna! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_NIGHT_VISION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_WATER_BREATHING)) {
                        if (hasPotionBase(player, "water_breathing")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_WATER_BREATHING,
                                "§e¡Poción de Respiración Acuática! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_WATER_BREATHING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_INVISIBILITY)) {
                        if (hasPotionBase(player, "invisibility")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_INVISIBILITY,
                                "§e¡Poción de Invisibilidad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_INVISIBILITY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_SLOW_FALLING)) {
                        if (hasPotionBase(player, "slow_falling")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_SLOW_FALLING,
                                "§e¡Poción de Caída Lenta! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_SLOW_FALLING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_POTION_TURTLE_MASTER)) {
                        if (hasPotionBase(player, "turtle_master")) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_POTION_TURTLE_MASTER,
                                "§e¡Poción del Maestro Tortuga! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_POTION_TURTLE_MASTER);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SHARPNESS)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.SHARPNESS)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_SHARPNESS,
                                "§e¡Encantamiento: Filo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SHARPNESS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SMITE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.SMITE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_SMITE,
                                "§e¡Encantamiento: Aspecto Rabioso! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SMITE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_BANE_ARTHROPODS)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.BANE_OF_ARTHROPODS)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_BANE_ARTHROPODS,
                                "§e¡Encantamiento: Perjuicio! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_BANE_ARTHROPODS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FIRE_ASPECT)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.FIRE_ASPECT)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_FIRE_ASPECT,
                                "§e¡Encantamiento: Aspecto de Fuego! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FIRE_ASPECT);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_KNOCKBACK)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.KNOCKBACK)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_KNOCKBACK,
                                "§e¡Encantamiento: Retroceso! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_KNOCKBACK);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LOOTING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.LOOTING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_LOOTING,
                                "§e¡Encantamiento: Botín! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LOOTING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SWEEPING_EDGE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.SWEEPING_EDGE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_SWEEPING_EDGE,
                                "§e¡Encantamiento: Filo Arrasador! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SWEEPING_EDGE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_BREACH)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.BREACH)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_BREACH,
                                "§e¡Encantamiento: Brecha! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_BREACH);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_DENSITY)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.DENSITY)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_DENSITY,
                                "§e¡Encantamiento: Densidad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_DENSITY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_WIND_BURST)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.WIND_BURST)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_WIND_BURST,
                                "§e¡Encantamiento: Ráfaga de Viento! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_WIND_BURST);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LUNGE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.LUNGE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_LUNGE,
                                "§e¡Encantamiento: Embestida! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LUNGE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_POWER)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.POWER)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_POWER,
                                "§e¡Encantamiento: Poder! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_POWER);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PUNCH)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.PUNCH)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_PUNCH,
                                "§e¡Encantamiento: Empuje! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PUNCH);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FLAME)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.FLAME)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_FLAME,
                                "§e¡Encantamiento: Llama! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FLAME);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_INFINITY)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.INFINITY)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_INFINITY,
                                "§e¡Encantamiento: Infinidad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_INFINITY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_MULTISHOT)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.MULTISHOT)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_MULTISHOT,
                                "§e¡Encantamiento: Multidisparo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_MULTISHOT);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PIERCING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.PIERCING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_PIERCING,
                                "§e¡Encantamiento: Perforación! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PIERCING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_QUICK_CHARGE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.QUICK_CHARGE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_QUICK_CHARGE,
                                "§e¡Encantamiento: Carga Rápida! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_QUICK_CHARGE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_CHANNELING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.CHANNELING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_CHANNELING,
                                "§e¡Encantamiento: Canalización! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_CHANNELING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_IMPALING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.IMPALING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_IMPALING,
                                "§e¡Encantamiento: Impaler! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_IMPALING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LOYALTY)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.LOYALTY)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_LOYALTY,
                                "§e¡Encantamiento: Lealtad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LOYALTY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_RIPTIDE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.RIPTIDE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_RIPTIDE,
                                "§e¡Encantamiento: Marejada! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_RIPTIDE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_EFFICIENCY)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.EFFICIENCY)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_EFFICIENCY,
                                "§e¡Encantamiento: Eficiencia! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_EFFICIENCY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FORTUNE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.FORTUNE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_FORTUNE,
                                "§e¡Encantamiento: Fortuna! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FORTUNE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SILK_TOUCH)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.SILK_TOUCH)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_SILK_TOUCH,
                                "§e¡Encantamiento: Toque de Seda! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SILK_TOUCH);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_UNBREAKING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.UNBREAKING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_UNBREAKING,
                                "§e¡Encantamiento: Reparación! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_UNBREAKING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_MENDING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.MENDING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_MENDING,
                                "§e¡Encantamiento: Mantenimiento! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_MENDING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LUCK_OF_THE_SEA)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.LUCK_OF_THE_SEA)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_LUCK_OF_THE_SEA,
                                "§e¡Encantamiento: Suerte Marina! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LUCK_OF_THE_SEA);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LURE)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.LURE)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_LURE,
                                "§e¡Encantamiento: Señuelo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_LURE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PROTECTION)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.PROTECTION)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_PROTECTION,
                                "§e¡Encantamiento: Protección! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PROTECTION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FIRE_PROTECTION)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.FIRE_PROTECTION)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_FIRE_PROTECTION,
                                "§e¡Encantamiento: Protección contra el Fuego! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FIRE_PROTECTION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_BLAST_PROTECTION)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.BLAST_PROTECTION)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_BLAST_PROTECTION,
                                "§e¡Encantamiento: Protección contra Explosiones! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_BLAST_PROTECTION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PROJECTILE_PROTECTION)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.PROJECTILE_PROTECTION)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_PROJECTILE_PROTECTION,
                                "§e¡Encantamiento: Protección contra Proyectiles! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_PROJECTILE_PROTECTION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FEATHER_FALLING)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.FEATHER_FALLING)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_FEATHER_FALLING,
                                "§e¡Encantamiento: Plumas! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FEATHER_FALLING);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_DEPTH_STRIDER)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.DEPTH_STRIDER)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_DEPTH_STRIDER,
                                "§e¡Encantamiento: Paso Ágil! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_DEPTH_STRIDER);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FROST_WALKER)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.FROST_WALKER)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_FROST_WALKER,
                                "§e¡Encantamiento: Paso Helado! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_FROST_WALKER);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SOUL_SPEED)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.SOUL_SPEED)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_SOUL_SPEED,
                                "§e¡Encantamiento: Velocidad de Alma! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SOUL_SPEED);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SWIFT_SNEAK)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.SWIFT_SNEAK)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_SWIFT_SNEAK,
                                "§e¡Encantamiento: Sigilo Veloz! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_SWIFT_SNEAK);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_AQUA_AFFINITY)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.AQUA_AFFINITY)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_AQUA_AFFINITY,
                                "§e¡Encantamiento: Afinidad Acuática! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_AQUA_AFFINITY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_RESPIRATION)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.RESPIRATION)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_RESPIRATION,
                                "§e¡Encantamiento: Respiración! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_RESPIRATION);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_ENCH_THORNS)) {
                        if (hasEnchantmentAnywhere(player, world.registryAccess(), Enchantments.THORNS)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_ENCH_THORNS,
                                "§e¡Encantamiento: Espinas! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_ENCH_THORNS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_GEAR_ARMOR_PIECE)) {
                        if (hasAnyEnchantedArmorPiece(player)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_GEAR_ARMOR_PIECE,
                                "§e¡Encantador Novato! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_GEAR_ARMOR_PIECE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_GEAR_ARMOR_FULL)) {
                        if (hasFullEnchantedArmorSet(player)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_GEAR_ARMOR_FULL,
                                "§e¡Blindaje Completo! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_GEAR_ARMOR_FULL);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_GEAR_PICKAXE)) {
                        if (hasEnchantedTool(player, ItemTags.PICKAXES)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_GEAR_PICKAXE,
                                "§e¡Pico Encantado! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_GEAR_PICKAXE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_GEAR_SWORD)) {
                        if (hasEnchantedTool(player, ItemTags.SWORDS)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_GEAR_SWORD,
                                "§e¡Espada Encantada! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_GEAR_SWORD);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MAGO_GEAR_AXE)) {
                        if (hasEnchantedTool(player, ItemTags.AXES)) {
                            completeQuest(player, world, QuestData.QUEST_MAGO_GEAR_AXE,
                                "§e¡Hacha Encantada! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MAGO_GEAR_AXE);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: La Mirada del Fin — ojo de ender
                    if (!saved.isCompleted(uuid, QuestData.QUEST_ENDER_EYE)) {
                        if (hasItem(player, Items.ENDER_EYE)) {
                            completeQuest(player, world, QuestData.QUEST_ENDER_EYE,
                                "§e¡La Mirada del Fin! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_ENDER_EYE);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: El Comienzo del Final — matar dragon (detectamos dragon egg)
                    if (!saved.isCompleted(uuid, QuestData.QUEST_BEGINNING_END) && !saved.hasKilledDragon(uuid)) {
                        if (hasItem(player, Items.DRAGON_EGG)) {
                            saved.markKilledDragon(uuid);
                            completeQuest(player, world, QuestData.QUEST_BEGINNING_END,
                                "§e¡El Comienzo del Final! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_BEGINNING_END);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: Desafiando la Oscuridad — invocar wither (detectamos nether star)
                    if (!saved.isCompleted(uuid, QuestData.QUEST_DEFYING_DARK) && !saved.hasSummonedWither(uuid)) {
                        if (hasItem(player, Items.NETHER_STAR)) {
                            saved.markSummonedWither(uuid);
                            completeQuest(player, world, QuestData.QUEST_DEFYING_DARK,
                                "§e¡Desafiando la Oscuridad! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_DEFYING_DARK);
                            syncToClient(player, saved);
                        }
                    }

                    // Quest: La Fortaleza Perdida — encontrar stronghold (detectamos con 2+ ojos de ender)
                    if (!saved.isCompleted(uuid, QuestData.QUEST_LOST_FORTRESS) && !saved.hasFoundStronghold(uuid)) {
                        if (countItem(player, Items.ENDER_EYE) >= 2) {
                            saved.markFoundStronghold(uuid);
                            completeQuest(player, world, QuestData.QUEST_LOST_FORTRESS,
                                "§e¡La Fortaleza Perdida! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_LOST_FORTRESS);
                            syncToClient(player, saved);
                        }
                    }

                    // Detección de estructuras — API oficial 26.2
                    // getStructureWithPieceAt devuelve non-empty SOLO si el jugador está dentro
                    BlockPos playerPos = player.blockPosition();
                    net.minecraft.world.level.StructureManager sm = world.structureManager();

                    if (!saved.isCompleted(uuid, QuestData.QUEST_SIGNS_OF_LIFE) && !saved.hasFoundVillage(uuid)) {
                        if (sm.getStructureWithPieceAt(playerPos, net.minecraft.tags.StructureTags.VILLAGE).isValid()) {
                            saved.markFoundVillage(uuid);
                            completeQuest(player, world, QuestData.QUEST_SIGNS_OF_LIFE,
                                "§e¡Señales de Vida! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_SIGNS_OF_LIFE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_ANCIENT_SANDS) && !saved.hasFoundDesertTemple(uuid)) {
                        if (isInsideStructure(sm, playerPos, "desert_pyramid")) {
                            saved.markFoundDesertTemple(uuid);
                            completeQuest(player, world, QuestData.QUEST_ANCIENT_SANDS,
                                "§e¡Arenas Antiguas! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_ANCIENT_SANDS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_JUNGLE_SECRETS) && !saved.hasFoundJungleTemple(uuid)) {
                        if (isInsideStructure(sm, playerPos, "jungle_pyramid")) {
                            saved.markFoundJungleTemple(uuid);
                            completeQuest(player, world, QuestData.QUEST_JUNGLE_SECRETS,
                                "§e¡La Selva Esconde Secretos! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_JUNGLE_SECRETS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_COLD_HOME) && !saved.hasFoundIgloo(uuid)) {
                        if (isInsideStructure(sm, playerPos, "igloo")) {
                            saved.markFoundIgloo(uuid);
                            completeQuest(player, world, QuestData.QUEST_COLD_HOME,
                                "§e¡Frío Hogar! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_COLD_HOME);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_ECHOES_PAST) && !saved.hasFoundShipwreck(uuid)) {
                        if (sm.getStructureWithPieceAt(playerPos, net.minecraft.tags.StructureTags.SHIPWRECK).isValid()) {
                            saved.markFoundShipwreck(uuid);
                            completeQuest(player, world, QuestData.QUEST_ECHOES_PAST,
                                "§e¡Ecos del Pasado! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_ECHOES_PAST);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_UNDER_WAVES) && !saved.hasFoundOceanRuins(uuid)) {
                        if (isInsideStructure(sm, playerPos, "ocean_ruin_cold") || isInsideStructure(sm, playerPos, "ocean_ruin_warm")) {
                            saved.markFoundOceanRuins(uuid);
                            completeQuest(player, world, QuestData.QUEST_UNDER_WAVES,
                                "§e¡Bajo las Olas! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_UNDER_WAVES);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_SEA_KINGDOM) && !saved.hasFoundMonument(uuid)) {
                        if (isInsideStructure(sm, playerPos, "ocean_monument")) {
                            saved.markFoundMonument(uuid);
                            completeQuest(player, world, QuestData.QUEST_SEA_KINGDOM,
                                "§e¡El Reino del Mar! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_SEA_KINGDOM);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_HOSTILE_TERRITORY) && !saved.hasFoundOutpost(uuid)) {
                        if (isInsideStructure(sm, playerPos, "pillager_outpost")) {
                            saved.markFoundOutpost(uuid);
                            completeQuest(player, world, QuestData.QUEST_HOSTILE_TERRITORY,
                                "§e¡Territorio Hostil! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_HOSTILE_TERRITORY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_FOREST_HOUSE) && !saved.hasFoundMansion(uuid)) {
                        if (isInsideStructure(sm, playerPos, "woodland_mansion")) {
                            saved.markFoundMansion(uuid);
                            completeQuest(player, world, QuestData.QUEST_FOREST_HOUSE,
                                "§e¡La Casa del Bosque! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_FOREST_HOUSE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_INFERNAL_FORTRESS) && !saved.hasFoundNetherFortress(uuid)) {
                        if (isInsideStructure(sm, playerPos, "fortress")) {
                            saved.markFoundNetherFortress(uuid);
                            completeQuest(player, world, QuestData.QUEST_INFERNAL_FORTRESS,
                                "§e¡Fortaleza Infernal! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_INFERNAL_FORTRESS);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_GOLD_KINGDOM) && !saved.hasFoundBastion(uuid)) {
                        if (isInsideStructure(sm, playerPos, "bastion_remnant")) {
                            saved.markFoundBastion(uuid);
                            completeQuest(player, world, QuestData.QUEST_GOLD_KINGDOM,
                                "§e¡El Reino del Oro! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_GOLD_KINGDOM);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_LOST_CITY) && !saved.hasFoundEndCity(uuid)) {
                        if (isInsideStructure(sm, playerPos, "end_city")) {
                            saved.markFoundEndCity(uuid);
                            completeQuest(player, world, QuestData.QUEST_LOST_CITY,
                                "§e¡La Ciudad Perdida! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_LOST_CITY);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_NO_NOISE) && !saved.hasFoundAncientCity(uuid)) {
                        if (isInsideStructure(sm, playerPos, "ancient_city")) {
                            saved.markFoundAncientCity(uuid);
                            completeQuest(player, world, QuestData.QUEST_NO_NOISE,
                                "§e¡No Hagas Ruido! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_NO_NOISE);
                            syncToClient(player, saved);
                        }
                    }
                    if (!saved.isCompleted(uuid, QuestData.QUEST_TRIAL_VALOR) && !saved.hasFoundTrialChamber(uuid)) {
                        if (isInsideStructure(sm, playerPos, "trial_chambers")) {
                            saved.markFoundTrialChamber(uuid);
                            completeQuest(player, world, QuestData.QUEST_TRIAL_VALOR,
                                "§e¡Pon a Prueba tu Valor! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_TRIAL_VALOR);
                            syncToClient(player, saved);
                        }
                    }
                    // BUGFIX: a esta quest (gemela de QUEST_LOST_FORTRESS) le faltaba por completo
                    // su disparador de completado — era imposible de conseguir para el jugador.
                    // Se usa un flag propio (foundStrongholdRuins) en vez de reutilizar
                    // hasFoundStronghold, porque ese otro flag ya lo marca QUEST_LOST_FORTRESS
                    // con una heurística distinta (tener 2+ ojos de Ender), no con pisar la estructura.
                    if (!saved.isCompleted(uuid, QuestData.QUEST_FORGOTTEN_FORTRESS) && !saved.hasFoundStrongholdRuins(uuid)) {
                        if (isInsideStructure(sm, playerPos, "stronghold")) {
                            saved.markFoundStrongholdRuins(uuid);
                            completeQuest(player, world, QuestData.QUEST_FORGOTTEN_FORTRESS,
                                "§e¡La Fortaleza Olvidada! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_FORGOTTEN_FORTRESS);
                            syncToClient(player, saved);
                        }
                    }

                    // ── TÉCNICO: detectar crafts vía inventario (igual que misiones Principal) ──

                    // Quest: El Primer Movimiento — craftear pistón
                    if (!saved.isCompleted(uuid, QuestData.QUEST_MOVEMENT_START) && !saved.hasCraftedPiston(uuid)) {
                        if (hasItem(player, Items.PISTON)) {
                            saved.markCraftedPiston(uuid);
                            completeQuest(player, world, QuestData.QUEST_MOVEMENT_START,
                                "§e¡El Primer Movimiento! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_MOVEMENT_START);
                            syncToClient(player, saved);
                        }
                    }
                    // Quest: Mecánica Pegajosa — craftear pistón pegajoso
                    if (!saved.isCompleted(uuid, QuestData.QUEST_STICKY_MECH) && !saved.hasCraftedStickyPiston(uuid)) {
                        if (hasItem(player, Items.STICKY_PISTON)) {
                            saved.markCraftedStickyPiston(uuid);
                            completeQuest(player, world, QuestData.QUEST_STICKY_MECH,
                                "§e¡Mecánica Pegajosa! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_STICKY_MECH);
                            syncToClient(player, saved);
                        }
                    }
                    // Quest: Señal con Paciencia — craftear repetidor
                    if (!saved.isCompleted(uuid, QuestData.QUEST_SIGNAL_PATIENCE) && !saved.hasCraftedRepeater(uuid)) {
                        if (hasItem(player, Items.REPEATER)) {
                            saved.markCraftedRepeater(uuid);
                            completeQuest(player, world, QuestData.QUEST_SIGNAL_PATIENCE,
                                "§e¡Señal con Paciencia! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_SIGNAL_PATIENCE);
                            syncToClient(player, saved);
                        }
                    }
                    // Quest: Lógica Binaria — craftear comparador
                    if (!saved.isCompleted(uuid, QuestData.QUEST_BINARY_LOGIC) && !saved.hasCraftedComparator(uuid)) {
                        if (hasItem(player, Items.COMPARATOR)) {
                            saved.markCraftedComparator(uuid);
                            completeQuest(player, world, QuestData.QUEST_BINARY_LOGIC,
                                "§e¡Lógica Binaria! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_BINARY_LOGIC);
                            syncToClient(player, saved);
                        }
                    }

                    // ── TÉCNICO: Movimiento Perpetuo — detectar pistón en movimiento cerca del jugador ──
                    // Usamos la presencia de PistonMovingBlockEntity como señal de activación
                    if (!saved.isCompleted(uuid, QuestData.QUEST_PERPETUAL_MOTION)) {
                        int activations = saved.getPistonActivations(uuid);
                        // Detectamos pistons en estado EXTENDED cerca del jugador como proxy de activación
                        int movingPistons = 0;
                        int checkR = 8;
                        for (int dx = -checkR; dx <= checkR && movingPistons < 3; dx++) {
                            for (int dy = -checkR; dy <= checkR && movingPistons < 3; dy++) {
                                for (int dz = -checkR; dz <= checkR && movingPistons < 3; dz++) {
                                    net.minecraft.world.level.block.state.BlockState bs =
                                        world.getBlockState(playerPos.offset(dx, dy, dz));
                                    if (bs.is(Blocks.MOVING_PISTON)) movingPistons++;
                                }
                            }
                        }
                        if (movingPistons > 0) {
                            saved.addPistonActivation(uuid);
                            if (saved.getPistonActivations(uuid) >= 100) {
                                completeQuest(player, world, QuestData.QUEST_PERPETUAL_MOTION,
                                    "§e¡Movimiento Perpetuo! §7— Abre el libro para reclamar");
                                saved.markCompleted(uuid, QuestData.QUEST_PERPETUAL_MOTION);
                                syncToClient(player, saved);
                            }
                        }
                    }

                    // ── TÉCNICO: Granja Automática — observer + piston + hopper en radio 16 ──
                    if (!saved.isCompleted(uuid, QuestData.QUEST_END_HARD_WORK) && !saved.hasBuiltAutoFarm(uuid)) {
                        boolean hasObs  = hasBlockNearby(world, playerPos, Blocks.OBSERVER, 16);
                        boolean hasPist = hasBlockNearby(world, playerPos, Blocks.PISTON, 16)
                                       || hasBlockNearby(world, playerPos, Blocks.STICKY_PISTON, 16);
                        boolean hasHop  = hasBlockNearby(world, playerPos, Blocks.HOPPER, 16);
                        if (hasObs && hasPist && hasHop) {
                            saved.markBuiltAutoFarm(uuid);
                            completeQuest(player, world, QuestData.QUEST_END_HARD_WORK,
                                "§e¡Fin del Trabajo Duro! §7— Abre el libro para reclamar");
                            saved.markCompleted(uuid, QuestData.QUEST_END_HARD_WORK);
                            syncToClient(player, saved);
                        }
                    }
                }
                // Se libera acá (no solo al principio de onServerTick): el dedup de
                // syncToClient solo debe cubrir ESTA pasada para ESTE jugador — si se
                // dejara acumulado, un sync legítimo por otro motivo (colocar un
                // bloque, matar un mob) momentos después de esta pasada se
                // saltearía por error hasta la próxima vez que onServerTick vuelva
                // a correr su lógica completa.
                syncedThisTick.remove(uuid);
            }
        }

        // Guardado periodico: si algo cambió (bloques rotos/colocados, quests completadas,
        // etc.) durante este intervalo, se escribe a disco UNA vez aquí, en vez de en cada
        // bloque individual. Si no hubo cambios, flushIfDirty() no toca el disco.
        saved.flushIfDirty();
    }

    /**
     * Revisa las misiones "Consigue X de &lt;mineral&gt;" comparando cuánto tiene el
     * jugador AHORA MISMO en su inventario contra el umbral de cada misión — tal
     * como dice el texto del libro ("Consigue 64 de carbón", no "mina 64 de carbón").
     * <p>
     * Antes esto se contaba con un contador que sumaba 1 por cada bloque de mena
     * roto, lo cual no coincidía con el texto en dos casos reales:
     * <ul>
     *   <li>Con Fortuna, un jugador puede obtener varios items de un solo bloque
     *       (el contador viejo solo sumaba 1, subestimando el progreso real).</li>
     *   <li>Con Toque de Seda, el bloque roto da la MENA (ej. "Mena de Carbón"),
     *       no el recurso en sí — el contador viejo igual sumaba 1 aunque el
     *       jugador nunca recibiera carbón.</li>
     * </ul>
     * Al chequear el inventario real, ambos casos quedan resueltos solos, y de
     * paso también cuenta si el jugador consiguió el recurso comerciando,
     * saqueando un cofre, etc. — cualquier forma de "conseguir" cuenta, tal
     * como dice el texto.
     */
    private void checkResourceGoals(ServerPlayer sp, ServerLevel world, QuestSavedData saved) {
        UUID uuid = sp.getUUID();
        // Solo reenviamos el estado al cliente si algo realmente cambió en este
        // chequeo (un contador subió o una quest se completó) — antes se mandaba
        // el paquete completo cada 2 segundos por cada jugador con quests
        // iniciadas, sin importar si había novedades o no.
        boolean changed = false;

        // Cada bloque de mineral se salta por completo una vez que su quest de
        // mayor umbral ya está cumplida: no tiene sentido seguir escaneando el
        // inventario ni reescribiendo el contador para un recurso que ya no
        // puede completar nada nuevo.
        if (!saved.isCompleted(uuid, QuestData.QUEST_COAL_KING)) {
            int coal = countItem(sp, Items.COAL);
            if (coal != saved.getCoalCount(uuid)) {
                saved.setCoal(uuid, coal);          // usado por la barra de progreso de "No Pasaremos Frío"
                saved.setCoalMined(uuid, coal);     // mismo valor: mantiene sincronizado el campo ya existente
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_STAY_WARM) && coal >= 10) {
                completeQuest(sp, world, QuestData.QUEST_STAY_WARM,
                    "§e¡No Pasaremos Frío! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_STAY_WARM);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_WINTER_FUEL) && coal >= 64) {
                completeQuest(sp, world, QuestData.QUEST_WINTER_FUEL,
                    "§e¡Combustible para el Invierno! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_WINTER_FUEL);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_COAL_KING) && coal >= 256) {
                completeQuest(sp, world, QuestData.QUEST_COAL_KING,
                    "§e¡Rey del Tizón! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_COAL_KING);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_IRON_WILL)) {
            int iron = countItem(sp, Items.RAW_IRON);
            if (iron != saved.getIronMined(uuid)) {
                saved.setIronMined(uuid, iron);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_METAL_AGE) && iron >= 10) {
                completeQuest(sp, world, QuestData.QUEST_METAL_AGE,
                    "§e¡Edad de los Metales! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_METAL_AGE);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_IRON_FEVER) && iron >= 64) {
                completeQuest(sp, world, QuestData.QUEST_IRON_FEVER,
                    "§e¡Fiebre del Hierro! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_IRON_FEVER);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_IRON_WILL) && iron >= 256) {
                completeQuest(sp, world, QuestData.QUEST_IRON_WILL,
                    "§e¡Voluntad de Hierro! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_IRON_WILL);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_LIBERTY_STATUE)) {
            int copper = countItem(sp, Items.RAW_COPPER);
            if (copper != saved.getCopperMined(uuid)) {
                saved.setCopperMined(uuid, copper);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_GOOD_CONDUCTORS) && copper >= 10) {
                completeQuest(sp, world, QuestData.QUEST_GOOD_CONDUCTORS,
                    "§e¡Buenos Conductores! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_GOOD_CONDUCTORS);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_LIBERTY_STATUE) && copper >= 64) {
                completeQuest(sp, world, QuestData.QUEST_LIBERTY_STATUE,
                    "§e¡Estatua de la Libertad! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_LIBERTY_STATUE);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_MIDAS_TOUCH)) {
            int gold = countItem(sp, Items.RAW_GOLD);
            if (gold != saved.getGoldMined(uuid)) {
                saved.setGoldMined(uuid, gold);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_COVETED_SHINE) && gold >= 10) {
                completeQuest(sp, world, QuestData.QUEST_COVETED_SHINE,
                    "§e¡Brillo Codiciable! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_COVETED_SHINE);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_MIDAS_TOUCH) && gold >= 64) {
                completeQuest(sp, world, QuestData.QUEST_MIDAS_TOUCH,
                    "§e¡Toque de Midas! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_MIDAS_TOUCH);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_CONTINUOUS_CURRENT)) {
            int redstone = countItem(sp, Items.REDSTONE);
            if (redstone != saved.getRedstoneMined(uuid)) {
                saved.setRedstoneMined(uuid, redstone);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_SPARKLING_DUST) && redstone >= 10) {
                completeQuest(sp, world, QuestData.QUEST_SPARKLING_DUST,
                    "§e¡Polvo Chispeante! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_SPARKLING_DUST);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_CONTINUOUS_CURRENT) && redstone >= 128) {
                completeQuest(sp, world, QuestData.QUEST_CONTINUOUS_CURRENT,
                    "§e¡Corriente Continua! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_CONTINUOUS_CURRENT);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_ULTRAMARINE)) {
            int lapis = countItem(sp, Items.LAPIS_LAZULI);
            if (lapis != saved.getLapisMined(uuid)) {
                saved.setLapisMined(uuid, lapis);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_WIZARD_DYE) && lapis >= 10) {
                completeQuest(sp, world, QuestData.QUEST_WIZARD_DYE,
                    "§e¡Tinte de Hechicero! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_WIZARD_DYE);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_ULTRAMARINE) && lapis >= 64) {
                completeQuest(sp, world, QuestData.QUEST_ULTRAMARINE,
                    "§e¡Azul Ultramar! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_ULTRAMARINE);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_UNBREAKABLE)) {
            int diamond = countItem(sp, Items.DIAMOND);
            if (diamond != saved.getDiamondMined(uuid)) {
                saved.setDiamondMined(uuid, diamond);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_FIVE_CHOSEN) && diamond >= 5) {
                completeQuest(sp, world, QuestData.QUEST_FIVE_CHOSEN,
                    "§e¡Los Cinco Elegidos! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_FIVE_CHOSEN);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_SHINE_HOARDER) && diamond >= 32) {
                completeQuest(sp, world, QuestData.QUEST_SHINE_HOARDER,
                    "§e¡Acaparador de Brillos! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_SHINE_HOARDER);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_UNBREAKABLE) && diamond >= 64) {
                completeQuest(sp, world, QuestData.QUEST_UNBREAKABLE,
                    "§e¡Inquebrantable! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_UNBREAKABLE);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_FORGED_HELL)) {
            int debris = countItem(sp, Items.ANCIENT_DEBRIS);
            if (debris != saved.getAncientDebrisMined(uuid)) {
                saved.setAncientDebrisMined(uuid, debris);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_ANCIENT_ECHOES) && debris >= 1) {
                completeQuest(sp, world, QuestData.QUEST_ANCIENT_ECHOES,
                    "§e¡Ecos de la Antigüedad! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_ANCIENT_ECHOES);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_FORGED_HELL) && debris >= 8) {
                completeQuest(sp, world, QuestData.QUEST_FORGED_HELL,
                    "§e¡Forjado en el Infierno! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_FORGED_HELL);
                changed = true;
            }
        }

        if (!saved.isCompleted(uuid, QuestData.QUEST_VILLAGER_FAVOR)) {
            int emerald = countItem(sp, Items.EMERALD);
            if (emerald != saved.getEmeraldMined(uuid)) {
                saved.setEmeraldMined(uuid, emerald);
                changed = true;
            }
            if (!saved.isCompleted(uuid, QuestData.QUEST_VILLAGER_FAVOR) && emerald >= 1) {
                completeQuest(sp, world, QuestData.QUEST_VILLAGER_FAVOR,
                    "§e¡El Favor del Aldeano! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_VILLAGER_FAVOR);
                changed = true;
            }
        }

        if (changed) {
            syncToClient(sp, saved);
        }
    }

    /**
     * GUERRERO: se llama desde el handler de AFTER_DEATH cada vez que un jugador
     * mata a un mob que está en WARRIOR_MOB_KEY. Suma 1 al contador de ese tipo de
     * mob y completa cualquier tier de "Cazador de X" que se acabe de alcanzar.
     * Mismo criterio de eficiencia que el resto del mod: si ya se completó el tier
     * más alto para este mob (o el jugador ya terminó TODO el mod), no se toca el
     * contador ni se sincroniza nada — matar ese mob de nuevo no puede completar
     * ninguna quest más.
     */
    private void onWarriorKill(ServerPlayer player, String mobKey) {
        QuestSavedData saved = QuestSavedData.get(player.level().getServer());
        UUID uuid = player.getUUID();
        if (saved.hasCompletedAllQuests(uuid)) return;

        WarriorTier[] tiers = WARRIOR_TIERS.get(mobKey);
        if (tiers != null) {
            WarriorTier topTier = tiers[tiers.length - 1];
            if (saved.isCompleted(uuid, topTier.questId())) return;
        }

        // El contador de kills se suma SIEMPRE que el mob esté en WARRIOR_MOB_KEY,
        // tenga o no tiers de Guerrero definidos — algunos mobs (ej. wither_skeleton)
        // solo están acá para que el Bestiario los pueda revelar, sin ser parte de
        // ninguna quest de "Cazador de X".
        int count = saved.addMobKill(uuid, mobKey);
        if (tiers != null) {
            for (WarriorTier tier : tiers) {
                if (!saved.isCompleted(uuid, tier.questId()) && count >= tier.threshold()) {
                    completeQuest(player, ((ServerLevel) player.level()), tier.questId(), tier.subtitle());
                    saved.markCompleted(uuid, tier.questId());
                }
            }
        }
        // El contador de kills cambió (relevante para la barra de progreso y ahora
        // también para el Bestiario) incluso si esta muerte no alcanzó a completar
        // ningún tier nuevo, así que siempre sincronizamos.
        syncToClient(player, saved);
    }

    /**
     * GUERRERO: detecta si el jugador ACABA de ganar una incursión, mirando el
     * flanco del efecto Héroe de la Aldea (vanilla se lo aplica automáticamente a
     * los jugadores cerca cuando una incursión termina en victoria). No hay forma
     * más directa de engancharse a "incursión ganada" desde Fabric API, así que se
     * infiere de este efecto — igual que el resto del mod, que ya prefiere
     * "revisar el estado actual" antes que depender de un evento que no existe.
     * <p>
     * Para el nivel exacto (Botella Ominosa I/III/V) se consulta el Raid activo en
     * la posición del jugador en el momento justo en que aparece el efecto — si por
     * lo que sea ya no está disponible, simplemente no se completan las quests de
     * nivel específico (RAID_I/III/V), pero sí las generales (RAID_FIRST/HERO/3).
     */
    private void checkRaidVictory(ServerPlayer player, QuestSavedData saved) {
        UUID uuid = player.getUUID();
        boolean hasHero = player.hasEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);
        boolean hadHero = saved.hadHeroEffect(uuid);
        if (hasHero == hadHero) return; // sin cambio de estado, nada que hacer
        saved.setHadHeroEffect(uuid, hasHero);
        if (!hasHero) return; // se le venció el efecto: no es una victoria nueva

        boolean changed = false;
        if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_FIRST)) {
            completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_FIRST,
                "§e¡Primera Incursión! §7— Abre el libro para reclamar");
            saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_FIRST);
            changed = true;
        }
        if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_HERO)) {
            completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_HERO,
                "§e¡El Héroe de la Aldea! §7— Abre el libro para reclamar");
            saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_HERO);
            changed = true;
        }

        saved.addRaidWon(uuid);
        if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_3) && saved.getRaidsWonTotal(uuid) >= 3) {
            completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_3,
                "§e¡Veterano de las Incursiones! §7— Abre el libro para reclamar");
            saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_3);
            changed = true;
        }

        net.minecraft.world.entity.raid.Raid raid = ((ServerLevel) player.level()).getRaidAt(player.blockPosition());
        if (raid != null) {
            int level = raid.getRaidOmenLevel();
            if (level == 1 && !saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_I)) {
                completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_I,
                    "§e¡Incursión de Nivel I! §7— Abre el libro para reclamar");
                saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_I);
                changed = true;
            } else if (level == 3) {
                if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_III)) {
                    completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_III,
                        "§e¡Incursión de Nivel III! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_III);
                    changed = true;
                }
                saved.addRaidWonLevel3(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_III_3) && saved.getRaidsWonLevel3(uuid) >= 3) {
                    completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_III_3,
                        "§e¡Veterano de Nivel III! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_III_3);
                    changed = true;
                }
            } else if (level == 5) {
                if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_V)) {
                    completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_V,
                        "§e¡Incursión de Nivel V! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_V);
                    changed = true;
                }
                saved.addRaidWonLevel5(uuid);
                if (!saved.isCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_V_3) && saved.getRaidsWonLevel5(uuid) >= 3) {
                    completeQuest(player, ((ServerLevel) player.level()), QuestData.QUEST_WARRIOR_RAID_V_3,
                        "§e¡Maestro de las Incursiones! §7— Abre el libro para reclamar");
                    saved.markCompleted(uuid, QuestData.QUEST_WARRIOR_RAID_V_3);
                    changed = true;
                }
            }
        }

        if (changed) syncToClient(player, saved);
    }



    /** Devuelve true si el bloque es cualquier tipo de vidrio (claro, teñido, panel) */
    /**
     * True si el jugador está dentro de la estructura con el ID de Minecraft dado.
     * Resuelve el objeto Structure desde el registro del mundo y lo pasa directamente.
     * OJO: sm.registryAccess().lookupOrThrow(...) NO devuelve un Registry<T> (eso daba
     * error de compilación) — devuelve un HolderLookup; getOrThrow(key) da un
     * Holder<Structure>, y .value() lo desenvuelve al Structure real.
     */
    private boolean isInsideStructure(net.minecraft.world.level.StructureManager sm,
                                       BlockPos pos, String structureId) {
        try {
            // Obtener el lookup de estructuras desde el StructureManager
            var registry = sm.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            // Resolver el objeto Structure por su ResourceKey
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> key =
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath("minecraft", structureId));
            net.minecraft.world.level.levelgen.structure.Structure structure = registry.getOrThrow(key).value();
            return sm.getStructureWithPieceAt(pos, structure).isValid();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isGlassBlock(net.minecraft.world.level.block.Block b) {
        if (b == Blocks.GLASS || b == Blocks.GLASS_PANE || b == Blocks.TINTED_GLASS) return true;
        // Vidrios de colores — verificar por ID (todos terminan en "_stained_glass" o "_stained_glass_pane")
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane");
    }

    /** Devuelve true si el bloque es hormigón o polvo de hormigón de cualquier color */
    private static boolean isConcreteOrPowder(net.minecraft.world.level.block.Block b) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_concrete") || path.endsWith("_concrete_powder");
    }

    /** Devuelve true si el bloque es cualquier variante de bloque tallado (chiseled) */
    private static boolean isChiseledBlock(net.minecraft.world.level.block.Block b) {
        if (b == Blocks.CHISELED_STONE_BRICKS || b == Blocks.CHISELED_DEEPSLATE
         || b == Blocks.CHISELED_NETHER_BRICKS || b == Blocks.CHISELED_QUARTZ_BLOCK
         || b == Blocks.CHISELED_SANDSTONE || b == Blocks.CHISELED_RED_SANDSTONE
         || b == Blocks.CHISELED_POLISHED_BLACKSTONE || b == Blocks.CHISELED_RESIN_BRICKS) return true;
        // Chiseled copper tiene múltiples estadios de oxidación — detectar por ID
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("chiseled_copper") || path.equals("chiseled_tuff");
    }

    private boolean checkFirstHome(ServerLevel world, BlockPos center) {
        int r = 8;
        boolean hasBed = false, hasCrafting = false, hasFurnace = false;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    var state = world.getBlockState(center.offset(x, y, z));
                    if (state.is(BlockTags.BEDS)) hasBed = true;
                    if (state.is(Blocks.CRAFTING_TABLE)) hasCrafting = true;
                    if (state.is(Blocks.FURNACE)) hasFurnace = true;
                    if (hasBed && hasCrafting && hasFurnace) return true;
                }
            }
        }
        return false;
    }

    /** Verifica si hay un bloque específico en un radio alrededor del jugador */
    private boolean hasBlockNearby(ServerLevel world, BlockPos center, net.minecraft.world.level.block.Block block, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (world.getBlockState(center.offset(x, y, z)).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void completeFirstHome(ServerPlayer player, ServerLevel world, BlockPos chestPos, QuestSavedData saved) {
        LOGGER.info("[Enhanced-vq] {} completó '¡Primera Casa!'", player.getName().getString());
        sendQuestComplete(player, world, "§e¡Primera Casa! §7— Obtén tu recompensa en el libro");
        player.sendSystemMessage(Component.literal(
            "§a[Enhanced-vq] §r¡Primera Casa completada! Abre el libro y pulsa §e§lObtener§r para reclamar tu recompensa."));
        // syncToClient es llamado por el caller DESPUÉS de markCompleted
    }

    /** Completar una quest genérica — la recompensa se reclama desde el libro */
    private void completeQuest(ServerPlayer player, ServerLevel world, String questId, String subtitle) {
        LOGGER.info("[Enhanced-vq] {} completó '{}'", player.getName().getString(), questId);
        sendQuestComplete(player, world, subtitle);
        player.sendSystemMessage(Component.literal(
            "§a[Enhanced-vq] §rQuest completada. Abre el libro y pulsa §e§lObtener§r para reclamar tu recompensa."));
        // syncToClient es llamado por el caller DESPUÉS de markCompleted

        // CORONA: recompensa por completar el libro al 100%. Esta quest que se acaba de
        // marcar como completada (arriba, en el caller) puede haber sido justo la última
        // que faltaba — por eso el chequeo va acá, que corre siempre DESPUÉS de
        // markCompleted. hasCrown() evita darla más de una vez.
        QuestSavedData saved = QuestSavedData.get(world.getServer());
        UUID uuid = player.getUUID();
        if (saved.hasCompletedAllQuests(uuid) && !saved.hasCrown(uuid)) {
            saved.markCrownGiven(uuid);
            ItemStack corona = new ItemStack(com.homequest.item.ModItems.CORONA);
            if (!player.getInventory().add(corona)) {
                player.drop(corona, false);
            }
            player.sendSystemMessage(Component.literal(
                "§6§l¡Completaste el libro al 100%! §r§6Recibís la Corona."));
        }
    }

    /** Recompensas por quest — usadas tanto en la entrega como en el libro */
    /**
     * Crea un ItemStack de `item` con `enchantment` nivel `level` YA aplicado — para las
     * recompensas "de nivel alto" de Mago (espada/pico/hacha/armadura encantada de
     * verdad, no solo materiales). Usa el sistema de Data Components (desde 1.21 los
     * encantamientos son datos registrados, no NBT plano ni un enum de Java), resuelto
     * contra el registro real del mundo.
     * OJO: registryAccess.lookupOrThrow(...) NO devuelve un Registry<T> (eso daba error
     * de compilación) — devuelve un HolderLookup, y el método para sacar el Holder desde
     * ahí es getOrThrow (no getHolderOrThrow, que es el nombre viejo). Se usa `var` en vez
     * de escribir el tipo exacto para no volver a errarle al nombre de la clase.
     */
    private static ItemStack enchantedItem(RegistryAccess registryAccess, net.minecraft.world.item.Item item,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        var registry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.getOrThrow(enchantment);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, level);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    /**
     * Pieza de armadura de cuero al azar (una de las 4). Usada como recompensa de
     * las primeras quests de "Cazador de X" — son mobs comunes/tempranos, así que
     * la recompensa es intencionalmente modesta y variada en vez de siempre la
     * misma pieza.
     */
    private static ItemStack randomLeatherArmor() {
        net.minecraft.world.item.Item[] pieces = {
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS
        };
        int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(pieces.length);
        return new ItemStack(pieces[idx]);
    }

    // ── Nombres ES de las "cabezas de trofeo" de mobs sin cabeza/cráneo vanilla ──
    // Minecraft solo trae cabeza/cráneo real para Zombie, Skeleton, Creeper, Wither
    // Skeleton, Piglin y Dragon — el resto de los mobs de Guerrero NO tiene un item
    // de cabeza propio en el juego base. En vez de inventar una textura custom (que
    // necesitaría un valor de skin base64 real, verificado, para no arriesgar un
    // ItemStack roto), la recompensa es una Cabeza de Jugador (Items.PLAYER_HEAD)
    // renombrada — sigue siendo un trofeo coleccionable válido y 100% funcional,
    // solo que con el modelo genérico de cabeza de jugador en vez del mob real. Si
    // más adelante se consigue una textura real por mob, alcanza con cambiar
    // customMobHead(...) para setear el perfil (GameProfile) correspondiente.
    private static final String HEAD_NAME_SPIDER = "Araña";
    private static final String HEAD_NAME_ENDERMAN = "Enderman";
    private static final String HEAD_NAME_WITCH = "Bruja";
    private static final String HEAD_NAME_DROWNED = "Ahogado";
    private static final String HEAD_NAME_HUSK = "Husk";
    private static final String HEAD_NAME_STRAY = "Stray";
    private static final String HEAD_NAME_PHANTOM = "Fantasma";
    private static final String HEAD_NAME_SLIME = "Slime";
    private static final String HEAD_NAME_MAGMA_CUBE = "Cubo de Magma";
    private static final String HEAD_NAME_BLAZE = "Blaze";
    private static final String HEAD_NAME_GHAST = "Ghast";
    private static final String HEAD_NAME_PIGLIN = "Piglin";
    private static final String HEAD_NAME_HOGLIN = "Hoglin";
    private static final String HEAD_NAME_PILLAGER = "Saqueador";
    private static final String HEAD_NAME_VINDICATOR = "Vindicator";
    private static final String HEAD_NAME_EVOKER = "Invocador";
    private static final String HEAD_NAME_GUARDIAN = "Guardián";
    private static final String HEAD_NAME_ELDER_GUARDIAN = "Guardián Anciano";
    private static final String HEAD_NAME_RAVAGER = "Devastador";
    private static final String HEAD_NAME_WARDEN = "Warden";
    private static final String HEAD_NAME_WITHER = "Wither";
    // ── Nombres ES para los trofeos de los mobs agregados en esta tanda ──
    private static final String HEAD_NAME_VEX = "Vex";
    private static final String HEAD_NAME_CREAKING = "Creaking";
    private static final String HEAD_NAME_ZOGLIN = "Zoglin";
    private static final String HEAD_NAME_BREEZE = "Breeze";
    private static final String HEAD_NAME_CAMEL_HUSK = "Camello Momificado";
    private static final String HEAD_NAME_ZOMBIFIED_PIGLIN = "Piglin Zombie";
    private static final String HEAD_NAME_SILVERFISH = "Lepisma";
    private static final String HEAD_NAME_BOGGED = "Bogged";
    private static final String HEAD_NAME_ZOMBIE_VILLAGER = "Aldeano Zombi";
    private static final String HEAD_NAME_ENDERMITE = "Endermite";
    private static final String HEAD_NAME_PIGLIN_BRUTE = "Piglin Brutal";
    private static final String HEAD_NAME_CAVE_SPIDER = "Araña de Cueva";
    private static final String HEAD_NAME_BABY_ZOMBIE = "Bebé Zombi";
    private static final String HEAD_NAME_ZOMBIE_HORSEMAN = "Jinete Zombie";
    private static final String HEAD_NAME_SKELETON_HORSEMAN = "Jinete Esqueleto";
    private static final String HEAD_NAME_SPIDER_JOCKEY = "Spider Jockey";

    private static ItemStack customMobHead(String mobNameEs) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.CUSTOM_NAME,
            Component.literal("Cabeza de " + mobNameEs).withStyle(s -> s.withItalic(false)));
        return head;
    }

    public static ItemStack[] getRewardsFor(String questId, RegistryAccess registryAccess) {
        return switch (questId) {
            // Misiones existentes
            case QuestData.QUEST_FIRST_HOME  -> new ItemStack[]{
                new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.BREAD, 10),
                new ItemStack(Items.FISHING_ROD)
            };
            case QuestData.QUEST_NEW_WORLD   -> new ItemStack[]{ new ItemStack(Items.TORCH, 8) };
            case QuestData.QUEST_HONEST_WORK -> new ItemStack[]{ new ItemStack(Items.OAK_PLANKS, 64) };
            case QuestData.QUEST_STAY_WARM   -> new ItemStack[]{ new ItemStack(Items.PORKCHOP, 4) };
            // Nuevas misiones Principal
            case QuestData.QUEST_NEW_BEGINNING   -> new ItemStack[]{ new ItemStack(Items.BREAD, 2) };
            case QuestData.QUEST_HANDS_ON        -> new ItemStack[]{ new ItemStack(Items.TORCH, 32) };
            case QuestData.QUEST_FIRST_INGOTS    -> new ItemStack[]{ new ItemStack(Items.FISHING_ROD) };
            case QuestData.QUEST_WELL_PROTECTED  -> new ItemStack[]{ new ItemStack(Items.CARROT, 20) };
            case QuestData.QUEST_BEST_DEFENSE    -> new ItemStack[]{ new ItemStack(Items.ARROW, 16) };
            case QuestData.QUEST_FOUND_IT        -> new ItemStack[]{ new ItemStack(Items.COAL, 32) };
            case QuestData.QUEST_READY_FOR_ALL   -> new ItemStack[]{
                new ItemStack(Items.TORCH, 32),
                new ItemStack(Items.COOKED_BEEF, 16)
            };
            case QuestData.QUEST_KNOWLEDGE       -> new ItemStack[]{ new ItemStack(Items.BOOK, 16) };
            case QuestData.QUEST_INTO_UNKNOWN    -> new ItemStack[]{ new ItemStack(Items.POTION) };
            case QuestData.QUEST_PLAYING_FIRE    -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_BETWEEN_DIMS    -> new ItemStack[]{ new ItemStack(Items.CROSSBOW) };
            case QuestData.QUEST_ENDER_EYE       -> new ItemStack[]{ new ItemStack(Items.BLAZE_POWDER, 4) };
            case QuestData.QUEST_LOST_FORTRESS   -> new ItemStack[]{ new ItemStack(Items.GOLDEN_APPLE) };
            case QuestData.QUEST_BEGINNING_END    -> new ItemStack[]{ new ItemStack(Items.FIREWORK_ROCKET, 64) };
            case QuestData.QUEST_TAKE_FLIGHT     -> new ItemStack[]{ new ItemStack(Items.FIREWORK_ROCKET, 64) };
            case QuestData.QUEST_DEFYING_DARK    -> new ItemStack[]{ new ItemStack(Items.TOTEM_OF_UNDYING) };
            case QuestData.QUEST_NEW_HOPE        -> new ItemStack[]{ new ItemStack(Items.WITHER_SKELETON_SKULL) };
            case QuestData.QUEST_STRANGE_WORLD   -> new ItemStack[]{ new ItemStack(Items.FIREWORK_ROCKET, 32) };
            // Nuevas misiones Explorador
            case QuestData.QUEST_BEYOND_HORIZON  -> new ItemStack[]{ new ItemStack(Items.FILLED_MAP) };
            case QuestData.QUEST_NO_BORDERS      -> new ItemStack[]{ new ItemStack(Items.GOLDEN_APPLE) };
            case QuestData.QUEST_ALL_SEEN        -> new ItemStack[]{
                new ItemStack(Items.TRIDENT),
                new ItemStack(Items.ENCHANTED_BOOK)
            };
            case QuestData.QUEST_SIGNS_OF_LIFE   -> new ItemStack[]{ new ItemStack(Items.POTATO, 10) };
            case QuestData.QUEST_ANCIENT_SANDS   -> new ItemStack[]{ new ItemStack(Items.TNT, 16) };
            case QuestData.QUEST_JUNGLE_SECRETS  -> new ItemStack[]{ new ItemStack(Items.ARROW, 32) };
            case QuestData.QUEST_COLD_HOME       -> new ItemStack[]{ new ItemStack(Items.COOKED_BEEF, 16) };
            case QuestData.QUEST_ECHOES_PAST     -> new ItemStack[]{ new ItemStack(Items.LEATHER, 8) };
            case QuestData.QUEST_UNDER_WAVES     -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_SEA_KINGDOM     -> new ItemStack[]{ new ItemStack(Items.PRISMARINE, 16) };
            case QuestData.QUEST_HOSTILE_TERRITORY -> new ItemStack[]{ new ItemStack(Items.ARROW, 32) };
            case QuestData.QUEST_FOREST_HOUSE    -> new ItemStack[]{ new ItemStack(Items.EMERALD, 64) };
            case QuestData.QUEST_INFERNAL_FORTRESS -> new ItemStack[]{ new ItemStack(Items.ENCHANTED_BOOK) };
            case QuestData.QUEST_GOLD_KINGDOM    -> new ItemStack[]{ new ItemStack(Items.GOLD_BLOCK, 16) };
            case QuestData.QUEST_FORGOTTEN_FORTRESS -> new ItemStack[]{ new ItemStack(Items.ENDER_EYE, 2) };
            case QuestData.QUEST_LOST_CITY       -> new ItemStack[]{ new ItemStack(Items.FIREWORK_ROCKET, 64) };
            case QuestData.QUEST_NO_NOISE        -> new ItemStack[]{ new ItemStack(Items.GOLDEN_CARROT, 16) };
            case QuestData.QUEST_TRIAL_VALOR     -> new ItemStack[]{ new ItemStack(Items.SPECTRAL_ARROW, 32) };
            // Nuevas misiones MINERO
            case QuestData.QUEST_WINTER_FUEL    -> new ItemStack[]{ new ItemStack(Items.COOKED_BEEF, 16) };
            case QuestData.QUEST_COAL_KING      -> new ItemStack[]{ new ItemStack(Items.COAL_BLOCK, 64) };
            case QuestData.QUEST_METAL_AGE      -> new ItemStack[]{ new ItemStack(Items.COAL, 32) };
            case QuestData.QUEST_IRON_FEVER     -> new ItemStack[]{ new ItemStack(Items.BREAD, 16) };
            case QuestData.QUEST_IRON_WILL      -> new ItemStack[]{ new ItemStack(Items.ENCHANTED_BOOK) };
            case QuestData.QUEST_GOOD_CONDUCTORS -> new ItemStack[]{ new ItemStack(Items.OAK_PLANKS, 32) };
            case QuestData.QUEST_LIBERTY_STATUE  -> new ItemStack[]{ new ItemStack(Items.COOKED_BEEF, 16) };
            case QuestData.QUEST_COVETED_SHINE   -> new ItemStack[]{ new ItemStack(Items.GOLD_INGOT, 10) };
            case QuestData.QUEST_MIDAS_TOUCH     -> new ItemStack[]{ new ItemStack(Items.GOLD_BLOCK, 16) };
            case QuestData.QUEST_SPARKLING_DUST  -> new ItemStack[]{ new ItemStack(Items.REDSTONE, 32) };
            case QuestData.QUEST_CONTINUOUS_CURRENT -> new ItemStack[]{ new ItemStack(Items.REPEATER, 16) };
            case QuestData.QUEST_WIZARD_DYE     -> new ItemStack[]{ new ItemStack(Items.LAPIS_LAZULI, 16) };
            case QuestData.QUEST_ULTRAMARINE    -> new ItemStack[]{ new ItemStack(Items.ENCHANTING_TABLE) };
            case QuestData.QUEST_FIVE_CHOSEN    -> new ItemStack[]{ new ItemStack(Items.DIAMOND_PICKAXE) };
            case QuestData.QUEST_SHINE_HOARDER  -> new ItemStack[]{ new ItemStack(Items.DIAMOND_BLOCK, 8) };
            case QuestData.QUEST_UNBREAKABLE    -> new ItemStack[]{ new ItemStack(Items.NETHERITE_INGOT, 4) };
            case QuestData.QUEST_ANCIENT_ECHOES -> new ItemStack[]{ new ItemStack(Items.NETHERITE_SCRAP) };
            case QuestData.QUEST_FORGED_HELL    -> new ItemStack[]{ new ItemStack(Items.NETHERITE_INGOT) };
            case QuestData.QUEST_VILLAGER_FAVOR  -> new ItemStack[]{ new ItemStack(Items.EMERALD, 5) };
            // Misiones CONSTRUCTOR
            case QuestData.QUEST_FIRM_FOUNDATIONS  -> new ItemStack[]{ new ItemStack(Items.STONE, 64) };
            case QuestData.QUEST_MASON_HANDS        -> new ItemStack[]{ new ItemStack(Items.IRON_INGOT, 32) };
            case QuestData.QUEST_GREAT_ARCHITECT    -> new ItemStack[]{
                new ItemStack(Items.DIAMOND, 5),
                new ItemStack(Items.NETHERITE_INGOT, 1)
            };
            case QuestData.QUEST_STEP_BY_STEP       -> new ItemStack[]{ new ItemStack(Items.OAK_STAIRS, 32) };
            case QuestData.QUEST_HALF_BLOCK         -> new ItemStack[]{ new ItemStack(Items.OAK_SLAB, 32) };
            case QuestData.QUEST_ESCAPE_HATCH       -> new ItemStack[]{ new ItemStack(Items.OAK_TRAPDOOR, 16) };
            case QuestData.QUEST_KNOCK_BEFORE       -> new ItemStack[]{ new ItemStack(Items.OAK_DOOR, 16) };
            case QuestData.QUEST_FRAGILE_TRANSP     -> new ItemStack[]{ new ItemStack(Items.GLASS, 32) };
            case QuestData.QUEST_NO_PASS            -> new ItemStack[]{ new ItemStack(Items.OAK_FENCE, 32) };
            case QuestData.QUEST_SAFE_NIGHTS        -> new ItemStack[]{ new ItemStack(Items.LANTERN, 16) };
            case QuestData.QUEST_MARINE_LIGHT       -> new ItemStack[]{ new ItemStack(Items.SEA_LANTERN, 16) };
            case QuestData.QUEST_DETAILS_MATTER     -> new ItemStack[]{ new ItemStack(Items.FLOWER_POT, 8) };
            case QuestData.QUEST_CHISEL_STONE       -> new ItemStack[]{ new ItemStack(Items.CHISELED_STONE_BRICKS, 32) };
            case QuestData.QUEST_PIG_HOUSE          -> new ItemStack[]{ new ItemStack(Items.BRICKS, 64) };
            case QuestData.QUEST_CLASSIC_TEMPLE     -> new ItemStack[]{ new ItemStack(Items.QUARTZ_BLOCK, 32) };
            case QuestData.QUEST_COLOR_PALETTE      -> new ItemStack[]{ new ItemStack(Items.EMERALD, 8) };
            case QuestData.QUEST_STICKY_STRUCT      -> new ItemStack[]{ new ItemStack(Items.HONEY_BLOCK, 16) };
            case QuestData.QUEST_PLAZA_MEETING      -> new ItemStack[]{ new ItemStack(Items.EMERALD, 16) };
            case QuestData.QUEST_BEACON_LIGHT       -> new ItemStack[]{
                new ItemStack(Items.NETHERITE_INGOT, 2),
                new ItemStack(Items.DIAMOND_BLOCK, 4)
            };
            // Misiones TÉCNICO
            case QuestData.QUEST_MOVEMENT_START     -> new ItemStack[]{ new ItemStack(Items.PISTON, 4) };
            case QuestData.QUEST_STICKY_MECH        -> new ItemStack[]{ new ItemStack(Items.STICKY_PISTON, 4) };
            case QuestData.QUEST_SIGNAL_PATIENCE    -> new ItemStack[]{ new ItemStack(Items.REPEATER, 8) };
            case QuestData.QUEST_BINARY_LOGIC       -> new ItemStack[]{ new ItemStack(Items.COMPARATOR, 8) };
            case QuestData.QUEST_BRUTE_FORCE        -> new ItemStack[]{ new ItemStack(Items.REDSTONE_TORCH, 32) };
            case QuestData.QUEST_BACK_FORTH         -> new ItemStack[]{ new ItemStack(Items.SLIME_BALL, 16) };
            case QuestData.QUEST_CONTROLLED_DELAY   -> new ItemStack[]{ new ItemStack(Items.REPEATER, 16) };
            case QuestData.QUEST_REDSTONE_BRAIN     -> new ItemStack[]{ new ItemStack(Items.COMPARATOR, 16) };
            case QuestData.QUEST_AUTO_LOGISTICS     -> new ItemStack[]{ new ItemStack(Items.HOPPER, 8) };
            case QuestData.QUEST_FIRE_AT_WILL       -> new ItemStack[]{ new ItemStack(Items.ARROW, 64) };
            case QuestData.QUEST_DROP_GOODS         -> new ItemStack[]{ new ItemStack(Items.CHEST, 4) };
            case QuestData.QUEST_EYES_WALLS         -> new ItemStack[]{ new ItemStack(Items.OBSERVER, 4) };
            case QuestData.QUEST_MANUAL_CTRL        -> new ItemStack[]{ new ItemStack(Items.LEVER, 16) };
            case QuestData.QUEST_DONT_PRESS         -> new ItemStack[]{ new ItemStack(Items.OAK_BUTTON, 16) };
            case QuestData.QUEST_UNDER_FEET         -> new ItemStack[]{ new ItemStack(Items.OAK_PRESSURE_PLATE, 16) };
            case QuestData.QUEST_INDUSTRIAL_SAFETY  -> new ItemStack[]{ new ItemStack(Items.IRON_TRAPDOOR, 8) };
            case QuestData.QUEST_PERPETUAL_MOTION   -> new ItemStack[]{ new ItemStack(Items.REDSTONE_BLOCK, 8) };
            case QuestData.QUEST_SMART_DIST         -> new ItemStack[]{ new ItemStack(Items.HOPPER, 16) };
            case QuestData.QUEST_TIC_TAC            -> new ItemStack[]{ new ItemStack(Items.CLOCK) };
            case QuestData.QUEST_END_HARD_WORK      -> new ItemStack[]{
                new ItemStack(Items.DIAMOND, 8),
                new ItemStack(Items.ENCHANTED_BOOK)
            };
            // ── MAGO ──
            case QuestData.QUEST_MAGO_BREWING_STAND    -> new ItemStack[]{ new ItemStack(Items.GLASS_BOTTLE, 8) };
            case QuestData.QUEST_MAGO_POTION_AWKWARD   -> new ItemStack[]{ new ItemStack(Items.NETHER_WART, 4) };
            case QuestData.QUEST_MAGO_POTION_SWIFTNESS -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_POISON    -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_WEAKNESS  -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_SLOWNESS  -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_FIRE_RESISTANCE -> new ItemStack[]{
                new ItemStack(Items.POTION, 2),
                new ItemStack(Items.MAGMA_CREAM, 2)
            };
            case QuestData.QUEST_MAGO_POTION_HEALING   -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_HARMING   -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_LEAPING   -> new ItemStack[]{ new ItemStack(Items.POTION, 2) };
            case QuestData.QUEST_MAGO_POTION_STRENGTH -> new ItemStack[]{
                new ItemStack(Items.POTION, 3),
                new ItemStack(Items.BLAZE_POWDER, 4)
            };
            case QuestData.QUEST_MAGO_POTION_REGENERATION -> new ItemStack[]{
                new ItemStack(Items.POTION, 3),
                new ItemStack(Items.GHAST_TEAR, 2)
            };
            case QuestData.QUEST_MAGO_POTION_NIGHT_VISION-> new ItemStack[]{ new ItemStack(Items.POTION, 3) };
            case QuestData.QUEST_MAGO_POTION_WATER_BREATHING-> new ItemStack[]{ new ItemStack(Items.POTION, 3) };
            case QuestData.QUEST_MAGO_POTION_INVISIBILITY -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.POTION, 3)
            };
            case QuestData.QUEST_MAGO_POTION_SLOW_FALLING -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.POTION, 3)
            };
            case QuestData.QUEST_MAGO_POTION_TURTLE_MASTER -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 4),
                new ItemStack(Items.POTION, 3)
            };
            case QuestData.QUEST_MAGO_ENCH_SHARPNESS   -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_SMITE       -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_BANE_ARTHROPODS-> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_FIRE_ASPECT -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_KNOCKBACK   -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_LOOTING     -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_SWEEPING_EDGE -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 4)
            };
            case QuestData.QUEST_MAGO_ENCH_BREACH -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_DENSITY -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_WIND_BURST -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_LUNGE -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_POWER       -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_PUNCH       -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_FLAME       -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_INFINITY    -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_MULTISHOT   -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_PIERCING    -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_QUICK_CHARGE-> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_CHANNELING -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_IMPALING -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 4)
            };
            case QuestData.QUEST_MAGO_ENCH_LOYALTY     -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_RIPTIDE -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 4)
            };
            case QuestData.QUEST_MAGO_ENCH_EFFICIENCY  -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_FORTUNE -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 4)
            };
            case QuestData.QUEST_MAGO_ENCH_SILK_TOUCH -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 4)
            };
            case QuestData.QUEST_MAGO_ENCH_UNBREAKING  -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_MENDING -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_LUCK_OF_THE_SEA-> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_LURE        -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_PROTECTION  -> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_FIRE_PROTECTION-> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_BLAST_PROTECTION-> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_PROJECTILE_PROTECTION-> new ItemStack[]{ new ItemStack(Items.BOOKSHELF, 2) };
            case QuestData.QUEST_MAGO_ENCH_FEATHER_FALLING-> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_DEPTH_STRIDER-> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_FROST_WALKER -> new ItemStack[]{
                new ItemStack(Items.ENCHANTED_BOOK),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_ENCH_SOUL_SPEED -> new ItemStack[]{
                enchantedItem(registryAccess, Items.DIAMOND_BOOTS, Enchantments.SOUL_SPEED, 3)
            };
            case QuestData.QUEST_MAGO_ENCH_SWIFT_SNEAK -> new ItemStack[]{
                enchantedItem(registryAccess, Items.DIAMOND_LEGGINGS, Enchantments.SWIFT_SNEAK, 3)
            };
            case QuestData.QUEST_MAGO_ENCH_AQUA_AFFINITY-> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_RESPIRATION -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_ENCH_THORNS      -> new ItemStack[]{ new ItemStack(Items.BOOK, 4) };
            case QuestData.QUEST_MAGO_GEAR_ARMOR_PIECE -> new ItemStack[]{
                new ItemStack(Items.LAPIS_LAZULI, 4),
                new ItemStack(Items.BOOK, 2)
            };
            case QuestData.QUEST_MAGO_GEAR_ARMOR_FULL -> new ItemStack[]{
                enchantedItem(registryAccess, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 4),
                new ItemStack(Items.LAPIS_LAZULI, 8)
            };
            case QuestData.QUEST_MAGO_GEAR_PICKAXE -> new ItemStack[]{
                enchantedItem(registryAccess, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 4)
            };
            case QuestData.QUEST_MAGO_GEAR_SWORD -> new ItemStack[]{
                enchantedItem(registryAccess, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 4)
            };
            case QuestData.QUEST_MAGO_GEAR_AXE -> new ItemStack[]{
                enchantedItem(registryAccess, Items.DIAMOND_AXE, Enchantments.EFFICIENCY, 4)
            };
            // ── GUERRERO ──
            case QuestData.QUEST_WARRIOR_ZOMBIE_1 -> new ItemStack[]{
                randomLeatherArmor()
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_5 -> new ItemStack[]{
                randomLeatherArmor()
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_10 -> new ItemStack[]{
                new ItemStack(Items.ZOMBIE_HEAD)
            };
            case QuestData.QUEST_WARRIOR_SKELETON_1 -> new ItemStack[]{
                new ItemStack(Items.BONE, 4)
            };
            case QuestData.QUEST_WARRIOR_SKELETON_5 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 8)
            };
            case QuestData.QUEST_WARRIOR_SKELETON_10 -> new ItemStack[]{
                new ItemStack(Items.SKELETON_SKULL)
            };
            case QuestData.QUEST_WARRIOR_CREEPER_1 -> new ItemStack[]{
                new ItemStack(Items.GUNPOWDER, 2)
            };
            case QuestData.QUEST_WARRIOR_CREEPER_5 -> new ItemStack[]{
                new ItemStack(Items.GUNPOWDER, 8)
            };
            case QuestData.QUEST_WARRIOR_CREEPER_10 -> new ItemStack[]{
                new ItemStack(Items.CREEPER_HEAD)
            };
            case QuestData.QUEST_WARRIOR_SPIDER_1 -> new ItemStack[]{
                new ItemStack(Items.STRING, 2)
            };
            case QuestData.QUEST_WARRIOR_SPIDER_5 -> new ItemStack[]{
                new ItemStack(Items.STRING, 8)
            };
            case QuestData.QUEST_WARRIOR_SPIDER_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_SPIDER)
            };
            case QuestData.QUEST_WARRIOR_ENDERMAN_1 -> new ItemStack[]{
                new ItemStack(Items.ENDER_PEARL)
            };
            case QuestData.QUEST_WARRIOR_ENDERMAN_5 -> new ItemStack[]{
                new ItemStack(Items.ENDER_PEARL, 2)
            };
            case QuestData.QUEST_WARRIOR_ENDERMAN_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_ENDERMAN)
            };
            case QuestData.QUEST_WARRIOR_WITCH_1 -> new ItemStack[]{
                new ItemStack(Items.GLASS_BOTTLE)
            };
            case QuestData.QUEST_WARRIOR_WITCH_5 -> new ItemStack[]{
                new ItemStack(Items.REDSTONE, 4)
            };
            case QuestData.QUEST_WARRIOR_WITCH_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_WITCH)
            };
            case QuestData.QUEST_WARRIOR_DROWNED_1 -> new ItemStack[]{
                new ItemStack(Items.COPPER_INGOT, 2)
            };
            case QuestData.QUEST_WARRIOR_DROWNED_5 -> new ItemStack[]{
                new ItemStack(Items.COPPER_INGOT, 8)
            };
            case QuestData.QUEST_WARRIOR_DROWNED_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_DROWNED)
            };
            case QuestData.QUEST_WARRIOR_HUSK_1 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 2)
            };
            case QuestData.QUEST_WARRIOR_HUSK_5 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 8)
            };
            case QuestData.QUEST_WARRIOR_HUSK_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_HUSK)
            };
            case QuestData.QUEST_WARRIOR_STRAY_1 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 4)
            };
            case QuestData.QUEST_WARRIOR_STRAY_5 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 8)
            };
            case QuestData.QUEST_WARRIOR_STRAY_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_STRAY)
            };
            case QuestData.QUEST_WARRIOR_PHANTOM_1 -> new ItemStack[]{
                new ItemStack(Items.PHANTOM_MEMBRANE)
            };
            case QuestData.QUEST_WARRIOR_PHANTOM_5 -> new ItemStack[]{
                new ItemStack(Items.PHANTOM_MEMBRANE, 2)
            };
            case QuestData.QUEST_WARRIOR_PHANTOM_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_PHANTOM)
            };
            case QuestData.QUEST_WARRIOR_SLIME_1 -> new ItemStack[]{
                new ItemStack(Items.SLIME_BALL, 2)
            };
            case QuestData.QUEST_WARRIOR_SLIME_5 -> new ItemStack[]{
                new ItemStack(Items.SLIME_BALL, 8)
            };
            case QuestData.QUEST_WARRIOR_SLIME_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_SLIME)
            };
            case QuestData.QUEST_WARRIOR_MAGMA_CUBE_1 -> new ItemStack[]{
                new ItemStack(Items.MAGMA_CREAM)
            };
            case QuestData.QUEST_WARRIOR_MAGMA_CUBE_5 -> new ItemStack[]{
                new ItemStack(Items.MAGMA_CREAM, 2)
            };
            case QuestData.QUEST_WARRIOR_MAGMA_CUBE_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_MAGMA_CUBE)
            };
            case QuestData.QUEST_WARRIOR_BLAZE_1 -> new ItemStack[]{
                new ItemStack(Items.BLAZE_ROD)
            };
            case QuestData.QUEST_WARRIOR_BLAZE_5 -> new ItemStack[]{
                new ItemStack(Items.BLAZE_ROD, 2)
            };
            case QuestData.QUEST_WARRIOR_BLAZE_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_BLAZE)
            };
            case QuestData.QUEST_WARRIOR_GHAST_1 -> new ItemStack[]{
                new ItemStack(Items.GHAST_TEAR)
            };
            case QuestData.QUEST_WARRIOR_GHAST_5 -> new ItemStack[]{
                new ItemStack(Items.GHAST_TEAR, 2)
            };
            case QuestData.QUEST_WARRIOR_GHAST_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_GHAST)
            };
            case QuestData.QUEST_WARRIOR_PIGLIN_1 -> new ItemStack[]{
                new ItemStack(Items.GOLD_NUGGET, 4)
            };
            case QuestData.QUEST_WARRIOR_PIGLIN_5 -> new ItemStack[]{
                new ItemStack(Items.GOLD_NUGGET, 8)
            };
            case QuestData.QUEST_WARRIOR_PIGLIN_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_PIGLIN)
            };
            case QuestData.QUEST_WARRIOR_HOGLIN_1 -> new ItemStack[]{
                new ItemStack(Items.PORKCHOP, 2)
            };
            case QuestData.QUEST_WARRIOR_HOGLIN_5 -> new ItemStack[]{
                new ItemStack(Items.PORKCHOP, 8)
            };
            case QuestData.QUEST_WARRIOR_HOGLIN_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_HOGLIN)
            };
            case QuestData.QUEST_WARRIOR_PILLAGER_1 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 4)
            };
            case QuestData.QUEST_WARRIOR_PILLAGER_5 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 8)
            };
            case QuestData.QUEST_WARRIOR_PILLAGER_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_PILLAGER)
            };
            case QuestData.QUEST_WARRIOR_VINDICATOR_1 -> new ItemStack[]{
                new ItemStack(Items.EMERALD)
            };
            case QuestData.QUEST_WARRIOR_VINDICATOR_5 -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 2)
            };
            case QuestData.QUEST_WARRIOR_VINDICATOR_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_VINDICATOR)
            };
            case QuestData.QUEST_WARRIOR_EVOKER_1 -> new ItemStack[]{
                new ItemStack(Items.EMERALD)
            };
            case QuestData.QUEST_WARRIOR_EVOKER_5 -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 2)
            };
            case QuestData.QUEST_WARRIOR_EVOKER_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_EVOKER)
            };
            case QuestData.QUEST_WARRIOR_GUARDIAN_1 -> new ItemStack[]{
                new ItemStack(Items.PRISMARINE_SHARD, 2)
            };
            case QuestData.QUEST_WARRIOR_GUARDIAN_5 -> new ItemStack[]{
                new ItemStack(Items.PRISMARINE_SHARD, 8)
            };
            case QuestData.QUEST_WARRIOR_GUARDIAN_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_GUARDIAN)
            };
            case QuestData.QUEST_WARRIOR_ELDER_GUARDIAN_2 -> new ItemStack[]{
                customMobHead(HEAD_NAME_ELDER_GUARDIAN)
            };
            case QuestData.QUEST_WARRIOR_RAVAGER_2 -> new ItemStack[]{
                customMobHead(HEAD_NAME_RAVAGER)
            };
            case QuestData.QUEST_WARRIOR_WARDEN_1 -> new ItemStack[]{
                customMobHead(HEAD_NAME_WARDEN)
            };
            case QuestData.QUEST_WARRIOR_WITHER_2 -> new ItemStack[]{
                customMobHead(HEAD_NAME_WITHER)
            };
            case QuestData.QUEST_WARRIOR_DRAGON_2 -> new ItemStack[]{
                new ItemStack(Items.DRAGON_HEAD)
            };
            // ── Agregados a pedido del usuario: mobs del Bestiario que faltaban ──
            case QuestData.QUEST_WARRIOR_WITHER_SKELETON_1 -> new ItemStack[]{
                new ItemStack(Items.COAL, 2)
            };
            case QuestData.QUEST_WARRIOR_WITHER_SKELETON_5 -> new ItemStack[]{
                new ItemStack(Items.COAL, 8)
            };
            case QuestData.QUEST_WARRIOR_WITHER_SKELETON_10 -> new ItemStack[]{
                // El drop real (2.5% de probabilidad) es la única fuente de este
                // ítem — el más difícil de conseguir de toda la tanda nueva.
                new ItemStack(Items.WITHER_SKELETON_SKULL)
            };
            case QuestData.QUEST_WARRIOR_VEX_1 -> new ItemStack[]{
                new ItemStack(Items.EMERALD)
            };
            case QuestData.QUEST_WARRIOR_VEX_5 -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 2)
            };
            case QuestData.QUEST_WARRIOR_VEX_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_VEX)
            };
            case QuestData.QUEST_WARRIOR_CREAKING_1 -> new ItemStack[]{
                customMobHead(HEAD_NAME_CREAKING),
                new ItemStack(Items.RESIN_BRICK, 8),
                new ItemStack(Items.RESIN_CLUMP, 4)
            };
            case QuestData.QUEST_WARRIOR_ZOGLIN_1 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 2)
            };
            case QuestData.QUEST_WARRIOR_ZOGLIN_5 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 8)
            };
            case QuestData.QUEST_WARRIOR_ZOGLIN_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_ZOGLIN)
            };
            case QuestData.QUEST_WARRIOR_BREEZE_1 -> new ItemStack[]{
                new ItemStack(Items.BREEZE_ROD)
            };
            case QuestData.QUEST_WARRIOR_BREEZE_5 -> new ItemStack[]{
                new ItemStack(Items.BREEZE_ROD, 2)
            };
            case QuestData.QUEST_WARRIOR_BREEZE_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_BREEZE),
                new ItemStack(Items.BREEZE_ROD, 2)
            };
            case QuestData.QUEST_WARRIOR_CAMEL_HUSK_1 -> new ItemStack[]{
                new ItemStack(Items.LEATHER, 2)
            };
            case QuestData.QUEST_WARRIOR_CAMEL_HUSK_3 -> new ItemStack[]{
                new ItemStack(Items.SADDLE)
            };
            case QuestData.QUEST_WARRIOR_CAMEL_HUSK_5 -> new ItemStack[]{
                customMobHead(HEAD_NAME_CAMEL_HUSK)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_1 -> new ItemStack[]{
                new ItemStack(Items.GOLD_NUGGET, 4)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_5 -> new ItemStack[]{
                new ItemStack(Items.GOLD_NUGGET, 8)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIFIED_PIGLIN_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_ZOMBIFIED_PIGLIN)
            };
            case QuestData.QUEST_WARRIOR_SILVERFISH_1 -> new ItemStack[]{
                new ItemStack(Items.IRON_NUGGET, 4)
            };
            case QuestData.QUEST_WARRIOR_SILVERFISH_5 -> new ItemStack[]{
                new ItemStack(Items.IRON_INGOT, 2)
            };
            case QuestData.QUEST_WARRIOR_SILVERFISH_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_SILVERFISH)
            };
            case QuestData.QUEST_WARRIOR_BOGGED_1 -> new ItemStack[]{
                new ItemStack(Items.BONE, 2)
            };
            case QuestData.QUEST_WARRIOR_BOGGED_5 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 8)
            };
            case QuestData.QUEST_WARRIOR_BOGGED_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_BOGGED)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_1 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 2)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_5 -> new ItemStack[]{
                new ItemStack(Items.CARROT, 8)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_VILLAGER_10 -> new ItemStack[]{
                // Temático: es justo lo que se usa para curarlos (Debilidad + esto).
                customMobHead(HEAD_NAME_ZOMBIE_VILLAGER),
                new ItemStack(Items.GOLDEN_APPLE)
            };
            case QuestData.QUEST_WARRIOR_ENDERMITE_1 -> new ItemStack[]{
                new ItemStack(Items.ENDER_PEARL)
            };
            case QuestData.QUEST_WARRIOR_ENDERMITE_5 -> new ItemStack[]{
                new ItemStack(Items.ENDER_PEARL, 2)
            };
            case QuestData.QUEST_WARRIOR_ENDERMITE_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_ENDERMITE)
            };
            case QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_1 -> new ItemStack[]{
                new ItemStack(Items.GOLD_NUGGET, 4)
            };
            case QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_5 -> new ItemStack[]{
                new ItemStack(Items.GOLD_INGOT, 2)
            };
            case QuestData.QUEST_WARRIOR_PIGLIN_BRUTE_10 -> new ItemStack[]{
                // Temático: los Piglin Brutos siempre pelean con hacha de oro.
                customMobHead(HEAD_NAME_PIGLIN_BRUTE),
                new ItemStack(Items.GOLDEN_AXE)
            };
            case QuestData.QUEST_WARRIOR_CAVE_SPIDER_1 -> new ItemStack[]{
                new ItemStack(Items.STRING, 2)
            };
            case QuestData.QUEST_WARRIOR_CAVE_SPIDER_5 -> new ItemStack[]{
                new ItemStack(Items.SPIDER_EYE, 2)
            };
            case QuestData.QUEST_WARRIOR_CAVE_SPIDER_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_CAVE_SPIDER)
            };
            case QuestData.QUEST_WARRIOR_SHULKER_1 -> new ItemStack[]{
                new ItemStack(Items.ENDER_PEARL)
            };
            case QuestData.QUEST_WARRIOR_SHULKER_5 -> new ItemStack[]{
                new ItemStack(Items.SHULKER_SHELL)
            };
            case QuestData.QUEST_WARRIOR_SHULKER_10 -> new ItemStack[]{
                // Caja de Shulker completa (normalmente hace falta craftearla con 2
                // caparazones + cofre) — el trofeo más práctico de conseguir así.
                new ItemStack(Items.SHULKER_BOX)
            };
            case QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_1 -> new ItemStack[]{
                new ItemStack(Items.FEATHER, 2)
            };
            case QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_2 -> new ItemStack[]{
                new ItemStack(Items.CHICKEN, 4)
            };
            case QuestData.QUEST_WARRIOR_CHICKEN_JOCKEY_3 -> new ItemStack[]{
                // Referencia directa al drop real (rarísimo) de este combo exacto.
                new ItemStack(Items.MUSIC_DISC_LAVA_CHICKEN)
            };
            case QuestData.QUEST_WARRIOR_BABY_ZOMBIE_1 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 2)
            };
            case QuestData.QUEST_WARRIOR_BABY_ZOMBIE_5 -> new ItemStack[]{
                new ItemStack(Items.CARROT, 8)
            };
            case QuestData.QUEST_WARRIOR_BABY_ZOMBIE_10 -> new ItemStack[]{
                customMobHead(HEAD_NAME_BABY_ZOMBIE)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_1 -> new ItemStack[]{
                new ItemStack(Items.ROTTEN_FLESH, 2)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_2 -> new ItemStack[]{
                new ItemStack(Items.IRON_NUGGET, 4)
            };
            case QuestData.QUEST_WARRIOR_ZOMBIE_HORSEMAN_3 -> new ItemStack[]{
                // La Armadura de Hierro para caballo NO se puede craftear en
                // vanilla — solo sale de cofres de botín o comercio. Trofeo raro.
                customMobHead(HEAD_NAME_ZOMBIE_HORSEMAN),
                new ItemStack(Items.IRON_HORSE_ARMOR)
            };
            case QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_1 -> new ItemStack[]{
                new ItemStack(Items.BONE, 2)
            };
            case QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_2 -> new ItemStack[]{
                new ItemStack(Items.ARROW, 8)
            };
            case QuestData.QUEST_WARRIOR_SKELETON_HORSEMAN_3 -> new ItemStack[]{
                // Temático: los 4 jinetes cargan arcos ya encantados.
                customMobHead(HEAD_NAME_SKELETON_HORSEMAN),
                enchantedItem(registryAccess, Items.BOW, Enchantments.POWER, 3)
            };
            case QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_1 -> new ItemStack[]{
                new ItemStack(Items.STRING, 2)
            };
            case QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_2 -> new ItemStack[]{
                new ItemStack(Items.BONE, 4)
            };
            case QuestData.QUEST_WARRIOR_SPIDER_JOCKEY_3 -> new ItemStack[]{
                customMobHead(HEAD_NAME_SPIDER_JOCKEY),
                new ItemStack(Items.COBWEB, 4)
            };
            case QuestData.QUEST_WARRIOR_RAID_FIRST -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 30)
            };
            case QuestData.QUEST_WARRIOR_RAID_HERO -> new ItemStack[]{
                new ItemStack(Items.OMINOUS_BOTTLE)
            };
            case QuestData.QUEST_WARRIOR_RAID_I -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 30)
            };
            case QuestData.QUEST_WARRIOR_RAID_3 -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 40)
            };
            case QuestData.QUEST_WARRIOR_RAID_III -> new ItemStack[]{
                new ItemStack(Items.OMINOUS_BOTTLE)
            };
            case QuestData.QUEST_WARRIOR_RAID_III_3 -> new ItemStack[]{
                new ItemStack(Items.EMERALD, 64)
            };
            case QuestData.QUEST_WARRIOR_RAID_V -> new ItemStack[]{
                new ItemStack(Items.TOTEM_OF_UNDYING)
            };
            case QuestData.QUEST_WARRIOR_RAID_V_3 -> new ItemStack[]{
                new ItemStack(Items.EMERALD_BLOCK),
                new ItemStack(Items.OMINOUS_BOTTLE)
            };
            default -> new ItemStack[0];
        };
    }

    // El mensaje grande centrado (título/subtítulo vanilla) se sacó: ahora la única
    // notificación de misión completada es el cartel lateral (QuestCompletedBanner,
    // disparado desde el cliente al recibir el sync). El parámetro "subtitle" ya no
    // se usa para nada visual, pero se deja en la firma para no tocar los 2 call
    // sites — total, no cuesta nada mantenerlo si más adelante hace falta.
    private void sendQuestComplete(ServerPlayer player, ServerLevel world, String subtitle) {
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
    }

    private void giveQuestChest(ServerPlayer player) {
        ItemStack chest = new ItemStack(ModItems.QUEST_CHEST);
        chest.set(DataComponents.CUSTOM_NAME,
            Component.literal("⚑ Cofre de Quest").withStyle(s -> s.withColor(0xFFAA00).withBold(true)));
        player.getInventory().add(chest);
    }

    /** Cuenta cuántos items de un tipo específico tiene el jugador en su inventario */
    private int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Verifica si el jugador tiene un item específico en su inventario */
    private boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        return countItem(player, item) > 0;
    }

    // ── Detección "Mago" (pociones + encantamientos) ──
    // Todo esto escanea el inventario completo del jugador — que en Minecraft ya
    // incluye la armadura puesta y el offhand (mismo rango que ya usa countItem más
    // arriba), así que no hace falta un caso aparte para "equipado".

    /**
     * True si el jugador tiene una Poción/Splash/Lingering cuyo ID interno contiene
     * `baseName` — por ejemplo "swiftness" matchea "swiftness", "long_swiftness" Y
     * "strong_swiftness" al mismo tiempo, que es justo lo que se pidió (no distinguir
     * nivel ni duración). Comparar por el string del ID en vez de contra una lista de
     * constantes Potions.X puntuales evita tener que acertarle a cuáles variantes
     * "fuertes"/"largas" existen para cada una.
     */
    private boolean hasPotionBase(ServerPlayer player, String baseName) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            var contents = s.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;
            var potionHolder = contents.potion();
            if (potionHolder.isEmpty()) continue;
            String path = potionHolder.get().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            if (path.contains(baseName)) return true;
        }
        return false;
    }

    /**
     * True si CUALQUIER item del inventario (herramienta, arma, armadura o libro
     * encantado) tiene `enchantment` aplicado, sin importar el nivel. Revisa tanto
     * encantamientos "puestos" (ENCHANTMENTS) como los "guardados" en un libro
     * encantado (STORED_ENCHANTMENTS).
     */
    private boolean hasEnchantmentAnywhere(ServerPlayer player, RegistryAccess registryAccess,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        var registry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.getOrThrow(enchantment);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0) return true;
            if (s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0) return true;
        }
        return false;
    }

    /** True si el jugador tiene puesta CUALQUIER pieza de armadura con algún encantamiento. */
    private boolean hasAnyEnchantedArmorPiece(ServerPlayer player) {
        for (int slot = 36; slot <= 39; slot++) {
            ItemStack s = player.getInventory().getItem(slot);
            if (s.isEmpty()) continue;
            if (hasAnyMagoEnchant(s)) return true;
        }
        return false;
    }

    /** True si las 4 piezas de armadura puestas tienen, cada una, al menos un encantamiento. */
    private boolean hasFullEnchantedArmorSet(ServerPlayer player) {
        for (int slot = 36; slot <= 39; slot++) {
            ItemStack s = player.getInventory().getItem(slot);
            if (s.isEmpty() || !hasAnyMagoEnchant(s)) return false;
        }
        return true;
    }

    /** True si tiene, en cualquier parte del inventario, una herramienta/arma con la etiqueta dada (pico/espada/hacha de cualquier material) con algún encantamiento. */
    private boolean hasEnchantedTool(ServerPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty() || !s.is(tag)) continue;
            if (hasAnyMagoEnchant(s)) return true;
        }
        return false;
    }

    /**
     * True si el ItemStack tiene algún encantamiento aplicado (sin importar cuál).
     * Se usa para los hitos de "encanta 1 pieza de armadura" / "pico" / etc., donde
     * no importa CUÁL encantamiento tenga, solo que tenga alguno. isEnchanted() es
     * el mismo método que usa el propio juego para decidir si dibujar el brillo de
     * encantado sobre el ítem, así que es un chequeo bien estable.
     */
    private boolean hasAnyMagoEnchant(ItemStack s) {
        return s.isEnchanted();
    }

    /** Verifica si el jugador tiene armadura completa de un material */
    private boolean hasFullArmor(ServerPlayer player, net.minecraft.world.item.Item helmet,
                                   net.minecraft.world.item.Item chestplate,
                                   net.minecraft.world.item.Item leggings,
                                   net.minecraft.world.item.Item boots) {
        return player.getInventory().getItem(39).getItem() == helmet &&
               player.getInventory().getItem(38).getItem() == chestplate &&
               player.getInventory().getItem(37).getItem() == leggings &&
               player.getInventory().getItem(36).getItem() == boots;
    }

    private static boolean hasQuestBook(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof com.homequest.item.QuestBookItem) return true;
        }
        return false;
    }

    /**
     * Envía el estado actual de quests del jugador a su cliente.
     * Llamar después de cualquier cambio que el libro deba reflejar.
     */
    public static void syncToClient(ServerPlayer player, QuestSavedData saved) {
        UUID uuid = player.getUUID();
        // Evita reconstruir y reenviar el mismo snapshot 2+ veces si varias quests se
        // completaron para este jugador en la misma pasada de onServerTick (ver
        // comentario en syncedThisTick). No afecta syncs desde otros lugares (login,
        // cofre, muerte de mob): el set solo se vacía al principio de cada pasada.
        if (!syncedThisTick.add(uuid)) return;
        List<String> completed = new ArrayList<>(saved.getCompletedQuests(uuid));
        List<String> claimed   = new ArrayList<>(saved.getClaimedRewards(uuid));
        boolean started  = saved.hasStartedQuests(uuid);
        boolean hasChest = saved.hasChestPlaced(uuid);
        String language = saved.getLanguage(uuid);
        boolean languageChosen = saved.hasChosenLanguage(uuid);
        int coal   = saved.getCoalCount(uuid);
        int blocks = saved.getBlocksPlaced(uuid);
        boolean visitedNether = saved.hasVisitedNether(uuid);
        boolean visitedEnd    = saved.hasVisitedEnd(uuid);
        boolean killedDragon  = saved.hasKilledDragon(uuid);
        boolean summonedWither = saved.hasSummonedWither(uuid);
        boolean foundStronghold = saved.hasFoundStronghold(uuid);
        boolean foundStrongholdRuins = saved.hasFoundStrongholdRuins(uuid);
        boolean foundVillage = saved.hasFoundVillage(uuid);
        boolean foundDesertTemple = saved.hasFoundDesertTemple(uuid);
        boolean foundJungleTemple = saved.hasFoundJungleTemple(uuid);
        boolean foundIgloo = saved.hasFoundIgloo(uuid);
        boolean foundShipwreck = saved.hasFoundShipwreck(uuid);
        boolean foundOceanRuins = saved.hasFoundOceanRuins(uuid);
        boolean foundMonument = saved.hasFoundMonument(uuid);
        boolean foundOutpost = saved.hasFoundOutpost(uuid);
        boolean foundMansion = saved.hasFoundMansion(uuid);
        boolean foundNetherFortress = saved.hasFoundNetherFortress(uuid);
        boolean foundBastion = saved.hasFoundBastion(uuid);
        boolean foundEndCity = saved.hasFoundEndCity(uuid);
        boolean foundAncientCity = saved.hasFoundAncientCity(uuid);
        boolean foundTrialChamber = saved.hasFoundTrialChamber(uuid);
        int coalMined = saved.getCoalMined(uuid);
        int ironMined = saved.getIronMined(uuid);
        int copperMined = saved.getCopperMined(uuid);
        int goldMined = saved.getGoldMined(uuid);
        int redstoneMined = saved.getRedstoneMined(uuid);
        int lapisMined = saved.getLapisMined(uuid);
        int diamondMined = saved.getDiamondMined(uuid);
        int ancientDebrisMined = saved.getAncientDebrisMined(uuid);
        int emeraldMined = saved.getEmeraldMined(uuid);
        // CONSTRUCTOR
        int stairsPlaced    = saved.getStairsPlaced(uuid);
        int slabsPlaced     = saved.getSlabsPlaced(uuid);
        int trapdoorsPlaced = saved.getTrapdoorsPlaced(uuid);
        int doorsPlaced     = saved.getDoorsPlaced(uuid);
        int glassPlaced     = saved.getGlassPlaced(uuid);
        int fencesPlaced    = saved.getFencesPlaced(uuid);
        int lanternsPlaced  = saved.getLanternsPlaced(uuid);
        int seaLanternsPlaced = saved.getSeaLanternsPlaced(uuid);
        int decorativePlaced = saved.getDecorativePlaced(uuid);
        int chiseledPlaced  = saved.getChiseledPlaced(uuid);
        int brickPlaced     = saved.getBrickPlaced(uuid);
        int quartzPlaced    = saved.getQuartzPlaced(uuid);
        int concretePlaced  = saved.getConcretePlaced(uuid);
        int honeyPlaced     = saved.getHoneyPlaced(uuid);
        boolean placedBell   = saved.hasPlacedBell(uuid);
        boolean placedBeacon = saved.hasPlacedBeacon(uuid);
        // TÉCNICO
        boolean craftedPiston       = saved.hasCraftedPiston(uuid);
        boolean craftedStickyPiston = saved.hasCraftedStickyPiston(uuid);
        boolean craftedRepeater     = saved.hasCraftedRepeater(uuid);
        boolean craftedComparator   = saved.hasCraftedComparator(uuid);
        int pistonsPlaced           = saved.getPistonsPlaced(uuid);
        int stickyPistonsPlaced     = saved.getStickyPistonsPlaced(uuid);
        int repeatersPlaced         = saved.getRepeatersPlaced(uuid);
        int comparatorsPlaced       = saved.getComparatorsPlaced(uuid);
        int hoppersPlaced           = saved.getHoppersPlaced(uuid);
        int dispensersPlaced        = saved.getDispensersPlaced(uuid);
        int droppersPlaced          = saved.getDroppersPlaced(uuid);
        int observersPlaced         = saved.getObserversPlaced(uuid);
        int leversPlaced            = saved.getLeversPlaced(uuid);
        int buttonsPlaced           = saved.getButtonsPlaced(uuid);
        int pressurePlatesPlaced    = saved.getPressurePlatesPlaced(uuid);
        int ironTrapdoorsPlaced     = saved.getIronTrapdoorsPlaced(uuid);
        int pistonActivations       = saved.getPistonActivations(uuid);
        boolean builtHopperChain    = saved.hasBuiltHopperChain(uuid);
        boolean builtRedstoneClock  = saved.hasBuiltRedstoneClock(uuid);
        boolean builtAutoFarm       = saved.hasBuiltAutoFarm(uuid);
        // BESTIARIO
        java.util.List<String> mobKillKeys = new java.util.ArrayList<>();
        java.util.List<Integer> mobKillCounts = new java.util.ArrayList<>();
        for (var e : saved.getAllMobKills(uuid).entrySet()) {
            mobKillKeys.add(e.getKey());
            mobKillCounts.add(e.getValue());
        }
        ServerPlayNetworking.send(player,
            new QuestSyncPayload(completed, claimed, started, hasChest, language, languageChosen, coal, blocks,
                visitedNether, visitedEnd, killedDragon, summonedWither, foundStronghold,
                foundStrongholdRuins,
                foundVillage, foundDesertTemple, foundJungleTemple, foundIgloo, foundShipwreck,
                foundOceanRuins, foundMonument, foundOutpost, foundMansion, foundNetherFortress,
                foundBastion, foundEndCity, foundAncientCity, foundTrialChamber,
                coalMined, ironMined, copperMined, goldMined, redstoneMined, lapisMined,
                diamondMined, ancientDebrisMined, emeraldMined,
                stairsPlaced, slabsPlaced, trapdoorsPlaced, doorsPlaced, glassPlaced,
                fencesPlaced, lanternsPlaced, seaLanternsPlaced, decorativePlaced, chiseledPlaced,
                brickPlaced, quartzPlaced, concretePlaced, honeyPlaced, placedBell, placedBeacon,
                craftedPiston, craftedStickyPiston, craftedRepeater, craftedComparator,
                pistonsPlaced, stickyPistonsPlaced, repeatersPlaced, comparatorsPlaced,
                hoppersPlaced, dispensersPlaced, droppersPlaced, observersPlaced,
                leversPlaced, buttonsPlaced, pressurePlatesPlaced, ironTrapdoorsPlaced,
                pistonActivations, builtHopperChain, builtRedstoneClock, builtAutoFarm,
                mobKillKeys, mobKillCounts, saved.hasSeenTutorial(uuid)));
    }
}
