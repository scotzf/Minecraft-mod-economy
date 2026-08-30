package com.pisomarket.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// Registers /eco give|take|set|total — the admin-only vault mutations from
// CLAUDE.md's "In-game command surface". Gated at permission level 2, same
// as every other admin command in that doc. This was documented as part of
// the command surface since early in the project but never actually
// registered until now — added because there was otherwise no way to fund
// a vault balance for testing except depositing real potatoes at the Shop
// block or waiting on the harvest faucet.
//
// Uses EntityArgument.player() rather than GameProfileArgument (as /pay
// does) so @s and other selectors work — needed for pisomarket:testkit,
// which calls "eco set @s ..." from inside a datapack function.
// GameProfileArgument rejects selectors at datapack-load validation time
// (a function with no player context can't resolve one), which is exactly
// what broke first. The real cost: /eco can't target an offline player the
// way /pay can. That's an acceptable trade for an admin/testing command.
public final class EcoCommands {
	private EcoCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(
						Commands.literal("eco")
								// LEVEL_GAMEMASTERS is 26.2's replacement for the old
								// int op-level system (source.hasPermission(int) no
								// longer exists) — it's the same "level 2" CLAUDE.md's
								// admin commands are documented to require.
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(
										Commands.literal("give")
												.then(Commands.argument("player", EntityArgument.player())
														.then(Commands.argument("amount", LongArgumentType.longArg(1))
																.executes(context -> give(context, LongArgumentType.getLong(context, "amount")))))
								)
								.then(
										Commands.literal("take")
												.then(Commands.argument("player", EntityArgument.player())
														.then(Commands.argument("amount", LongArgumentType.longArg(1))
																.executes(context -> take(context, LongArgumentType.getLong(context, "amount")))))
								)
								.then(
										Commands.literal("set")
												.then(Commands.argument("player", EntityArgument.player())
														.then(Commands.argument("amount", LongArgumentType.longArg(0))
																.executes(context -> set(context, LongArgumentType.getLong(context, "amount")))))
								)
								.then(Commands.literal("total").executes(EcoCommands::total))
				)
		);
	}

	private static PisoVault vault(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoVault.TYPE);
	}

	private static int give(final CommandContext<CommandSourceStack> context, final long amount) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		vault(context.getSource().getServer()).deposit(target.getUUID(), amount);
		VaultSync.sync(target);
		logAdminAction(context, "Gave " + amount + " to " + target.getGameProfile().name());
		context.getSource().sendSuccess(() -> Component.literal("Gave " + amount), true);
		return 1;
	}

	private static int take(final CommandContext<CommandSourceStack> context, final long amount) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		boolean success = vault(context.getSource().getServer()).withdraw(target.getUUID(), amount);
		if (!success) {
			context.getSource().sendFailure(Component.literal("That player doesn't have " + amount));
			return 0;
		}
		VaultSync.sync(target);
		logAdminAction(context, "Took " + amount + " from " + target.getGameProfile().name());
		context.getSource().sendSuccess(() -> Component.literal("Took " + amount), true);
		return 1;
	}

	private static int set(final CommandContext<CommandSourceStack> context, final long amount) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		vault(context.getSource().getServer()).setBalance(target.getUUID(), amount);
		VaultSync.sync(target);
		logAdminAction(context, "Set " + target.getGameProfile().name() + " to " + amount);
		context.getSource().sendSuccess(() -> Component.literal("Set balance to " + amount), true);
		return 1;
	}

	private static int total(final CommandContext<CommandSourceStack> context) {
		long total = vault(context.getSource().getServer()).totalVaultBalance();
		context.getSource().sendSuccess(
				() -> Component.literal("Total vault balance: " + total + " (vault only — cash in inventories/chests isn't tracked)"),
				false
		);
		return 1;
	}

	// Every admin mint/burn is logged, per CLAUDE.md's rule for /eco give
	// and /eco take specifically ("log every use").
	private static void logAdminAction(final CommandContext<CommandSourceStack> context, final String message) {
		com.pisomarket.PisoMarket.LOGGER.info("[eco] {} (by {})", message, context.getSource().getTextName());
	}
}
