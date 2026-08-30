package com.pisomarket.revive;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;
import com.pisomarket.shop.system.PisoShopStock;

// The revive system from CLAUDE.md's "v2 redesign" §11: a death holds the
// player instead of letting them play immediately, the hold gets longer
// per consecutive death (up to a cap), and paying from the vault skips
// whatever's left.
//
// Deliberately does NOT try to intercept the death-screen respawn click —
// CLAUDE.md already flags that path as fiddly (the client predicts the
// respawn locally; fighting that is a resync problem, not a one-line
// cancel). Instead this lets vanilla respawn happen normally and then, in
// AFTER_RESPAWN, immediately switches the freshly-respawned player to
// SPECTATOR for the hold duration — they're back, they can look around,
// they just can't touch anything until released. Simpler, and avoids the
// exact class of bug this project has already been burned by once
// (denying a placement server-side without resyncing the client).
public final class ReviveManager {
	static final int RESET_WINDOW_DAYS = 3;

	private static final int BASE_SECONDS = 120;
	private static final int INCREMENT_SECONDS = 120;
	private static final int CAP_SECONDS = 3600;
	private static final long PRICE_PER_SECOND = 3;

	// Computed at death, consumed at the next AFTER_RESPAWN for that player.
	// A death always immediately precedes exactly one respawn, so there's no
	// ambiguity about which death a pending value belongs to.
	private static final Map<UUID, Integer> PENDING_HOLD_SECONDS = new HashMap<>();

	// Active holds: player -> the server game-time tick they're released at.
	// Transient by design — a mid-cooldown server restart releasing everyone
	// immediately is an acceptable edge case (see ReviveState for the part
	// that ISN'T acceptable to lose).
	private static final Map<UUID, Long> RELEASE_AT_TICK = new HashMap<>();
	private static final Map<UUID, GameType> ORIGINAL_GAME_TYPE = new HashMap<>();

	private ReviveManager() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			int today = PisoShopStock.currentDay(player.level().getServer());
			ReviveState state = player.level().getServer().getDataStorage().computeIfAbsent(ReviveState.TYPE);
			int consecutive = state.recordDeath(today, player.getUUID());
			int seconds = Math.min(BASE_SECONDS + (consecutive - 1) * INCREMENT_SECONDS, CAP_SECONDS);
			PENDING_HOLD_SECONDS.put(player.getUUID(), seconds);
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// alive == true means this was a dimension change or similar,
			// not an actual death respawn — nothing to hold for.
			if (alive) {
				return;
			}
			Integer seconds = PENDING_HOLD_SECONDS.remove(newPlayer.getUUID());
			if (seconds == null) {
				return;
			}
			beginHold(newPlayer, seconds);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (RELEASE_AT_TICK.isEmpty()) {
				return;
			}
			long now = server.overworld().getGameTime();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Long releaseAt = RELEASE_AT_TICK.get(player.getUUID());
				if (releaseAt != null && now >= releaseAt) {
					release(player);
				}
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal("revive").executes(ReviveManager::tryPay))
		);
	}

	private static void beginHold(final ServerPlayer player, final int seconds) {
		ORIGINAL_GAME_TYPE.put(player.getUUID(), player.gameMode());
		player.setGameMode(GameType.SPECTATOR);
		RELEASE_AT_TICK.put(player.getUUID(), player.level().getServer().overworld().getGameTime() + seconds * 20L);
		player.sendSystemMessage(Component.literal(
				"You're held for " + seconds + "s before you can play again. "
						+ "Pay " + (seconds * PRICE_PER_SECOND) + " from your vault with /revive to skip it."
		));
	}

	private static void release(final ServerPlayer player) {
		RELEASE_AT_TICK.remove(player.getUUID());
		GameType original = ORIGINAL_GAME_TYPE.remove(player.getUUID());
		player.setGameMode(original != null ? original : GameType.SURVIVAL);
		player.sendSystemMessage(Component.literal("You can play again."));
	}

	private static int tryPay(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Long releaseAt = RELEASE_AT_TICK.get(player.getUUID());
		if (releaseAt == null) {
			context.getSource().sendFailure(Component.literal("You're not being held right now."));
			return 0;
		}

		long remainingTicks = Math.max(0, releaseAt - player.level().getServer().overworld().getGameTime());
		long remainingSeconds = (remainingTicks + 19) / 20; // round up — never let a fraction of a second be free
		if (remainingSeconds <= 0) {
			// The hold already elapsed; the tick loop just hasn't caught up
			// yet this tick. Nothing to pay for.
			release(player);
			context.getSource().sendSuccess(() -> Component.literal("You can play again."), false);
			return 1;
		}
		long cost = remainingSeconds * PRICE_PER_SECOND;

		PisoVault vault = player.level().getServer().getDataStorage().computeIfAbsent(PisoVault.TYPE);
		if (!vault.withdraw(player.getUUID(), cost)) {
			context.getSource().sendFailure(Component.literal(
					"Need " + cost + " to skip the remaining " + remainingSeconds + "s — you don't have enough."
			));
			return 0;
		}

		VaultSync.sync(player);
		release(player);
		context.getSource().sendSuccess(() -> Component.literal("Paid " + cost + " — you can play again."), false);
		return 1;
	}
}
