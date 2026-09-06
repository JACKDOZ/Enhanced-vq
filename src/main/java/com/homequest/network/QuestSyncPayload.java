package com.homequest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record QuestSyncPayload(
    List<String> completedQuests,
    List<String> claimedRewards,
    boolean questsStarted,
    boolean hasChestPlaced,
    String language,
    boolean languageChosen,
    int coalCount,
    int blocksPlaced,
    boolean visitedNether,
    boolean visitedEnd,
    boolean killedDragon,
    boolean summonedWither,
    boolean foundStronghold,
    boolean foundStrongholdRuins,
    boolean foundVillage,
    boolean foundDesertTemple,
    boolean foundJungleTemple,
    boolean foundIgloo,
    boolean foundShipwreck,
    boolean foundOceanRuins,
    boolean foundMonument,
    boolean foundOutpost,
    boolean foundMansion,
    boolean foundNetherFortress,
    boolean foundBastion,
    boolean foundEndCity,
    boolean foundAncientCity,
    boolean foundTrialChamber,
    int coalMined,
    int ironMined,
    int copperMined,
    int goldMined,
    int redstoneMined,
    int lapisMined,
    int diamondMined,
    int ancientDebrisMined,
    int emeraldMined,
    // CONSTRUCTOR
    int stairsPlaced,
    int slabsPlaced,
    int trapdoorsPlaced,
    int doorsPlaced,
    int glassPlaced,
    int fencesPlaced,
    int lanternsPlaced,
    int seaLanternsPlaced,
    int decorativePlaced,
    int chiseledPlaced,
    int brickPlaced,
    int quartzPlaced,
    int concretePlaced,
    int honeyPlaced,
    boolean placedBell,
    boolean placedBeacon,
    // TÉCNICO
    boolean craftedPiston,
    boolean craftedStickyPiston,
    boolean craftedRepeater,
    boolean craftedComparator,
    int pistonsPlaced,
    int stickyPistonsPlaced,
    int repeatersPlaced,
    int comparatorsPlaced,
    int hoppersPlaced,
    int dispensersPlaced,
    int droppersPlaced,
    int observersPlaced,
    int leversPlaced,
    int buttonsPlaced,
    int pressurePlatesPlaced,
    int ironTrapdoorsPlaced,
    int pistonActivations,
    boolean builtHopperChain,
    boolean builtRedstoneClock,
    boolean builtAutoFarm,
    // BESTIARIO: kills por mob, listas paralelas (clave <-> cantidad) en vez de un
    // Map para no depender de un codec extra — mismo patrón que completedQuests.
    List<String> mobKillKeys,
    List<Integer> mobKillCounts,
    boolean tutorialSeen
) implements CustomPacketPayload {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("homequest", "quest_sync");

    public static final Type<QuestSyncPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, QuestSyncPayload> CODEC =
        StreamCodec.of(QuestSyncPayload::encode, QuestSyncPayload::decode);

    private static void encode(FriendlyByteBuf buf, QuestSyncPayload p) {
        buf.writeCollection(p.completedQuests(), FriendlyByteBuf::writeUtf);
        buf.writeCollection(p.claimedRewards(),  FriendlyByteBuf::writeUtf);
        buf.writeBoolean(p.questsStarted());
        buf.writeBoolean(p.hasChestPlaced());
        buf.writeUtf(p.language());
        buf.writeBoolean(p.languageChosen());
        buf.writeVarInt(p.coalCount());
        buf.writeVarInt(p.blocksPlaced());
        buf.writeBoolean(p.visitedNether());
        buf.writeBoolean(p.visitedEnd());
        buf.writeBoolean(p.killedDragon());
        buf.writeBoolean(p.summonedWither());
        buf.writeBoolean(p.foundStronghold());
        buf.writeBoolean(p.foundStrongholdRuins());
        buf.writeBoolean(p.foundVillage());
        buf.writeBoolean(p.foundDesertTemple());
        buf.writeBoolean(p.foundJungleTemple());
        buf.writeBoolean(p.foundIgloo());
        buf.writeBoolean(p.foundShipwreck());
        buf.writeBoolean(p.foundOceanRuins());
        buf.writeBoolean(p.foundMonument());
        buf.writeBoolean(p.foundOutpost());
        buf.writeBoolean(p.foundMansion());
        buf.writeBoolean(p.foundNetherFortress());
        buf.writeBoolean(p.foundBastion());
        buf.writeBoolean(p.foundEndCity());
        buf.writeBoolean(p.foundAncientCity());
        buf.writeBoolean(p.foundTrialChamber());
        buf.writeVarInt(p.coalMined());
        buf.writeVarInt(p.ironMined());
        buf.writeVarInt(p.copperMined());
        buf.writeVarInt(p.goldMined());
        buf.writeVarInt(p.redstoneMined());
        buf.writeVarInt(p.lapisMined());
        buf.writeVarInt(p.diamondMined());
        buf.writeVarInt(p.ancientDebrisMined());
        buf.writeVarInt(p.emeraldMined());
        // CONSTRUCTOR
        buf.writeVarInt(p.stairsPlaced());
        buf.writeVarInt(p.slabsPlaced());
        buf.writeVarInt(p.trapdoorsPlaced());
        buf.writeVarInt(p.doorsPlaced());
        buf.writeVarInt(p.glassPlaced());
        buf.writeVarInt(p.fencesPlaced());
        buf.writeVarInt(p.lanternsPlaced());
        buf.writeVarInt(p.seaLanternsPlaced());
        buf.writeVarInt(p.decorativePlaced());
        buf.writeVarInt(p.chiseledPlaced());
        buf.writeVarInt(p.brickPlaced());
        buf.writeVarInt(p.quartzPlaced());
        buf.writeVarInt(p.concretePlaced());
        buf.writeVarInt(p.honeyPlaced());
        buf.writeBoolean(p.placedBell());
        buf.writeBoolean(p.placedBeacon());
        // TÉCNICO
        buf.writeBoolean(p.craftedPiston());
        buf.writeBoolean(p.craftedStickyPiston());
        buf.writeBoolean(p.craftedRepeater());
        buf.writeBoolean(p.craftedComparator());
        buf.writeVarInt(p.pistonsPlaced());
        buf.writeVarInt(p.stickyPistonsPlaced());
        buf.writeVarInt(p.repeatersPlaced());
        buf.writeVarInt(p.comparatorsPlaced());
        buf.writeVarInt(p.hoppersPlaced());
        buf.writeVarInt(p.dispensersPlaced());
        buf.writeVarInt(p.droppersPlaced());
        buf.writeVarInt(p.observersPlaced());
        buf.writeVarInt(p.leversPlaced());
        buf.writeVarInt(p.buttonsPlaced());
        buf.writeVarInt(p.pressurePlatesPlaced());
        buf.writeVarInt(p.ironTrapdoorsPlaced());
        buf.writeVarInt(p.pistonActivations());
        buf.writeBoolean(p.builtHopperChain());
        buf.writeBoolean(p.builtRedstoneClock());
        buf.writeBoolean(p.builtAutoFarm());
        buf.writeCollection(p.mobKillKeys(),   FriendlyByteBuf::writeUtf);
        buf.writeCollection(p.mobKillCounts(), FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(p.tutorialSeen());
    }

    private static QuestSyncPayload decode(FriendlyByteBuf buf) {
        List<String> completed = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        List<String> claimed   = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        boolean started  = buf.readBoolean();
        boolean hasChest = buf.readBoolean();
        String lang = buf.readUtf();
        boolean langChosen = buf.readBoolean();
        int coal   = buf.readVarInt();
        int blocks = buf.readVarInt();
        boolean visitedNether = buf.readBoolean();
        boolean visitedEnd    = buf.readBoolean();
        boolean killedDragon  = buf.readBoolean();
        boolean summonedWither = buf.readBoolean();
        boolean foundStronghold = buf.readBoolean();
        boolean foundStrongholdRuins = buf.readBoolean();
        boolean foundVillage = buf.readBoolean();
        boolean foundDesertTemple = buf.readBoolean();
        boolean foundJungleTemple = buf.readBoolean();
        boolean foundIgloo = buf.readBoolean();
        boolean foundShipwreck = buf.readBoolean();
        boolean foundOceanRuins = buf.readBoolean();
        boolean foundMonument = buf.readBoolean();
        boolean foundOutpost = buf.readBoolean();
        boolean foundMansion = buf.readBoolean();
        boolean foundNetherFortress = buf.readBoolean();
        boolean foundBastion = buf.readBoolean();
        boolean foundEndCity = buf.readBoolean();
        boolean foundAncientCity = buf.readBoolean();
        boolean foundTrialChamber = buf.readBoolean();
        int coalMined = buf.readVarInt();
        int ironMined = buf.readVarInt();
        int copperMined = buf.readVarInt();
        int goldMined = buf.readVarInt();
        int redstoneMined = buf.readVarInt();
        int lapisMined = buf.readVarInt();
        int diamondMined = buf.readVarInt();
        int ancientDebrisMined = buf.readVarInt();
        int emeraldMined = buf.readVarInt();
        // CONSTRUCTOR
        int stairsPlaced    = buf.readVarInt();
        int slabsPlaced     = buf.readVarInt();
        int trapdoorsPlaced = buf.readVarInt();
        int doorsPlaced     = buf.readVarInt();
        int glassPlaced     = buf.readVarInt();
        int fencesPlaced    = buf.readVarInt();
        int lanternsPlaced  = buf.readVarInt();
        int seaLanternsPlaced = buf.readVarInt();
        int decorativePlaced = buf.readVarInt();
        int chiseledPlaced  = buf.readVarInt();
        int brickPlaced     = buf.readVarInt();
        int quartzPlaced    = buf.readVarInt();
        int concretePlaced  = buf.readVarInt();
        int honeyPlaced     = buf.readVarInt();
        boolean placedBell   = buf.readBoolean();
        boolean placedBeacon = buf.readBoolean();
        // TÉCNICO
        boolean craftedPiston       = buf.readBoolean();
        boolean craftedStickyPiston = buf.readBoolean();
        boolean craftedRepeater     = buf.readBoolean();
        boolean craftedComparator   = buf.readBoolean();
        int pistonsPlaced           = buf.readVarInt();
        int stickyPistonsPlaced     = buf.readVarInt();
        int repeatersPlaced         = buf.readVarInt();
        int comparatorsPlaced       = buf.readVarInt();
        int hoppersPlaced           = buf.readVarInt();
        int dispensersPlaced        = buf.readVarInt();
        int droppersPlaced          = buf.readVarInt();
        int observersPlaced         = buf.readVarInt();
        int leversPlaced            = buf.readVarInt();
        int buttonsPlaced           = buf.readVarInt();
        int pressurePlatesPlaced    = buf.readVarInt();
        int ironTrapdoorsPlaced     = buf.readVarInt();
        int pistonActivations       = buf.readVarInt();
        boolean builtHopperChain    = buf.readBoolean();
        boolean builtRedstoneClock  = buf.readBoolean();
        boolean builtAutoFarm       = buf.readBoolean();
        List<String> mobKillKeys    = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        List<Integer> mobKillCounts = buf.readCollection(ArrayList::new, FriendlyByteBuf::readVarInt);
        boolean tutorialSeen        = buf.readBoolean();
        return new QuestSyncPayload(completed, claimed, started, hasChest, lang, langChosen, coal, blocks,
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
            mobKillKeys, mobKillCounts, tutorialSeen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
