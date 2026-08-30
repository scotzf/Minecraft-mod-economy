package com.pisomarket.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.pisomarket.economy.PisoCurrency;

// Keeps the wealth leaderboard up to date. See PisoLeaderboard for why this
// is a periodic snapshot rather than a live ranking.
//
// Two jobs, deliberately separated:
//   1. Remember what online players are carrying, refreshed on a slow tick
//      and again the moment they disconnect. This is the only chance we get
//      to see inventory contents at all.
//   2. Every 2 in-game days, rank everyone and publish the board.
public final class LeaderboardTracker {
	public static final int SNAPSHOT_INTERVAL_DAYS = 2;

	// Same cadence as RentCollector — slow enough to be free, frequent
	// enough that a player who logs off mid-day still gets counted.
	private static final int CHECK_INTERVAL_TICKS = 200; // ~10s
	private static final long TICKS_PER_DAY = 24000L;

	private static int tickCounter;

	private LeaderboardTracker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter % CHECK_INTERVAL_TICKS != 0) {
				return;
			}

			PisoLeaderboard board = board(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				board.recordCarried(player.getUUID(), player.getGameProfile().name(), countCarried(player));
			}

			int today = currentDay(server);
			if (today >= board.lastSnapshotDay() + SNAPSHOT_INTERVAL_DAYS) {
				publish(server, board, today);
			}
		});

		// Catch the player's final inventory on the way out. Without this a
		// board published while they're offline would use a count up to ten
		// seconds stale — and worse, someone who logs off right after
		// spending would still show as rich.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			board(server).recordCarried(player.getUUID(), player.getGameProfile().name(), countCarried(player));
		});
	}

	private static PisoLeaderboard board(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoLeaderboard.TYPE);
	}

	// Day number, matching CLAUDE.md's rule to store dates as integer day
	// numbers rather than timestamps — integer comparison, and it survives
	// restarts cleanly.
	//
	// getGameTime, NOT the overworld clock: game time is total ticks the
	// world has ever run and only ever increases. The day/night clock can be
	// moved backwards by /time set, which would freeze the leaderboard
	// indefinitely (or, set forward, let someone trigger snapshots on
	// demand). Like every other in-game timer here it stops while the server
	// is empty, so "2 days" means 2 days of the world actually running.
	private static int currentDay(final MinecraftServer server) {
		return (int) (server.overworld().getGameTime() / TICKS_PER_DAY);
	}

	// Cash in hand: the main inventory plus the ender chest. The ender chest
	// counts because Land Deeds already treat it as the player's own secure
	// storage, so money kept there is still plainly theirs. Ordinary chests
	// are NOT counted and cannot be — see PisoLeaderboard.
	private static long countCarried(final ServerPlayer player) {
		return (long) player.getInventory().countItem(PisoCurrency.SUNSTONE_SHARD)
				+ player.getEnderChestInventory().countItem(PisoCurrency.SUNSTONE_SHARD);
	}

	private static void publish(final MinecraftServer server, final PisoLeaderboard board, final int today) {
		PisoVault vault = server.getDataStorage().computeIfAbsent(PisoVault.TYPE);

		// Rank every player we have ever seen carrying anything, not just
		// those online right now — otherwise the board would empty out as
		// soon as people logged off.
		// Everyone we have ever seen goes on the board, INCLUDING players
		// with nothing.
		//
		// This used to skip anyone whose total was 0, which meant a server
		// where nobody had earned yet showed a completely blank leaderboard
		// — indistinguishable from the feature being broken. A board reading
		// "1. Scotz - 0" is honest and visibly working; an empty one just
		// looks like a bug, and did.
		List<PisoLeaderboard.Entry> ranked = new ArrayList<>();
		for (Map.Entry<UUID, Long> entry : board.carried().entrySet()) {
			UUID id = entry.getKey();
			long total = entry.getValue() + vault.getBalance(id);
			ranked.add(new PisoLeaderboard.Entry(id, board.nameFor(id), total));
		}

		// Nothing to rank yet — leave the clock ALONE and try again on the
		// next check.
		//
		// Publishing an empty board here was a real bug: it stamped
		// lastSnapshotDay, so a single check that happened to run while the
		// server was empty (or before anyone had earned anything) locked the
		// leaderboard blank for a further 2 in-game days. Skipping instead
		// means the first real board appears within ten seconds of the first
		// player having any money.
		if (ranked.isEmpty()) {
			return;
		}

		ranked.sort((a, b) -> Long.compare(b.total(), a.total()));
		if (ranked.size() > PisoLeaderboard.TOP_N) {
			ranked = ranked.subList(0, PisoLeaderboard.TOP_N);
		}

		board.publish(today, ranked);

		// Only announce once there is actually a leader worth naming — an
		// all-zero board still publishes (so it is visible and obviously
		// working) but broadcasting "Leader: someone (0)" is just noise.
		if (ranked.get(0).total() <= 0) {
			return;
		}

		server.getPlayerList().broadcastSystemMessage(
				Component.literal("Richest players updated — /leaderboard to see the board. Leader: "
						+ ranked.get(0).name() + " (" + ranked.get(0).total() + ")"),
				false
		);
	}
}
