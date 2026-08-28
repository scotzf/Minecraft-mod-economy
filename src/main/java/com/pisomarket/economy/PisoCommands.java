package com.pisomarket.economy;

import java.util.Collection;
import java.util.UUID;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Registers /balance, /pay, /deposit, /withdraw. See CLAUDE.md's "In-game
// command surface" for the full documented behavior of each.
public final class PisoCommands {
	private PisoCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(Commands.literal("balance").executes(PisoCommands::balance));

			dispatcher.register(
					Commands.literal("pay")
							.then(
									Commands.argument("player", GameProfileArgument.gameProfile())
											.then(
													Commands.argument("amount", LongArgumentType.longArg(1))
															.executes(PisoCommands::pay)
											)
							)
			);

			dispatcher.register(
					Commands.literal("deposit")
							.executes(context -> deposit(context, Long.MAX_VALUE))
							.then(
									Commands.argument("amount", LongArgumentType.longArg(1))
											.executes(context -> deposit(context, LongArgumentType.getLong(context, "amount")))
							)
			);

			dispatcher.register(
					Commands.literal("withdraw")
							.then(
									Commands.argument("amount", LongArgumentType.longArg(1))
											.executes(PisoCommands::withdraw)
							)
			);
		});
	}

	private static PisoVault vault(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoVault.TYPE);
	}

	private static int balance(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long amount = vault(context.getSource().getServer()).getBalance(player.getUUID());
		VaultSync.sync(player); // cheap self-heal if the HUD ever drifts
		context.getSource().sendSuccess(() -> Component.literal("Balance: " + amount), false);
		return 1;
	}

	private static int pay(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer sender = context.getSource().getPlayerOrException();
		long amount = LongArgumentType.getLong(context, "amount");

		// GameProfileArgument resolves by name even if the target has never
		// been online this session (it goes through the server's persistent
		// name->UUID cache), matching CLAUDE.md's "recipient may be offline".
		Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "player");
		NameAndId target = targets.iterator().next();
		UUID targetId = target.id();

		if (targetId.equals(sender.getUUID())) {
			context.getSource().sendFailure(Component.literal("You can't pay yourself"));
			return 0;
		}

		MinecraftServer server = context.getSource().getServer();
		boolean success = vault(server).transfer(sender.getUUID(), targetId, amount);
		if (!success) {
			context.getSource().sendFailure(Component.literal("Insufficient balance"));
			return 0;
		}

		VaultSync.sync(sender);
		ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetId);
		if (targetPlayer != null) {
			VaultSync.sync(targetPlayer);
		}

		String targetName = target.name();
		context.getSource().sendSuccess(() -> Component.literal("Paid " + amount + " to " + targetName), false);
		return 1;
	}

	private static int deposit(
			final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, final long requestedAmount
	) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long held = player.getInventory().countItem(Items.POISONOUS_POTATO);
		long amount = Math.min(held, requestedAmount);

		if (amount <= 0) {
			context.getSource().sendFailure(Component.literal("You have no Poisonous Potato to deposit"));
			return 0;
		}

		// Credit the vault BEFORE touching the inventory. If anything here
		// throws, the player still has every item — a failed deposit
		// should never be able to destroy items, only refuse to happen.
		try {
			vault(context.getSource().getServer()).deposit(player.getUUID(), amount);
		} catch (RuntimeException e) {
			com.pisomarket.PisoMarket.LOGGER.error("Deposit failed for {} amount {}", player.getGameProfile().name(), amount, e);
			context.getSource().sendFailure(Component.literal("Deposit failed — nothing was taken. This has been logged."));
			return 0;
		}

		removeItems(player, amount);
		VaultSync.sync(player);
		context.getSource().sendSuccess(() -> Component.literal("Deposited " + amount), false);
		return 1;
	}

	private static int withdraw(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long amount = LongArgumentType.getLong(context, "amount");

		PisoVault vault = vault(context.getSource().getServer());
		if (!vault.withdraw(player.getUUID(), amount)) {
			context.getSource().sendFailure(Component.literal("Insufficient balance"));
			return 0;
		}

		long given = giveItems(player, amount);
		if (given < amount) {
			// Inventory didn't have room for everything — refund the part
			// that couldn't be given so no money is destroyed.
			vault.deposit(player.getUUID(), amount - given);
		}
		if (given == 0) {
			context.getSource().sendFailure(Component.literal("No inventory space"));
			return 0;
		}

		VaultSync.sync(player);
		long finalGiven = given;
		context.getSource().sendSuccess(() -> Component.literal("Withdrew " + finalGiven), false);
		return 1;
	}

	// Removes exactly `amount` poisonous potatoes from the player's
	// inventory. Caller must have already confirmed the player holds at
	// least this many (see countItem in deposit()).
	private static void removeItems(final ServerPlayer player, final long amount) {
		long remaining = amount;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() == Items.POISONOUS_POTATO) {
				int take = (int) Math.min(remaining, stack.getCount());
				stack.shrink(take);
				remaining -= take;
			}
		}
	}

	// Gives up to `amount` poisonous potatoes to the player, stopping early
	// if the inventory fills up. Returns how many were actually given.
	private static long giveItems(final ServerPlayer player, final long amount) {
		long remaining = amount;
		while (remaining > 0) {
			int stackSize = (int) Math.min(remaining, 64);
			ItemStack stack = new ItemStack(Items.POISONOUS_POTATO, stackSize);
			boolean added = player.getInventory().add(stack);
			if (!added || !stack.isEmpty()) {
				int leftover = stack.isEmpty() ? 0 : stack.getCount();
				remaining -= stackSize - leftover;
				break;
			}
			remaining -= stackSize;
		}
		return amount - remaining;
	}
}
