package com.pisomarket.claims;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.util.InventoryUtil;

// Registers /deed browse and /deed buy <id> — buying a Land Deed from
// BlackMarket. Separate from ShopCommands because deeds need a
// custom-built ItemStack (size baked into CUSTOM_DATA), not a plain
// vanilla item, so they don't fit ShopCommands.tryBuy's generic path.
public final class DeedCommands {
	private DeedCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(
					Commands.literal("deed")
							.then(Commands.literal("browse").executes(DeedCommands::browse))
							.then(
									Commands.literal("buy")
											.then(
													Commands.argument("id", IntegerArgumentType.integer(1))
															.executes(context -> buy(context, IntegerArgumentType.getInteger(context, "id")))
											)
							)
							// Run by the [Yes, claim here] / [Cancel] buttons
							// on the confirmation page (LandDeedItem.useOn).
							.then(Commands.literal("confirm").executes(DeedCommands::confirmClaim))
							.then(Commands.literal("cancel").executes(DeedCommands::cancelClaim))
			);
		});
	}

	private static int confirmClaim(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof LandDeedItem)) {
			context.getSource().sendFailure(Component.literal("Hold the Land Deed you want to activate."));
			return 0;
		}

		String error = LandDeedItem.confirmPendingClaim(player, held);
		if (error != null) {
			context.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	private static int cancelClaim(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof LandDeedItem) {
			LandDeedItem.cancelPendingClaim(held);
		}
		context.getSource().sendSuccess(() -> Component.literal("Claim cancelled — nothing was claimed."), false);
		return 1;
	}

	private static int browse(final CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("Land Deeds (BlackMarket)"), false);
		for (DeedCatalog.DeedSize size : DeedCatalog.SIZES) {
			String line = "#" + size.id() + " — " + size.label() + " (" + size.width() + "x" + size.length() + "x" + size.height() + ") — " + size.price();
			context.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int buy(final CommandContext<CommandSourceStack> context, final int id) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String error = tryBuy(player, id);
		if (error != null) {
			context.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Bought a deed — right-click the ground where you want to claim it"), false);
		return 1;
	}

	// Shared by the /deed buy command and PisoShopMenu's BlackMarket grid.
	// Returns null on success, or a player-facing error message.
	public static String tryBuy(final ServerPlayer player, final int id) {
		DeedCatalog.DeedSize size = DeedCatalog.byId(id);
		if (size == null) {
			return "No such deed #" + id;
		}

		PisoVault vault = player.level().getServer().getDataStorage().computeIfAbsent(PisoVault.TYPE);
		if (!vault.withdraw(player.getUUID(), size.price())) {
			return "Insufficient balance";
		}

		ItemStack stack = LandDeedItem.createUnbound(ClaimsContent.LAND_DEED, size.label(), size.width(), size.length(), size.height(), size.price());
		if (!InventoryUtil.giveItem(player, stack)) {
			vault.deposit(player.getUUID(), size.price());
			com.pisomarket.economy.VaultSync.sync(player);
			return "No inventory space";
		}

		com.pisomarket.economy.VaultSync.sync(player);
		return null;
	}
}
