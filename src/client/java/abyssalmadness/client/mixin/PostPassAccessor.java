package abyssalmadness.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Uniform values in a post-effect JSON are read once when the pass is built. The
// buffers they were written into are still live, so we take them and rewrite the
// contents every frame instead.
@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> abyssalmadness$getCustomUniforms();
}
