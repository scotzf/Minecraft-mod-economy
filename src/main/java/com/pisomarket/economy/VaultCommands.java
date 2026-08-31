package com.pisomarket.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.util.InventoryUtil;
import com.pisomarket.util.PisoText;

// /deposit and /withdraw — moving Shards between inventory and vault.
//
// These used to be deliberately unregistered so that the only way to reach
// the vault was standing at a Shop block. That reasoning is dropped in v2:
// the block is no longer required, so leaving the vault unreachable by
// command would just mean it is unreachable.
//
// Both directions move the exact same amount, and every item created is
// subtracted from the vault in the same operation. Amounts are validated
// positive BEFORE touching storage, since a negative amount on a transfer
// reverses its direction — a real exploit, not a hypothetical.
public final class VaultCommands {
	private VaultCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(
					Commands.literal("deposit")
							// No argument = deposit everything held, which is
							// what players actually want most of the time.
							.executes(context -> deposit(context, -1))
							.then(Commands.argument("amount", LongArgumentType.longArg(1))
									.executes(context -> deposit(context, LongArgumentType.getLong(context, "amount"))))
			);

			dispatcher.register(
					Commands.literal("withdraw")
							.then(Commands.argument("amount", LongArgumentType.longArg(1))
									.executes(context -> withdraw(context, LongArgumentType.getLong(context, "amount"))))
			);
		});
	}

	private static PisoVault vault(final ServerPlayer player) {
		return player.level().getServer().getDataStorage().computeIfAbsent(PisoVault.TYPE);
	}

	private static int deposit(final CommandContext<CommandSourceStack> context, final long requested) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long held = player.getInventory().countItem(PisoCurrency.SUNSTONE_SHARD);

		if (held <= 0) {
			context.getSource().sendFailure(PisoText.failure("You have no Sunstone Shards to deposit."));
			return 0;
		}

		long amount = requested < 0 ? held : Math.min(requested, held);
		if (amount <= 0) {
			context.getSource().sendFailure(PisoText.failure("You don't have that many Shards."));
			return 0;
		}

		// Remove FIRST, then credit — if removal somehow comes up short we
		// credit only what was actually taken, so the two can never diverge.
		int removed = removeShards(player, (int) amount);
		if (removed <= 0) {
			context.getSource().sendFailure(PisoText.failure("You have no Sunstone Shards to deposit."));
			return 0;
		}

		vault(player).deposit(player.getUUID(), removed);
		VaultSync.sync(player);
		final int deposited = removed;
		context.getSource().sendSuccess(() -> PisoText.success("Deposited ").append(PisoText.money(deposited)),
				false);
		return 1;
	}

	private static int withdraw(final CommandContext<CommandSourceStack> context, final long amount) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		PisoVault vault = vault(player);

		if (!vault.withdraw(player.getUUID(), amount)) {
			context.getSource().sendFailure(PisoText.failure("Insufficient balance."));
			return 0;
		}

		// Shards cap at a 64-item stack, so hand them over in chunks —
		// otherwise anything above 64 silently vanishes into one oversized
		// stack.
		long remaining = amount;
		while (remaining > 0) {
			int chunk = (int) Math.min(remaining, 64);
			if (!InventoryUtil.giveItem(player, new ItemStack(PisoCurrency.SUNSTONE_SHARD, chunk))) {
				break;
			}
			remaining -= chunk;
		}

		long given = amount - remaining;
		if (remaining > 0) {
			// Didn't all fit — refund the part that didn't, so no money is
			// destroyed by a full inventory.
			vault.deposit(player.getUUID(), remaining);
		}
		VaultSync.sync(player);

		if (given == 0) {
			context.getSource().sendFailure(PisoText.failure("No inventory space."));
			return 0;
		}

		final long withdrawn = given;
		context.getSource().sendSuccess(() -> PisoText.success("Withdrew ").append(PisoText.money(withdrawn)),
				false);
		return 1;
	}

	// Takes up to `amount` Shards out of the player's inventory and returns
	// how many were actually removed.
	private static int removeShards(final ServerPlayer player, final int amount) {
		int left = amount;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() != PisoCurrency.SUNSTONE_SHARD) {
				continue;
			}
			int take = Math.min(left, stack.getCount());
			stack.shrink(take);
			left -= take;
		}
		return amount - left;
	}
}
