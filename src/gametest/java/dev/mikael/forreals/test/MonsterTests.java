package dev.mikael.forreals.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;

public class MonsterTests {
	private static final BlockPos SPOT = new BlockPos(3, 2, 3);

	@GameTest(skyAccess = true)
	public void noMonsterSpawnsUnderOpenSky(final GameTestHelper helper) {
		helper.succeedWhen(() -> {
			helper.assertTrue(spawns(helper, MobCategory.MONSTER).isEmpty(), "monsters can spawn under open sky");
			helper.assertTrue(spawns(helper, MobCategory.CREATURE).unwrap().equals(biomeSpawns(helper, MobCategory.CREATURE).unwrap()), "animals lost their spawn list");
		});
	}

	@GameTest(skyAccess = true)
	public void monstersSpawnUnderARoof(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 6, z, Blocks.STONE);
				boolean wall = x == 0 || x == 7 || z == 0 || z == 7;
				for (int y = 0; y < 6; y++) {
					if (wall || y == 0) {
						helper.setBlock(x, y, z, Blocks.STONE);
					}
				}
			}
		}

		helper.succeedWhen(
			() -> helper.assertTrue(
				spawns(helper, MobCategory.MONSTER).unwrap().equals(biomeSpawns(helper, MobCategory.MONSTER).unwrap()),
				"monsters cannot spawn in the dark, sky light " + helper.getLevel().getBrightness(LightLayer.SKY, helper.absolutePos(SPOT))
			)
		);
	}

	private static WeightedList<MobSpawnSettings.SpawnerData> spawns(final GameTestHelper helper, final MobCategory category) {
		ServerLevel level = helper.getLevel();
		return level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), category, helper.absolutePos(SPOT));
	}

	private static WeightedList<MobSpawnSettings.SpawnerData> biomeSpawns(final GameTestHelper helper, final MobCategory category) {
		return helper.getLevel().environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, helper.absolutePos(SPOT)).getMobsToSpawn(category);
	}
}
