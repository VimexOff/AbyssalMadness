package abyssalmadness;

import abyssalmadness.ambient.PhantomSounds;
import abyssalmadness.atmosphere.AtmosphereController;
import abyssalmadness.beacon.LureBeacon;
import abyssalmadness.blackout.Blackout;
import abyssalmadness.cleansing.Cleansing;
import abyssalmadness.command.ModCommands;
import abyssalmadness.event.CrystalGuard;
import abyssalmadness.event.GodsLandsRules;
import abyssalmadness.hollow.Hollows;
import abyssalmadness.hollow.ModEntities;
import abyssalmadness.infection.InfectionState;
import abyssalmadness.network.BeaconPayload;
import abyssalmadness.network.BlackoutPayload;
import abyssalmadness.network.CleansingPayload;
import abyssalmadness.network.DecayPayload;
import abyssalmadness.network.InfectionPayload;
import abyssalmadness.network.StarStatePayload;
import abyssalmadness.sound.ModSounds;
import abyssalmadness.star.StarCycle;
import abyssalmadness.teleport.FogTeleport;
import abyssalmadness.worldgen.ModWorldgen;
import abyssalmadness.worldgen.feature.ModFeatures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbyssalMadness implements ModInitializer {
    public static final String MOD_ID = "abyssalmadness";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // register content and worldgen types before any datapack is parsed
        ModFeatures.register();
        ModEntities.register();
        ModSounds.register();
        ModWorldgen.register();
        PayloadTypeRegistry.playS2C().register(StarStatePayload.TYPE, StarStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(InfectionPayload.TYPE, InfectionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CleansingPayload.TYPE, CleansingPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BeaconPayload.TYPE, BeaconPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BlackoutPayload.TYPE, BlackoutPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DecayPayload.TYPE, DecayPayload.STREAM_CODEC);
        InfectionState.register();
        CrystalGuard.register();
        GodsLandsRules.register();
        StarCycle.register();
        AtmosphereController.register();
        PhantomSounds.register();
        FogTeleport.register();
        Cleansing.register();
        LureBeacon.register();
        Blackout.register();
        Hollows.register();
        ModCommands.register();

        LOGGER.info("AbyssalMadness initialized: Запределье worldgen registered.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
