package abyssalmadness.mixin;

import abyssalmadness.worldgen.AbyssTerrain;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Feeds the world seed to AbyssTerrain so its value noise differs per world.
@Mixin(RandomState.class)
public abstract class RandomStateMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void abyssalmadness$captureWorldSeed(
            NoiseGeneratorSettings noiseGeneratorSettings,
            HolderGetter<NormalNoise.NoiseParameters> holderGetter,
            long seed,
            CallbackInfo ci
    ) {
        AbyssTerrain.setWorldSeed(seed);
    }
}
