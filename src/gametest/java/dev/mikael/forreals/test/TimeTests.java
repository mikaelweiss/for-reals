package dev.mikael.forreals.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class TimeTests {
	@GameTest
	public void clockStandsStillOnAnEmptyServer(final GameTestHelper helper) {
		long before = helper.getLevel().getOverworldClockTime();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getLevel().getOverworldClockTime() == before, "the clock ran with nobody online");
			helper.succeed();
		});
	}
}
