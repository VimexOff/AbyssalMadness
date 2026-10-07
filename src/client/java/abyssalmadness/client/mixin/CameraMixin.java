package abyssalmadness.client.mixin;

import abyssalmadness.client.CleansingClientState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Shakes the view while the Cleansing is near. Applied after vanilla has set the
// camera up, so it rides on top of the player's own aim instead of fighting it.
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("RETURN"))
    private void abyssalmadness$cleansingShake(Level level, Entity entity, boolean bl,
                                               boolean bl2, float partialTick, CallbackInfo ci) {
        float intensity = CleansingClientState.raw();
        if (intensity <= 0.01F) {
            return;
        }
        // Two detuned waves per axis, so the shake never settles into a rhythm.
        double t = System.currentTimeMillis() / 1000.0;
        float amount = intensity * intensity * 3.2F; // degrees at its worst
        float yaw = (float) (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 61.0) * 0.4) * amount;
        float pitch = (float) (Math.sin(t * 43.0) * 0.6 + Math.sin(t * 71.0) * 0.4) * amount;
        setRotation(this.yRot + yaw, this.xRot + pitch);
    }
}
