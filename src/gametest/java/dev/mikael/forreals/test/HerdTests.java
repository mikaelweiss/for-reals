package dev.mikael.forreals.test;

import dev.mikael.forreals.animals.Herd;
import dev.mikael.forreals.animals.Herds;
import dev.mikael.forreals.animals.Species;
import dev.mikael.forreals.animals.SpeciesTypes;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;

public class HerdTests {
	private static final int YARD = 8;

	static Herd herdInYard(final GameTestHelper helper, final Species species, final int count) {
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT);
				helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
			}
		}

		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(YARD / 2, 2, YARD / 2));
		Herd herd = Herds.ecosystem(level.getServer()).addHerd(species, center.getX(), center.getZ(), count);
		Herds.bringIntoTheWorld(level, herd);
		return herd;
	}

	static void sendAway(final Herd herd) {
		List.copyOf(Herds.members(herd)).forEach(Mob::discard);
		herd.addMembers(-herd.count());
	}

	@GameTest(skyAccess = true, maxTicks = 100)
	public void aLedgerHerdWalksTheWorldAndIsNeverSaved(final GameTestHelper helper) {
		Herd herd = herdInYard(helper, Species.COW, 3);
		List<Mob> cows = List.copyOf(Herds.members(herd));
		helper.assertTrue(cows.size() == 3, "the ledger said three cows, the world got " + cows.size());
		for (Mob cow : cows) {
			helper.assertTrue(cow instanceof Cow, "a cow herd produced a " + cow.getType());
			helper.assertFalse(cow.shouldBeSaved(), "a herd animal would be saved with its chunk");
		}

		cows.getFirst().kill(helper.getLevel());
		helper.succeedWhen(() -> {
			helper.assertTrue(herd.count() == 2, "a cow died and the ledger still says " + herd.count());
			helper.assertTrue(Herds.members(herd).size() == 2, "a dead cow is still counted as walking");
			sendAway(herd);
		});
	}

	@GameTest(skyAccess = true)
	public void theGameNoLongerSpawnsHerdAnimalsItself(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var spawns = level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.CREATURE, helper.absolutePos(new BlockPos(3, 2, 3)));
		helper.assertTrue(
			spawns.unwrap().stream().allMatch(spawn -> SpeciesTypes.speciesOf(spawn.value().type()) == null), "the natural spawner can still place a herd animal"
		);
		helper.succeed();
	}
}
