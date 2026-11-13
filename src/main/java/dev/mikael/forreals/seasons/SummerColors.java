package dev.mikael.forreals.seasons;

/**
 * The grass and leaf color the game gives a biome from its temperature and downfall. The game reads these from two
 * images that only clients have. Each image is close to a flat blend between three corner colors, so that blend is
 * done here. The result is within a few shades of the real thing.
 */
public final class SummerColors {
	private static final int[] GRASS_COLD = {128, 180, 151};
	private static final int[] GRASS_HOT_DRY = {191, 183, 85};
	private static final int[] GRASS_HOT_WET = {87, 204, 56};
	private static final int[] FOLIAGE_COLD = {96, 161, 123};
	private static final int[] FOLIAGE_HOT_DRY = {174, 164, 42};
	private static final int[] FOLIAGE_HOT_WET = {45, 190, 4};

	private SummerColors() {
	}

	public static int grass(final double temperature, final double downfall) {
		return blend(temperature, downfall, GRASS_COLD, GRASS_HOT_DRY, GRASS_HOT_WET);
	}

	public static int foliage(final double temperature, final double downfall) {
		return blend(temperature, downfall, FOLIAGE_COLD, FOLIAGE_HOT_DRY, FOLIAGE_HOT_WET);
	}

	private static int blend(final double temperature, final double downfall, final int[] cold, final int[] hotDry, final int[] hotWet) {
		double heat = Math.clamp(temperature, 0, 1);
		double wet = Math.clamp(downfall, 0, 1) * heat;
		double dry = heat - wet;
		int color = 0;
		for (int channel = 0; channel < 3; channel++) {
			color = color << 8 | (int)Math.round(cold[channel] * (1 - heat) + hotDry[channel] * dry + hotWet[channel] * wet);
		}

		return color;
	}
}
