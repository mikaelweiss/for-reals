package dev.mikael.forreals.seasons;

import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.climate.ClimateModel;
import dev.mikael.forreals.time.Calendar;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.Sky;
import dev.mikael.forreals.weather.Weather;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Shows the season on vanilla clients. A client draws grass, leaves, and falling rain or snow from the biome it is
 * told about. So every biome gets a set of look-alikes, one per stage of the year, each in a rain and a snow form, and
 * players are told about the look-alike that fits the place right now. The world itself keeps its real biomes.
 */
public final class SeasonalLook {
	private static final String PREFIX = "seasonal/";
	private static final String[] PRECIPITATION = {"rain", "snow"};
	/** A client turns rain to snow by biome temperature and height. These sit far enough from the line that height never flips them. */
	private static final float RAIN_TEMPERATURE = 2;
	private static final float SNOW_TEMPERATURE = -0.5F;
	private static final Set<ResourceKey<Biome>> NEVER_SEE_THE_SKY = Set.of(
		Biomes.THE_VOID,
		Biomes.NETHER_WASTES,
		Biomes.WARPED_FOREST,
		Biomes.CRIMSON_FOREST,
		Biomes.SOUL_SAND_VALLEY,
		Biomes.BASALT_DELTAS,
		Biomes.THE_END,
		Biomes.END_HIGHLANDS,
		Biomes.END_MIDLANDS,
		Biomes.SMALL_END_ISLANDS,
		Biomes.END_BARRENS,
		Biomes.DRIPSTONE_CAVES,
		Biomes.LUSH_CAVES,
		Biomes.DEEP_DARK,
		Biomes.SULFUR_CAVES
	);
	private static final double FULL_SEASONS_SWING = 13;
	private static final long TREND_PROBE_TICKS = Calendar.TICKS_PER_DAY;
	private static final double VARIETY_DEGREES = 3;
	private static final int REFRESH_TICKS = 20;
	private static final int CHUNKS_PER_REFRESH = 48;

	private static final Map<Biome, Holder<Biome>[][]> LOOKS = new IdentityHashMap<>();
	private static final Map<UUID, Shown> SHOWN = new HashMap<>();
	private static MinecraftServer looksBuiltFor;

	private SeasonalLook() {
	}

	private static final class Shown {
		final Long2IntOpenHashMap sentByChunk = new Long2IntOpenHashMap();
		long[] round = new long[0];
		int next;
	}

	public static void forgetWorld() {
		LOOKS.clear();
		SHOWN.clear();
		looksBuiltFor = null;
	}

	/**
	 * Adds the look-alikes of every biome in a biome registry that has just finished loading, so they exist for any
	 * biome a datapack or another mod brings.
	 */
	public static void addTo(final WritableRegistry<Biome> registry) {
		for (Map.Entry<ResourceKey<Biome>, Biome> entry : List.copyOf(registry.entrySet())) {
			Identifier id = entry.getKey().identifier();
			boolean isALook = id.getNamespace().equals(ForReals.MOD_ID) && id.getPath().startsWith(PREFIX);
			if (!isALook && !NEVER_SEE_THE_SKY.contains(entry.getKey())) {
				registerLooksOf(registry, id, entry.getValue());
			}
		}
	}

	private static Identifier lookId(final Identifier biome, final LookStage stage, final int precipitation) {
		return Identifier.fromNamespaceAndPath(
			ForReals.MOD_ID, PREFIX + biome.getNamespace() + "/" + biome.getPath() + "/" + stage.name().toLowerCase() + "_" + PRECIPITATION[precipitation]
		);
	}

