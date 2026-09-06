package com.homequest.data;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.UUID;

/**
 * Fachada de acceso a datos de quest — cliente (RAM) y servidor (delega a QuestSavedData).
 * Las constantes de ID son la fuente de verdad para todo el mod.
 */
public class QuestData {

    // ── PRINCIPAL ──
    public static final String QUEST_FIRST_HOME         = "first_home";
    public static final String QUEST_NEW_BEGINNING      = "new_beginning";       // mesa crafteo
    public static final String QUEST_HANDS_ON           = "hands_on";            // herramientas piedra
    public static final String QUEST_FIRST_INGOTS       = "first_ingots";        // 10 hierro
    public static final String QUEST_WELL_PROTECTED     = "well_protected";      // armadura hierro
    public static final String QUEST_BEST_DEFENSE       = "best_defense";        // escudo
    public static final String QUEST_FOUND_IT           = "found_it";            // 1 diamante
    public static final String QUEST_READY_FOR_ALL      = "ready_for_all";       // pico diamante
    public static final String QUEST_KNOWLEDGE          = "knowledge";           // mesa encantamientos
    public static final String QUEST_INTO_UNKNOWN       = "into_unknown";        // entrar nether
    public static final String QUEST_PLAYING_FIRE       = "playing_fire";        // 5 blaze rods
    public static final String QUEST_BETWEEN_DIMS       = "between_dims";        // 8 ender pearls
    public static final String QUEST_ENDER_EYE          = "ender_eye";           // ojo de ender
    public static final String QUEST_LOST_FORTRESS      = "lost_fortress";       // stronghold
    public static final String QUEST_BEGINNING_END      = "beginning_end";       // matar dragon
    public static final String QUEST_TAKE_FLIGHT        = "take_flight";         // elytra
    public static final String QUEST_DEFYING_DARK       = "defying_dark";        // invocar wither
    public static final String QUEST_NEW_HOPE           = "new_hope";            // nether star

    // ── EXPLORADOR ──
    public static final String QUEST_NEW_WORLD          = "new_world";
    public static final String QUEST_BEYOND_HORIZON     = "beyond_horizon";      // 3 biomas
    public static final String QUEST_NO_BORDERS         = "no_borders";          // 10 biomas
    public static final String QUEST_ALL_SEEN           = "all_seen";            // todos biomas
    public static final String QUEST_SIGNS_OF_LIFE      = "signs_of_life";       // aldea
    public static final String QUEST_ANCIENT_SANDS      = "ancient_sands";       // templo desierto
    public static final String QUEST_JUNGLE_SECRETS     = "jungle_secrets";      // templo jungla
    public static final String QUEST_COLD_HOME          = "cold_home";           // iglú
    public static final String QUEST_ECHOES_PAST        = "echoes_past";         // naufragio
    public static final String QUEST_UNDER_WAVES        = "under_waves";         // ruinas oceánicas
    public static final String QUEST_SEA_KINGDOM        = "sea_kingdom";         // monumento
    public static final String QUEST_HOSTILE_TERRITORY  = "hostile_territory";   // outpost
    public static final String QUEST_FOREST_HOUSE       = "forest_house";        // mansión
    public static final String QUEST_INFERNAL_FORTRESS  = "infernal_fortress";   // fortaleza nether
    public static final String QUEST_GOLD_KINGDOM       = "gold_kingdom";        // bastión
    public static final String QUEST_FORGOTTEN_FORTRESS = "forgotten_fortress";  // stronghold
    public static final String QUEST_STRANGE_WORLD      = "strange_world";       // llegar al End
    public static final String QUEST_LOST_CITY          = "lost_city";           // end city
    public static final String QUEST_NO_NOISE           = "no_noise";            // ancient city
    public static final String QUEST_TRIAL_VALOR        = "trial_valor";         // trial chamber

    // ── MINERO ──
    public static final String QUEST_STAY_WARM          = "stay_warm";           // 10 carbón
    public static final String QUEST_WINTER_FUEL        = "winter_fuel";         // 64 carbón
    public static final String QUEST_COAL_KING          = "coal_king";           // 256 carbón
    public static final String QUEST_METAL_AGE          = "metal_age";           // 10 hierro
    public static final String QUEST_IRON_FEVER         = "iron_fever";          // 64 hierro
    public static final String QUEST_IRON_WILL          = "iron_will";           // 256 hierro
    public static final String QUEST_GOOD_CONDUCTORS    = "good_conductors";     // 10 cobre
    public static final String QUEST_LIBERTY_STATUE     = "liberty_statue";      // 64 cobre
    public static final String QUEST_COVETED_SHINE      = "coveted_shine";       // 10 oro
    public static final String QUEST_MIDAS_TOUCH        = "midas_touch";         // 64 oro
    public static final String QUEST_SPARKLING_DUST     = "sparkling_dust";      // 10 redstone
    public static final String QUEST_CONTINUOUS_CURRENT = "continuous_current";  // 128 redstone
    public static final String QUEST_WIZARD_DYE         = "wizard_dye";          // 10 lapislázuli
    public static final String QUEST_ULTRAMARINE        = "ultramarine";         // 64 lapislázuli
    public static final String QUEST_FIVE_CHOSEN        = "five_chosen";         // 5 diamantes
    public static final String QUEST_SHINE_HOARDER      = "shine_hoarder";       // 32 diamantes
    public static final String QUEST_UNBREAKABLE        = "unbreakable";         // 64 diamantes
    public static final String QUEST_ANCIENT_ECHOES     = "ancient_echoes";      // 1 ancient debris
    public static final String QUEST_FORGED_HELL        = "forged_hell";         // 8 ancient debris
    public static final String QUEST_VILLAGER_FAVOR     = "villager_favor";      // 1 esmeralda

    // ── CONSTRUCTOR ──
    public static final String QUEST_HONEST_WORK        = "honest_work";         // 50 bloques
    public static final String QUEST_FIRM_FOUNDATIONS   = "firm_foundations";    // 250 bloques
    public static final String QUEST_MASON_HANDS        = "mason_hands";         // 1000 bloques
    public static final String QUEST_GREAT_ARCHITECT    = "great_architect";     // 5000 bloques
    public static final String QUEST_STEP_BY_STEP       = "step_by_step";        // 10 escaleras
    public static final String QUEST_HALF_BLOCK         = "half_block";          // 10 losas
    public static final String QUEST_ESCAPE_HATCH       = "escape_hatch";        // 10 trapdoors
    public static final String QUEST_KNOCK_BEFORE       = "knock_before";        // 10 puertas
    public static final String QUEST_FRAGILE_TRANSP     = "fragile_transp";      // 20 cristales
    public static final String QUEST_NO_PASS            = "no_pass";             // 20 vallas
    public static final String QUEST_SAFE_NIGHTS        = "safe_nights";         // 10 faroles
    public static final String QUEST_MARINE_LIGHT       = "marine_light";        // 10 linternas
    public static final String QUEST_DETAILS_MATTER     = "details_matter";      // 20 decorativos
    public static final String QUEST_CHISEL_STONE       = "chisel_stone";        // 20 tallados
    public static final String QUEST_PIG_HOUSE          = "pig_house";           // 20 ladrillo
    public static final String QUEST_CLASSIC_TEMPLE     = "classic_temple";      // 20 cuarzo
    public static final String QUEST_COLOR_PALETTE      = "color_palette";       // 20 hormigón
    public static final String QUEST_STICKY_STRUCT      = "sticky_struct";       // 20 bloques miel
    public static final String QUEST_PLAZA_MEETING      = "plaza_meeting";       // 1 campana
    public static final String QUEST_BEACON_LIGHT       = "beacon_light";        // 1 beacon

