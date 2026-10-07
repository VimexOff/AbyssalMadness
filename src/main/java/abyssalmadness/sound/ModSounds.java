package abyssalmadness.sound;

import abyssalmadness.AbyssalMadness;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    /** The thing itself going past. Positional, and it has to move. */
    public static SoundEvent RUSH;
    /** Dread in the ears. Never given a position. */
    public static SoundEvent RUSH_AMBIENT;

    private ModSounds() {
    }

    public static void register() {
        RUSH = register("abyss_rush");
        RUSH_AMBIENT = register("abyss_rush_ambient");
    }

    private static SoundEvent register(String name) {
        Identifier id = AbyssalMadness.id(name);
        // Variable range, not fixed: the audible distance then scales with the volume
        // it is played at, which is the only way to make one of these carry across the
        // desert instead of dying at the usual sixteen blocks.
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
