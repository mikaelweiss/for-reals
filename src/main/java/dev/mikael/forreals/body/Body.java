package dev.mikael.forreals.body;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.Switch;
import dev.mikael.forreals.body.BodyHeat.Clothing;
import dev.mikael.forreals.body.BodyHeat.Stage;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.Sky;
import dev.mikael.forreals.weather.Weather;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Makes players feel the climate: a body that chills and overheats, warns in stages, then slows, then hurts. */
public final class Body {
	private static final AttachmentType<Double> EXPOSURE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "exposure"), Codec.DOUBLE
	);

	private static final int UPDATE_TICKS = 20;
	private static final int EFFECT_TICKS = 3 * UPDATE_TICKS;
	/** At the far end of exposure, one update in this many hurts. */
	private static final int HURT_EVERY_UPDATES = 2;
	private static final double COMFORTABLE_AIR = 18;
	private static final double WET_DEGREES = 6;
	private static final double STORM_WIND_DEGREES = 4;
	private static final double SUN_DEGREES = 3;
	private static final double SUN_BURNS_ABOVE = 25;
	private static final double ON_FIRE_DEGREES = 30;
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int DEEP_SNOW_LAYERS = 3;
	private static final int VERY_DEEP_SNOW_LAYERS = 5;

	private Body() {
	}

	public static double exposure(final ServerPlayer player) {
		return player.getAttachedOrElse(EXPOSURE, 0.0);
	}

	public static void tick(final MinecraftServer server) {
		if (!Switch.isOn()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((server.getTickCount() + player.getId()) % UPDATE_TICKS == 0) {
				for (int i = 0; i < WorldTime.speedUp(); i++) {
					update(player);
				}
			}

			showFrost(player);
		}
	}

	/** One second of living in the weather. */
	public static void update(final ServerPlayer player) {
		ServerLevel level = player.level();
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL) {
			player.removeAttached(EXPOSURE);
			return;
		}

		Stage before = BodyHeat.stage(exposure(player));
		double exposure = BodyHeat.next(exposure(player), feltTemperature(player), clothing(player), 1);
		player.setAttached(EXPOSURE, exposure);
		Stage stage = BodyHeat.stage(exposure);
		if (stage != before && (stage.isWorseThan(before) || stage == Stage.COMFORTABLE)) {
			player.sendOverlayMessage(Component.literal(stage.note()));
		}

		suffer(player, stage);
		wadeThroughSnow(player);
	}

	public static double feltTemperature(final ServerPlayer player) {
		ServerLevel level = player.level();
		if (!Climate.hasClimate(level)) {
			return COMFORTABLE_AIR;
		}

		BlockPos head = BlockPos.containing(player.getEyePosition());
		double felt = Climate.feltAt(level, head);
		Sky sky = Weather.sky(level, head);
		if (!Climate.hasRoof(level, head)) {
			felt -= STORM_WIND_DEGREES * sky.precipitation();
		}

		if (level.canSeeSky(head) && level.isBrightOutside() && felt > SUN_BURNS_ABOVE) {
			felt += SUN_DEGREES * (1 - sky.cloudCover());
		}

		if (player.isInWaterOrRain()) {
			felt -= WET_DEGREES;
		}

		return player.isOnFire() ? felt + ON_FIRE_DEGREES : felt;
	}

	private static Clothing clothing(final ServerPlayer player) {
		int warm = 0;
		int other = 0;
		for (EquipmentSlot slot : WORN) {
			ItemStack worn = player.getItemBySlot(slot);
			if (worn.is(ItemTags.FREEZE_IMMUNE_WEARABLES)) {
				warm++;
			} else if (!worn.isEmpty()) {
				other++;
			}
		}

		return new Clothing(warm, other);
	}

	private static void suffer(final ServerPlayer player, final Stage stage) {
		if (stage.isColdAtLeast(Stage.COLD)) {
			afflict(player, MobEffects.SLOWNESS, stage.isColdAtLeast(Stage.FREEZING) ? 1 : 0);
		}

		if (stage.isColdAtLeast(Stage.FREEZING)) {
			afflict(player, MobEffects.MINING_FATIGUE, 0);
		}

		if (stage.isHotAtLeast(Stage.HOT)) {
			afflict(player, MobEffects.HUNGER, 0);
		}

		if (stage.isHotAtLeast(Stage.OVERHEATING)) {
			afflict(player, MobEffects.WEAKNESS, 0);
			afflict(player, MobEffects.SLOWNESS, 0);
		}

		ServerLevel level = player.level();
		if (level.getRandom().nextInt(HURT_EVERY_UPDATES) != 0) {
			return;
		}

		if (stage == Stage.HYPOTHERMIA) {
			hurt(player, level.damageSources().freeze());
		} else if (stage == Stage.HEATSTROKE) {
			hurt(player, level.damageSources().dryOut());
		}
	}

	/** Exposure hurts by the same rule starvation does: Easy stops at half health, Normal at half a heart, Hard kills. */
	private static void hurt(final ServerPlayer player, final DamageSource source) {
		float spare = switch (player.level().getDifficulty()) {
			case EASY -> 10;
			case NORMAL -> 1;
			default -> 0;
		};
		if (player.getHealth() > spare) {
			player.hurtServer(player.level(), source, 1);
		}
	}

	private static void wadeThroughSnow(final ServerPlayer player) {
		BlockState feet = player.level().getBlockState(player.blockPosition());
		if (!feet.is(Blocks.SNOW) || player.getItemBySlot(EquipmentSlot.FEET).is(ItemTags.FREEZE_IMMUNE_WEARABLES)) {
			return;
		}

		int layers = feet.getValue(SnowLayerBlock.LAYERS);
		if (layers >= DEEP_SNOW_LAYERS) {
			afflict(player, MobEffects.SLOWNESS, layers >= VERY_DEEP_SNOW_LAYERS ? 2 : 1);
		}
	}

	private static void afflict(final ServerPlayer player, final Holder<MobEffect> effect, final int amplifier) {
		player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, true, false, true));
	}

	/**
	 * Frost creeps over the screen from cold to hypothermia. Vanilla thaws a little every tick and hurts at full
	 * frost, so the frost is set every tick and kept just short of full.
	 */
	private static void showFrost(final ServerPlayer player) {
		double exposure = exposure(player);
		if (exposure > -50) {
			return;
		}

		double share = Math.min(1, (-exposure - 50) / 50);
		player.setTicksFrozen((int)(share * (player.getTicksRequiredToFreeze() - 1)));
	}
}
