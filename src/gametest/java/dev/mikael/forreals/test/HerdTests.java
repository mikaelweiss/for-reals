package dev.mikael.forreals.test;

import dev.mikael.forreals.animals.Herd;
import dev.mikael.forreals.animals.Herds;
import dev.mikael.forreals.animals.Owned;
import dev.mikael.forreals.animals.Species;
import dev.mikael.forreals.animals.SpeciesTypes;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class HerdTests {
	private static final int YARD = 8;
	/** The yard is all the room a test animal has, so running away means crossing it. */
	private static final double FAR_SIDE_OF_THE_YARD = 5.5;

	static Herd herdInYard(final GameTestHelper helper, final Species species, final int count) {
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT);
				helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
			}
		}

		// Frost kills grass and snow buries it, so herd tests hold the weather mild whatever the calendar says.
		TestClimate.pin(helper, TestClimate.GROWING_WEATHER);
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(YARD / 2, 2, YARD / 2));
		Herd herd = Herds.ecosystem(level.getServer()).addHerd(species, center.getX(), center.getZ(), count);
		Herds.bringIntoTheWorld(level, herd);
		// Herds spread wider than a test yard, so the animals are gathered into the middle of it.
		int spot = 0;
		for (Mob member : Herds.members(herd)) {
			BlockPos inside = helper.absolutePos(new BlockPos(2 + spot % 4, 2, 2 + spot / 4 % 4));
			member.snapTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
			spot += 5;
		}

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

	private static void wallIn(final GameTestHelper helper) {
		for (int i = 0; i < YARD; i++) {
			for (int y = 2; y <= 3; y++) {
				helper.setBlock(i, y, 0, Blocks.STONE);
				helper.setBlock(i, y, YARD - 1, Blocks.STONE);
				helper.setBlock(0, y, i, Blocks.STONE);
				helper.setBlock(YARD - 1, y, i, Blocks.STONE);
			}
		}
	}

	private static int count(final GameTestHelper helper, final Block block) {
		int found = 0;
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				found += helper.getBlockState(new BlockPos(x, 2, z)).is(block) ? 1 : 0;
			}
		}

		return found;
	}

	@GameTest(skyAccess = true, maxTicks = 1200)
	public void aHungryCowGrazesAndAFullOneDoesNot(final GameTestHelper helper) {
		Herd herd = herdInYard(helper, Species.COW, 1);
		wallIn(helper);
		for (int x = 2; x < YARD - 2; x++) {
			for (int z = 2; z < YARD - 2; z++) {
				if (helper.getBlockState(new BlockPos(x, 2, z)).isAir()) {
					helper.setBlock(x, 2, z, Blocks.SHORT_GRASS);
				}
			}
		}

		int grass = count(helper, Blocks.SHORT_GRASS);
		int[] grassWhenHungerBegan = {grass};
		Mob cow = Herds.members(herd).iterator().next();
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(count(helper, Blocks.SHORT_GRASS) >= grass, "a cow with a full belly ate anyway");
			grassWhenHungerBegan[0] = count(helper, Blocks.SHORT_GRASS);
			Herds.setEnergy(cow, 0.3F);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 100, "still watching the full cow");
			helper.assertTrue(count(helper, Blocks.SHORT_GRASS) < grassWhenHungerBegan[0], "a hungry cow ate no grass");
			helper.assertTrue(Herds.energyOf(cow) > 0.3F, "eating did not feed the cow");
			sendAway(herd);
		});
	}

	@GameTest(skyAccess = true, maxTicks = 2400)
	public void aFedWolfLeavesSheepAloneAndAHungryOneHunts(final GameTestHelper helper) {
		Herd sheep = herdInYard(helper, Species.SHEEP, 2);
		Herd wolves = herdInYard(helper, Species.WOLF, 1);
		wallIn(helper);
		Mob wolf = Herds.members(wolves).iterator().next();
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(sheep.count() == 2, "a fed wolf killed a sheep");
			Herds.setEnergy(wolf, 0.1F);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 200, "still watching the fed wolf");
			helper.assertTrue(sheep.count() < 2, "a starving wolf shut in with sheep did not hunt");
			helper.assertTrue(Herds.energyOf(wolf) > 0.1F, "the kill did not feed the wolf");
			sendAway(sheep);
			sendAway(wolves);
		});
	}

	@GameTest(environment = TestPlayers.ENVIRONMENT, skyAccess = true, maxTicks = 400)
	public void aHerdRunsFromAPlayer(final GameTestHelper helper) {
		Herd herd = herdInYard(helper, Species.COW, 3);
		ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL);
		BlockPos corner = helper.absolutePos(new BlockPos(0, 2, 0));
		player.snapTo(corner.getX() + 0.5, corner.getY(), corner.getZ() + 0.5);
		TestPlayers.leaveBy(helper, player, 395);
		helper.succeedWhen(() -> {
			double nearest = Herds.members(herd).stream().mapToDouble(cow -> cow.distanceTo(player)).min().orElse(0);
			helper.assertTrue(nearest > FAR_SIDE_OF_THE_YARD, "a cow is still " + nearest + " blocks from the player");
			sendAway(herd);
			TestPlayers.leave(player);
		});
	}

	@GameTest(skyAccess = true, maxTicks = 100)
	public void anAnimalBesideAFenceIsOwnedAndIsSaved(final GameTestHelper helper) {
		herdInYard(helper, Species.COW, 0);
		helper.setBlock(2, 2, 2, Blocks.OAK_FENCE);
		Mob cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 2, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(Owned.isOwned(cow), "a cow beside a fence was not taken as owned");
			helper.assertTrue(!Herds.isWild(cow) && cow.shouldBeSaved(), "an owned cow would not be saved");
			cow.discard();
		});
	}

	@GameTest(skyAccess = true, maxTicks = 100)
	public void anAnimalInTheOpenJoinsAWildHerd(final GameTestHelper helper) {
		herdInYard(helper, Species.COW, 0);
		Mob cow = helper.spawn(EntityTypes.COW, new BlockPos(YARD / 2, 2, YARD / 2));
		helper.succeedWhen(() -> {
			Herd herd = Herds.herdOf(cow);
			helper.assertTrue(herd != null && herd.count() >= 1, "a cow in the open did not join a herd");
			helper.assertFalse(cow.shouldBeSaved(), "a wild cow would be saved");
			sendAway(herd);
		});
	}

	@GameTest(skyAccess = true, maxTicks = 1200)
	public void anOwnedCowEatsHay(final GameTestHelper helper) {
		herdInYard(helper, Species.COW, 0);
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}

		wallIn(helper);
		helper.setBlock(1, 2, 1, Blocks.OAK_FENCE);
		helper.setBlock(5, 2, 5, Blocks.HAY_BLOCK);
		Mob cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 2, 3));
		helper.runAfterDelay(5, () -> Herds.setEnergy(cow, 0.2F));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 5 && Owned.isOwned(cow), "the cow is not owned yet");
			helper.assertTrue(Herds.energyOf(cow) > 0.2F, "an owned cow beside hay did not eat");
			cow.discard();
		});
	}
}
