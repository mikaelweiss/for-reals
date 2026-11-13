package dev.mikael.forreals.animals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.climate.BiomeClimate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The {@link Ecosystem} of a world, saved with it. */
public final class HerdLedger extends SavedData {
	private static final long NEVER = Long.MIN_VALUE;

	private record SavedRange(int x, int z, double forage, BiomeClimate climate, List<String> habitat) {
		static final Codec<BiomeClimate> CLIMATE = RecordCodecBuilder.create(
			i -> i.group(
					Codec.DOUBLE.fieldOf("yearly_mean").forGetter(BiomeClimate::yearlyMean),
					Codec.DOUBLE.fieldOf("seasonal_swing").forGetter(BiomeClimate::seasonalSwing),
					Codec.DOUBLE.fieldOf("daily_swing").forGetter(BiomeClimate::dailySwing),
					Codec.DOUBLE.fieldOf("wetness").forGetter(BiomeClimate::wetness)
				)
				.apply(i, BiomeClimate::new)
		);
		static final Codec<SavedRange> CODEC = RecordCodecBuilder.create(
			i -> i.group(
					Codec.INT.fieldOf("x").forGetter(SavedRange::x),
					Codec.INT.fieldOf("z").forGetter(SavedRange::z),
					Codec.DOUBLE.fieldOf("forage").forGetter(SavedRange::forage),
					CLIMATE.fieldOf("climate").forGetter(SavedRange::climate),
					Codec.STRING.listOf().fieldOf("habitat").forGetter(SavedRange::habitat)
				)
				.apply(i, SavedRange::new)
		);
	}

	private record SavedHerd(int id, String species, double x, double z, int count, double hunger, double fear) {
		static final Codec<SavedHerd> CODEC = RecordCodecBuilder.create(
			i -> i.group(
					Codec.INT.fieldOf("id").forGetter(SavedHerd::id),
					Codec.STRING.fieldOf("species").forGetter(SavedHerd::species),
					Codec.DOUBLE.fieldOf("x").forGetter(SavedHerd::x),
					Codec.DOUBLE.fieldOf("z").forGetter(SavedHerd::z),
					Codec.INT.fieldOf("count").forGetter(SavedHerd::count),
					Codec.DOUBLE.fieldOf("hunger").forGetter(SavedHerd::hunger),
					Codec.DOUBLE.fieldOf("fear").forGetter(SavedHerd::fear)
				)
				.apply(i, SavedHerd::new)
		);
	}

	public static final Codec<HerdLedger> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				SavedRange.CODEC.listOf().fieldOf("ranges").forGetter(HerdLedger::savedRanges),
				SavedHerd.CODEC.listOf().fieldOf("herds").forGetter(HerdLedger::savedHerds),
				Codec.LONG.fieldOf("last_hour").forGetter(ledger -> ledger.lastHour)
			)
			.apply(i, HerdLedger::new)
	);
	public static final SavedDataType<HerdLedger> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "herds"), HerdLedger::new, CODEC, null
	);

	private final Ecosystem ecosystem = new Ecosystem();
	private long lastHour = NEVER;

	private HerdLedger() {
	}

	/** A species name this version does not know, written by a newer one, is skipped. The rest of the ledger still loads. */
	private HerdLedger(final List<SavedRange> ranges, final List<SavedHerd> herds, final long lastHour) {
		this.lastHour = lastHour;
		for (SavedRange saved : ranges) {
			Set<Species> habitat = EnumSet.noneOf(Species.class);
			saved.habitat.forEach(name -> speciesNamed(name, habitat));
			this.ecosystem.restoreRange(Range.key(saved.x, saved.z), new Range(saved.climate, habitat, saved.forage));
		}

		for (SavedHerd saved : herds) {
			Set<Species> species = EnumSet.noneOf(Species.class);
			speciesNamed(saved.species, species);
			species.forEach(known -> this.ecosystem.restoreHerd(new Herd(saved.id, known, saved.x, saved.z, saved.count, saved.hunger, saved.fear)));
		}
	}

	private static void speciesNamed(final String name, final Set<Species> into) {
		for (Species species : Species.values()) {
			if (species.name().equals(name)) {
				into.add(species);
			}
		}
	}

	private List<SavedRange> savedRanges() {
		return this.ecosystem.ranges().entrySet().stream().map(entry -> {
			Range range = entry.getValue();
			return new SavedRange(
				Range.xOf(entry.getKey()), Range.zOf(entry.getKey()), range.forage(), range.climate(), range.habitat().stream().map(Species::name).sorted().toList()
			);
		}).toList();
	}

	private List<SavedHerd> savedHerds() {
		return this.ecosystem.herds().stream()
			.map(herd -> new SavedHerd(herd.id(), herd.species().name(), herd.x(), herd.z(), herd.count(), herd.hunger(), herd.fear()))
			.toList();
	}

	/** The ledger changes all the time, so it is always written at the next save. */
	public Ecosystem ecosystem() {
		this.setDirty();
		return this.ecosystem;
	}

	/**
	 * How many whole hours have passed since the ledger last lived one, up to a limit. A new ledger starts counting
	 * from now.
	 */
	public int hoursOwed(final long hourNow, final int atMost) {
		if (this.lastHour == NEVER || hourNow < this.lastHour) {
			this.lastHour = hourNow;
		}

		return (int)Math.min(atMost, hourNow - this.lastHour);
	}

	public long nextHour() {
		return ++this.lastHour;
	}
}
