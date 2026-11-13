package dev.mikael.forreals.time;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Remembers the clock reading that counts as the first spring equinox, so a world starts its first year in spring
 * whatever its clock says.
 */
public final class CalendarData extends SavedData {
	private static final long UNSET = -1;
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
			this.epochTicks = clockTicks - Calendar.timeOfDay(clockTicks);
			this.setDirty();
		}

		return clockTicks - this.epochTicks;
	}
}
