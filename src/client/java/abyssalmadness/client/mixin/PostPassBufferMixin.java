package abyssalmadness.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Vanilla creates post-effect uniform buffers read-only: the values come from JSON
// once and never change. We write to them every frame, so the write flag has to be
// added at creation - otherwise mapBuffer rightly answers "Buffer is not writable".
@Mixin(PostPass.class)
public class PostPassBufferMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"
            ),
            index = 1
    )
    private int abyssalmadness$makeWritable(int usage) {
        return usage | GpuBuffer.USAGE_MAP_WRITE;
    }
}
