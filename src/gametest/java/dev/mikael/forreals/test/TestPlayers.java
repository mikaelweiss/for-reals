package dev.mikael.forreals.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

final class TestPlayers {
	/** Tests that put a player online run in their own batch, apart from tests that need an empty server. */
	static final String ENVIRONMENT = "forreals-test:with_player";

	private TestPlayers() {
	}

	/** Nothing ticks this player. A test that needs it to live calls {@link ServerPlayer#doTick} itself. */
	static ServerPlayer join(final GameTestHelper helper, final GameType mode) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-player"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return mode;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	static void leave(final ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	/** Runs assertions and takes the player offline if they fail, so a failed test cannot leak a player into the next. */
	static void leaveOnFailure(final ServerPlayer player, final Runnable assertions) {
		try {
			assertions.run();
		} catch (RuntimeException failure) {
			leave(player);
			throw failure;
		}
	}
}
