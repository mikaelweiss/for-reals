package dev.mikael.forreals.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.gamerules.GameRules;

public class TestSetup implements ModInitializer {
	@Override
	public void onInitialize() {
		// Herds stay in the ledger while mob spawning is off, so no stray animal walks into a test.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getGlobalGameRules().set(GameRules.SPAWN_MOBS, false, server));
	}
}
