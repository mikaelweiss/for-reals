package dev.mikael.forreals.test;

import dev.mikael.forreals.body.Body;
import dev.mikael.forreals.time.Calendar;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class BodyTests {
	private static final long MIDWINTER_NIGHT = (long)(0.875 * Calendar.TICKS_PER_YEAR) + 20000;
	private static final long MIDSUMMER_AFTERNOON = (long)(0.375 * Calendar.TICKS_PER_YEAR) + 8000;
	private static final int FIVE_MINUTES = 300;
	private static final int TEN_MINUTES = 600;
	private static final BlockPos STANDING = new BlockPos(3, 1, 3);

	private static ServerPlayer playerOutside(final GameTestHelper helper, final long calendarTicks) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}

		TestClimate.setCalendar(helper, calendarTicks);
		TestClimate.clearSkies(helper);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL);
		BlockPos spot = helper.absolutePos(STANDING);
		player.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		return player;
	}

	private static void live(final ServerPlayer player, final int seconds) {
		for (int i = 0; i < seconds; i++) {
			Body.update(player);
		}
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void aWinterNightFreezesABarePlayerButSparesTheirLastHalfHeart(final GameTestHelper helper) {
		TestClimate.pin(helper, TestClimate.FROZEN);
		ServerPlayer player = playerOutside(helper, MIDWINTER_NIGHT);
		TestPlayers.leaveOnFailure(player, () -> {
			live(player, TEN_MINUTES);
			helper.assertTrue(Body.exposure(player) == -100, "a bare player in deep frost ended at exposure " + Body.exposure(player));
			helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "freezing did not slow the player");
			for (int second = 0; second < TEN_MINUTES; second++) {
				player.damageCooldownTime = 0;
				Body.update(player);
			}

			helper.assertTrue(player.isAlive() && player.getHealth() == 1, "on Normal the cold should stop at half a heart, health is " + player.getHealth());
		});
		TestPlayers.leave(player);
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void leatherAndAFireKeepAWinterNightSurvivable(final GameTestHelper helper) {
		TestClimate.pin(helper, TestClimate.TEMPERATE);
		ServerPlayer player = playerOutside(helper, MIDWINTER_NIGHT);
		TestPlayers.leaveOnFailure(player, () -> {
			double bare = Body.feltTemperature(player);
			helper.setBlock(STANDING.east(), Blocks.CAMPFIRE);
			helper.assertTrue(Body.feltTemperature(player) > bare + 5, "a campfire one block away added no warmth");
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.setItemSlot(slot, new ItemStack(slot == EquipmentSlot.HEAD ? Items.LEATHER_HELMET : slot == EquipmentSlot.CHEST ? Items.LEATHER_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.LEATHER_LEGGINGS : Items.LEATHER_BOOTS));
			}

			live(player, TEN_MINUTES);
			helper.assertTrue(Body.exposure(player) == 0, "leather and a fire left the player at exposure " + Body.exposure(player));
		});
		TestPlayers.leave(player);
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void desertSunOverheatsAndGoingIndoorsFixesIt(final GameTestHelper helper) {
		TestClimate.pin(helper, TestClimate.SCORCHING_AFTERNOONS);
		ServerPlayer player = playerOutside(helper, MIDSUMMER_AFTERNOON);
		TestPlayers.leaveOnFailure(player, () -> {
			live(player, FIVE_MINUTES);
			helper.assertTrue(Body.exposure(player) > 50, "five minutes of desert sun left exposure at " + Body.exposure(player) + ", felt " + Body.feltTemperature(player));
			helper.assertTrue(player.hasEffect(MobEffects.HUNGER), "heat did not make the player hungry");
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					helper.setBlock(x, 4, z, Blocks.STONE);
					if (x == 0 || x == 7 || z == 0 || z == 7) {
						helper.setBlock(x, 1, z, Blocks.STONE);
						helper.setBlock(x, 2, z, Blocks.STONE);
						helper.setBlock(x, 3, z, Blocks.STONE);
					}
				}
			}
		});
		helper.runAfterDelay(10, () -> {
			TestPlayers.leaveOnFailure(player, () -> {
				TestClimate.pin(helper, TestClimate.SCORCHING_AFTERNOONS);
				TestClimate.setCalendar(helper, MIDSUMMER_AFTERNOON);
				live(player, FIVE_MINUTES);
				helper.assertTrue(Body.exposure(player) == 0, "a closed room left the player at exposure " + Body.exposure(player));
			});
			TestPlayers.leave(player);
			helper.succeed();
		});
	}
}
