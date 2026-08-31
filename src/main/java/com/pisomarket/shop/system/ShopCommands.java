package com.pisomarket.shop.system;

import java.util.List;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;
import com.pisomarket.util.InventoryUtil;

import com.pisomarket.util.PisoText;

// Registers /shop browse [tier] and /shop buy <id> [qty]. See CLAUDE.md's
// "System shop catalog" and ShopEntry's scope note for what's actually in
// the catalog right now.
public final class ShopCommands {
	private ShopCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(
					Commands.literal("shop")
							// Bare /shop opens the UI. Without this the literal has
							// no executes() at all, so typing just "/shop" failed as
							// an incomplete command — the subcommands were the only
							// runnable forms.
							.executes(ShopCommands::openUi)
							.then(
									Commands.literal("browse")
											.executes(context -> browse(context, -1))
											.then(
													Commands.argument("tier", IntegerArgumentType.integer(1, 4))
															.executes(context -> browse(context, IntegerArgumentType.getInteger(context, "tier")))
											)
							)
							.then(
									Commands.literal("buy")
											.then(
													Commands.argument("id", IntegerArgumentType.integer(1))
															.executes(context -> buy(context, 1))
															.then(
																	Commands.argument("qty", IntegerArgumentType.integer(1, 64))
																			.executes(context -> buy(context, IntegerArgumentType.getInteger(context, "qty")))
															)
											)
							)
			);
		});
	}

	// Opens the same menu the Shop block opens, so the block is no longer
	// the only way in. The block still works — this is an additional door,
	// not a replacement.
	private static int openUi(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.literal("Piso Market");
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
					final int syncId, final net.minecraft.world.entity.player.Inventory inventory,
					final net.minecraft.world.entity.player.Player menuPlayer) {
				return new com.pisomarket.shop.PisoShopMenu(syncId, inventory);
			}
		});
		return 1;
	}

	private static int browse(final CommandContext<CommandSourceStack> context, final int tierFilter) {
		List<ShopEntry> shown = ShopCatalog.ENTRIES.stream()
				.filter(entry -> tierFilter < 0 || entry.tier() == tierFilter)
				.collect(Collectors.toList());

		if (shown.isEmpty()) {
			context.getSource().sendSuccess(() -> PisoText.body("BlackMarket: nothing in that tier"), false);
			return 1;
		}

		PisoShopStock stock = stock(context.getSource().getServer());
		context.getSource().sendSuccess(() -> PisoText.body("BlackMarket"), false);
		for (ShopEntry entry : shown) {
			int remaining = stock.remainingFor(context.getSource().getServer(), entry);
			String line = "#" + entry.id() + " — " + ShopStacks.build(context.getSource().getServer(), entry, 1).getHoverName().getString()
					+ " — " + entry.price() + " (tier " + entry.tier() + ", " + remaining + " left)";
			context.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int buy(final CommandContext<CommandSourceStack> context, final int qty) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(context, "id");

		String error = tryBuy(context.getSource().getServer(), player, id, qty);
		if (error != null) {
			context.getSource().sendFailure(PisoText.failure(error));
			return 0;
		}

		context.getSource().sendSuccess(() -> PisoText.success("Bought " + qty + "x ").append(PisoText.name("#" + id)), false);
		return 1;
	}

	// Shared by the /shop buy command and the shop block's clickable grid
	// (PisoShopMenu). Returns null on success, or a player-facing error
	// message on failure.
	public static String tryBuy(final MinecraftServer server, final ServerPlayer player, final int id, final int qty) {
		ShopEntry entry = ShopCatalog.byId(id);
		if (entry == null) {
			return "No such item #" + id;
		}

		PisoShopStock stock = stock(server);
		if (!stock.take(server, entry, qty)) {
			return "Not enough stock";
		}

		long totalPrice = entry.price() * qty;
		PisoVault vault = vault(server);
		if (!vault.withdraw(player.getUUID(), totalPrice)) {
			// Undo the stock deduction — the system shop never actually
			// removed the items from the world, so this is just restoring
			// the counter, not a real refund.
			stock.take(server, entry, -qty);
			return "Insufficient balance";
		}

		ItemStack stack = ShopStacks.build(server, entry, qty);
		if (!InventoryUtil.giveItem(player, stack)) {
			vault.deposit(player.getUUID(), totalPrice);
			stock.take(server, entry, -qty);
			VaultSync.sync(player);
			return "No inventory space";
		}

		VaultSync.sync(player);
		return null;
	}

	public static PisoShopStock stock(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoShopStock.TYPE);
	}

	private static PisoVault vault(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoVault.TYPE);
	}
}
