package com.pisomarket.level;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.pisomarket.PisoMarket;
import com.pisomarket.util.PisoText;

// Player stats driven by the VANILLA XP LEVEL — the green number already in
// the HUD. There is no separate Piso level; levelling up the normal way is
// what grants Max Health, Attack and Armor Toughness.
//
// Two deliberate properties:
//
// STATS FOLLOW YOUR PEAK, NOT YOUR CURRENT LEVEL. Enchanting spends XP and
// drops your level; losing hearts because you used an enchanting table
// would be a miserable mechanic. Reaching level 30 grants those stats for
// good, and spending back down to 0 keeps them.
//
// APPLIED AS ATTRIBUTE MODIFIERS, RECOMPUTED WHOLESALE. applyStats always
// removes then re-adds, so it is idempotent — running it twice leaves the
// same result. The alternative (adding one modifier per level gained)
// silently double-applies if a handler ever fires twice, and would surface
// much later as a player with inexplicable stats.
public final class LevelManager {
	// Bonus XP for the activities this mod cares about, weighted
	// farming < mobs < PvP. Awarded as real vanilla XP so it feeds the same
	// bar and the same level the stats read from.
	public static final int XP_FARMING = 1;
	public static final int XP_MOB_KILL = 3;
	public static final int XP_PLAYER_KILL = 9;

	// Every 5th level grants health; all other levels alternate between
	// attack and toughness.
	private static final int HEALTH_EVERY = 5;
	private static final double HEALTH_PER_GRANT = 4.0;   // 2 hearts
	private static final double TOUGHNESS_PER_GRANT = 0.4;
	// Attack deliberately small. The custom weapons already deal 23-44, and
	// a large attack stat on a 44-damage Divine Reaper re-creates the
	// one-shot problem the combat rebalance exists to avoid. Survivability
	// scales; lethality mostly does not.
	private static final double ATTACK_PER_GRANT = 0.15;

	// Vanilla fires no "XP level changed" event, so levels are polled. One
	// second is far below what a player notices and costs a single int
	// comparison per online player.
	private static final int POLL_INTERVAL_TICKS = 20;