    // ── TÉCNICO ──
    public static final String QUEST_MOVEMENT_START     = "movement_start";      // craftea pistón
    public static final String QUEST_STICKY_MECH        = "sticky_mech";         // pistón pegajoso
    public static final String QUEST_SIGNAL_PATIENCE    = "signal_patience";     // repetidor
    public static final String QUEST_BINARY_LOGIC       = "binary_logic";        // comparador
    public static final String QUEST_BRUTE_FORCE        = "brute_force";         // 10 pistones
    public static final String QUEST_BACK_FORTH         = "back_forth";          // 10 pegajosos
    public static final String QUEST_CONTROLLED_DELAY   = "controlled_delay";    // 20 repetidores
    public static final String QUEST_REDSTONE_BRAIN     = "redstone_brain";      // 20 comparadores
    public static final String QUEST_AUTO_LOGISTICS     = "auto_logistics";      // 20 tolvas
    public static final String QUEST_FIRE_AT_WILL       = "fire_at_will";        // 10 dispensadores
    public static final String QUEST_DROP_GOODS         = "drop_goods";          // 10 soltadores
    public static final String QUEST_EYES_WALLS         = "eyes_walls";          // 10 observadores
    public static final String QUEST_MANUAL_CTRL        = "manual_ctrl";         // 20 palancas
    public static final String QUEST_DONT_PRESS         = "dont_press";          // 20 botones
    public static final String QUEST_UNDER_FEET         = "under_feet";          // 20 placas presión
    public static final String QUEST_INDUSTRIAL_SAFETY  = "industrial_safety";   // 20 trampillas hierro
    public static final String QUEST_PERPETUAL_MOTION   = "perpetual_motion";    // 100 activaciones pistón
    public static final String QUEST_SMART_DIST         = "smart_dist";          // transportar con tolvas
    public static final String QUEST_TIC_TAC            = "tic_tac";             // reloj redstone
    public static final String QUEST_END_HARD_WORK      = "end_hard_work";       // granja automática

    // ── MAGO (pociones + encantamientos) ──
    public static final String QUEST_MAGO_BREWING_STAND        = "mago_brewing_stand"; // mesa pociones
    public static final String QUEST_MAGO_POTION_AWKWARD       = "mago_potion_awkward";
    public static final String QUEST_MAGO_POTION_SWIFTNESS     = "mago_potion_swiftness";
    public static final String QUEST_MAGO_POTION_POISON        = "mago_potion_poison";
    public static final String QUEST_MAGO_POTION_WEAKNESS      = "mago_potion_weakness";
    public static final String QUEST_MAGO_POTION_SLOWNESS      = "mago_potion_slowness";
    public static final String QUEST_MAGO_POTION_FIRE_RESISTANCE= "mago_potion_fire_resistance";
    public static final String QUEST_MAGO_POTION_HEALING       = "mago_potion_healing";
    public static final String QUEST_MAGO_POTION_HARMING       = "mago_potion_harming";
    public static final String QUEST_MAGO_POTION_LEAPING       = "mago_potion_leaping";
    public static final String QUEST_MAGO_POTION_STRENGTH      = "mago_potion_strength";
    public static final String QUEST_MAGO_POTION_REGENERATION  = "mago_potion_regeneration";
    public static final String QUEST_MAGO_POTION_NIGHT_VISION  = "mago_potion_night_vision";
    public static final String QUEST_MAGO_POTION_WATER_BREATHING= "mago_potion_water_breathing";
    public static final String QUEST_MAGO_POTION_INVISIBILITY  = "mago_potion_invisibility";
    public static final String QUEST_MAGO_POTION_SLOW_FALLING  = "mago_potion_slow_falling";
    public static final String QUEST_MAGO_POTION_TURTLE_MASTER = "mago_potion_turtle_master";
    public static final String QUEST_MAGO_ENCH_SHARPNESS       = "mago_ench_sharpness"; // tier B
    public static final String QUEST_MAGO_ENCH_SMITE           = "mago_ench_smite"; // tier B
    public static final String QUEST_MAGO_ENCH_BANE_ARTHROPODS = "mago_ench_bane_arthropods"; // tier B
    public static final String QUEST_MAGO_ENCH_FIRE_ASPECT     = "mago_ench_fire_aspect"; // tier A
    public static final String QUEST_MAGO_ENCH_KNOCKBACK       = "mago_ench_knockback"; // tier A
    public static final String QUEST_MAGO_ENCH_LOOTING         = "mago_ench_looting"; // tier B
    public static final String QUEST_MAGO_ENCH_SWEEPING_EDGE   = "mago_ench_sweeping_edge"; // tier C
    public static final String QUEST_MAGO_ENCH_BREACH          = "mago_ench_breach"; // tier E
    public static final String QUEST_MAGO_ENCH_DENSITY         = "mago_ench_density"; // tier E
    public static final String QUEST_MAGO_ENCH_WIND_BURST      = "mago_ench_wind_burst"; // tier E
    public static final String QUEST_MAGO_ENCH_LUNGE           = "mago_ench_lunge"; // tier E
    public static final String QUEST_MAGO_ENCH_POWER           = "mago_ench_power"; // tier B
    public static final String QUEST_MAGO_ENCH_PUNCH           = "mago_ench_punch"; // tier A
    public static final String QUEST_MAGO_ENCH_FLAME           = "mago_ench_flame"; // tier A
    public static final String QUEST_MAGO_ENCH_INFINITY        = "mago_ench_infinity"; // tier B
    public static final String QUEST_MAGO_ENCH_MULTISHOT       = "mago_ench_multishot"; // tier B
    public static final String QUEST_MAGO_ENCH_PIERCING        = "mago_ench_piercing"; // tier A
    public static final String QUEST_MAGO_ENCH_QUICK_CHARGE    = "mago_ench_quick_charge"; // tier A
    public static final String QUEST_MAGO_ENCH_CHANNELING      = "mago_ench_channeling"; // tier D
    public static final String QUEST_MAGO_ENCH_IMPALING        = "mago_ench_impaling"; // tier C
    public static final String QUEST_MAGO_ENCH_LOYALTY         = "mago_ench_loyalty"; // tier B
    public static final String QUEST_MAGO_ENCH_RIPTIDE         = "mago_ench_riptide"; // tier C
    public static final String QUEST_MAGO_ENCH_EFFICIENCY      = "mago_ench_efficiency"; // tier B
    public static final String QUEST_MAGO_ENCH_FORTUNE         = "mago_ench_fortune"; // tier C
    public static final String QUEST_MAGO_ENCH_SILK_TOUCH      = "mago_ench_silk_touch"; // tier C
    public static final String QUEST_MAGO_ENCH_UNBREAKING      = "mago_ench_unbreaking"; // tier B
    public static final String QUEST_MAGO_ENCH_MENDING         = "mago_ench_mending"; // tier D
    public static final String QUEST_MAGO_ENCH_LUCK_OF_THE_SEA = "mago_ench_luck_of_the_sea"; // tier A
    public static final String QUEST_MAGO_ENCH_LURE            = "mago_ench_lure"; // tier A
    public static final String QUEST_MAGO_ENCH_PROTECTION      = "mago_ench_protection"; // tier B
    public static final String QUEST_MAGO_ENCH_FIRE_PROTECTION = "mago_ench_fire_protection"; // tier B
    public static final String QUEST_MAGO_ENCH_BLAST_PROTECTION= "mago_ench_blast_protection"; // tier B
    public static final String QUEST_MAGO_ENCH_PROJECTILE_PROTECTION= "mago_ench_projectile_protection"; // tier B
    public static final String QUEST_MAGO_ENCH_FEATHER_FALLING = "mago_ench_feather_falling"; // tier A
    public static final String QUEST_MAGO_ENCH_DEPTH_STRIDER   = "mago_ench_depth_strider"; // tier A
    public static final String QUEST_MAGO_ENCH_FROST_WALKER    = "mago_ench_frost_walker"; // tier D
    public static final String QUEST_MAGO_ENCH_SOUL_SPEED      = "mago_ench_soul_speed"; // tier F
    public static final String QUEST_MAGO_ENCH_SWIFT_SNEAK     = "mago_ench_swift_sneak"; // tier F
    public static final String QUEST_MAGO_ENCH_AQUA_AFFINITY   = "mago_ench_aqua_affinity"; // tier A
    public static final String QUEST_MAGO_ENCH_RESPIRATION     = "mago_ench_respiration"; // tier A
    public static final String QUEST_MAGO_ENCH_THORNS          = "mago_ench_thorns"; // tier A
    public static final String QUEST_MAGO_GEAR_ARMOR_PIECE     = "mago_gear_armor_piece";
    public static final String QUEST_MAGO_GEAR_ARMOR_FULL      = "mago_gear_armor_full";
    public static final String QUEST_MAGO_GEAR_PICKAXE         = "mago_gear_pickaxe";
    public static final String QUEST_MAGO_GEAR_SWORD           = "mago_gear_sword";
    public static final String QUEST_MAGO_GEAR_AXE             = "mago_gear_axe";

