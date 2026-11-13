package dev.mikael.forreals.mixin;

import dev.mikael.forreals.animals.Herds;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityMixin {
	// The herd ledger is the one record of a wild animal. Saving the animal too would bring it back twice.
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void forreals$herdAnimalsLiveInTheLedger(final CallbackInfoReturnable<Boolean> cir) {
		if (Herds.isWild((Entity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
