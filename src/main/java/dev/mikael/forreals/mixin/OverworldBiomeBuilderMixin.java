package dev.mikael.forreals.mixin;

import dev.mikael.forreals.terrain.NewBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverworldBiomeBuilder.class)
abstract class OverworldBiomeBuilderMixin {
	@Shadow
	@Final
	private ResourceKey<Biome>[][] MIDDLE_BIOMES;
	@Shadow
	@Final
	private ResourceKey<Biome>[][] MIDDLE_BIOMES_VARIANT;

	// Both tables run from frozen to hot, then from dry to wet.
	@Inject(method = "<init>", at = @At("TAIL"))
	private void forreals$fillTheGapsBetweenBiomes(final CallbackInfo ci) {
		this.MIDDLE_BIOMES[0][0] = NewBiomes.TUNDRA;
		this.MIDDLE_BIOMES[1][0] = NewBiomes.STEPPE;
		this.MIDDLE_BIOMES[1][2] = NewBiomes.MIXED_FOREST;
		this.MIDDLE_BIOMES[2][0] = NewBiomes.PRAIRIE;
		this.MIDDLE_BIOMES[3][0] = NewBiomes.SCRUBLAND;
		this.MIDDLE_BIOMES[3][2] = NewBiomes.DRY_WOODLAND;
		this.MIDDLE_BIOMES_VARIANT[1][3] = NewBiomes.MARSH;
		this.MIDDLE_BIOMES_VARIANT[2][2] = Biomes.FLOWER_FOREST;
		this.MIDDLE_BIOMES_VARIANT[2][4] = NewBiomes.TEMPERATE_RAINFOREST;
	}
}
