package dev.mikael.forreals.animals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/** What grazers eat, and what eating does to the land. */
public final class Grazing {
	/** A full belly lasts this many days. */
	public static final double DAYS_IN_A_FULL_BELLY = 3;

	private static final float PLANT = 0.15F;
	private static final float CROP = 0.2F;
	private static final float HAY = 0.3F;
	private static final float TURF = 0.08F;
	private static final int BITES_IN_A_HAY_BALE = 9;

	private Grazing() {
	}

	/** Share of a full belly one bite of this block gives. 0 for something a grazer will not eat. */
	public static float foodIn(final BlockState state) {
		if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(BlockTags.SMALL_FLOWERS)) {
			return PLANT;
		} else if (state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) {
			return CROP;
		} else if (state.is(Blocks.HAY_BLOCK)) {
			return HAY;
		}

		return state.is(Blocks.GRASS_BLOCK) ? TURF : 0;
	}

	/** Turf is a last resort: it only counts as food when the animal is standing on it. */
	public static boolean isWorthWalkingTo(final BlockState state) {
		return foodIn(state) > TURF;
	}

	/** One bite. The animal gains energy, the block is eaten, and the range it stands on has that much less forage. */
	public static void eat(final Mob animal, final BlockPos pos) {
		ServerLevel level = (ServerLevel)animal.level();
		BlockState state = level.getBlockState(pos);
		float food = foodIn(state);
		if (food == 0) {
			return;
		}

		if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			if (state.is(Blocks.GRASS_BLOCK)) {
				level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
				level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			} else if (!state.is(Blocks.HAY_BLOCK) || animal.getRandom().nextInt(BITES_IN_A_HAY_BALE) == 0) {
				level.destroyBlock(pos, false);
			}
		}

		Herds.setEnergy(animal, Herds.energyOf(animal) + food);
		Species species = SpeciesTypes.speciesOf(animal.getType());
		Range range = Herds.ecosystem(level.getServer()).range(Range.keyAt(pos.getX(), pos.getZ()));
		if (range != null && species != null && !state.is(Blocks.HAY_BLOCK)) {
			range.graze(food * DAYS_IN_A_FULL_BELLY * species.dailyFood());
		}
	}
}
