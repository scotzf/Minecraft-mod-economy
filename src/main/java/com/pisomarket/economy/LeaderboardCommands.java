package com.pisomarket.economy;

import java.util.List;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.pisomarket.util.PisoText;

// /leaderboard — shows the last published wealth snapshot. Read-only; the board
// itself is built by LeaderboardTracker every 2 in-game days.
public final class LeaderboardCommands {
	private LeaderboardCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal("leaderboard").executes(LeaderboardCommands::showTop))
		);
	}

	private static int showTop(final CommandContext<CommandSourceStack> context) {
		PisoLeaderboard board = context.getSource().getServer().getDataStorage().computeIfAbsent(PisoLeaderboard.TYPE);
		List<PisoLeaderboard.Entry> entries = board.snapshot();

		if (entries.isEmpty()) {
			context.getSource().sendSuccess(
					() -> Component.literal("No leaderboard yet — it appears within seconds of anyone "
							+ "holding Shards or having a vault balance, then refreshes every "
							+ LeaderboardTracker.SNAPSHOT_INTERVAL_DAYS + " in-game days."),
					false
			);
			return 1;
		}

		context.getSource().sendSuccess(
				() -> PisoText.body("Richest players"),
				false
		);

		for (int i = 0; i < entries.size(); i++) {
			PisoLeaderboard.Entry entry = entries.get(i);
			int rank = i + 1;
			// Top three get colour so the board reads at a glance instead of
			// as a wall of identical lines.
			ChatFormatting colour = switch (rank) {
				case 1 -> ChatFormatting.YELLOW;
				case 2 -> ChatFormatting.WHITE;
				case 3 -> ChatFormatting.GOLD;
				default -> ChatFormatting.GRAY;
			};
			context.getSource().sendSuccess(
					() -> PisoText.plain(rank + ". ").append(PisoText.name(entry.name()))
							.append(PisoText.plain(" — ")).append(PisoText.money(entry.total())),
					false
			);
		}

		// Say plainly that this is a snapshot and what it can't see, so a
		// player whose chest hoard is missing knows that's intended.
		context.getSource().sendSuccess(
				() -> Component.literal("Vault + carried Shards. Updates every "
						+ LeaderboardTracker.SNAPSHOT_INTERVAL_DAYS + " in-game days; chests aren't counted.")
						.withStyle(ChatFormatting.DARK_GRAY),
				false
		);
		return 1;
	}
}
