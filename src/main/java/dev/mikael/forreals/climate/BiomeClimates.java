package dev.mikael.forreals.climate;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** The yearly climate each biome stands for. */
final class BiomeClimates {
	/** Biomes whose vanilla temperature says little about how warm they are. Open water also swings less than land. */
	private static final Map<ResourceKey<Biome>, BiomeClimate> KNOWN = Map.ofEntries(
		Map.entry(Biomes.WARM_OCEAN, new BiomeClimate(26, 2, 2, 0.8)),
		Map.entry(Biomes.LUKEWARM_OCEAN, new BiomeClimate(21, 4, 2, 0.7)),
		Map.entry(Biomes.DEEP_LUKEWARM_OCEAN, new BiomeClimate(21, 4, 2, 0.7)),
		Map.entry(Biomes.OCEAN, new BiomeClimate(12, 6, 2, 0.6)),
		Map.entry(Biomes.DEEP_OCEAN, new BiomeClimate(12, 6, 2, 0.6)),
		Map.entry(Biomes.COLD_OCEAN, new BiomeClimate(5, 6, 2, 0.6)),
		Map.entry(Biomes.DEEP_COLD_OCEAN, new BiomeClimate(5, 6, 2, 0.6)),
		Map.entry(Biomes.FROZEN_OCEAN, new BiomeClimate(-6, 8, 3, 0.5)),
		Map.entry(Biomes.DEEP_FROZEN_OCEAN, new BiomeClimate(-6, 8, 3, 0.5)),
		Map.entry(Biomes.MANGROVE_SWAMP, new BiomeClimate(25, 3, 3, 0.9)),
		Map.entry(Biomes.MUSHROOM_FIELDS, new BiomeClimate(16, 5, 3, 0.8))
	);
	/** Biomes that follow the land around them and have no climate of their own. */
	private static final Set<ResourceKey<Biome>> BORROWED = Set.of(
		Biomes.STONY_SHORE, Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK, Biomes.SULFUR_CAVES
	);

	private static final Map<Biome, BiomeClimate> BY_BIOME = new IdentityHashMap<>();

	private BiomeClimates() {
	}

	/** Empty for a biome that borrows its climate from its neighbors. */
	static Optional<BiomeClimate> ownClimate(final Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH) || biome.unwrapKey().filter(BORROWED::contains).isPresent()) {
			return Optional.empty();
		}

		return Optional.of(fromBiome(biome));
	}

	static BiomeClimate fromBiome(final Holder<Biome> biome) {
		return BY_BIOME.computeIfAbsent(biome.value(), value -> biome.unwrapKey().map(KNOWN::get).orElseGet(
			() -> BiomeClimate.fromVanilla(value.getBaseTemperature(), value.climateSettings.downfall())
		));
	}
}
