package com.homequest.sound;

import com.homequest.HomeQuestMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {

    public static SoundEvent PAGE_FLIP;
    public static SoundEvent CLAIM_REWARD;

    public static void initialize() {
        Identifier id = Identifier.fromNamespaceAndPath(HomeQuestMod.MOD_ID, "page_flip");
        ResourceKey<SoundEvent> key = ResourceKey.create(Registries.SOUND_EVENT, id);
        PAGE_FLIP = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            key,
            SoundEvent.createVariableRangeEvent(id)
        );

        Identifier claimId = Identifier.fromNamespaceAndPath(HomeQuestMod.MOD_ID, "claim_reward");
        ResourceKey<SoundEvent> claimKey = ResourceKey.create(Registries.SOUND_EVENT, claimId);
        CLAIM_REWARD = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            claimKey,
            SoundEvent.createVariableRangeEvent(claimId)
        );

        HomeQuestMod.LOGGER.info("[Enhanced-vq] Sonido page_flip registrado.");
        HomeQuestMod.LOGGER.info("[Enhanced-vq] Sonido claim_reward registrado.");
    }
}
