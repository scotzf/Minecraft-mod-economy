package com.pisomarket.level;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.pisomarket.PisoMarket;
import com.pisomarket.economy.harvest.HarvestFaucet;

import com.pisomarket.util.PisoText;

// The level system: XP from farming, mob kills and PvP, spent automatically
// on Max Health, Attack and Armor Toughness.
//
// Stats are applied as ATTRIBUTE MODIFIERS, recomputed from scratch on every
// level-up and on every login. That is deliberately idempotent — the
// alternative (incrementally adding a modifier per level) silently
// double-applies if a login handler ever runs twice, and the bug would only
// show up as a player with mysteriously wrong stats much later.
public final class LevelManager {
	// XP weighting, farming < mobs < PvP.
	public static final int XP_FARMING = 1;
	public static final int XP_MOB_KILL = 3;
	public static final int XP_PLAYER_KILL = 9;

	// Every 5th level grants health; all other levels alternate between
	// attack and toughness.
	private static final int HEALTH_EVERY = 5;
	// Doubled from the first pass so levelling actually feels like
	// progression: +40 HP and +8 toughness at level 50, not +20 and +4.
	private static final double HEALTH_PER_GRANT = 4.0;   // 2 hearts
	private static final double TOUGHNESS_PER_GRANT = 0.4;
	// Attack deliberately NOT doubled. The custom weapons already deal
	// 23-44, and stacking a large attack stat on a 44-damage Divine Reaper
	// re-creates the one-shot problem the whole combat rebalance exists to
	// avoid. Survivability scales; lethality mostly does not.
	private static final double ATTACK_PER_GRANT = 0.15;

	private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "level_health");
	private static final Identifier ATTACK_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "level_attack");
	private static final Identifier TOUGHNESS_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "level_toughness");

	private LevelManager() {
	}

	private static PlayerLevels levels(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PlayerLevels.TYPE);
	}

	// How many of each stat grant a player at this level has earned.
	// Levels 2..level are the ones that granted anything (level 1 is the
	// starting state and grants nothing).
	static int healthGrants(final int level) {
		int count = 0;
		for (int l = 2; l <= level; l++) {
			if (l % HEALTH_EVERY == 0) {
				count++;
			}
		}
		return count;
	}

	static int attackGrants(final int level) {
		int count = 0;
		int nonHealth = 0;
		for (int l = 2; l <= level; l++) {
			if (l % HEALTH_EVERY == 0) {
				continue;
			}
			// Alternate attack / toughness across the non-health levels.
			if (nonHealth % 2 == 0) {
				count++;
			}
			nonHealth++;
		}
		return count;
	}

	static int toughnessGrants(final int level) {
		int nonHealth = 0;
		int count = 0;
		for (int l = 2; l <= level; l++) {
			if (l % HEALTH_EVERY == 0) {
				continue;
			}
			if (nonHealth % 2 == 1) {
				count++;
			}
			nonHealth++;
		}
		return count;
	}

	// Recomputes every stat modifier from the player's current level.
	// Idempotent by construction: each modifier is removed before being
	// re-added, so calling this twice leaves the same result.
	public static void applyStats(final ServerPlayer player) {
		int level = levels(player.level().getServer()).levelOf(player.getUUID());

		set(player, Attributes.MAX_HEALTH, HEALTH_ID, healthGrants(level) * HEALTH_PER_GRANT);
		set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, attackGrants(level) * ATTACK_PER_GRANT);
		set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughnessGrants(level) * TOUGHNESS_PER_GRANT);

		// Clamp health into the new maximum. Without this a player who
		// somehow lost max health would sit above it and never regen
		// correctly.
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void set(final ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			final Identifier id, final double value) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		if (value != 0.0) {
			instance.addPermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void award(final ServerPlayer player, final int xp) {
		MinecraftServer server = player.level().getServer();
		int gained = levels(server).addXp(player.getUUID(), xp);
		if (gained <= 0) {
			return;
		}
		applyStats(player);
		int level = levels(server).levelOf(player.getUUID());
		player.sendSystemMessage(PisoText.success("Level ").append(PisoText.money(level)).append(PisoText.plain("!")));
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
				SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	public static void register() {
		// Stats must be reapplied on join: attribute modifiers added with
		// addPermanentModifier live on the player entity, and a fresh
		// entity is built on every login and every respawn.
		ServerPlayerEvents.JOIN.register(LevelManager::applyStats);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyStats(newPlayer));

		// Farming XP — same player-break-only rule as the currency faucet,
		// and only for crops that actually pay out, so breaking a stone
		// block or an immature seedling earns nothing.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}
			if (HarvestFaucet.baseChanceFor(state) > 0.0) {
				award(serverPlayer, XP_FARMING);
			}
		});

		// Combat XP — player must land the killing blow, same safeguard
		// MobDrops uses so AFK grinders award nothing.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(damageSource.getEntity() instanceof ServerPlayer killer)) {
				return;
			}
			if (entity == killer) {
				return; // no XP for killing yourself
			}
			award(killer, entity instanceof ServerPlayer ? XP_PLAYER_KILL : XP_MOB_KILL);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(
						Commands.literal("level")
								.executes(LevelManager::showLevel)
								// Admin-only, and purely so this can be VERIFIED in
								// game — levelling to 50 legitimately takes tens of
								// thousands of actions, which is not a testable loop.
								.then(Commands.literal("set")
										.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
										.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
												.then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, PlayerLevels.MAX_LEVEL))
														.executes(LevelManager::setLevel))))
				)
		);
	}

	private static int setLevel(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
		int wanted = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");

		PlayerLevels data = levels(context.getSource().getServer());
		data.setLevel(target.getUUID(), wanted);
		applyStats(target);
		// Fill the new hearts, otherwise the extra capacity shows as empty
		// and it looks like nothing happened.
		target.setHealth(target.getMaxHealth());

		context.getSource().sendSuccess(() -> PisoText.success("Set Piso level to ")
				.append(PisoText.money(wanted)), true);
		return 1;
	}

	private static int showLevel(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		PlayerLevels data = levels(context.getSource().getServer());
		int level = data.levelOf(player.getUUID());
		int xp = data.xpOf(player.getUUID());

		context.getSource().sendSuccess(() -> PisoText.body("Piso level ").append(PisoText.money(level)), false);
		// Spelled out because this is NOT the green XP number in the HUD —
		// that is vanilla's enchanting XP and drives nothing here.
		context.getSource().sendSuccess(
				() -> PisoText.hint("separate from your vanilla XP level (" + player.experienceLevel + ")"), false);

		if (level < PlayerLevels.MAX_LEVEL) {
			context.getSource().sendSuccess(() -> Component.literal(
					xp + " / " + PlayerLevels.xpToNext(level) + " XP").withStyle(ChatFormatting.DARK_GRAY), false);
		} else {
			context.getSource().sendSuccess(() -> PisoText.hint("Max level"), false);
		}

		context.getSource().sendSuccess(() -> PisoText.name(
				"+" + (int) (healthGrants(level) * HEALTH_PER_GRANT) + " health   "
						+ "+" + String.format("%.2f", attackGrants(level) * ATTACK_PER_GRANT) + " attack   "
						+ "+" + String.format("%.1f", toughnessGrants(level) * TOUGHNESS_PER_GRANT) + " toughness"
		), false);
		context.getSource().sendSuccess(
				() -> PisoText.hint("max health now " + (int) player.getMaxHealth()
						+ " (" + (int) (player.getMaxHealth() / 2) + " hearts)"), false);
		return 1;
	}
}
