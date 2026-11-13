package dev.mikael.forreals;

import net.fabricmc.api.ModInitializer;
import dev.mikael.forreals.animals.Herds;
import dev.mikael.forreals.body.Body;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.plants.Crops;
import dev.mikael.forreals.seasons.SeasonalLook;
import dev.mikael.forreals.terrain.FilledOverworld;
import dev.mikael.forreals.plants.WildPlants;
import dev.mikael.forreals.time.ChunkClock;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.SnowAndIce;
import dev.mikael.forreals.weather.Weather;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForReals implements ModInitializer {
	public static final String MOD_ID = "forreals";
	public static final Logger LOGGER = LoggerFactory.getLogger("For Reals");

	@Override
	public void onInitialize() {
		Switch.offerThePack();
		FilledOverworld.register();
		ServerLifecycleEvents.SERVER_STARTING.register(Switch::worldStarting);
		ServerTickEvents.END_SERVER_TICK.register(SeasonalLook::tick);
		ServerTickEvents.START_SERVER_TICK.register(WorldTime::tick);
		ServerTickEvents.END_SERVER_TICK.register(Weather::showPlayersTheirSky);
		ServerTickEvents.END_SERVER_TICK.register(Body::tick);
		ServerTickEvents.END_SERVER_TICK.register(Herds::tick);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Herds.chunkLoaded(level, chunk));
		ServerEntityEvents.ENTITY_LOAD.register(Herds::entityLoaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(Herds::entityUnloaded);
		ServerLivingEntityEvents.AFTER_DEATH.register(Herds::died);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> ChunkClock.chunkLoaded(level, chunk));
		ServerChunkEvents.CHUNK_UNLOAD.register(ChunkClock::chunkUnloaded);
		ChunkClock.onCatchUp(SnowAndIce::catchUp);
		ChunkClock.onCatchUp(Crops::catchUp);
		ChunkClock.onCatchUp(WildPlants::catchUp);
		ServerLifecycleEvents.SERVER_STOPPING.register(ChunkClock::serverStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Switch.worldStopped();
			Climate.forgetWorld();
			Weather.forgetWorld();
			SnowAndIce.forgetWorld();
			Herds.forgetWorld();
			SeasonalLook.forgetWorld();
		});
	}
}
