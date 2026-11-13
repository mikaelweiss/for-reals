package dev.mikael.forreals.monsters;

import dev.mikael.forreals.Switch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public final class SurfaceSpawns {
	private SurfaceSpawns() {
	}

	public static boolean isBlocked(final Level level, final MobCategory category, final BlockPos pos) {
		return category == MobCategory.MONSTER && isOpenSky(level, pos);
	}

	public static boolean isOpenSky(final Level level, final BlockPos pos) {
		return Switch.isOn() && level.dimension() == Level.OVERWORLD && level.getBrightness(LightLayer.SKY, pos) > 0;
	}
}
