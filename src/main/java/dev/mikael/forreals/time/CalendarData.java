package dev.mikael.forreals.time;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Remembers which clock reading counts as the first spring equinox, so a world starts its first year in late spring
 * whatever its clock says. Late, because the equinox itself still has winter's snow on the ground.
 */
public final class CalendarData extends SavedData {
	private static final long UNSET = Long.MIN_VALUE;
	private static final int FIRST_DAY_OF_YEAR_ONE = 9;
	public static final Codec<CalendarData> CODEC = Codec.LONG.fieldOf("epoch_ticks").xmap(CalendarData::new, data -> data.epochTicks).codec();
	public static final SavedDataType<CalendarData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "calendar"), () -> new CalendarData(UNSET), CODEC, null
	);

	private long epochTicks;

	private CalendarData(final long epochTicks) {
		this.epochTicks = epochTicks;
	}

	public long ticksSinceEpoch(final long clockTicks) {
		if (this.epochTicks == UNSET) {
			this.epochTicks = clockTicks - Calendar.timeOfDay(clockTicks) - (long)FIRST_DAY_OF_YEAR_ONE * Calendar.TICKS_PER_DAY;
			this.setDirty();
		}

		return clockTicks - this.epochTicks;
	}
}
