package dev.mikael.forreals.weather;

import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.WorldTime;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;

/** The weather of a running overworld: one {@link Sky} per place, shown to each player where they stand. */
public final class Weather {
	public static final double SNOW_BELOW = 0.5;

	private static final double WET_FROM = 0.1;
	private static final double THUNDER_ABOVE_DEGREES = 4;
	private static final int SKY_FRESH_TICKS = 20;
	private static final int PLAYER_UPDATE_TICKS = 10;
	private static final int MAX_CACHED_CHUNKS = 16384;
	private static final float SHOWN_STEP = 0.01F;
	private static final int LIGHTNING_ODDS = 100000;
	private static final Sky FORCED_RAIN = new Sky(0, 1, 0.7, 0);
	private static final Sky FORCED_THUNDER = new Sky(0, 1, 1, 1);

	private static final Long2ObjectOpenHashMap<CachedSky> SKY_BY_CHUNK = new Long2ObjectOpenHashMap<>();
	private static final Map<UUID, Shown> SHOWN_TO_PLAYER = new HashMap<>();
	private static WeatherField field;
	private static long fieldSeed;

	private Weather() {
	}

	private record CachedSky(Sky sky, long gameTime) {
	}

	private record Shown(ServerLevel level, float rain, float thunder) {
	}

	public static void forgetWorld() {
		SKY_BY_CHUNK.clear();
		SHOWN_TO_PLAYER.clear();
		field = null;
	}

	public static WeatherField field(final ServerLevel level) {
		if (field == null || fieldSeed != level.getSeed()) {
			fieldSeed = level.getSeed();
			field = new WeatherField(fieldSeed);
		}

		return field;
	}

	public static Sky sky(final ServerLevel level, final BlockPos pos) {
		if (!level.getServer().isSameThread()) {
			return compute(level, pos.getX() >> 4, pos.getZ() >> 4);
		}

		long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		CachedSky cached = SKY_BY_CHUNK.get(key);
		if (cached == null || level.getGameTime() - cached.gameTime >= SKY_FRESH_TICKS) {
			if (SKY_BY_CHUNK.size() >= MAX_CACHED_CHUNKS) {
				SKY_BY_CHUNK.clear();
			}

			cached = new CachedSky(compute(level, pos.getX() >> 4, pos.getZ() >> 4), level.getGameTime());
			SKY_BY_CHUNK.put(key, cached);
		}

		return cached.sky;
	}

	private static Sky compute(final ServerLevel level, final int chunkX, final int chunkZ) {
		long ticks = WorldTime.ticks(level.getServer());
		double x = (chunkX << 4) + 8;
		double z = (chunkZ << 4) + 8;
		Sky natural = Sky.over(field(level), Climate.base(level, chunkX, chunkZ), x, z, ticks);
		WeatherData forced = level.getServer().getWeatherData();
		if (forced.getClearWeatherTime() > 0) {
			return new Sky(natural.airMass(), 0, 0, 0);
		} else if (forced.isRaining()) {
			Sky shape = forced.isThundering() ? FORCED_THUNDER : FORCED_RAIN;
			return new Sky(natural.airMass(), shape.cloudCover(), shape.precipitation(), shape.thunder());
		}

		return natural;
	}

	/** Rain or snow reaching this exact block, or none if the sky is dry or something is overhead. */
	public static Biome.Precipitation precipitationAt(final ServerLevel level, final BlockPos pos) {
		Sky sky = sky(level, pos);
		if (sky.precipitation() < WET_FROM || !level.canSeeSky(pos) || level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
			return Biome.Precipitation.NONE;
		}

		return isSnow(level, pos, sky) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
	}

	public static boolean isSnow(final ServerLevel level, final BlockPos pos, final Sky sky) {
		return Climate.openAir(level, pos, sky, WorldTime.ticks(level.getServer())) < SNOW_BELOW;
	}

	/** Thunder needs warm air. A winter storm of the same strength is a blizzard. */
	public static double thunderAt(final ServerLevel level, final BlockPos pos, final Sky sky) {
		return Climate.openAir(level, pos, sky, WorldTime.ticks(level.getServer())) > THUNDER_ABOVE_DEGREES ? sky.thunder() : 0;
	}

	/**
	 * Vanilla weather is one switch for the whole world. Here it only carries what the weather command asked for: a
	 * forced spell that runs out, after which the sky goes back to its own weather.
	 */
	public static void tickForcedWeather(final ServerLevel level) {
		level.setRainLevel(0);
		level.setThunderLevel(0);
		if (WorldTime.isPaused(level.getServer()) || !level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
			return;
		}

		WeatherData forced = level.getServer().getWeatherData();
		if (forced.getClearWeatherTime() > 0) {
			forced.setClearWeatherTime(forced.getClearWeatherTime() - 1);
		}

		if (forced.isRaining()) {
			forced.setRainTime(forced.getRainTime() - 1);
			if (forced.getRainTime() <= 0) {
				forced.setRaining(false);
				forced.setThundering(false);
			}
		}
	}

	public static void strikeLightning(final ServerLevel level, final LevelChunk chunk) {
		if (level.getRandom().nextInt(LIGHTNING_ODDS) >= WorldTime.speedUp()) {
			return;
		}

		ChunkPos chunkPos = chunk.getPos();
		BlockPos target = level.findLightningTargetAround(level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15));
		if (level.getRandom().nextDouble() < thunderAt(level, target, sky(level, target)) && level.isRainingAt(target)) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
			if (bolt != null) {
				bolt.snapTo(Vec3.atBottomCenterOf(target));
				level.addFreshEntity(bolt);
			}
		}
	}

	public static void showPlayersTheirSky(final MinecraftServer server) {
		if (server.getTickCount() % PLAYER_UPDATE_TICKS != 0) {
			return;
		}

		SHOWN_TO_PLAYER.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (!Climate.hasClimate(level)) {
				SHOWN_TO_PLAYER.remove(player.getUUID());
				continue;
			}

			BlockPos pos = player.blockPosition();
			Sky sky = sky(level, pos);
			float rain = (float)sky.precipitation();
			float thunder = (float)thunderAt(level, pos, sky);
			Shown shown = SHOWN_TO_PLAYER.get(player.getUUID());
			if (shown == null || shown.level != level) {
				shown = new Shown(level, 0, 0);
			}

			if (Math.abs(rain - shown.rain) < SHOWN_STEP && Math.abs(thunder - shown.thunder) < SHOWN_STEP && (rain > 0) == (shown.rain > 0)) {
				SHOWN_TO_PLAYER.put(player.getUUID(), shown);
				continue;
			}

			if (rain > 0 && shown.rain == 0) {
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
			} else if (rain == 0 && shown.rain > 0) {
				player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
			}

			player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
			player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
			SHOWN_TO_PLAYER.put(player.getUUID(), new Shown(level, rain, thunder));
		}
	}
}