    // ── GUERRERO ── (cazador de mobs + incursiones)
    public static final String QUEST_WARRIOR_ZOMBIE_1         = "warrior_zombie_1";
    public static final String QUEST_WARRIOR_ZOMBIE_5         = "warrior_zombie_5";
    public static final String QUEST_WARRIOR_ZOMBIE_10        = "warrior_zombie_10";
    public static final String QUEST_WARRIOR_SKELETON_1       = "warrior_skeleton_1";
    public static final String QUEST_WARRIOR_SKELETON_5       = "warrior_skeleton_5";
    public static final String QUEST_WARRIOR_SKELETON_10      = "warrior_skeleton_10";
    public static final String QUEST_WARRIOR_CREEPER_1        = "warrior_creeper_1";
    public static final String QUEST_WARRIOR_CREEPER_5        = "warrior_creeper_5";
    public static final String QUEST_WARRIOR_CREEPER_10       = "warrior_creeper_10";
    public static final String QUEST_WARRIOR_SPIDER_1         = "warrior_spider_1";
    public static final String QUEST_WARRIOR_SPIDER_5         = "warrior_spider_5";
    public static final String QUEST_WARRIOR_SPIDER_10        = "warrior_spider_10";
    public static final String QUEST_WARRIOR_ENDERMAN_1       = "warrior_enderman_1";
    public static final String QUEST_WARRIOR_ENDERMAN_5       = "warrior_enderman_5";
    public static final String QUEST_WARRIOR_ENDERMAN_10      = "warrior_enderman_10";
    public static final String QUEST_WARRIOR_WITCH_1          = "warrior_witch_1";
    public static final String QUEST_WARRIOR_WITCH_5          = "warrior_witch_5";
    public static final String QUEST_WARRIOR_WITCH_10         = "warrior_witch_10";
    public static final String QUEST_WARRIOR_DROWNED_1        = "warrior_drowned_1";
    public static final String QUEST_WARRIOR_DROWNED_5        = "warrior_drowned_5";
    public static final String QUEST_WARRIOR_DROWNED_10       = "warrior_drowned_10";
    public static final String QUEST_WARRIOR_HUSK_1           = "warrior_husk_1";
    public static final String QUEST_WARRIOR_HUSK_5           = "warrior_husk_5";
    public static final String QUEST_WARRIOR_HUSK_10          = "warrior_husk_10";
    public static final String QUEST_WARRIOR_STRAY_1          = "warrior_stray_1";
    public static final String QUEST_WARRIOR_STRAY_5          = "warrior_stray_5";
    public static final String QUEST_WARRIOR_STRAY_10         = "warrior_stray_10";
    public static final String QUEST_WARRIOR_PHANTOM_1        = "warrior_phantom_1";
    public static final String QUEST_WARRIOR_PHANTOM_5        = "warrior_phantom_5";
    public static final String QUEST_WARRIOR_PHANTOM_10       = "warrior_phantom_10";
    public static final String QUEST_WARRIOR_SLIME_1          = "warrior_slime_1";
    public static final String QUEST_WARRIOR_SLIME_5          = "warrior_slime_5";
    public static final String QUEST_WARRIOR_SLIME_10         = "warrior_slime_10";
    public static final String QUEST_WARRIOR_MAGMA_CUBE_1     = "warrior_magma_cube_1";
    public static final String QUEST_WARRIOR_MAGMA_CUBE_5     = "warrior_magma_cube_5";
    public static final String QUEST_WARRIOR_MAGMA_CUBE_10    = "warrior_magma_cube_10";
    public static final String QUEST_WARRIOR_BLAZE_1          = "warrior_blaze_1";
    public static final String QUEST_WARRIOR_BLAZE_5          = "warrior_blaze_5";
    public static final String QUEST_WARRIOR_BLAZE_10         = "warrior_blaze_10";
    public static final String QUEST_WARRIOR_GHAST_1          = "warrior_ghast_1";
    public static final String QUEST_WARRIOR_GHAST_5          = "warrior_ghast_5";
    public static final String QUEST_WARRIOR_GHAST_10         = "warrior_ghast_10";
    public static final String QUEST_WARRIOR_PIGLIN_1         = "warrior_piglin_1";
    public static final String QUEST_WARRIOR_PIGLIN_5         = "warrior_piglin_5";
    public static final String QUEST_WARRIOR_PIGLIN_10        = "warrior_piglin_10";
    public static final String QUEST_WARRIOR_HOGLIN_1         = "warrior_hoglin_1";
    public static final String QUEST_WARRIOR_HOGLIN_5         = "warrior_hoglin_5";
    public static final String QUEST_WARRIOR_HOGLIN_10        = "warrior_hoglin_10";
    public static final String QUEST_WARRIOR_PILLAGER_1       = "warrior_pillager_1";
    public static final String QUEST_WARRIOR_PILLAGER_5       = "warrior_pillager_5";
    public static final String QUEST_WARRIOR_PILLAGER_10      = "warrior_pillager_10";
    public static final String QUEST_WARRIOR_VINDICATOR_1     = "warrior_vindicator_1";
    public static final String QUEST_WARRIOR_VINDICATOR_5     = "warrior_vindicator_5";
    public static final String QUEST_WARRIOR_VINDICATOR_10    = "warrior_vindicator_10";
    public static final String QUEST_WARRIOR_EVOKER_1         = "warrior_evoker_1";
    public static final String QUEST_WARRIOR_EVOKER_5         = "warrior_evoker_5";
    public static final String QUEST_WARRIOR_EVOKER_10        = "warrior_evoker_10";
    public static final String QUEST_WARRIOR_GUARDIAN_1       = "warrior_guardian_1";
    public static final String QUEST_WARRIOR_GUARDIAN_5       = "warrior_guardian_5";
    public static final String QUEST_WARRIOR_GUARDIAN_10      = "warrior_guardian_10";
    public static final String QUEST_WARRIOR_ELDER_GUARDIAN_2 = "warrior_elder_guardian_2";
    public static final String QUEST_WARRIOR_RAVAGER_2        = "warrior_ravager_2";
    public static final String QUEST_WARRIOR_WARDEN_1         = "warrior_warden_1";
    public static final String QUEST_WARRIOR_WITHER_2         = "warrior_wither_2";
    public static final String QUEST_WARRIOR_DRAGON_2         = "warrior_dragon_2";
    // ── Mobs del Bestiario que faltaban en Guerrero (agregados a pedido del
    // usuario): ya se contaban para revelar el Bestiario (WARRIOR_MOB_KEY) pero no
    // tenían quest/recompensa propia. Mismo patrón 1/5/10 que el resto, salvo los
    // realmente raros (jockeys, jinetes, camello momificado), que usan 1/2/3 o 1/3/5
    // para que sean alcanzables, y el Creaking (boss del bestiario) que usa un único
    // tier como los demás jefes.
    public static final String QUEST_WARRIOR_WITHER_SKELETON_1  = "warrior_wither_skeleton_1";
    public static final String QUEST_WARRIOR_WITHER_SKELETON_5  = "warrior_wither_skeleton_5";
    public static final String QUEST_WARRIOR_WITHER_SKELETON_10 = "warrior_wither_skeleton_10";
    public static final String QUEST_WARRIOR_VEX_1              = "warrior_vex_1";
    public static final String QUEST_WARRIOR_VEX_5              = "warrior_vex_5";
    public static final String QUEST_WARRIOR_VEX_10             = "warrior_vex_10";
    public static final String QUEST_WARRIOR_CREAKING_1         = "warrior_creaking_1";
    public static final String QUEST_WARRIOR_ZOGLIN_1           = "warrior_zoglin_1";
    public static final String QUEST_WARRIOR_ZOGLIN_5           = "warrior_zoglin_5";
    public static final String QUEST_WARRIOR_ZOGLIN_10          = "warrior_zoglin_10";
    public static final String QUEST_WARRIOR_BREEZE_1           = "warrior_breeze_1";
    public static final String QUEST_WARRIOR_BREEZE_5           = "warrior_breeze_5";
    public static final String QUEST_WARRIOR_BREEZE_10          = "warrior_breeze_10";
    public static final String QUEST_WARRIOR_CAMEL_HUSK_1       = "warrior_camel_husk_1";
    public static final String QUEST_WARRIOR_CAMEL_HUSK_3       = "warrior_camel_husk_3";
    public static final String QUEST_WARRIOR_CAMEL_HUSK_5       = "warrior_camel_husk_5";
    public static final String QUEST_WARRIOR_ZOMBIFIED_PIGLIN_1  = "warrior_zombified_piglin_1";
    public static final String QUEST_WARRIOR_ZOMBIFIED_PIGLIN_5  = "warrior_zombified_piglin_5";
    public static final String QUEST_WARRIOR_ZOMBIFIED_PIGLIN_10 = "warrior_zombified_piglin_10";
    public static final String QUEST_WARRIOR_SILVERFISH_1       = "warrior_silverfish_1";
    public static final String QUEST_WARRIOR_SILVERFISH_5       = "warrior_silverfish_5";
    public static final String QUEST_WARRIOR_SILVERFISH_10      = "warrior_silverfish_10";
    public static final String QUEST_WARRIOR_BOGGED_1           = "warrior_bogged_1";
    public static final String QUEST_WARRIOR_BOGGED_5           = "warrior_bogged_5";
    public static final String QUEST_WARRIOR_BOGGED_10          = "warrior_bogged_10";
    public static final String QUEST_WARRIOR_ZOMBIE_VILLAGER_1  = "warrior_zombie_villager_1";
    public static final String QUEST_WARRIOR_ZOMBIE_VILLAGER_5  = "warrior_zombie_villager_5";
    public static final String QUEST_WARRIOR_ZOMBIE_VILLAGER_10 = "warrior_zombie_villager_10";
    public static final String QUEST_WARRIOR_ENDERMITE_1        = "warrior_endermite_1";
    public static final String QUEST_WARRIOR_ENDERMITE_5        = "warrior_endermite_5";
    public static final String QUEST_WARRIOR_ENDERMITE_10       = "warrior_endermite_10";
    public static final String QUEST_WARRIOR_PIGLIN_BRUTE_1     = "warrior_piglin_brute_1";
    public static final String QUEST_WARRIOR_PIGLIN_BRUTE_5     = "warrior_piglin_brute_5";
    public static final String QUEST_WARRIOR_PIGLIN_BRUTE_10    = "warrior_piglin_brute_10";
    public static final String QUEST_WARRIOR_CAVE_SPIDER_1      = "warrior_cave_spider_1";
    public static final String QUEST_WARRIOR_CAVE_SPIDER_5      = "warrior_cave_spider_5";
    public static final String QUEST_WARRIOR_CAVE_SPIDER_10     = "warrior_cave_spider_10";
    public static final String QUEST_WARRIOR_SHULKER_1          = "warrior_shulker_1";
    public static final String QUEST_WARRIOR_SHULKER_5          = "warrior_shulker_5";
    public static final String QUEST_WARRIOR_SHULKER_10         = "warrior_shulker_10";
    public static final String QUEST_WARRIOR_CHICKEN_JOCKEY_1   = "warrior_chicken_jockey_1";
    public static final String QUEST_WARRIOR_CHICKEN_JOCKEY_2   = "warrior_chicken_jockey_2";
    public static final String QUEST_WARRIOR_CHICKEN_JOCKEY_3   = "warrior_chicken_jockey_3";
    public static final String QUEST_WARRIOR_BABY_ZOMBIE_1      = "warrior_baby_zombie_1";
    public static final String QUEST_WARRIOR_BABY_ZOMBIE_5      = "warrior_baby_zombie_5";
    public static final String QUEST_WARRIOR_BABY_ZOMBIE_10     = "warrior_baby_zombie_10";
    public static final String QUEST_WARRIOR_ZOMBIE_HORSEMAN_1  = "warrior_zombie_horseman_1";
    public static final String QUEST_WARRIOR_ZOMBIE_HORSEMAN_2  = "warrior_zombie_horseman_2";
    public static final String QUEST_WARRIOR_ZOMBIE_HORSEMAN_3  = "warrior_zombie_horseman_3";
    public static final String QUEST_WARRIOR_SKELETON_HORSEMAN_1 = "warrior_skeleton_horseman_1";
    public static final String QUEST_WARRIOR_SKELETON_HORSEMAN_2 = "warrior_skeleton_horseman_2";
    public static final String QUEST_WARRIOR_SKELETON_HORSEMAN_3 = "warrior_skeleton_horseman_3";
    public static final String QUEST_WARRIOR_SPIDER_JOCKEY_1    = "warrior_spider_jockey_1";
    public static final String QUEST_WARRIOR_SPIDER_JOCKEY_2    = "warrior_spider_jockey_2";
    public static final String QUEST_WARRIOR_SPIDER_JOCKEY_3    = "warrior_spider_jockey_3";
    public static final String QUEST_WARRIOR_RAID_FIRST       = "warrior_raid_first";
    public static final String QUEST_WARRIOR_RAID_HERO        = "warrior_raid_hero";
    public static final String QUEST_WARRIOR_RAID_I           = "warrior_raid_i";
    public static final String QUEST_WARRIOR_RAID_3           = "warrior_raid_3";
    public static final String QUEST_WARRIOR_RAID_III         = "warrior_raid_iii";
    public static final String QUEST_WARRIOR_RAID_III_3       = "warrior_raid_iii_3";
    public static final String QUEST_WARRIOR_RAID_V           = "warrior_raid_v";
    public static final String QUEST_WARRIOR_RAID_V_3         = "warrior_raid_v_3";

