package abyssalmadness.client;

import abyssalmadness.sound.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.phys.Vec3;

// Client-side mirror of how close the Cleansing is, 0..1. Packets arrive once a tick
// and the value is eased here, on the client tick, so the shake and the post effect
// ramp instead of stepping. The easing must live somewhere that always runs: hanging
// it off a renderer means it silently stops the moment that renderer goes away.
public final class CleansingClientState {
    private static volatile float target;
    private static volatile boolean running;
    private static volatile Vec3 position = Vec3.ZERO;
    private static float shown;
    private static boolean soundsStarted;

    private CleansingClientState() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void set(boolean isRunning, float value, Vec3 at) {
        running = isRunning;
        // Only while it is actually moving. The end-of-run packet carries zeroes, and
        // writing those in would fling a still-playing pass off to the world origin.
        if (isRunning) {
            position = at;
        }
        target = Math.max(0.0F, Math.min(1.0F, value));

        if (!isRunning) {
            soundsStarted = false;
            return;
        }
        // Both together, on the same tick, and it has to stay that way: the two files
        // are cut against each other with their own lead-in, so anything that staggers
        // them here pulls the mix apart.
        if (!soundsStarted) {
            soundsStarted = true;
            SoundManager sounds = Minecraft.getInstance().getSoundManager();
            sounds.play(RushSound.inHead(ModSounds.RUSH_AMBIENT));
            sounds.play(RushSound.travelling(ModSounds.RUSH));
        }
    }

    public static boolean running() {
        return running;
    }

    /** Where the sweep is right now, in world coordinates. */
    public static Vec3 position() {
        return position;
    }

    private static void tick() {
        float t = target;
        // rise quickly, fall away slowly, so the wave feels like it lingers
        float rate = t > shown ? 0.25F : 0.06F;
        shown += (t - shown) * rate;
        if (Math.abs(t - shown) < 0.002F) {
            shown = t;
        }
    }

    /** Current eased intensity, 0..1. */
    public static float raw() {
        return shown;
    }

    public static void clear() {
        target = 0.0F;
        shown = 0.0F;
        running = false;
        soundsStarted = false;
    }
}
