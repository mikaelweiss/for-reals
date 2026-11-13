package dev.mikael.forreals.test;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.SnowAndIce;
import dev.mikael.forreals.weather.Weather;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.WorldClocks;

final class TestClimate {
	/**
	 * Tests that move the calendar, pin a climate, or force the weather run in their own batch. They share one world,
	 * so each sets what it needs and checks it within the same tick.
	 */
	static final String ENVIRONMENT = "forreals-test:own_calendar";

	static final BiomeClimate FROZEN = BiomeClimate.fromVanilla(-0.5, 0.8);
	static final BiomeClimate TEMPERATE = BiomeClimate.fromVanilla(0.7, 0.8);
	static final BiomeClimate DESERT = BiomeClimate.fromVanilla(2.0, 0.0);

	private static final int PINNED_CHUNK_REACH = 6;

	private TestClimate() {
	}

	/** Gives the land around a test the same climate whatever biome the test landed in. */
	static void pin(final GameTestHelper helper, final BiomeClimate climate) {
		forgetCachedWeather();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		for (int dx = -PINNED_CHUNK_REACH; dx <= PINNED_CHUNK_REACH; dx++) {
			for (int dz = -PINNED_CHUNK_REACH; dz <= PINNED_CHUNK_REACH; dz++) {
				Climate.pin((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, climate);
			}
		}
	}

	static void setCalendar(final GameTestHelper helper, final long calendarTicks) {
		MinecraftServer server = helper.getLevel().getServer();
		long clockNow = helper.getLevel().getOverworldClockTime();
		long epoch = clockNow - WorldTime.ticks(server);
		server.clockManager().setTotalTicks(server.registryAccess().get(WorldClocks.OVERWORLD).orElseThrow(), epoch + calendarTicks);
		forgetCachedWeather();
	}

	/** The sky over a test is whatever the random seed made it. Tests about temperature hold it clear. */
	static void clearSkies(final GameTestHelper helper) {
		helper.getLevel().getServer().setWeatherParameters(1_000_000, 0, false, false);
		forgetCachedWeather();
	}

	static void forgetCachedWeather() {
		Weather.forgetWorld();
		SnowAndIce.forgetWorld();
	}
}
