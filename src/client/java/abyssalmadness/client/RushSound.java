package abyssalmadness.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Cleansing, heard rather than seen. Two shapes of the same class: the travelling
 * one moves its source every tick to wherever the sweep has got to, so it crosses the
 * ears; the other is pinned to the listener with attenuation off, so it has no
 * direction and just swells as the thing closes in.
 */
public class RushSound extends AbstractTickableSoundInstance {
    private final boolean inHead;

    private RushSound(SoundEvent event, boolean inHead) {
        // MASTER, not HOSTILE: a set piece should not vanish to a category slider.
        super(event, SoundSource.MASTER, RandomSource.create());
        this.inHead = inHead;
        this.looping = false;
        this.delay = 0;
        this.pitch = 1.0F;
        this.volume = 1.0F;

        if (inHead) {
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        }
    }

    public static RushSound travelling(SoundEvent event) {
        RushSound sound = new RushSound(event, false);
        sound.follow();
        return sound;
    }

    public static RushSound inHead(SoundEvent event) {
        return new RushSound(event, true);
    }

    /**
     * Without this the travelling one never starts: it is set off 160 blocks away, its
     * volume works out to zero, and the engine drops a sound that starts silent.
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        // Never stopped from here: the sweep finishes travelling long before the file
        // has finished playing, and killing them cut both off mid-word.
        if (!CleansingClientState.running()) {
            return;
        }
        if (inHead) {
            // Never all the way to nothing, or it cuts out as the distance jitters.
            this.volume = 0.35F + 0.65F * CleansingClientState.raw();
        } else {
            follow();
        }
    }

    private void follow() {
        Vec3 at = CleansingClientState.position();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }
}
