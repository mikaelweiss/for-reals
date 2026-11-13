package dev.mikael.forreals.mixin;

import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomSpawner.class)
abstract class PhantomSpawnerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void forreals$neverSpawn(final ServerLevel level, final boolean spawnEnemies, final CallbackInfo ci) {
		ci.cancel();
	}
}
