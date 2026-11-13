package dev.mikael.forreals.test;

import dev.mikael.forreals.Switch;
import dev.mikael.forreals.animals.Owned;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.Calendar;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;

public class SwitchTests {
	/** A test that turns the mod off does so for the whole server, so it runs alone and puts it back within one tick. */
	private static final String ENVIRONMENT = "forreals-test:switched_off";
	private static final long MIDWINTER_NIGHT = (long)(0.875 * Calendar.TICKS_PER_YEAR) + 20000;
	private static final BlockPos CROP = new BlockPos(3, 2, 3);

	@GameTest
	public void aWorldWithThePackOnUsesTheMod(final GameTestHelper helper) {
		helper.assertTrue(Switch.isOn(), "the test world has every pack on, yet the mod is off");
		helper.succeed();
	}

	@GameTest(environment = ENVIRONMENT, skyAccess = true)
	public void aWorldWithThePackOffPlaysAsVanilla(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				helper.setBlock(x, 1, z, Blocks.FARMLAND);
			}
		}

		helper.setBlock(CROP, Blocks.WHEAT);
		TestClimate.pin(helper, TestClimate.FROZEN);
		TestClimate.setCalendar(helper, MIDWINTER_NIGHT);
		BlockPos crop = helper.absolutePos(CROP);
		Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
		Switch.set(false);
		try {
			helper.assertFalse(Climate.hasClimate(level), "a world with the mod off still has a climate");
			for (MobCategory category : new MobCategory[] {MobCategory.MONSTER, MobCategory.CREATURE}) {
				helper.assertTrue(
					level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), category, crop)
						== level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, crop).getMobsToSpawn(category),
					"the mod changed what spawns while it was off: " + category
				);
			}

			for (int i = 0; i < 50; i++) {
				level.getBlockState(crop).randomTick(level, crop, level.getRandom());
			}

			helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT), "frost killed wheat in a world with the mod off");
			Owned.fedByPlayer(cow);
			helper.assertFalse(Owned.isOwned(cow), "feeding an animal marked it as owned in a world with the mod off");
		} finally {
			Switch.set(true);
		}

		helper.succeed();
	}
}
