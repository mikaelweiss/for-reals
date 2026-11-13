package dev.mikael.forreals.time;

import dev.mikael.forreals.Switch;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

public final class WorldTime {
	private static final float SLEEP_RATE = 40;
	private static final float RATE_TOLERANCE = 0.005F;

	private static boolean sleeping;

	private WorldTime() {
	}

	/** Calendar ticks: the overworld clock, counted from the world's first spring equinox. */
	public static long ticks(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(CalendarData.TYPE).ticksSinceEpoch(server.overworld().getOverworldClockTime());
	}

	public static boolean isPaused(final MinecraftServer server) {
		return server.getPlayerCount() == 0;
	}

	public static void setSleeping(final boolean enoughPlayersAsleep) {
		sleeping = enoughPlayersAsleep;
	}

	/** How many times faster than normal the world lives right now. */
	public static int speedUp() {
		return sleeping ? (int)SLEEP_RATE : 1;
	}

	public static void tick(final MinecraftServer server) {
		if (!Switch.isOn() || isPaused(server)) {
			sleeping = false;
			return;
		}

		Holder<WorldClock> clock = server.registryAccess().get(WorldClocks.OVERWORLD).orElseThrow();
		ServerClockManager clocks = server.clockManager();
		float rate = sleeping ? SLEEP_RATE : Calendar.clockRate(ticks(server));
		if (Math.abs(rate - clocks.getInstance(clock).rate()) > RATE_TOLERANCE) {
			clocks.setRate(clock, rate);
		}
	}
}