    // ── Catálogo total de IDs de quest (para saber cuándo un jugador terminó TODO) ──
    // Se arma por reflexión sobre las constantes QUEST_* de arriba en vez de mantener
    // una lista manual aparte, así nunca se desincroniza si se agrega/quita una quest.
    // Ver QuestSavedData#hasCompletedAllQuests: permite dejar de escanear/actualizar
    // progreso para un jugador que ya completó absolutamente todo.
    public static final Set<String> ALL_QUEST_IDS;
    static {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (java.lang.reflect.Field f : QuestData.class.getDeclaredFields()) {
            if (f.getType() == String.class && f.getName().startsWith("QUEST_")) {
                try {
                    ids.add((String) f.get(null));
                } catch (IllegalAccessException ignored) {
                    // No debería pasar: son todos public static final.
                }
            }
        }
        ALL_QUEST_IDS = java.util.Collections.unmodifiableSet(ids);
    }

    // ── Singleton cliente ──
    private static final QuestData CLIENT_INSTANCE = new QuestData();
    public static QuestData get() { return CLIENT_INSTANCE; }

    /**
     * Vacía por completo el caché del cliente. Hay que llamarlo al conectarse
     * a un servidor/mundo nuevo (ver ClientPlayConnectionEvents.JOIN en
     * HomeQuestClient), porque este caché es un singleton que vive durante
     * toda la sesión del juego — sin este reseteo, el progreso de un mundo
     * anterior (misiones completadas, quest fijada, etc.) se quedaba pegado
     * al abrir un mundo distinto, hasta que el servidor lo pisara con datos
     * nuevos (y algunos campos nunca se pisaban, solo se sumaban).
     */
    public void clearAll() {
        completed.clear();
        claimed.clear();
        pinned.clear();
        language.clear();
        languageChosen.clear();
        started.clear();
        hasChest.clear();
        chestPos.clear();
        coal.clear();
        blocks.clear();
        biomes.clear();
        visitedNether.clear();
        visitedEnd.clear();
        killedDragon.clear();
        summonedWither.clear();
        foundStronghold.clear();
        foundStrongholdRuins.clear();
        foundVillage.clear();
        foundDesertTemple.clear();
        foundJungleTemple.clear();
        foundIgloo.clear();
        foundShipwreck.clear();
        foundOceanRuins.clear();
        foundMonument.clear();
        foundOutpost.clear();
        foundMansion.clear();
        foundNetherFortress.clear();
        foundBastion.clear();
        foundEndCity.clear();
        foundAncientCity.clear();
        foundTrialChamber.clear();
        coalMined.clear();
        ironMined.clear();
        copperMined.clear();
        goldMined.clear();
        redstoneMined.clear();
        lapisMined.clear();
        diamondMined.clear();
        ancientDebrisMined.clear();
        emeraldMined.clear();
        blocksPlacedTotal.clear();
        stairsPlaced.clear();
        slabsPlaced.clear();
        trapdoorsPlaced.clear();
        doorsPlaced.clear();
        glassPlaced.clear();
        fencesPlaced.clear();
        lanternsPlaced.clear();
        seaLanternsPlaced.clear();
        decorativePlaced.clear();
        chiseledPlaced.clear();
        brickPlaced.clear();
        quartzPlaced.clear();
        concretePlaced.clear();
        honeyPlaced.clear();
        placedBell.clear();
        placedBeacon.clear();
        craftedPiston.clear();
        craftedStickyPiston.clear();
        craftedRepeater.clear();
        craftedComparator.clear();
        pistonsPlaced.clear();
        stickyPistonsPlaced.clear();
        repeatersPlaced.clear();
        comparatorsPlaced.clear();
        hoppersPlaced.clear();
        dispensersPlaced.clear();
        droppersPlaced.clear();
        observersPlaced.clear();
        leversPlaced.clear();
        buttonsPlaced.clear();
        pressurePlatesPlaced.clear();
        ironTrapdoorsPlaced.clear();
        pistonActivations.clear();
        builtHopperChain.clear();
        builtRedstoneClock.clear();
        builtAutoFarm.clear();
    }


