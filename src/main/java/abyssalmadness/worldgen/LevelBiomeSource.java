package abyssalmadness.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

// Picks the biome from horizontal distance to spawn using the Chebyshev metric
// (max(|x|,|z|)), so each ring out is a different biome with square boundaries.
// Rings are defined in the world_preset JSON.
public class LevelBiomeSource extends BiomeSource {
    public static final MapCodec<LevelBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            LevelEntry.CODEC.listOf().fieldOf("levels").forGetter(s -> s.levels),
            Biome.CODEC.fieldOf("fallback").forGetter(s -> s.fallback)
    ).apply(instance, LevelBiomeSource::new));

    private final List<LevelEntry> levels;
    private final Holder<Biome> fallback;

    public LevelBiomeSource(List<LevelEntry> levels, Holder<Biome> fallback) {
        this.levels = levels;
        this.fallback = fallback;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(levels.stream().map(LevelEntry::biome), Stream.of(fallback));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        long bx = QuartPos.toBlock(qx);
        long bz = QuartPos.toBlock(qz);
        long distance = Math.max(Math.abs(bx), Math.abs(bz));
        for (LevelEntry entry : levels) {
            if (distance <= entry.maxDistance()) {
                return entry.biome();
            }
        }
        return fallback;
    }

    public record LevelEntry(int maxDistance, Holder<Biome> biome) {
        public static final Codec<LevelEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("max_distance").forGetter(LevelEntry::maxDistance),
                Biome.CODEC.fieldOf("biome").forGetter(LevelEntry::biome)
        ).apply(instance, LevelEntry::new));
    }
}
