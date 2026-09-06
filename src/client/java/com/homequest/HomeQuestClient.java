package com.homequest;

import com.homequest.ClientBridge;
import com.homequest.data.QuestData;
import com.homequest.network.GameStatsPayload;
import com.homequest.network.QuestSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public class HomeQuestClient implements ClientModInitializer {

    private static boolean shouldOpenBook = false;
    // Evita que el primer sync después de conectarse (que trae TODO lo ya completado
    // en sesiones anteriores) dispare un cartel de "Quest Completada" por cada una —
    // solo dispara para completados que lleguen en syncs POSTERIORES al primero.
    private static boolean firstSyncReceived = false;

    public static void requestOpenBook() {
        shouldOpenBook = true;
    }

    @Override
    public void onInitializeClient() {
        ClientBridge.setOpenBookCallback(() -> { shouldOpenBook = true; });

        // Al conectarse a CUALQUIER mundo/servidor (incluye cambiar de un mundo
        // singleplayer a otro): vaciar el caché del cliente antes de que llegue
        // el primer sync. Este caché es un singleton que vive durante toda la
        // sesión del juego, así que sin esto, el progreso de un mundo anterior
        // (misiones completadas, quest fijada al HUD, contadores) quedaba
        // pegado al abrir un mundo distinto.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> { QuestData.get().clearAll(); firstSyncReceived = false; });

        // Cofre de Quest — modelo y renderer propios (cofre real, animado, textura independiente)
        //
        // BUG RAÍZ (encontrado 2026-09-03): estas 3 líneas registraban las
        // geometrías VANILLA de net.minecraft.client.model.object.chest.ChestModel
        // en vez de com.homequest.block.QuestChestModel (la geometría propia,
        // hecha a medida para quest_chest.png). Por eso QuestChestModel.java
        // nunca se ejecutaba pese a estar bien escrito: el juego seguía
        // dibujando la caja vanilla (64x64, proporciones de cofre normal) con
        // nuestra textura de 56x48 encima, con las UV completamente desalineadas
        // — de ahí las franjas de ancho inconsistente y el flickering/z-fighting.
        // Solo el layer "single" tiene geometría propia por ahora; left/right
        // (cofre doble) se dejan en vanilla hasta tener esa forma en Blockbench.
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
            com.homequest.block.QuestChestRenderer.LAYER_SINGLE,
            com.homequest.block.QuestChestModel::createSingleBodyLayer);
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
            com.homequest.block.QuestChestRenderer.LAYER_LEFT,
            net.minecraft.client.model.object.chest.ChestModel::createDoubleBodyLeftLayer);
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
            com.homequest.block.QuestChestRenderer.LAYER_RIGHT,
            net.minecraft.client.model.object.chest.ChestModel::createDoubleBodyRightLayer);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
            com.homequest.block.ModBlockEntities.QUEST_CHEST,
            com.homequest.block.QuestChestRenderer::new);

        // Recibir sync de estado de quests desde el servidor y actualizar QuestData local
        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.TYPE, (payload, context) -> {            context.client().execute(() -> {
                java.util.UUID uuid = context.client().player != null
                    ? context.client().player.getUUID() : null;
                if (uuid == null) return;

                QuestData data = QuestData.get();

                // Quests completadas (verdad del servidor) - se reemplaza por completo
                // en cada sync, no solo se agrega, para que nunca quede una misión
                // marcada como completa que el servidor ya no reporta como tal.
                // Antes de pisar el estado viejo, se detectan las que son NUEVAS
                // (no estaban completas antes de este sync) para disparar el cartel
                // de "Quest Completada" una vez por cada una — pero NO en el primer
                // sync tras conectarse (ese trae todo lo de sesiones anteriores, no
                // son logros nuevos).
                if (firstSyncReceived) {
                    boolean questEnglish = "en".equals(data.getLanguage(uuid));
                    for (String qid : payload.completedQuests()) {
                        if (!data.isCompleted(uuid, qid)) {
                            QuestCompletedBanner.trigger(QuestBookScreen.questNameFor(qid, questEnglish));
                        }
                    }
                    // Mismo criterio que arriba para las quests: se compara contra el
                    // conteo que YA teníamos antes de pisarlo con setMobKills(), y solo
                    // cuenta como "nuevo" pasar de 0 a >0 (subir el conteo de un mob ya
                    // desbloqueado no es una entrada nueva en el bestiario).
                    for (int i = 0; i < payload.mobKillKeys().size(); i++) {
                        String key = payload.mobKillKeys().get(i);
                        int newCount = payload.mobKillCounts().get(i);
                        if (newCount > 0 && data.getMobKills(uuid, key) == 0) {
                            BestiaryEntryBanner.trigger();
                        }
                    }
                }
                firstSyncReceived = true;
                data.setCompletedQuests(uuid, payload.completedQuests());
                data.setClaimedRewards(uuid, payload.claimedRewards());
                data.setMobKills(uuid, payload.mobKillKeys(), payload.mobKillCounts());

                // Flags
                if (payload.questsStarted()) data.markStartedQuests(uuid);
                if (payload.hasChestPlaced()) {
                    data.setChestPos(uuid, context.client().player.blockPosition());
                }
                data.setLanguage(uuid, payload.language());
                data.setLanguageChosen(uuid, payload.languageChosen());
                data.setTutorialSeen(uuid, payload.tutorialSeen());

                // Contadores
                int coalDelta = payload.coalCount() - data.getCoalCount(uuid);
                data.addCoal(uuid, coalDelta); // sin filtro de signo: ahora coalCount refleja lo que el jugador TIENE, y puede bajar (ej. craftea antorchas)
                int blocksDelta = payload.blocksPlaced() - data.getBlocksPlaced(uuid);
                if (blocksDelta > 0) data.addBlockPlaced(uuid);

                // Nuevos campos booleanos para misiones Principal
                if (payload.visitedNether()) data.setVisitedNether(uuid, true);
                if (payload.visitedEnd()) data.setVisitedEnd(uuid, true);
                if (payload.killedDragon()) data.setKilledDragon(uuid, true);
                if (payload.summonedWither()) data.setSummonedWither(uuid, true);
                if (payload.foundStronghold()) data.setFoundStronghold(uuid, true);
                if (payload.foundStrongholdRuins()) data.setFoundStrongholdRuins(uuid, true);
                // Nuevos campos para misiones Explorador (estructuras)
                if (payload.foundVillage()) data.setFoundVillage(uuid, true);
                if (payload.foundDesertTemple()) data.setFoundDesertTemple(uuid, true);
                if (payload.foundJungleTemple()) data.setFoundJungleTemple(uuid, true);
                if (payload.foundIgloo()) data.setFoundIgloo(uuid, true);
                if (payload.foundShipwreck()) data.setFoundShipwreck(uuid, true);
                if (payload.foundOceanRuins()) data.setFoundOceanRuins(uuid, true);
                if (payload.foundMonument()) data.setFoundMonument(uuid, true);
                if (payload.foundOutpost()) data.setFoundOutpost(uuid, true);
                if (payload.foundMansion()) data.setFoundMansion(uuid, true);
                if (payload.foundNetherFortress()) data.setFoundNetherFortress(uuid, true);
                if (payload.foundBastion()) data.setFoundBastion(uuid, true);
                if (payload.foundEndCity()) data.setFoundEndCity(uuid, true);
                if (payload.foundAncientCity()) data.setFoundAncientCity(uuid, true);
                if (payload.foundTrialChamber()) data.setFoundTrialChamber(uuid, true);
                // Nuevos campos para misiones MINERO (ahora reflejan inventario actual, pueden subir o bajar)
                data.addCoalMined(uuid, payload.coalMined() - data.getCoalMined(uuid));
                data.addIronMined(uuid, payload.ironMined() - data.getIronMined(uuid));
                data.addCopperMined(uuid, payload.copperMined() - data.getCopperMined(uuid));
                data.addGoldMined(uuid, payload.goldMined() - data.getGoldMined(uuid));
                data.addRedstoneMined(uuid, payload.redstoneMined() - data.getRedstoneMined(uuid));
                data.addLapisMined(uuid, payload.lapisMined() - data.getLapisMined(uuid));
                data.addDiamondMined(uuid, payload.diamondMined() - data.getDiamondMined(uuid));
                data.addAncientDebrisMined(uuid, payload.ancientDebrisMined() - data.getAncientDebrisMined(uuid));
                data.addEmeraldMined(uuid, payload.emeraldMined() - data.getEmeraldMined(uuid));
                // CONSTRUCTOR
                int stairsDelta = payload.stairsPlaced() - data.getStairsPlaced(uuid);
                if (stairsDelta > 0) for (int i = 0; i < stairsDelta; i++) data.addStairsPlaced(uuid);
                int slabsDelta = payload.slabsPlaced() - data.getSlabsPlaced(uuid);
                if (slabsDelta > 0) for (int i = 0; i < slabsDelta; i++) data.addSlabsPlaced(uuid);
                int trapdoorsDelta = payload.trapdoorsPlaced() - data.getTrapdoorsPlaced(uuid);
                if (trapdoorsDelta > 0) for (int i = 0; i < trapdoorsDelta; i++) data.addTrapdoorsPlaced(uuid);
                int doorsDelta = payload.doorsPlaced() - data.getDoorsPlaced(uuid);
                if (doorsDelta > 0) for (int i = 0; i < doorsDelta; i++) data.addDoorsPlaced(uuid);
                int glassDelta = payload.glassPlaced() - data.getGlassPlaced(uuid);
                if (glassDelta > 0) for (int i = 0; i < glassDelta; i++) data.addGlassPlaced(uuid);
                int fencesDelta = payload.fencesPlaced() - data.getFencesPlaced(uuid);
                if (fencesDelta > 0) for (int i = 0; i < fencesDelta; i++) data.addFencesPlaced(uuid);
                int lanternsDelta = payload.lanternsPlaced() - data.getLanternsPlaced(uuid);
                if (lanternsDelta > 0) for (int i = 0; i < lanternsDelta; i++) data.addLanternsPlaced(uuid);
                int seaLanternsDelta = payload.seaLanternsPlaced() - data.getSeaLanternsPlaced(uuid);
                if (seaLanternsDelta > 0) for (int i = 0; i < seaLanternsDelta; i++) data.addSeaLanternsPlaced(uuid);
                int decDelta = payload.decorativePlaced() - data.getDecorativePlaced(uuid);
                if (decDelta > 0) for (int i = 0; i < decDelta; i++) data.addDecorativePlaced(uuid);
                int chiseled = payload.chiseledPlaced() - data.getChiseledPlaced(uuid);
                if (chiseled > 0) for (int i = 0; i < chiseled; i++) data.addChiseledPlaced(uuid);
                int brick = payload.brickPlaced() - data.getBrickPlaced(uuid);
                if (brick > 0) for (int i = 0; i < brick; i++) data.addBrickPlaced(uuid);
                int quartz = payload.quartzPlaced() - data.getQuartzPlaced(uuid);
                if (quartz > 0) for (int i = 0; i < quartz; i++) data.addQuartzPlaced(uuid);
                int concrete = payload.concretePlaced() - data.getConcretePlaced(uuid);
                if (concrete > 0) for (int i = 0; i < concrete; i++) data.addConcretePlaced(uuid);
                int honey = payload.honeyPlaced() - data.getHoneyPlaced(uuid);
                if (honey > 0) for (int i = 0; i < honey; i++) data.addHoneyPlaced(uuid);
                if (payload.placedBell())   data.setPlacedBell(uuid, true);
                if (payload.placedBeacon()) data.setPlacedBeacon(uuid, true);
                // TÉCNICO
                if (payload.craftedPiston())       data.setCraftedPiston(uuid, true);
                if (payload.craftedStickyPiston()) data.setCraftedStickyPiston(uuid, true);
                if (payload.craftedRepeater())     data.setCraftedRepeater(uuid, true);
                if (payload.craftedComparator())   data.setCraftedComparator(uuid, true);
                int pistonD = payload.pistonsPlaced() - data.getPistonsPlaced(uuid);
                if (pistonD > 0) for (int i = 0; i < pistonD; i++) data.addPistonPlaced(uuid);
                int stickyD = payload.stickyPistonsPlaced() - data.getStickyPistonsPlaced(uuid);
                if (stickyD > 0) for (int i = 0; i < stickyD; i++) data.addStickyPistonPlaced(uuid);
                int repD = payload.repeatersPlaced() - data.getRepeatersPlaced(uuid);
                if (repD > 0) for (int i = 0; i < repD; i++) data.addRepeaterPlaced(uuid);
                int compD = payload.comparatorsPlaced() - data.getComparatorsPlaced(uuid);
                if (compD > 0) for (int i = 0; i < compD; i++) data.addComparatorPlaced(uuid);
                int hopD = payload.hoppersPlaced() - data.getHoppersPlaced(uuid);
                if (hopD > 0) for (int i = 0; i < hopD; i++) data.addHopperPlaced(uuid);
                int dispD = payload.dispensersPlaced() - data.getDispensersPlaced(uuid);
                if (dispD > 0) for (int i = 0; i < dispD; i++) data.addDispenserPlaced(uuid);
                int dropD = payload.droppersPlaced() - data.getDroppersPlaced(uuid);
                if (dropD > 0) for (int i = 0; i < dropD; i++) data.addDropperPlaced(uuid);
                int obsD = payload.observersPlaced() - data.getObserversPlaced(uuid);
                if (obsD > 0) for (int i = 0; i < obsD; i++) data.addObserverPlaced(uuid);
                int levD = payload.leversPlaced() - data.getLeversPlaced(uuid);
                if (levD > 0) for (int i = 0; i < levD; i++) data.addLeverPlaced(uuid);
                int butD = payload.buttonsPlaced() - data.getButtonsPlaced(uuid);
                if (butD > 0) for (int i = 0; i < butD; i++) data.addButtonPlaced(uuid);
                int ppD = payload.pressurePlatesPlaced() - data.getPressurePlatesPlaced(uuid);
                if (ppD > 0) for (int i = 0; i < ppD; i++) data.addPressurePlatePlaced(uuid);
                int itD = payload.ironTrapdoorsPlaced() - data.getIronTrapdoorsPlaced(uuid);
                if (itD > 0) for (int i = 0; i < itD; i++) data.addIronTrapdoorPlaced(uuid);
                int paD = payload.pistonActivations() - data.getPistonActivations(uuid);
                if (paD > 0) for (int i = 0; i < paD; i++) data.addPistonActivation(uuid);
                if (payload.builtHopperChain())   data.setBuiltHopperChain(uuid, true);
                if (payload.builtRedstoneClock()) data.setBuiltRedstoneClock(uuid, true);
                if (payload.builtAutoFarm())      data.setBuiltAutoFarm(uuid, true);
                // Pin nunca se toca aquí — es solo-cliente
            });
        });

        // Recibir la respuesta de "Datos de la Partida" y guardarla en el holder
        // chico de RAM — GameStatsClient. Se pide una vez por apertura de esa
        // página (ver QuestBookScreen), no hace falta más que esto acá.
        ClientPlayNetworking.registerGlobalReceiver(GameStatsPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                GameStatsClient.update(payload.mobKills(), payload.blocksMined(), payload.blocksPlaced()));
        });

        // HUD tracker
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("homequest", "quest_tracker"),
            HomeQuestClient::renderHud
        );
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        QuestCompletedBanner.render(graphics, client);
        BestiaryEntryBanner.render(graphics, client);

        if (shouldOpenBook) {
            shouldOpenBook = false;
            UUID playerUuid = client.player.getUUID();
            if (!QuestData.get().hasChosenLanguage(playerUuid)) {
                Minecraft.getInstance().gui.setScreen(new LanguageSelectScreen());
            } else {
                boolean english = "en".equals(QuestData.get().getLanguage(playerUuid));
                Minecraft.getInstance().gui.setScreen(new QuestBookScreen(english));
            }
            return;
        }

        UUID uuid = client.player.getUUID();
        String pinned = QuestData.get().getPinnedQuest(uuid);
        if (pinned == null) return;

        if (pinned.equals(QuestData.QUEST_FIRST_HOME)) {
            renderFirstHomeHud(graphics, client, uuid);
        } else {
            renderGenericHud(graphics, client, uuid, pinned);
        }
    }

    private static void renderGenericHud(GuiGraphicsExtractor graphics, Minecraft client, UUID uuid, String questId) {
        boolean done = QuestData.get().isCompleted(uuid, questId);
        boolean english = "en".equals(QuestData.get().getLanguage(uuid));

        // Si la quest está completa, quitar el pin y no renderizar nada
        if (done) {
            QuestData.get().unpinQuest(uuid);
            return;
        }

        String questName;
        String[] descLines;
        String progressLine;

        switch (questId) {
            case QuestData.QUEST_NEW_WORLD -> {
                questName = english ? "A New World" : "Un Nuevo Mundo";
                descLines = english ? new String[]{ "Travel and discover a new biome." } : new String[]{ "Viaja y descubre un nuevo bioma." };
                int biomes = QuestData.get().getDiscoveredBiomes(uuid).size();
                progressLine = english
                    ? (biomes > 0 ? "§aBiome discovered ✔" : "§6Exploring...")
                    : (biomes > 0 ? "§aBioma descubierto ✔" : "§6Explorando...");
            }
            case QuestData.QUEST_HONEST_WORK -> {
                questName = english ? "Honest Work" : "Es Trabajo Honesto";
                descLines = english ? new String[]{ "Place 10 blocks to build." } : new String[]{ "Coloca 10 bloques para construir." };
                int placed = QuestData.get().getBlocksPlaced(uuid);
                progressLine = (english ? "§6Blocks: " : "§6Bloques: ") + Math.min(placed, 10) + "/10";
            }
            case QuestData.QUEST_STAY_WARM -> {
                questName = english ? "Staying Warm" : "No Pasaremos Frío";
                descLines = english ? new String[]{ "Get 10 Coal." } : new String[]{ "Consigue 10 de Carbón." };
                int coal = QuestData.get().getCoalCount(uuid);
                progressLine = (english ? "§6Coal: " : "§6Carbón: ") + Math.min(coal, 10) + "/10";
            }
            // Nuevas misiones Principal - sin barra de progreso específica
            case QuestData.QUEST_NEW_BEGINNING -> {
                questName = english ? "A New Beginning" : "Un Nuevo Comienzo";
                descLines = english ? new String[]{ "Craft a Crafting Table." } : new String[]{ "Craftea una Mesa de Crafteo." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_HANDS_ON -> {
                questName = english ? "Hands On" : "Manos a la Obra";
                descLines = english ? new String[]{ "Craft stone tools." } : new String[]{ "Fabrica herramientas de piedra." };
                progressLine = english ? "§6Get all the items..." : "§6Consigue todos los items...";
            }
            case QuestData.QUEST_FIRST_INGOTS -> {
                questName = english ? "The First Ingots" : "Los Primeros Lingotes";
                descLines = english ? new String[]{ "Get 10 iron ingots." } : new String[]{ "Consigue 10 lingotes de hierro." };
                progressLine = english ? "§6Get 10 iron..." : "§6Consigue 10 hierro...";
            }
            case QuestData.QUEST_WELL_PROTECTED -> {
                questName = english ? "Well Protected" : "Bien Protegido";
                descLines = english ? new String[]{ "Wear iron armor." } : new String[]{ "Equipa armadura de hierro." };
                progressLine = english ? "§6Equip the armor..." : "§6Equipa la armadura...";
            }
            case QuestData.QUEST_BEST_DEFENSE -> {
                questName = english ? "The Best Defense" : "La Mejor Defensa";
                descLines = english ? new String[]{ "Craft a shield." } : new String[]{ "Fabrica un escudo." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_FOUND_IT -> {
                questName = english ? "Found It!" : "¡Lo Encontré!";
                descLines = english ? new String[]{ "Get a diamond." } : new String[]{ "Consigue un diamante." };
                progressLine = english ? "§6Get 1 diamond..." : "§6Consigue 1 diamante...";
            }
            case QuestData.QUEST_READY_FOR_ALL -> {
                questName = english ? "Ready for Anything" : "Listo para Todo";
                descLines = english ? new String[]{ "Craft a diamond pickaxe." } : new String[]{ "Fabrica un pico de diamante." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_KNOWLEDGE -> {
                questName = english ? "The Power of Knowledge" : "El Poder del Conocimiento";
                descLines = english ? new String[]{ "Craft an enchanting table." } : new String[]{ "Fabrica mesa de encantamientos." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_INTO_UNKNOWN -> {
                questName = english ? "Into the Unknown" : "Hacia lo Desconocido";
                descLines = english ? new String[]{ "Enter the Nether." } : new String[]{ "Entra al Nether." };
                progressLine = english ? "§6Travel to the Nether..." : "§6Viaja al Nether...";
            }
            case QuestData.QUEST_PLAYING_FIRE -> {
                questName = english ? "Playing with Fire" : "Jugando con Fuego";
                descLines = english ? new String[]{ "Get 5 Blaze Rods." } : new String[]{ "Consigue 5 Blaze Rods." };
                progressLine = english ? "§6Get 5 blaze rods..." : "§6Consigue 5 blaze rods...";
            }
            case QuestData.QUEST_BETWEEN_DIMS -> {
                questName = english ? "Between Dimensions" : "Entre Dimensiones";
                descLines = english ? new String[]{ "Get 8 Ender Pearls." } : new String[]{ "Consigue 8 Ender Pearls." };
                progressLine = english ? "§6Get 8 ender pearls..." : "§6Consigue 8 ender pearls...";
            }
            case QuestData.QUEST_ENDER_EYE -> {
                questName = english ? "The Eye of the End" : "La Mirada del Fin";
                descLines = english ? new String[]{ "Craft an Eye of Ender." } : new String[]{ "Crea un Ojo de Ender." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_LOST_FORTRESS -> {
                questName = english ? "The Lost Fortress" : "La Fortaleza Perdida";
                descLines = english ? new String[]{ "Find the Stronghold." } : new String[]{ "Encuentra el Stronghold." };
                progressLine = english ? "§6Explore and find it..." : "§6Explora y encuentra...";
            }
            case QuestData.QUEST_BEGINNING_END -> {
                questName = english ? "The Beginning of the End" : "El Comienzo del Final";
                descLines = english ? new String[]{ "Defeat the Ender Dragon." } : new String[]{ "Derrota al Ender Dragon." };
                progressLine = english ? "§6Defeat the dragon..." : "§6Derrota al dragon...";
            }
            case QuestData.QUEST_TAKE_FLIGHT -> {
                questName = english ? "Take Flight" : "Alza el Vuelo";
                descLines = english ? new String[]{ "Get an Elytra." } : new String[]{ "Consigue una Elytra." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_DEFYING_DARK -> {
                questName = english ? "Defying the Dark" : "Desafiando la Oscuridad";
                descLines = english ? new String[]{ "Summon the Wither." } : new String[]{ "Invoca al Wither." };
                progressLine = english ? "§6Summon the wither..." : "§6Invoca al wither...";
            }
            case QuestData.QUEST_NEW_HOPE -> {
                questName = english ? "A New Hope" : "Una Nueva Esperanza";
                descLines = english ? new String[]{ "Get a Nether Star." } : new String[]{ "Consigue una Nether Star." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_STRANGE_WORLD -> {
                questName = english ? "A Strange World" : "Un Mundo Extraño";
                descLines = english ? new String[]{ "Reach the End." } : new String[]{ "Llega al End." };
                progressLine = english ? "§6Travel to the End..." : "§6Viaja al End...";
            }
            // Nuevas misiones Explorador
            case QuestData.QUEST_BEYOND_HORIZON -> {
                questName = english ? "Beyond the Horizon" : "Más Allá del Horizonte";
                descLines = english ? new String[]{ "Discover 3 biomes." } : new String[]{ "Descubre 3 biomas." };
                progressLine = english ? "§6Explore new biomes..." : "§6Explora nuevos biomas...";
            }
            case QuestData.QUEST_NO_BORDERS -> {
                questName = english ? "No Borders" : "Sin Fronteras";
                descLines = english ? new String[]{ "Discover 10 biomes." } : new String[]{ "Descubre 10 biomas." };
                progressLine = english ? "§6Explore new biomes..." : "§6Explora nuevos biomas...";
            }
            case QuestData.QUEST_ALL_SEEN -> {
                questName = english ? "Nothing Left to See" : "No Queda Nada por Ver";
                descLines = english ? new String[]{ "Discover every biome." } : new String[]{ "Descubre todos los biomas." };
                progressLine = english ? "§6Explore new biomes..." : "§6Explora nuevos biomas...";
            }
            case QuestData.QUEST_SIGNS_OF_LIFE -> {
                questName = english ? "Signs of Life" : "Señales de Vida";
                descLines = english ? new String[]{ "Find a village." } : new String[]{ "Encuentra una aldea." };
                progressLine = english ? "§6Explore and find it..." : "§6Explora y encuentra...";
            }
            case QuestData.QUEST_ANCIENT_SANDS -> {
                questName = english ? "Ancient Sands" : "Arenas Antiguas";
                descLines = english ? new String[]{ "Find a desert temple." } : new String[]{ "Encuentra templo desierto." };
                progressLine = english ? "§6Explore the desert..." : "§6Explora el desierto...";
            }
            case QuestData.QUEST_JUNGLE_SECRETS -> {
                questName = english ? "The Jungle Hides Secrets" : "La Selva Esconde Secretos";
                descLines = english ? new String[]{ "Find a jungle temple." } : new String[]{ "Encuentra templo jungla." };
                progressLine = english ? "§6Explore the jungle..." : "§6Explora la selva...";
            }
            case QuestData.QUEST_COLD_HOME -> {
                questName = english ? "Cold Home" : "Frío Hogar";
                descLines = english ? new String[]{ "Find an igloo." } : new String[]{ "Encuentra un iglú." };
                progressLine = english ? "§6Explore cold areas..." : "§6Explora zonas frías...";
            }
            case QuestData.QUEST_ECHOES_PAST -> {
                questName = english ? "Echoes of the Past" : "Ecos del Pasado";
                descLines = english ? new String[]{ "Find a shipwreck." } : new String[]{ "Encuentra naufragio." };
                progressLine = english ? "§6Explore the ocean..." : "§6Explora el océano...";
            }
            case QuestData.QUEST_UNDER_WAVES -> {
                questName = english ? "Under the Waves" : "Bajo las Olas";
                descLines = english ? new String[]{ "Find ocean ruins." } : new String[]{ "Encuentra ruinas oceánicas." };
                progressLine = english ? "§6Explore the ocean..." : "§6Explora el océano...";
            }
            case QuestData.QUEST_SEA_KINGDOM -> {
                questName = english ? "The Kingdom of the Sea" : "El Reino del Mar";
                descLines = english ? new String[]{ "Find an ocean monument." } : new String[]{ "Encuentra monumento oceánico." };
                progressLine = english ? "§6Explore the deep ocean..." : "§6Explora el océano profundo...";
            }
            case QuestData.QUEST_HOSTILE_TERRITORY -> {
                questName = english ? "Hostile Territory" : "Territorio Hostil";
                descLines = english ? new String[]{ "Find a Pillager Outpost." } : new String[]{ "Encuentra outpost." };
                progressLine = english ? "§6Explore the dark forest..." : "§6Explora el bosque oscuro...";
            }
            case QuestData.QUEST_FOREST_HOUSE -> {
                questName = english ? "The House in the Woods" : "La Casa del Bosque";
                descLines = english ? new String[]{ "Find a woodland mansion." } : new String[]{ "Encuentra mansión." };
                progressLine = english ? "§6Explore the dark forest..." : "§6Explora el bosque oscuro...";
            }
            case QuestData.QUEST_INFERNAL_FORTRESS -> {
                questName = english ? "Infernal Fortress" : "Fortaleza Infernal";
                descLines = english ? new String[]{ "Find a Nether fortress." } : new String[]{ "Encuentra fortaleza nether." };
                progressLine = english ? "§6Explore the Nether..." : "§6Explora el Nether...";
            }
            case QuestData.QUEST_GOLD_KINGDOM -> {
                questName = english ? "The Kingdom of Gold" : "El Reino del Oro";
                descLines = english ? new String[]{ "Find a bastion remnant." } : new String[]{ "Encuentra bastión." };
                progressLine = english ? "§6Explore the Nether..." : "§6Explora el Nether...";
            }
            case QuestData.QUEST_FORGOTTEN_FORTRESS -> {
                questName = english ? "The Forgotten Fortress" : "La Fortaleza Olvidada";
                descLines = english ? new String[]{ "Find the Stronghold." } : new String[]{ "Encuentra el Stronghold." };
                progressLine = english ? "§6Explore the Stronghold..." : "§6Usa ojos de ender...";
            }
            case QuestData.QUEST_LOST_CITY -> {
                questName = english ? "The Lost City" : "La Ciudad Perdida";
                descLines = english ? new String[]{ "Find an End City." } : new String[]{ "Encuentra End City." };
                progressLine = english ? "§6Explore the End..." : "§6Explora el End...";
            }
            case QuestData.QUEST_NO_NOISE -> {
                questName = english ? "Don't Make a Sound" : "No Hagas Ruido";
                descLines = english ? new String[]{ "Find an Ancient City." } : new String[]{ "Encuentra Ancient City." };
                progressLine = english ? "§6Explore the deep dark..." : "§6Explora el deep dark...";
            }
            case QuestData.QUEST_TRIAL_VALOR -> {
                questName = english ? "Put Your Courage to the Test" : "Pon a Prueba tu Valor";
                descLines = english ? new String[]{ "Find a Trial Chamber." } : new String[]{ "Encuentra Trial Chamber." };
                progressLine = english ? "§6Explore deep caves..." : "§6Explora cuevas profundas...";
            }
            // Nuevas misiones MINERO
            case QuestData.QUEST_WINTER_FUEL -> {
                questName = english ? "Winter Fuel" : "Combustible para el Invierno";
                descLines = english ? new String[]{ "Get 64 coal." } : new String[]{ "Consigue 64 de carbón." };
                progressLine = english ? "§6Get coal..." : "§6Mina carbón...";
            }
            case QuestData.QUEST_COAL_KING -> {
                questName = english ? "King of Coal" : "Rey del Tizón";
                descLines = english ? new String[]{ "Get 256 coal." } : new String[]{ "Consigue 256 de carbón." };
                progressLine = english ? "§6Get coal..." : "§6Mina carbón...";
            }
            case QuestData.QUEST_METAL_AGE -> {
                questName = english ? "Age of Metals" : "Edad de los Metales";
                descLines = english ? new String[]{ "Get 10 iron." } : new String[]{ "Consigue 10 hierro." };
                progressLine = english ? "§6Get iron..." : "§6Mina hierro...";
            }
            case QuestData.QUEST_IRON_FEVER -> {
                questName = english ? "Iron Fever" : "Fiebre del Hierro";
                descLines = english ? new String[]{ "Get 64 iron." } : new String[]{ "Consigue 64 hierro." };
                progressLine = english ? "§6Get iron..." : "§6Mina hierro...";
            }
            case QuestData.QUEST_IRON_WILL -> {
                questName = english ? "Iron Will" : "Voluntad de Hierro";
                descLines = english ? new String[]{ "Get 256 iron." } : new String[]{ "Consigue 256 hierro." };
                progressLine = english ? "§6Get iron..." : "§6Mina hierro...";
            }
            case QuestData.QUEST_GOOD_CONDUCTORS -> {
                questName = english ? "Good Conductors" : "Buenos Conductores";
                descLines = english ? new String[]{ "Get 10 copper." } : new String[]{ "Consigue 10 de cobre." };
                progressLine = english ? "§6Get copper..." : "§6Mina cobre...";
            }
            case QuestData.QUEST_LIBERTY_STATUE -> {
                questName = english ? "Statue of Liberty" : "Estatua de la Libertad";
                descLines = english ? new String[]{ "Get 64 copper." } : new String[]{ "Consigue 64 de cobre." };
                progressLine = english ? "§6Get copper..." : "§6Mina cobre...";
            }
            case QuestData.QUEST_COVETED_SHINE -> {
                questName = english ? "Coveted Shine" : "Brillo Codiciable";
                descLines = english ? new String[]{ "Get 10 gold." } : new String[]{ "Consigue 10 oro." };
                progressLine = english ? "§6Get gold..." : "§6Mina oro...";
            }
            case QuestData.QUEST_MIDAS_TOUCH -> {
                questName = english ? "Midas Touch" : "Toque de Midas";
                descLines = english ? new String[]{ "Get 64 gold." } : new String[]{ "Consigue 64 oro." };
                progressLine = english ? "§6Get gold..." : "§6Mina oro...";
            }
            case QuestData.QUEST_SPARKLING_DUST -> {
                questName = english ? "Sparkling Dust" : "Polvo Brillante";
                descLines = english ? new String[]{ "Get 10 redstone." } : new String[]{ "Consigue 10 redstone." };
                progressLine = english ? "§6Get redstone..." : "§6Mina redstone...";
            }
            case QuestData.QUEST_CONTINUOUS_CURRENT -> {
                questName = english ? "Continuous Current" : "Corriente Continua";
                descLines = english ? new String[]{ "Get 128 redstone." } : new String[]{ "Consigue 128 redstone." };
                progressLine = english ? "§6Get redstone..." : "§6Mina redstone...";
            }
            case QuestData.QUEST_WIZARD_DYE -> {
                questName = english ? "Wizard's Dye" : "Tinte de Mago";
                descLines = english ? new String[]{ "Get 10 lapis lazuli." } : new String[]{ "Consigue 10 lapislázuli." };
                progressLine = english ? "§6Get lapis lazuli..." : "§6Mina lapislázuli...";
            }
            case QuestData.QUEST_ULTRAMARINE -> {
                questName = english ? "Ultramarine" : "Ultramarino";
                descLines = english ? new String[]{ "Get 64 lapis lazuli." } : new String[]{ "Consigue 64 lapislázuli." };
                progressLine = english ? "§6Get lapis lazuli..." : "§6Mina lapislázuli...";
            }
            case QuestData.QUEST_FIVE_CHOSEN -> {
                questName = english ? "The Chosen Five" : "Los Cinco Elegidos";
                descLines = english ? new String[]{ "Get 5 diamonds." } : new String[]{ "Consigue 5 diamantes." };
                progressLine = english ? "§6Get diamonds..." : "§6Mina diamantes...";
            }
            case QuestData.QUEST_SHINE_HOARDER -> {
                questName = english ? "Shine Hoarder" : "Acumulador de Brillo";
                descLines = english ? new String[]{ "Get 32 diamonds." } : new String[]{ "Consigue 32 diamantes." };
                progressLine = english ? "§6Get diamonds..." : "§6Mina diamantes...";
            }
            case QuestData.QUEST_UNBREAKABLE -> {
                questName = english ? "Unbreakable!" : "Irrompible";
                descLines = english ? new String[]{ "Get 64 diamonds." } : new String[]{ "Consigue 64 diamantes." };
                progressLine = english ? "§6Get diamonds..." : "§6Mina diamantes...";
            }
            case QuestData.QUEST_ANCIENT_ECHOES -> {
                questName = english ? "Echoes of Antiquity" : "Ecos Antiguos";
                descLines = english ? new String[]{ "Get 1 Ancient Debris." } : new String[]{ "Consigue 1 ancient debris." };
                progressLine = english ? "§6Explore the Nether..." : "§6Mina en el Nether...";
            }
            case QuestData.QUEST_FORGED_HELL -> {
                questName = english ? "Forged in Hell" : "Forjado en el Infierno";
                descLines = english ? new String[]{ "Get 8 Ancient Debris." } : new String[]{ "Consigue 8 ancient debris." };
                progressLine = english ? "§6Explore the Nether..." : "§6Mina en el Nether...";
            }
            case QuestData.QUEST_VILLAGER_FAVOR -> {
                questName = english ? "The Villager's Favor" : "Favor del Aldeano";
                descLines = english ? new String[]{ "Get 1 emerald." } : new String[]{ "Consigue 1 esmeralda." };
                progressLine = english ? "§6Mine or trade..." : "§6Mina o comercia...";
            }
            // ── CONSTRUCTOR ──
            case QuestData.QUEST_FIRM_FOUNDATIONS -> {
                questName = english ? "Firm Foundations" : "Bases Sólidas";
                descLines = english ? new String[]{ "Place 250 blocks." } : new String[]{ "Coloca 250 bloques." };
                int pl = QuestData.get().getBlocksPlaced(uuid);
                progressLine = (english ? "§6Blocks: " : "§6Bloques: ") + Math.min(pl, 250) + "/250";
            }
            case QuestData.QUEST_MASON_HANDS -> {
                questName = english ? "Mason's Hands" : "Manos de Albañil";
                descLines = english ? new String[]{ "Place 1,000 blocks." } : new String[]{ "Coloca 1000 bloques." };
                int pl = QuestData.get().getBlocksPlaced(uuid);
                progressLine = (english ? "§6Blocks: " : "§6Bloques: ") + Math.min(pl, 1000) + "/1000";
            }
            case QuestData.QUEST_GREAT_ARCHITECT -> {
                questName = english ? "Great Architect" : "Gran Arquitecto";
                descLines = english ? new String[]{ "Place 5,000 blocks." } : new String[]{ "Coloca 5000 bloques." };
                int pl = QuestData.get().getBlocksPlaced(uuid);
                progressLine = (english ? "§6Blocks: " : "§6Bloques: ") + Math.min(pl, 5000) + "/5000";
            }
            case QuestData.QUEST_STEP_BY_STEP -> {
                questName = english ? "Step by Step" : "Paso a Paso";
                descLines = english ? new String[]{ "Place 10 stairs." } : new String[]{ "Coloca 10 escaleras." };
                progressLine = english ? "§6Place stairs..." : "§6Coloca escaleras...";
            }
            case QuestData.QUEST_HALF_BLOCK -> {
                questName = english ? "Halfway There" : "Medio Bloque";
                descLines = english ? new String[]{ "Place 10 slabs." } : new String[]{ "Coloca 10 losas." };
                progressLine = english ? "§6Place slabs..." : "§6Coloca losas...";
            }
            case QuestData.QUEST_ESCAPE_HATCH -> {
                questName = english ? "Escape Hatch" : "Escotilla de Escape";
                descLines = english ? new String[]{ "Place 10 trapdoors." } : new String[]{ "Coloca 10 trampillas." };
                progressLine = english ? "§6Place trapdoors..." : "§6Coloca trampillas...";
            }
            case QuestData.QUEST_KNOCK_BEFORE -> {
                questName = english ? "Knock Before Entering" : "Llama Antes de Entrar";
                descLines = english ? new String[]{ "Place 10 doors." } : new String[]{ "Coloca 10 puertas." };
                progressLine = english ? "§6Place doors..." : "§6Coloca puertas...";
            }
            case QuestData.QUEST_FRAGILE_TRANSP -> {
                questName = english ? "Fragile Transparency" : "Frágil y Transparente";
                descLines = english ? new String[]{ "Place 20 panes of glass." } : new String[]{ "Coloca 20 cristales." };
                progressLine = english ? "§6Place glass..." : "§6Coloca vidrio...";
            }
            case QuestData.QUEST_NO_PASS -> {
                questName = english ? "They Shall Not Pass!" : "No Pasarás";
                descLines = english ? new String[]{ "Place 20 fences." } : new String[]{ "Coloca 20 vallas." };
                progressLine = english ? "§6Place fences..." : "§6Coloca vallas...";
            }
            case QuestData.QUEST_SAFE_NIGHTS -> {
                questName = english ? "Safe Nights" : "Noches Seguras";
                descLines = english ? new String[]{ "Place 10 lanterns." } : new String[]{ "Coloca 10 faroles." };
                progressLine = english ? "§6Place lanterns..." : "§6Coloca faroles...";
            }
            case QuestData.QUEST_MARINE_LIGHT -> {
                questName = english ? "Marine Lighting" : "Luz Marina";
                descLines = english ? new String[]{ "Place 10 sea lanterns." } : new String[]{ "Coloca 10 linternas marinas." };
                progressLine = english ? "§6Place sea lanterns..." : "§6Coloca sea lanterns...";
            }
            case QuestData.QUEST_DETAILS_MATTER -> {
                questName = english ? "Details Matter" : "Los Detalles Importan";
                descLines = english ? new String[]{ "Place 20 decorative blocks." } : new String[]{ "Coloca 20 decorativos." };
                progressLine = english ? "§6Place decorations..." : "§6Coloca decoraciones...";
            }
            case QuestData.QUEST_CHISEL_STONE -> {
                questName = english ? "Chisel and Stone" : "Cincel y Piedra";
                descLines = english ? new String[]{ "Place 20 chiseled blocks." } : new String[]{ "Coloca 20 bloques tallados." };
                progressLine = english ? "§6Place chiseled blocks..." : "§6Coloca bloques chiseled...";
            }
            case QuestData.QUEST_PIG_HOUSE -> {
                questName = english ? "Brick House" : "Casa de Ladrillo";
                descLines = english ? new String[]{ "Place 20 brick blocks." } : new String[]{ "Coloca 20 bloques de ladrillo." };
                progressLine = english ? "§6Place bricks..." : "§6Coloca ladrillos...";
            }
            case QuestData.QUEST_CLASSIC_TEMPLE -> {
                questName = english ? "Classic Temple" : "Templo Clásico";
                descLines = english ? new String[]{ "Place 20 quartz blocks." } : new String[]{ "Coloca 20 bloques de cuarzo." };
                progressLine = english ? "§6Place quartz..." : "§6Coloca cuarzo...";
            }
            case QuestData.QUEST_COLOR_PALETTE -> {
                questName = english ? "Color Palette" : "Paleta de Colores";
                descLines = english ? new String[]{ "Place 20 concrete blocks." } : new String[]{ "Coloca 20 bloques de hormigón." };
                progressLine = english ? "§6Place concrete..." : "§6Coloca hormigón...";
            }
            case QuestData.QUEST_STICKY_STRUCT -> {
                questName = english ? "Sticky Structure" : "Estructura Pegajosa";
                descLines = english ? new String[]{ "Place 20 honey blocks." } : new String[]{ "Coloca 20 bloques de miel." };
                progressLine = english ? "§6Place honey blocks..." : "§6Coloca bloques de miel...";
            }
            case QuestData.QUEST_PLAZA_MEETING -> {
                questName = english ? "Town Square Meeting" : "Plaza de Reunión";
                descLines = english ? new String[]{ "Place a bell." } : new String[]{ "Coloca una campana." };
                progressLine = english ? "§6Place a bell..." : "§6Coloca una bell...";
            }
            case QuestData.QUEST_BEACON_LIGHT -> {
                questName = english ? "Guiding Light" : "Luz de Baliza";
                descLines = english ? new String[]{ "Place a beacon." } : new String[]{ "Coloca un beacon." };
                progressLine = english ? "§6Place a beacon..." : "§6Coloca un beacon...";
            }
            // ── TÉCNICO ──
            case QuestData.QUEST_MOVEMENT_START -> {
                questName = english ? "The Movement Begins" : "El Primer Movimiento";
                descLines = english ? new String[]{ "Craft a piston." } : new String[]{ "Craftea un pistón." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_STICKY_MECH -> {
                questName = english ? "Sticky Mechanism" : "Mecánica Pegajosa";
                descLines = english ? new String[]{ "Craft a sticky piston." } : new String[]{ "Craftea un pistón pegajoso." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_SIGNAL_PATIENCE -> {
                questName = english ? "Patience in the Signal" : "Señal con Paciencia";
                descLines = english ? new String[]{ "Craft a repeater." } : new String[]{ "Craftea un repetidor." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_BINARY_LOGIC -> {
                questName = english ? "Binary Logic" : "Lógica Binaria";
                descLines = english ? new String[]{ "Craft a comparator." } : new String[]{ "Craftea un comparador." };
                progressLine = english ? "§6Get the item..." : "§6Consigue el item...";
            }
            case QuestData.QUEST_BRUTE_FORCE -> {
                questName = english ? "Brute Force" : "Fuerza Bruta";
                descLines = english ? new String[]{ "Place 10 pistons." } : new String[]{ "Coloca 10 pistones." };
                progressLine = english ? "§6Place pistons..." : "§6Coloca pistones...";
            }
            case QuestData.QUEST_BACK_FORTH -> {
                questName = english ? "Back and Forth" : "Ida y Vuelta";
                descLines = english ? new String[]{ "Place 10 sticky pistons." } : new String[]{ "Coloca 10 pistones pegajosos." };
                progressLine = english ? "§6Place sticky pistons..." : "§6Coloca sticky pistons...";
            }
            case QuestData.QUEST_CONTROLLED_DELAY -> {
                questName = english ? "Controlled Delay" : "Delay Controlado";
                descLines = english ? new String[]{ "Place 20 repeaters." } : new String[]{ "Coloca 20 repetidores." };
                progressLine = english ? "§6Place repeaters..." : "§6Coloca repetidores...";
            }
            case QuestData.QUEST_REDSTONE_BRAIN -> {
                questName = english ? "Redstone Brain" : "Cerebro de Redstone";
                descLines = english ? new String[]{ "Place 20 comparators." } : new String[]{ "Coloca 20 comparadores." };
                progressLine = english ? "§6Place comparators..." : "§6Coloca comparadores...";
            }
            case QuestData.QUEST_AUTO_LOGISTICS -> {
                questName = english ? "Automated Logistics" : "Logística Automática";
                descLines = english ? new String[]{ "Place 20 hoppers." } : new String[]{ "Coloca 20 tolvas." };
                progressLine = english ? "§6Place hoppers..." : "§6Coloca hoppers...";
            }
            case QuestData.QUEST_FIRE_AT_WILL -> {
                questName = english ? "Fire at Will" : "Fuego a Voluntad";
                descLines = english ? new String[]{ "Place 10 dispensers." } : new String[]{ "Coloca 10 dispensadores." };
                progressLine = english ? "§6Place dispensers..." : "§6Coloca dispensers...";
            }
            case QuestData.QUEST_DROP_GOODS -> {
                questName = english ? "Drop the Goods" : "Suelta la Mercancía";
                descLines = english ? new String[]{ "Place 10 droppers." } : new String[]{ "Coloca 10 soltadores." };
                progressLine = english ? "§6Place droppers..." : "§6Coloca droppers...";
            }
            case QuestData.QUEST_EYES_WALLS -> {
                questName = english ? "Eyes on the Walls" : "Ojos en las Paredes";
                descLines = english ? new String[]{ "Place 10 observers." } : new String[]{ "Coloca 10 observadores." };
                progressLine = english ? "§6Place observers..." : "§6Coloca observers...";
            }
            case QuestData.QUEST_MANUAL_CTRL -> {
                questName = english ? "Manual Control" : "Control Manual";
                descLines = english ? new String[]{ "Place 20 levers." } : new String[]{ "Coloca 20 palancas." };
                progressLine = english ? "§6Place levers..." : "§6Coloca palancas...";
            }
            case QuestData.QUEST_DONT_PRESS -> {
                questName = english ? "Don't Touch That Button" : "No Lo Presiones";
                descLines = english ? new String[]{ "Place 20 buttons." } : new String[]{ "Coloca 20 botones." };
                progressLine = english ? "§6Place buttons..." : "§6Coloca botones...";
            }
            case QuestData.QUEST_UNDER_FEET -> {
                questName = english ? "Under Your Feet" : "Bajo tus Pies";
                descLines = english ? new String[]{ "Place 20 pressure plates." } : new String[]{ "Coloca 20 placas de presión." };
                progressLine = english ? "§6Place pressure plates..." : "§6Coloca pressure plates...";
            }
            case QuestData.QUEST_INDUSTRIAL_SAFETY -> {
                questName = english ? "Industrial Safety" : "Seguridad Industrial";
                descLines = english ? new String[]{ "Place 20 iron trapdoors." } : new String[]{ "Coloca 20 trampillas de hierro." };
                progressLine = english ? "§6Place iron trapdoors..." : "§6Coloca iron trapdoors...";
            }
            case QuestData.QUEST_PERPETUAL_MOTION -> {
                questName = english ? "Perpetual Motion" : "Movimiento Perpetuo";
                descLines = english ? new String[]{ "Activate pistons 100 times." } : new String[]{ "Activa pistones 100 veces." };
                progressLine = english ? "§6Activate pistons..." : "§6Activa pistones...";
            }
            case QuestData.QUEST_SMART_DIST -> {
                questName = english ? "Smart Distribution" : "Distribución Inteligente";
                descLines = english ? new String[]{ "Build a hopper chain." } : new String[]{ "Construye una cadena de tolvas." };
                progressLine = english ? "§6Connect hoppers..." : "§6Conecta tolvas...";
            }
            case QuestData.QUEST_TIC_TAC -> {
                questName = english ? "Tic-Tock" : "Tic Tac";
                descLines = english ? new String[]{ "Build a redstone clock." } : new String[]{ "Construye un reloj de redstone." };
                progressLine = english ? "§6Connect observers..." : "§6Conecta observers...";
            }
            case QuestData.QUEST_END_HARD_WORK -> {
                questName = english ? "The End of Hard Work" : "Fin del Trabajo Duro";
                descLines = english ? new String[]{ "Build an automatic farm." } : new String[]{ "Construye una granja automática." };
                progressLine = english ? "§6Observer + piston + hopper..." : "§6Observer + pistón + tolva...";
            }
            default -> { return; }
        }

        boolean hasBar = questId.equals(QuestData.QUEST_HONEST_WORK) || questId.equals(QuestData.QUEST_STAY_WARM);

        // Calcular ancho dinámico según el texto más ancho de todas las líneas
        String pinnedHeader = english ? "⚑ Pinned Quest" : "⚑ Quest Fijada";
        int maxTextW = client.font.width(pinnedHeader);
        maxTextW = Math.max(maxTextW, client.font.width(questName));
        for (String desc : descLines) maxTextW = Math.max(maxTextW, client.font.width(desc));
        maxTextW = Math.max(maxTextW, client.font.width(progressLine));
        int pw = maxTextW + 12; // margen horizontal

        int ph = 18 + 10 + (descLines.length * 10) + 6 + 10 + (hasBar ? 12 : 0);
        int sw = client.getWindow().getGuiScaledWidth();
        int px = sw - pw - 6, py = 10;

        graphics.fill(px-2, py-2, px+pw+2, py+ph+2, 0xBB000000);
        graphics.fill(px, py, px+pw, py+ph, 0x99111111);
        graphics.fill(px, py, px+pw, py+1, 0xFFFFAA00);

        int ty = py + 4;
        graphics.text(client.font, Component.literal("§6§l" + pinnedHeader),
            px+4, ty, ARGB.opaque(0xFFFF00), true);
        ty += 14;

        graphics.text(client.font, Component.literal("§f§l" + questName),
            px+4, ty, ARGB.opaque(0xFFFFFF), false);
        ty += 11;

        for (String desc : descLines) {
            graphics.text(client.font, Component.literal("§7" + desc),
                px+4, ty, ARGB.opaque(0xAAAAAA), false);
            ty += 10;
        }
        ty += 4;

        graphics.text(client.font, Component.literal(progressLine),
            px+4, ty, ARGB.opaque(0xFFFFFF), false);
        ty += 10;

        if (hasBar) {
            int current = questId.equals(QuestData.QUEST_STAY_WARM)
                ? QuestData.get().getCoalCount(uuid)
                : QuestData.get().getBlocksPlaced(uuid);
            int barW = pw - 8;
            int filled = (barW * Math.min(current, 10)) / 10;
            graphics.fill(px+4, ty, px+4+barW, ty+8, 0xFF333333);
            graphics.fill(px+4, ty, px+4+filled, ty+8, current >= 10 ? 0xFF00DD00 : 0xFFFFAA00);
        }
    }

    private static void renderFirstHomeHud(GuiGraphicsExtractor graphics, Minecraft client, UUID uuid) {
        boolean done     = QuestData.get().isCompleted(uuid, QuestData.QUEST_FIRST_HOME);
        boolean english  = "en".equals(QuestData.get().getLanguage(uuid));

        // Si está completa, quitar el pin automáticamente y no renderizar
        if (done) {
            QuestData.get().unpinQuest(uuid);
            return;
        }

        boolean hasChest = QuestData.get().hasChestPlaced(uuid);

        BlockPos center = hasChest
            ? QuestData.get().getChestPos(uuid)
            : client.player.blockPosition();

        boolean hasBed = false, hasCrafting = false, hasFurnace = false;

        if (client.level != null) {
            int r = 8;
            outer:
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        var state = client.level.getBlockState(center.offset(x, y, z));
                        if (state.is(BlockTags.BEDS)) hasBed = true;
                        if (state.is(Blocks.CRAFTING_TABLE)) hasCrafting = true;
                        if (state.is(Blocks.FURNACE)) hasFurnace = true;
                        if (hasBed && hasCrafting && hasFurnace) break outer;
                    }
                }
            }
        }

        // Calcular ancho dinámico
        String pinnedHeader = english ? "⚑ Pinned Quest" : "⚑ Quest Fijada";
        String craftingLabel = english ? "Crafting Table" : "Mesa de Trabajo";
        int maxTextW = client.font.width(pinnedHeader);
        maxTextW = Math.max(maxTextW, client.font.width("✔ " + craftingLabel));
        int pw = maxTextW + 12;
        int ph = 90;
        int sw = client.getWindow().getGuiScaledWidth();
        int px = sw - pw - 6, py = 10;

        graphics.fill(px-2, py-2, px+pw+2, py+ph+2, 0xBB000000);
        graphics.fill(px, py, px+pw, py+ph, 0x99111111);
        graphics.fill(px, py, px+pw, py+1, 0xFFFFAA00);

        graphics.text(client.font, Component.literal("§6§l" + pinnedHeader),
            px+4, py+4, ARGB.opaque(0xFFFF00), true);

        graphics.text(client.font, Component.literal((hasChest   ?"§a✔":"§c✘")+" §f" + (english ? "Chest placed" : "Cofre colocado")),
            px+4, py+18, ARGB.opaque(0xFFFFFF), false);
        graphics.text(client.font, Component.literal((hasBed     ?"§a✔":"§c✘")+" §f" + (english ? "Bed" : "Cama")),
            px+4, py+30, ARGB.opaque(0xFFFFFF), false);
        graphics.text(client.font, Component.literal((hasCrafting?"§a✔":"§c✘")+" §f" + craftingLabel),
            px+4, py+42, ARGB.opaque(0xFFFFFF), false);
        graphics.text(client.font, Component.literal((hasFurnace ?"§a✔":"§c✘")+" §f" + (english ? "Furnace" : "Horno")),
            px+4, py+54, ARGB.opaque(0xFFFFFF), false);

        int total = (hasChest?1:0)+(hasBed?1:0)+(hasCrafting?1:0)+(hasFurnace?1:0);
        int barW  = pw-8;
        int filled = (barW*total)/4;
        graphics.fill(px+4, py+68, px+4+barW, py+76, 0xFF333333);
        graphics.fill(px+4, py+68, px+4+filled, py+76, total==4?0xFF00DD00:0xFFFFAA00);
    }
}