    // Estado en memoria (cliente)
    private final java.util.Map<UUID, Set<String>> completed = new java.util.HashMap<>();
    private final java.util.Map<UUID, Set<String>> claimed   = new java.util.HashMap<>();
    private final java.util.Map<UUID, String>      pinned    = new java.util.HashMap<>();
    private final java.util.Map<UUID, String>      language       = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     languageChosen = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     tutorialSeen   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     started   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     hasChest  = new java.util.HashMap<>();
    private final java.util.Map<UUID, BlockPos>    chestPos  = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     coal      = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     blocks    = new java.util.HashMap<>();
    private final java.util.Map<UUID, Set<String>> biomes    = new java.util.HashMap<>();
    // Nuevos campos para misiones Principal
    private final java.util.Map<UUID, Boolean>     visitedNether = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     visitedEnd    = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     killedDragon  = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     summonedWither = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundStronghold = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundStrongholdRuins = new java.util.HashMap<>();
    // Nuevos campos para misiones Explorador (estructuras)
    private final java.util.Map<UUID, Boolean>     foundVillage = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundDesertTemple = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundJungleTemple = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundIgloo = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundShipwreck = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundOceanRuins = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundMonument = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundOutpost = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundMansion = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundNetherFortress = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundBastion = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundEndCity = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundAncientCity = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     foundTrialChamber = new java.util.HashMap<>();
    // Nuevos campos para misiones MINERO (conteo de bloques minados)
    private final java.util.Map<UUID, Integer>     coalMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     ironMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     copperMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     goldMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     redstoneMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     lapisMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     diamondMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     ancientDebrisMined = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     emeraldMined = new java.util.HashMap<>();
    // Nuevos campos para misiones CONSTRUCTOR (conteo de bloques colocados por tipo)
    private final java.util.Map<UUID, Integer>     blocksPlacedTotal  = new java.util.HashMap<>(); // alias de blocks, para claridad
    private final java.util.Map<UUID, Integer>     stairsPlaced  = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     slabsPlaced   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     trapdoorsPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     doorsPlaced   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     glassPlaced   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     fencesPlaced  = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     lanternsPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     seaLanternsPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     decorativePlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     chiseledPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     brickPlaced   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     quartzPlaced  = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     concretePlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     honeyPlaced   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     placedBell    = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     placedBeacon  = new java.util.HashMap<>();
    // Nuevos campos para misiones TÉCNICO (crafteo/colocación de componentes redstone)
    private final java.util.Map<UUID, Boolean>     craftedPiston       = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     craftedStickyPiston = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     craftedRepeater     = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     craftedComparator   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     pistonsPlaced       = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     stickyPistonsPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     repeatersPlaced     = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     comparatorsPlaced   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     hoppersPlaced       = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     dispensersPlaced    = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     droppersPlaced      = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     observersPlaced     = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     leversPlaced        = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     buttonsPlaced       = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     pressurePlatesPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     ironTrapdoorsPlaced = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer>     pistonActivations   = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     builtHopperChain    = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     builtRedstoneClock  = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean>     builtAutoFarm       = new java.util.HashMap<>();
    // BESTIARIO
    private final java.util.Map<UUID, java.util.Map<String, Integer>> mobKills = new java.util.HashMap<>();

