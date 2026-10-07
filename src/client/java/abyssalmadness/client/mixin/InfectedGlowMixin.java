package abyssalmadness.client.mixin;

import abyssalmadness.client.InfectionClientState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Infected players count as glowing, which is what gets them drawn into the entity
// outline buffer. That buffer is the silhouette mask the infection shader cuts by,
// so this is not cosmetic: without it the mask is empty and nothing is painted.
//
// The vanilla outline that would normally be blitted over the frame is suppressed in
// LevelRendererMixin.
@Mixin(Entity.class)
public abstract class InfectedGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void abyssalmadness$glowWhenInfected(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        // Not restricted to players: the hollows are mobs carrying the same level, and
        // they need the outline just as much or the shader has nothing to cut by.
        Entity self = (Entity) (Object) this;
        if (InfectionClientState.get(self.getUUID()) > 0) {
            cir.setReturnValue(true);
        }
    }
}
