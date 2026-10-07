package abyssalmadness.client;

import abyssalmadness.AbyssalMadness;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

// The white-out for the first-person arm.
//
// The world pass cannot reach the held arm: it is drawn with its own projection, so
// reconstructing a world position from its depth gives nonsense. The arm therefore
// gets a plain overlay instead - flat white, no pattern, faded in by how far the
// infection has climbed. Smooth, so it matches the shader rather than looking like
// the old speckled skin.
public final class InfectionArm {
    private static final int SIZE = 64;
    private static final int STEPS = 20;

    // The arm and sleeve patches of the skin layout, and the body height each row
    // sits at (0 = feet, 1 = crown). Only the arms matter in first person.
    private static final double[][] REGIONS = {
            {40, 16, 56, 32, 0.36, 0.78},   // right arm
            {40, 32, 56, 48, 0.36, 0.78},   // right sleeve
            {32, 48, 48, 64, 0.36, 0.78},   // left arm
            {48, 48, 64, 64, 0.36, 0.78},   // left sleeve
    };

    private static final Map<Integer, Identifier> CACHE = new HashMap<>();

    /** Slightly inflated, so the overlay sits over the skin instead of z-fighting it. */
    private static final float INFLATE = 0.15F;
    private static final PlayerModel[] MODELS = new PlayerModel[2];

    private InfectionArm() {
    }

    /** The inflated model the overlay is drawn on. Baked once per skin shape. */
    public static PlayerModel model(boolean slim) {
        int i = slim ? 1 : 0;
        if (MODELS[i] == null) {
            ModelPart root = LayerDefinition
                    .create(PlayerModel.createMesh(new CubeDeformation(INFLATE), slim), 64, 64)
                    .bakeRoot();
            MODELS[i] = new PlayerModel(root, slim);
        }
        return MODELS[i];
    }

    /** A white overlay for this infection level, or null when there is nothing to draw. */
    public static Identifier get(int percent) {
        if (percent <= 0) {
            return null;
        }
        int step = Math.max(1, Math.min(STEPS, Math.round(percent * STEPS / 100.0F)));
        Identifier cached = CACHE.get(step);
        if (cached != null) {
            return cached;
        }
        Identifier id = build(step);
        CACHE.put(step, id);
        return id;
    }

    private static Identifier build(int step) {
        double fill = step / (double) STEPS;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                image.setPixel(u, v, pixel(u, v, fill));
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "abyssalmadness_infection_arm", image);
        texture.upload();   // otherwise the GPU keeps an empty texture and draws white
        Identifier id = AbyssalMadness.id("infection_arm_" + step);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    private static int pixel(int u, int v, double fill) {
        for (double[] r : REGIONS) {
            if (u < r[0] || u >= r[2] || v < r[1] || v >= r[3]) {
                continue;
            }
            double f = (v - r[1]) / (r[3] - r[1]);      // 0 at region top, 1 at bottom
            double height = r[5] + (r[4] - r[5]) * f;
            // Soft edge, so the front is a gradient rather than a hard line.
            double t = (fill * 1.05 - height) / 0.10;
            int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * 255.0);
            return (alpha << 24) | 0x00FFFFFF;
        }
        return 0;
    }
}