	private static void registerLooksOf(final WritableRegistry<Biome> registry, final Identifier id, final Biome biome) {
		BiomeSpecialEffects effects = biome.getSpecialEffects();
		float downfall = biome.climateSettings.downfall();
		int summerGrass = effects.grassColorOverride().orElseGet(() -> SummerColors.grass(biome.getBaseTemperature(), downfall));
		int summerFoliage = effects.foliageColorOverride().orElseGet(() -> SummerColors.foliage(biome.getBaseTemperature(), downfall));
		double seasonality = Math.clamp(BiomeClimate.fromVanilla(biome.getBaseTemperature(), downfall).seasonalSwing() / FULL_SEASONS_SWING, 0, 1);
		for (LookStage stage : LookStage.values()) {
			BiomeSpecialEffects seasonal = new BiomeSpecialEffects(
				effects.waterColor(),
				Optional.of(stage.foliage(summerFoliage, seasonality)),
				effects.dryFoliageColorOverride(),
				Optional.of(stage.grass(summerGrass, seasonality)),
				effects.grassColorModifier()
			);
			for (int precipitation = 0; precipitation < PRECIPITATION.length; precipitation++) {
				ResourceKey<Biome> look = ResourceKey.create(Registries.BIOME, lookId(id, stage, precipitation));
				if (registry.containsKey(look)) {
					continue;
				}

				registry.register(
					look,
					new Biome.BiomeBuilder()
						.hasPrecipitation(biome.hasPrecipitation())
						.temperature(precipitation == 0 ? RAIN_TEMPERATURE : SNOW_TEMPERATURE)
						.downfall(downfall)
						.putAttributes(biome.getAttributes())
						.specialEffects(seasonal)
						.generationSettings(BiomeGenerationSettings.EMPTY)
						.build(),
					RegistrationInfo.BUILT_IN
				);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Holder<Biome>[][] looksOf(final ServerLevel level, final Holder<Biome> biome) {
		if (looksBuiltFor != level.getServer()) {
			LOOKS.clear();
			looksBuiltFor = level.getServer();
			Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
			registry.listElements().forEach(real -> {
				Identifier id = real.key().identifier();
				Holder<Biome>[][] looks = new Holder[LookStage.values().length][PRECIPITATION.length];
				for (LookStage stage : LookStage.values()) {
					for (int precipitation = 0; precipitation < PRECIPITATION.length; precipitation++) {
						Optional<Holder.Reference<Biome>> look = registry.get(lookId(id, stage, precipitation));
						if (look.isEmpty()) {
							return;
						}

						looks[stage.ordinal()][precipitation] = look.get();
					}
				}

				LOOKS.put(real.value(), looks);
			});
		}

		return LOOKS.get(biome.value());
	}

	/** The biome a client should be told is at this spot right now. */
	public static Holder<Biome> lookAt(final ServerLevel level, final BlockPos pos) {
		return new ChunkSeason(level, ChunkPos.containing(pos)).lookOf(level, level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2), pos.getX(), pos.getY(), pos.getZ());
	}

	/** The parts of the season that are the same across one chunk. */
	private record ChunkSeason(double typicalAtSea, boolean warming, double nowAtSea, int seaLevel) {
		ChunkSeason(final ServerLevel level, final ChunkPos chunk) {
			this(level, Climate.base(level, chunk.x(), chunk.z()), Weather.sky(level, chunk.getMiddleBlockPosition(0)), WorldTime.ticks(level.getServer()));
		}

		private ChunkSeason(final ServerLevel level, final BiomeClimate climate, final Sky sky, final long ticks) {
			this(
				climate.yearlyMean() + ClimateModel.seasonalOffset(climate, ticks),
				ClimateModel.seasonalOffset(climate, ticks + TREND_PROBE_TICKS) > ClimateModel.seasonalOffset(climate, ticks),
				ClimateModel.openAir(climate, ticks, 0, sky.airMass(), sky.cloudCover()),
				level.getSeaLevel()
			);
		}

		Holder<Biome> lookOf(final ServerLevel level, final Holder<Biome> real, final int x, final int y, final int z) {
			Holder<Biome>[][] looks = looksOf(level, real);
			if (looks == null) {
				return real;
			}

			double altitude = ClimateModel.altitudeOffset(y - this.seaLevel);
			double variety = variety(x >> 2, z >> 2);
			LookStage stage = LookStage.at(this.typicalAtSea + altitude + (variety - 0.5) * VARIETY_DEGREES, this.warming, variety(z >> 2, x >> 2));
			return looks[stage.ordinal()][this.nowAtSea + altitude < Weather.SNOW_BELOW ? 1 : 0];
		}

		/** A fixed number from 0 to 1 for a spot, the same every time it is asked. */
		private static double variety(final int a, final int b) {
			int hash = a * 73428767 ^ b * 912931;
			hash ^= hash >>> 15;
			hash *= 0x2C1B3C6D;
			hash ^= hash >>> 12;
			return (hash & 0xFFFF) / 65536.0;
		}
	}

	/** The seasonal biomes of one chunk, in the form a client reads. */
	private static byte[] looksOfChunk(final ServerLevel level, final LevelChunk chunk) {
		ChunkSeason season = new ChunkSeason(level, chunk.getPos());
		LevelChunkSection[] sections = chunk.getSections();
		List<PalettedContainer<Holder<Biome>>> looks = new ArrayList<>(sections.length);
		int size = 0;
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int index = 0; index < sections.length; index++) {
			PalettedContainer<Holder<Biome>> container = sections[index].getBiomes().recreate();
			int minY = chunk.getSectionYFromSectionIndex(index) << 4;
			for (int qx = 0; qx < 4; qx++) {
				for (int qy = 0; qy < 4; qy++) {
					for (int qz = 0; qz < 4; qz++) {
						Holder<Biome> real = sections[index].getNoiseBiome(qx, qy, qz);
						container.set(qx, qy, qz, season.lookOf(level, real, minX + qx * 4 + 2, minY + qy * 4 + 2, minZ + qz * 4 + 2));
					}
				}
			}

			looks.add(container);
			size += container.getSerializedSize();
		}

		byte[] bytes = new byte[size];
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
		buffer.writerIndex(0);
		looks.forEach(container -> container.write(buffer));
		return bytes;
	}

	/** Bedrock players join through Floodgate, which gives them ids with an empty first half. Their client cannot show these biomes. */
	private static boolean seesSeasons(final ServerPlayer player) {
		return player.getUUID().getMostSignificantBits() != 0;
	}

	/** Called right after a chunk is sent to a player. The season follows it. */
	public static void chunkSent(final ServerPlayer player, final ServerLevel level, final LevelChunk chunk) {
		if (Climate.hasClimate(level) && seesSeasons(player)) {
			byte[] looks = looksOfChunk(level, chunk);
			SHOWN.computeIfAbsent(player.getUUID(), id -> new Shown()).sentByChunk.put(chunk.getPos().pack(), Arrays.hashCode(looks));
			player.connection.send(new ClientboundChunksBiomesPacket(List.of(new ClientboundChunksBiomesPacket.ChunkBiomeData(chunk.getPos(), looks))));
		}
	}

	/** Works through each player's chunks a few at a time and resends any whose season has moved on. */
	public static void tick(final MinecraftServer server) {
		if (server.getTickCount() % REFRESH_TICKS != 0) {
			return;
		}

		SHOWN.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Shown shown = SHOWN.get(player.getUUID());
			ServerLevel level = player.level();
			if (shown == null || !Climate.hasClimate(level)) {
				continue;
			}

			if (shown.next >= shown.round.length) {
				shown.round = shown.sentByChunk.keySet().toLongArray();
				shown.next = 0;
			}

			for (int checked = 0; checked < CHUNKS_PER_REFRESH && shown.next < shown.round.length; checked++) {
				refresh(player, level, shown, shown.round[shown.next++]);
			}
		}
	}

	private static void refresh(final ServerPlayer player, final ServerLevel level, final Shown shown, final long packed) {
		int x = ChunkPos.getX(packed);
		int z = ChunkPos.getZ(packed);
		LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
		if (chunk == null || !player.getChunkTrackingView().contains(x, z)) {
			shown.sentByChunk.remove(packed);
			return;
		}

		byte[] looks = looksOfChunk(level, chunk);
		int hash = Arrays.hashCode(looks);
		if (shown.sentByChunk.get(packed) != hash) {
			shown.sentByChunk.put(packed, hash);
			player.connection.send(new ClientboundChunksBiomesPacket(List.of(new ClientboundChunksBiomesPacket.ChunkBiomeData(new ChunkPos(x, z), looks))));
		}
	}
}
