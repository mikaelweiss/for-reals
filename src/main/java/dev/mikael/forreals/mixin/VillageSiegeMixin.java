package dev.mikael.forreals.mixin;

import dev.mikael.forreals.Switch;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillageSiege.class)
abstract class VillageSiegeMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void forreals$neverSpawn(final ServerLevel level, final boolean spawnEnemies, final CallbackInfo ci) {
		if (Switch.isOn()) {
			ci.cancel();
		}
	}
}
