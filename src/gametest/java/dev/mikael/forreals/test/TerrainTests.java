package dev.mikael.forreals.test;

import dev.mikael.forreals.terrain.NewBiomes;
import dev.mikael.forreals.terrain.Vegetation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;

public class TerrainTests {
	private static final List<ResourceKey<Biome>> NEW_BIOMES = List.of(
		NewBiomes.STEPPE,
		NewBiomes.SCRUBLAND,
		NewBiomes.PRAIRIE,
		NewBiomes.MARSH,
		NewBiomes.MIXED_FOREST,
		NewBiomes.DRY_WOODLAND,
		NewBiomes.TEMPERATE_RAINFOREST,
		NewBiomes.TUNDRA
	);

	@GameTest
	public void theWorldGeneratesTheNewBiomesAndKeepsTheOldOnes(final GameTestHelper helper) {
		// A test world is not built from the overworld's biome layout, so the layout itself is read.
		Set<ResourceKey<Biome>> generated = helper.getLevel()
			.registryAccess()
			.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
			.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
			.value()
			.parameters()
			.values()
			.stream()
			.flatMap(placement -> placement.getSecond().unwrapKey().stream())
			.collect(Collectors.toSet());
		for (ResourceKey<Biome> biome : NEW_BIOMES) {
			helper.assertTrue(generated.contains(biome), biome.identifier() + " is not part of world generation");
		}

		for (ResourceKey<Biome> biome : List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.SAVANNA, Biomes.SNOWY_PLAINS, Biomes.SWAMP)) {
			helper.assertTrue(generated.contains(biome), biome.identifier() + " dropped out of world generation");
		}

		helper.succeed();
	}

	@GameTest
	public void theLandHasClearingsAndThickets(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos here = helper.absolutePos(BlockPos.ZERO);
		int bare = 0;
		int ordinary = 0;
		int rich = 0;
		int samples = 0;
		for (int dx = -600; dx <= 600; dx += 12) {
			for (int dz = -600; dz <= 600; dz += 12) {
				double fertility = Vegetation.fertility(level, here.getX() + dx, here.getZ() + dz);
				bare += fertility < 0.3 ? 1 : 0;
				rich += fertility > 1.5 ? 1 : 0;
				ordinary += fertility > 0.7 && fertility < 1.3 ? 1 : 0;
				samples++;
			}
		}

		helper.assertTrue(bare > samples / 50 && bare < samples / 3, bare + " of " + samples + " spots are clearings");
		helper.assertTrue(rich > samples / 50 && rich < samples / 3, rich + " of " + samples + " spots are thickets");
		helper.assertTrue(ordinary > samples / 5, "only " + ordinary + " of " + samples + " spots grow about what vanilla grows");
		helper.succeed();
	}
}
