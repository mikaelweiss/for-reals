package dev.mikael.forreals.terrain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Makes plant cover uneven when land is generated. Vanilla spreads a biome's plants evenly from edge to edge. Here a
 * slow-changing fertility decides how much grows: clearings where it is low, thickets where it is high, and more
 * beside water.
 */
public final class Vegetation {
	private static final double BROAD_PATCH_BLOCKS = 110;
	private static final double FINE_PATCH_BLOCKS = 31;
	private static final double WATERSIDE_BOOST = 0.5;
	private static final int[][] WATERSIDE_PROBES = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}};
	private static final double RICHEST = 2;
	private static final double RICH_ENOUGH_TO_PLANT_TWICE = 1.25;
	private static final double BLEND_PATCH_BLOCKS = 40;
	private static final double BLEND_REACH_BLOCKS = 12;
	/** How thick trees grow in the new biomes, against the vanilla biome they borrow their trees from. */
	private static final Map<ResourceKey<Biome>, Double> TREE_DENSITY = Map.of(
		NewBiomes.DRY_WOODLAND, 0.45, NewBiomes.SCRUBLAND, 0.35, NewBiomes.MIXED_FOREST, 0.6
	);

	private enum Kind {
		TREES,
		GROUND_COVER,
		OTHER
	}

	private record Noises(SimplexNoise broad, SimplexNoise fine, SimplexNoise blendX, SimplexNoise blendZ) {
		Noises(final RandomSource random) {
			this(new SimplexNoise(random), new SimplexNoise(random), new SimplexNoise(random), new SimplexNoise(random));
		}
	}

	private static final Map<Long, Noises> NOISES_BY_SEED = new ConcurrentHashMap<>();
	private static final Map<PlacedFeature, Kind> KIND_BY_FEATURE = new ConcurrentHashMap<>();

	private Vegetation() {
	}

	private static Noises noises(final WorldGenLevel level) {
		return NOISES_BY_SEED.computeIfAbsent(level.getSeed(), seed -> new Noises(RandomSource.create(seed ^ 0x56656765746174L)));
	}

	private static Kind kindOf(final WorldGenLevel level, final PlacedFeature feature) {
		return KIND_BY_FEATURE.computeIfAbsent(feature, placed -> level.registryAccess()
			.lookupOrThrow(Registries.PLACED_FEATURE)
			.getResourceKey(placed)
			.map(key -> key.identifier().getPath())
			.map(name -> name.startsWith("trees_") ? Kind.TREES : isGroundCover(name) ? Kind.GROUND_COVER : Kind.OTHER)
			.orElse(Kind.OTHER));
	}

	private static boolean isGroundCover(final String name) {
		return name.startsWith("patch_grass")
			|| name.startsWith("patch_tall_grass")
			|| name.startsWith("patch_large_fern")
			|| name.startsWith("patch_bush")
			|| name.startsWith("patch_berry")
			|| name.startsWith("flower")
			|| name.startsWith("forest_flowers");
	}

	/** How much grows at a spot, from 0 for bare ground to {@link #RICHEST}. 1 is what vanilla grows everywhere. */
	public static double fertility(final WorldGenLevel level, final int x, final int z) {
		Noises noises = noises(level);
		double patches = 1 + 0.9 * noises.broad.get(x / BROAD_PATCH_BLOCKS, z / BROAD_PATCH_BLOCKS) + 0.45 * noises.fine.get(x / FINE_PATCH_BLOCKS, z / FINE_PATCH_BLOCKS);
		return Math.clamp(patches + (isNearWater(level, x, z) ? WATERSIDE_BOOST : 0), 0, RICHEST);
	}

	/** Rivers, lakes, and seas all sit at sea level, so open water a few blocks away at that height means a bank or shore. */
	private static boolean isNearWater(final WorldGenLevel level, final int x, final int z) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int[] side : WATERSIDE_PROBES) {
			probe.set(x + side[0], level.getSeaLevel() - 1, z + side[1]);
			if (level.hasChunk(probe.getX() >> 4, probe.getZ() >> 4) && level.getFluidState(probe).is(FluidTags.WATER)) {
				return true;
			}
		}

		return false;
	}

	/** How many times a feature gets to place in a chunk. Rich ground plants twice, and {@link #grows} thins the result. */
	public static int plantings(final WorldGenLevel level, final PlacedFeature feature, final BlockPos chunkOrigin) {
		return kindOf(level, feature) == Kind.OTHER ? 1 : plantingsAround(level, chunkOrigin.getX(), chunkOrigin.getZ());
	}

	private static int plantingsAround(final WorldGenLevel level, final int x, final int z) {
		return fertility(level, (x & ~15) + 8, (z & ~15) + 8) > RICH_ENOUGH_TO_PLANT_TWICE ? 2 : 1;
	}

	/** Whether one plant of this feature takes at this exact spot. */
	public static boolean grows(final WorldGenLevel level, final PlacedFeature feature, final BlockPos pos, final RandomSource random) {
		Kind kind = kindOf(level, feature);
		if (kind == Kind.OTHER) {
			return true;
		}

		double share = fertility(level, pos.getX(), pos.getZ()) / plantingsAround(level, pos.getX(), pos.getZ());
		if (kind == Kind.TREES) {
			share *= level.getBiome(pos).unwrapKey().map(TREE_DENSITY::get).orElse(1.0);
		}

		return random.nextDouble() < share;
	}

	/**
	 * Where a feature looks to learn which biome it is in. Looking a little to one side, by an amount that drifts
	 * slowly across the land, lets the plants of two biomes reach into each other along their border.
	 */
	public static BlockPos biomeLookup(final WorldGenLevel level, final PlacedFeature feature, final BlockPos pos) {
		if (kindOf(level, feature) == Kind.OTHER) {
			return pos;
		}

		Noises noises = noises(level);
		double dx = noises.blendX.get(pos.getX() / BLEND_PATCH_BLOCKS, pos.getZ() / BLEND_PATCH_BLOCKS) * BLEND_REACH_BLOCKS;
		double dz = noises.blendZ.get(pos.getX() / BLEND_PATCH_BLOCKS, pos.getZ() / BLEND_PATCH_BLOCKS) * BLEND_REACH_BLOCKS;
		return pos.offset((int)Math.round(dx), 0, (int)Math.round(dz));
	}
}
