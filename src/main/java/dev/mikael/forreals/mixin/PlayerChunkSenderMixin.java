package dev.mikael.forreals.mixin;

import dev.mikael.forreals.seasons.SeasonalLook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {
	@Inject(method = "sendChunk", at = @At("TAIL"))
	private static void forreals$theSeasonFollowsTheChunk(final ServerGamePacketListenerImpl connection, final ServerLevel level, final LevelChunk chunk, final CallbackInfo ci) {
		SeasonalLook.chunkSent(connection.player, level, chunk);
	}
}
