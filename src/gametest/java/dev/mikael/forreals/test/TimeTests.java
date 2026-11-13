package dev.mikael.forreals.test;

import dev.mikael.forreals.time.WorldTime;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public class TimeTests {
	private static final long NIGHTFALL = 14000;
	private static final long MOST_OF_A_NIGHT = 8000;
	private static final int SKY_SETTLE_TICKS = 5;
	private static final BlockPos BED_HEAD = new BlockPos(3, 1, 3);

	@GameTest
	public void clockStandsStillOnAnEmptyServer(final GameTestHelper helper) {
		long before = helper.getLevel().getOverworldClockTime();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getLevel().getOverworldClockTime() == before, "the clock ran with nobody online");
			helper.succeed();
		});
	}

	@GameTest(environment = TestPlayers.ENVIRONMENT, maxTicks = 700, skyAccess = true)
	public void sleepingLivesThroughTheNight(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Holder<WorldClock> clock = server.registryAccess().get(WorldClocks.OVERWORLD).orElseThrow();
		server.clockManager().setTotalTicks(clock, NIGHTFALL);
		server.setWeatherParameters(0, 1_000_000, true, false);

		BlockState head = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD);
		helper.setBlock(BED_HEAD.south(), head.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(BED_HEAD, head);

		BlockPos bed = helper.absolutePos(BED_HEAD);
		ServerPlayer player = TestPlayers.join(helper, GameType.CREATIVE);
		player.snapTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5);
		AbstractBedBlock bedBlock = (AbstractBedBlock)head.getBlock();

		boolean[] sleptFast = {false};
		helper.onEachTick(() -> {
			player.doTick();
			sleptFast[0] |= WorldTime.speedUp() > 1;
		});
		// The sky needs a tick to catch up with the new clock before a bed accepts a sleeper.
		helper.runAfterDelay(SKY_SETTLE_TICKS, () -> TestPlayers.leaveOnFailure(player, () -> {
			var problem = player.startSleepInBed(bedBlock, head, bedBlock.getBedRule(level, bed), bed).left();
			helper.assertTrue(problem.isEmpty(), "the player could not get into bed: " + problem);
		}));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > SKY_SETTLE_TICKS, "the player has not gone to bed yet");
			helper.assertFalse(player.isSleeping(), "the player is still asleep");
			TestPlayers.leaveOnFailure(player, () -> {
				helper.assertTrue(sleptFast[0], "time never sped up");
				helper.assertTrue(level.getOverworldClockTime() - NIGHTFALL > MOST_OF_A_NIGHT, "the night did not pass");
				helper.assertTrue(server.getWeatherData().isRaining(), "sleeping cleared the weather");
				helper.assertTrue(WorldTime.speedUp() == 1, "time is still sped up after waking");
			});
			TestPlayers.leave(player);
		});
	}
}
