package abyssalmadness.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The camera keeps its position to itself, and the infection pass needs it to place
// the pattern relative to the viewer.
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("position")
    Vec3 abyssalmadness$getPosition();
}
