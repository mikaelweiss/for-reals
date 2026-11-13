package dev.mikael.forreals;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForReals implements ModInitializer {
	public static final String MOD_ID = "forreals";
	public static final Logger LOGGER = LoggerFactory.getLogger("For Reals");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				LOGGER.info("For Reals loaded on {} server", server.isDedicatedServer() ? "dedicated" : "integrated"));
	}
}