	private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "level_health");
	private static final Identifier ATTACK_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "level_attack");
	private static final Identifier TOUGHNESS_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "level_toughness");

	private static int tickCounter;

	private LevelManager() {
	}

	private static PlayerLevels levels(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PlayerLevels.TYPE);
	}

	// How many of each stat grant a level has earned. Levels 2..level are
	// the ones that grant anything; level 1 is the starting state.
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
		return alternatingGrants(level, 0);
	}

	static int toughnessGrants(final int level) {
		return alternatingGrants(level, 1);
	}

	// Non-health levels alternate attack / toughness; `phase` picks which.
	private static int alternatingGrants(final int level, final int phase) {
		int count = 0;
		int nonHealth = 0;
		for (int l = 2; l <= level; l++) {
			if (l % HEALTH_EVERY == 0) {
				continue;
			}
			if (nonHealth % 2 == phase) {
				count++;
			}
			nonHealth++;
		}
		return count;
	}

	public static void applyStats(final ServerPlayer player) {
		int level = levels(player.level().getServer()).effectiveLevel(player.getUUID());

		set(player, Attributes.MAX_HEALTH, HEALTH_ID, healthGrants(level) * HEALTH_PER_GRANT);
		set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, attackGrants(level) * ATTACK_PER_GRANT);
		set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughnessGrants(level) * TOUGHNESS_PER_GRANT);

		// Never leave the player above their maximum — that state does not
		// regenerate correctly.
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void set(final ServerPlayer player, final Holder<Attribute> attribute,
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

	// Raises the recorded peak if this player has levelled past it, then
	// reapplies stats. Does nothing when the peak has not moved, so this is
	// cheap to call on a poll.
	private static void syncLevel(final ServerPlayer player, final boolean announce) {
		syncLevel(player, levels(player.level().getServer()), announce);
	}

	// Takes the PlayerLevels explicitly so the per-tick poll can resolve the
	// SavedData once per pass instead of once per online player.
	private static void syncLevel(final ServerPlayer player, final PlayerLevels data, final boolean announce) {
		int before = data.effectiveLevel(player.getUUID());

		if (!data.raisePeak(player.getUUID(), player.experienceLevel)) {
			return;
		}

		int after = data.effectiveLevel(player.getUUID());
		double gainedHealth = (healthGrants(after) - healthGrants(before)) * HEALTH_PER_GRANT;
		applyStats(player);

		// Fill newly granted hearts. Extra max-health capacity otherwise
		// shows as empty outlines, which reads as "nothing happened".
		if (gainedHealth > 0) {
			player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float) gainedHealth));
		}

		if (announce) {
			player.sendSystemMessage(PisoText.success("Level ").append(PisoText.money(after))
					.append(PisoText.plain(gainedHealth > 0 ? "  +" + (int) (gainedHealth / 2) + " hearts" : "")));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
					SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	public static void register() {
		// Attribute modifiers live on the player ENTITY, and a fresh entity
		// is built on every login and every respawn — so stats must be
		// reapplied both times or they silently vanish.
		// applyStats FIRST, unconditionally. syncLevel alone was not enough:
		// it early-returns when the recorded peak has not moved, which is the
		// normal case on login, so a returning player got no modifiers at all.
		ServerPlayerEvents.JOIN.register(player -> {
			applyStats(player);
			syncLevel(player, false);
		});

		// Respawn rebuilds the player entity from scratch, wiping every
		// attribute modifier — hence reapplying here. The heal matters just
		// as much: vanilla sets health to the DEFAULT max (20) as it
		// respawns you, so raising max health afterwards left the extra
		// hearts empty and read as "my HP reset".
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			applyStats(newPlayer);
			newPlayer.setHealth(newPlayer.getMaxHealth());
		});

		// Vanilla has no XP-level-changed event, so poll for it.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % POLL_INTERVAL_TICKS != 0) {
				return;
			}
			PlayerLevels data = levels(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				syncLevel(player, data, true);
			}
		});

		// Farming XP is awarded by HarvestFaucet, not here — it already
		// resolves whether the broken block was a paying crop, and doing it
		// twice per block break was pure duplicated work.

		// Combat XP — killer must be a player, the same safeguard MobDrops
		// uses so AFK grinders award nothing. This is a BONUS on top of the
		// XP the mob already drops normally.
		//
		// This deliberately stays a SEPARATE listener from MobDrops' rather
		// than being folded in. Both re-test "was the killer a player", but
		// that is one instanceof — merging them would couple the level
		// system to the drop table to save nothing measurable.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(damageSource.getEntity() instanceof ServerPlayer killer) || entity == killer) {
				return;
			}
			killer.giveExperiencePoints(entity instanceof ServerPlayer ? XP_PLAYER_KILL : XP_MOB_KILL);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(
						Commands.literal("level")
								.executes(LevelManager::showLevel)
								// Admin-only, and purely so this is verifiable in game:
								// levelling legitimately is a long grind, which is not a
								// testable loop.
								.then(Commands.literal("set")
										.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
										.then(Commands.argument("player", EntityArgument.player())
												.then(Commands.argument("level", IntegerArgumentType.integer(0, PlayerLevels.MAX_LEVEL))
														.executes(LevelManager::setLevel))))
				)
		);
	}

	private static int setLevel(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		int wanted = IntegerArgumentType.getInteger(context, "level");

		// Set the real vanilla level too, so the HUD number and the stats
		// agree. Setting one without the other is exactly the confusion this
		// rework exists to remove.
		target.setExperienceLevels(wanted);
		levels(context.getSource().getServer()).raisePeak(target.getUUID(), wanted);
		applyStats(target);
		target.setHealth(target.getMaxHealth());

		context.getSource().sendSuccess(
				() -> PisoText.success("Set level to ").append(PisoText.money(wanted)), true);
		return 1;
	}

	private static int showLevel(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int level = levels(context.getSource().getServer()).effectiveLevel(player.getUUID());

		context.getSource().sendSuccess(() -> PisoText.body("Level ").append(PisoText.money(level)), false);
		context.getSource().sendSuccess(() -> PisoText.name(
				"+" + (int) (healthGrants(level) * HEALTH_PER_GRANT) + " health   "
						+ "+" + String.format("%.2f", attackGrants(level) * ATTACK_PER_GRANT) + " attack   "
						+ "+" + String.format("%.1f", toughnessGrants(level) * TOUGHNESS_PER_GRANT) + " toughness"
		), false);
		context.getSource().sendSuccess(() -> PisoText.hint(
				(int) player.getMaxHealth() / 2 + " hearts · stats follow your highest level reached, "
						+ "so enchanting never costs you any"), false);
		return 1;
	}
}