    public int getMobKills(UUID p, String mobKey) {
        java.util.Map<String, Integer> m = mobKills.get(p);
        return m == null ? 0 : m.getOrDefault(mobKey, 0);
    }
    public void setMobKills(UUID p, java.util.List<String> keys, java.util.List<Integer> counts) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < keys.size() && i < counts.size(); i++) m.put(keys.get(i), counts.get(i));
        mobKills.put(p, m);
    }

    public boolean hasStartedQuests(UUID p)  { return started.getOrDefault(p, false); }
    public void markStartedQuests(UUID p)    { started.put(p, true); }

    public boolean isCompleted(UUID p, String q) { Set<String> s = completed.get(p); return s != null && s.contains(q); }
    public void setCompletedQuests(UUID p, java.util.List<String> qs) {
        completed.put(p, new java.util.HashSet<>(qs));
    }

    public boolean isClaimed(UUID p, String q)   { Set<String> s = claimed.get(p); return s != null && s.contains(q); }
    public void setClaimedRewards(UUID p, java.util.List<String> qs) {
        claimed.put(p, new java.util.HashSet<>(qs));
    }

    public boolean hasChestPlaced(UUID p)         { return hasChest.getOrDefault(p, false); }
    public void setChestPos(UUID p, BlockPos pos) { hasChest.put(p, true); chestPos.put(p, pos); }
    public void removeChestPos(UUID p)            { hasChest.put(p, false); chestPos.remove(p); }
    public BlockPos getChestPos(UUID p)           { return chestPos.get(p); }

    public String getPinnedQuest(UUID p)         { return pinned.get(p); }
    public void pinQuest(UUID p, String q)       { pinned.put(p, q); }
    public void unpinQuest(UUID p)               { pinned.remove(p); }

    public String getLanguage(UUID p)             { return language.getOrDefault(p, "es"); }
    public void setLanguage(UUID p, String lang)  { language.put(p, lang); }
    public boolean hasChosenLanguage(UUID p)      { return languageChosen.getOrDefault(p, false); }
    public void setLanguageChosen(UUID p, boolean v) { languageChosen.put(p, v); }
    public boolean hasSeenTutorial(UUID p)        { return tutorialSeen.getOrDefault(p, false); }
    public void setTutorialSeen(UUID p, boolean v) { tutorialSeen.put(p, v); }

    public int getCoalCount(UUID p)              { return coal.getOrDefault(p, 0); }
    public void addCoal(UUID p, int n)           { coal.merge(p, n, Integer::sum); }

    public int getBlocksPlaced(UUID p)           { return blocks.getOrDefault(p, 0); }
    public void addBlockPlaced(UUID p)           { blocks.merge(p, 1, Integer::sum); }

    public Set<String> getDiscoveredBiomes(UUID p)       { return biomes.getOrDefault(p, java.util.Collections.emptySet()); }
    public void addDiscoveredBiome(UUID p, String b)     { biomes.computeIfAbsent(p, k -> new java.util.HashSet<>()).add(b); }

    // Nuevos getters/setters para misiones Principal
    public boolean hasVisitedNether(UUID p)              { return visitedNether.getOrDefault(p, false); }
    public void setVisitedNether(UUID p, boolean v)      { visitedNether.put(p, v); }

    public boolean hasVisitedEnd(UUID p)                 { return visitedEnd.getOrDefault(p, false); }
    public void setVisitedEnd(UUID p, boolean v)         { visitedEnd.put(p, v); }

    public boolean hasKilledDragon(UUID p)               { return killedDragon.getOrDefault(p, false); }
    public void setKilledDragon(UUID p, boolean v)       { killedDragon.put(p, v); }

    public boolean hasSummonedWither(UUID p)             { return summonedWither.getOrDefault(p, false); }
    public void setSummonedWither(UUID p, boolean v)     { summonedWither.put(p, v); }

    public boolean hasFoundStronghold(UUID p)            { return foundStronghold.getOrDefault(p, false); }
    public void setFoundStronghold(UUID p, boolean v)    { foundStronghold.put(p, v); }
    public boolean hasFoundStrongholdRuins(UUID p)       { return foundStrongholdRuins.getOrDefault(p, false); }
    public void setFoundStrongholdRuins(UUID p, boolean v) { foundStrongholdRuins.put(p, v); }

    // -- Nuevos getters/setters para misiones Explorador (estructuras) --
    public boolean hasFoundVillage(UUID p)               { return foundVillage.getOrDefault(p, false); }
    public void setFoundVillage(UUID p, boolean v)       { foundVillage.put(p, v); }

    public boolean hasFoundDesertTemple(UUID p)          { return foundDesertTemple.getOrDefault(p, false); }
    public void setFoundDesertTemple(UUID p, boolean v)  { foundDesertTemple.put(p, v); }

    public boolean hasFoundJungleTemple(UUID p)          { return foundJungleTemple.getOrDefault(p, false); }
    public void setFoundJungleTemple(UUID p, boolean v)  { foundJungleTemple.put(p, v); }

    public boolean hasFoundIgloo(UUID p)                 { return foundIgloo.getOrDefault(p, false); }
    public void setFoundIgloo(UUID p, boolean v)         { foundIgloo.put(p, v); }

    public boolean hasFoundShipwreck(UUID p)              { return foundShipwreck.getOrDefault(p, false); }
    public void setFoundShipwreck(UUID p, boolean v)    { foundShipwreck.put(p, v); }

    public boolean hasFoundOceanRuins(UUID p)             { return foundOceanRuins.getOrDefault(p, false); }
    public void setFoundOceanRuins(UUID p, boolean v)    { foundOceanRuins.put(p, v); }

    public boolean hasFoundMonument(UUID p)               { return foundMonument.getOrDefault(p, false); }
    public void setFoundMonument(UUID p, boolean v)       { foundMonument.put(p, v); }

    public boolean hasFoundOutpost(UUID p)                { return foundOutpost.getOrDefault(p, false); }
    public void setFoundOutpost(UUID p, boolean v)      { foundOutpost.put(p, v); }

    public boolean hasFoundMansion(UUID p)                { return foundMansion.getOrDefault(p, false); }
    public void setFoundMansion(UUID p, boolean v)        { foundMansion.put(p, v); }

    public boolean hasFoundNetherFortress(UUID p)         { return foundNetherFortress.getOrDefault(p, false); }
    public void setFoundNetherFortress(UUID p, boolean v) { foundNetherFortress.put(p, v); }

    public boolean hasFoundBastion(UUID p)                { return foundBastion.getOrDefault(p, false); }
    public void setFoundBastion(UUID p, boolean v)        { foundBastion.put(p, v); }

    public boolean hasFoundEndCity(UUID p)                { return foundEndCity.getOrDefault(p, false); }
    public void setFoundEndCity(UUID p, boolean v)        { foundEndCity.put(p, v); }

    public boolean hasFoundAncientCity(UUID p)             { return foundAncientCity.getOrDefault(p, false); }
    public void setFoundAncientCity(UUID p, boolean v)     { foundAncientCity.put(p, v); }

    public boolean hasFoundTrialChamber(UUID p)           { return foundTrialChamber.getOrDefault(p, false); }
    public void setFoundTrialChamber(UUID p, boolean v)   { foundTrialChamber.put(p, v); }

    // -- Nuevos getters/setters para misiones MINERO (conteo de bloques minados) --
    public int getCoalMined(UUID p)                      { return coalMined.getOrDefault(p, 0); }
    public void addCoalMined(UUID p, int n)               { coalMined.merge(p, n, Integer::sum); }

    public int getIronMined(UUID p)                      { return ironMined.getOrDefault(p, 0); }
    public void addIronMined(UUID p, int n)               { ironMined.merge(p, n, Integer::sum); }

    public int getCopperMined(UUID p)                     { return copperMined.getOrDefault(p, 0); }
    public void addCopperMined(UUID p, int n)             { copperMined.merge(p, n, Integer::sum); }

    public int getGoldMined(UUID p)                      { return goldMined.getOrDefault(p, 0); }
    public void addGoldMined(UUID p, int n)               { goldMined.merge(p, n, Integer::sum); }

    public int getRedstoneMined(UUID p)                  { return redstoneMined.getOrDefault(p, 0); }
    public void addRedstoneMined(UUID p, int n)          { redstoneMined.merge(p, n, Integer::sum); }

    public int getLapisMined(UUID p)                     { return lapisMined.getOrDefault(p, 0); }
    public void addLapisMined(UUID p, int n)             { lapisMined.merge(p, n, Integer::sum); }

    public int getDiamondMined(UUID p)                   { return diamondMined.getOrDefault(p, 0); }
    public void addDiamondMined(UUID p, int n)            { diamondMined.merge(p, n, Integer::sum); }

    public int getAncientDebrisMined(UUID p)              { return ancientDebrisMined.getOrDefault(p, 0); }
    public void addAncientDebrisMined(UUID p, int n)      { ancientDebrisMined.merge(p, n, Integer::sum); }

    public int getEmeraldMined(UUID p)                   { return emeraldMined.getOrDefault(p, 0); }
    public void addEmeraldMined(UUID p, int n)            { emeraldMined.merge(p, n, Integer::sum); }

    // -- Getters/setters CONSTRUCTOR --
    public int getStairsPlaced(UUID p)        { return stairsPlaced.getOrDefault(p, 0); }
    public void addStairsPlaced(UUID p)       { stairsPlaced.merge(p, 1, Integer::sum); }

    public int getSlabsPlaced(UUID p)         { return slabsPlaced.getOrDefault(p, 0); }
    public void addSlabsPlaced(UUID p)        { slabsPlaced.merge(p, 1, Integer::sum); }

    public int getTrapdoorsPlaced(UUID p)     { return trapdoorsPlaced.getOrDefault(p, 0); }
    public void addTrapdoorsPlaced(UUID p)    { trapdoorsPlaced.merge(p, 1, Integer::sum); }

    public int getDoorsPlaced(UUID p)         { return doorsPlaced.getOrDefault(p, 0); }
    public void addDoorsPlaced(UUID p)        { doorsPlaced.merge(p, 1, Integer::sum); }

    public int getGlassPlaced(UUID p)         { return glassPlaced.getOrDefault(p, 0); }
    public void addGlassPlaced(UUID p)        { glassPlaced.merge(p, 1, Integer::sum); }

    public int getFencesPlaced(UUID p)        { return fencesPlaced.getOrDefault(p, 0); }
    public void addFencesPlaced(UUID p)       { fencesPlaced.merge(p, 1, Integer::sum); }

    public int getLanternsPlaced(UUID p)      { return lanternsPlaced.getOrDefault(p, 0); }
    public void addLanternsPlaced(UUID p)     { lanternsPlaced.merge(p, 1, Integer::sum); }

    public int getSeaLanternsPlaced(UUID p)   { return seaLanternsPlaced.getOrDefault(p, 0); }
    public void addSeaLanternsPlaced(UUID p)  { seaLanternsPlaced.merge(p, 1, Integer::sum); }

    public int getDecorativePlaced(UUID p)    { return decorativePlaced.getOrDefault(p, 0); }
    public void addDecorativePlaced(UUID p)   { decorativePlaced.merge(p, 1, Integer::sum); }

    public int getChiseledPlaced(UUID p)      { return chiseledPlaced.getOrDefault(p, 0); }
    public void addChiseledPlaced(UUID p)     { chiseledPlaced.merge(p, 1, Integer::sum); }

    public int getBrickPlaced(UUID p)         { return brickPlaced.getOrDefault(p, 0); }
    public void addBrickPlaced(UUID p)        { brickPlaced.merge(p, 1, Integer::sum); }

    public int getQuartzPlaced(UUID p)        { return quartzPlaced.getOrDefault(p, 0); }
    public void addQuartzPlaced(UUID p)       { quartzPlaced.merge(p, 1, Integer::sum); }

    public int getConcretePlaced(UUID p)      { return concretePlaced.getOrDefault(p, 0); }
    public void addConcretePlaced(UUID p)     { concretePlaced.merge(p, 1, Integer::sum); }

    public int getHoneyPlaced(UUID p)         { return honeyPlaced.getOrDefault(p, 0); }
    public void addHoneyPlaced(UUID p)        { honeyPlaced.merge(p, 1, Integer::sum); }

    public boolean hasPlacedBell(UUID p)      { return placedBell.getOrDefault(p, false); }
    public void setPlacedBell(UUID p, boolean v) { placedBell.put(p, v); }

    public boolean hasPlacedBeacon(UUID p)    { return placedBeacon.getOrDefault(p, false); }
    public void setPlacedBeacon(UUID p, boolean v) { placedBeacon.put(p, v); }

    // -- Getters/setters TÉCNICO --
    public boolean hasCraftedPiston(UUID p)       { return craftedPiston.getOrDefault(p, false); }
    public void setCraftedPiston(UUID p, boolean v) { craftedPiston.put(p, v); }

    public boolean hasCraftedStickyPiston(UUID p) { return craftedStickyPiston.getOrDefault(p, false); }
    public void setCraftedStickyPiston(UUID p, boolean v) { craftedStickyPiston.put(p, v); }

    public boolean hasCraftedRepeater(UUID p)     { return craftedRepeater.getOrDefault(p, false); }
    public void setCraftedRepeater(UUID p, boolean v) { craftedRepeater.put(p, v); }

    public boolean hasCraftedComparator(UUID p)   { return craftedComparator.getOrDefault(p, false); }
    public void setCraftedComparator(UUID p, boolean v) { craftedComparator.put(p, v); }

    public int getPistonsPlaced(UUID p)           { return pistonsPlaced.getOrDefault(p, 0); }
    public void addPistonPlaced(UUID p)           { pistonsPlaced.merge(p, 1, Integer::sum); }

    public int getStickyPistonsPlaced(UUID p)     { return stickyPistonsPlaced.getOrDefault(p, 0); }
    public void addStickyPistonPlaced(UUID p)     { stickyPistonsPlaced.merge(p, 1, Integer::sum); }

    public int getRepeatersPlaced(UUID p)         { return repeatersPlaced.getOrDefault(p, 0); }
    public void addRepeaterPlaced(UUID p)         { repeatersPlaced.merge(p, 1, Integer::sum); }

    public int getComparatorsPlaced(UUID p)       { return comparatorsPlaced.getOrDefault(p, 0); }
    public void addComparatorPlaced(UUID p)       { comparatorsPlaced.merge(p, 1, Integer::sum); }

    public int getHoppersPlaced(UUID p)           { return hoppersPlaced.getOrDefault(p, 0); }
    public void addHopperPlaced(UUID p)           { hoppersPlaced.merge(p, 1, Integer::sum); }

    public int getDispensersPlaced(UUID p)        { return dispensersPlaced.getOrDefault(p, 0); }
    public void addDispenserPlaced(UUID p)        { dispensersPlaced.merge(p, 1, Integer::sum); }

    public int getDroppersPlaced(UUID p)          { return droppersPlaced.getOrDefault(p, 0); }
    public void addDropperPlaced(UUID p)          { droppersPlaced.merge(p, 1, Integer::sum); }

    public int getObserversPlaced(UUID p)         { return observersPlaced.getOrDefault(p, 0); }
    public void addObserverPlaced(UUID p)         { observersPlaced.merge(p, 1, Integer::sum); }

    public int getLeversPlaced(UUID p)            { return leversPlaced.getOrDefault(p, 0); }
    public void addLeverPlaced(UUID p)            { leversPlaced.merge(p, 1, Integer::sum); }

    public int getButtonsPlaced(UUID p)           { return buttonsPlaced.getOrDefault(p, 0); }
    public void addButtonPlaced(UUID p)           { buttonsPlaced.merge(p, 1, Integer::sum); }

    public int getPressurePlatesPlaced(UUID p)    { return pressurePlatesPlaced.getOrDefault(p, 0); }
    public void addPressurePlatePlaced(UUID p)    { pressurePlatesPlaced.merge(p, 1, Integer::sum); }

    public int getIronTrapdoorsPlaced(UUID p)     { return ironTrapdoorsPlaced.getOrDefault(p, 0); }
    public void addIronTrapdoorPlaced(UUID p)     { ironTrapdoorsPlaced.merge(p, 1, Integer::sum); }

    public int getPistonActivations(UUID p)       { return pistonActivations.getOrDefault(p, 0); }
    public void addPistonActivation(UUID p)       { pistonActivations.merge(p, 1, Integer::sum); }

    public boolean hasBuiltHopperChain(UUID p)    { return builtHopperChain.getOrDefault(p, false); }
    public void setBuiltHopperChain(UUID p, boolean v) { builtHopperChain.put(p, v); }

    public boolean hasBuiltRedstoneClock(UUID p)  { return builtRedstoneClock.getOrDefault(p, false); }
    public void setBuiltRedstoneClock(UUID p, boolean v) { builtRedstoneClock.put(p, v); }

    public boolean hasBuiltAutoFarm(UUID p)       { return builtAutoFarm.getOrDefault(p, false); }
    public void setBuiltAutoFarm(UUID p, boolean v) { builtAutoFarm.put(p, v); }

}
