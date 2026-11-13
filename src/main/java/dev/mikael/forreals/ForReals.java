package dev.mikael.forreals;

import net.fabricmc.api.ModInitializer;
import dev.mikael.forreals.time.WorldTime;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForReals implements ModInitializer {
	public static final String MOD_ID = "forreals";
	public static final Logger LOGGER = LoggerFactory.getLogger("For Reals");

	@Override
	public void onInitialize() {
		ServerTickEvents.START_SERVER_TICK.register(WorldTime::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				LOGGER.info("For Reals loaded on {} server", server.isDedicatedServer() ? "dedicated" : "integrated"));
	}
}
