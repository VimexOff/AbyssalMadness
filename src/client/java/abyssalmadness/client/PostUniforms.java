package abyssalmadness.client;

import abyssalmadness.client.mixin.PostChainAccessor;
import abyssalmadness.client.mixin.PostPassAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.joml.Matrix4f;

import java.util.function.Consumer;

// Writes live values into a post effect's uniform buffer. Vanilla reads the values in
// a post-effect JSON once, when the pass is built, but the buffer is still there - so
// find it and overwrite it each frame. PostPassBufferMixin adds the write flag.
public final class PostUniforms {
    private PostUniforms() {
    }

    /**
     * Finds the named uniform block and hands it over to be filled.
     *
     * @return false when the block is missing, in which case running the pass is
     *         pointless
     */
    public static boolean write(PostChain chain, String block, Consumer<Std140Builder> filler) {
        GpuBuffer buffer = findBuffer(chain, block);
        if (buffer == null) {
            return false;
        }
        try (GpuBuffer.MappedView view =
                     RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, false, true)) {
            filler.accept(Std140Builder.intoBuffer(view.data()));
        }
        return true;
    }

    /**
     * Puts a matrix in as four vectors: a post-effect JSON has no mat4 type, so the
     * shader rebuilds it as {@code mat4(c0, c1, c2, c3)}.
     */
    public static void putMatrix(Std140Builder builder, Matrix4f m) {
        builder.putVec4(m.m00(), m.m01(), m.m02(), m.m03())
                .putVec4(m.m10(), m.m11(), m.m12(), m.m13())
                .putVec4(m.m20(), m.m21(), m.m22(), m.m23())
                .putVec4(m.m30(), m.m31(), m.m32(), m.m33());
    }

    /** Clip space back to camera-relative coordinates. */
    public static Matrix4f inverseViewProjection(Matrix4f modelView, Matrix4f projection) {
        return new Matrix4f(projection).mul(modelView).invert();
    }

    private static GpuBuffer findBuffer(PostChain chain, String block) {
        for (PostPass pass : ((PostChainAccessor) chain).abyssalmadness$getPasses()) {
            GpuBuffer buffer = ((PostPassAccessor) pass).abyssalmadness$getCustomUniforms().get(block);
            if (buffer != null) {
                return buffer;
            }
        }
        return null;
    }
}
