package com.pisomarket.market;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;
import com.pisomarket.util.InventoryUtil;

import com.pisomarket.util.PisoText;

// Registers /market list, browse, buy, mine, cancel. See CLAUDE.md's
// "In-game command surface" for the documented behavior of each.
public final class MarketCommands {
	private static final int PAGE_SIZE = 8;

	private MarketCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(
					Commands.literal("market")
							.then(
									Commands.literal("list")
											.then(
													Commands.argument("price", LongArgumentType.longArg(1))
															.executes(MarketCommands::list)
											)
							)
							.then(
									Commands.literal("browse")
											.executes(context -> browse(context, 1))
											.then(
													Commands.argument("page", IntegerArgumentType.integer(1))
															.executes(context -> browse(context, IntegerArgumentType.getInteger(context, "page")))
											)
							)
							.then(
									Commands.literal("buy")
											.then(
													Commands.argument("id", IntegerArgumentType.integer(1))
															.executes(MarketCommands::buy)
											)
							)
							.then(Commands.literal("mine").executes(MarketCommands::mine))
							.then(
									Commands.literal("cancel")
											.then(
													Commands.argument("id", IntegerArgumentType.integer(1))
															.executes(context -> cancelPrompt(context, IntegerArgumentType.getInteger(context, "id")))
															.then(Commands.literal("confirm").executes(MarketCommands::cancelConfirmed))
											)
							)
			);
		});
	}

	public static PisoMarketListings listings(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoMarketListings.TYPE);
	}

	private static PisoVault vault(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoVault.TYPE);
	}

	private static int list(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long price = LongArgumentType.getLong(context, "price");

		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			context.getSource().sendFailure(PisoText.failure("You must be holding the item you want to sell"));
			return 0;
		}

		ItemStack toList = held.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		int id = createListing(context.getSource().getServer(), player.getUUID(), toList, price);
		context.getSource().sendSuccess(() -> PisoText.success("Listed as ").append(PisoText.name("#" + id))
				.append(PisoText.plain(" for ")).append(PisoText.money(price)), false);
		return 1;
	}

	// Shared by /market list and PisoShopMenu's Sell screen — the stack
	// passed in is expected to already be a copy owned by the caller (the
	// caller is responsible for removing it from wherever it came from).
	public static int createListing(final MinecraftServer server, final java.util.UUID seller, final ItemStack stack, final long price) {
		return listings(server).list(seller, stack, price);
	}

	private static int browse(final CommandContext<CommandSourceStack> context, final int page) {
		List<MarketListing> all = listings(context.getSource().getServer()).all();
		sendPage(context.getSource(), all, page, "Market");
		return 1;
	}

	private static int mine(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		List<MarketListing> mine = listings(context.getSource().getServer()).bySeller(player.getUUID());
		sendPage(context.getSource(), mine, 1, "Your listings");
		return 1;
	}

	private static void sendPage(final CommandSourceStack source, final List<MarketListing> all, final int page, final String heading) {
		if (all.isEmpty()) {
			source.sendSuccess(() -> PisoText.body(heading + ": nothing listed"), false);
			return;
		}

		int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int clampedPage = Math.max(1, Math.min(page, totalPages));
		int from = (clampedPage - 1) * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, all.size());

		source.sendSuccess(() -> PisoText.body(heading).append(PisoText.hint("  page " + clampedPage + "/" + totalPages)), false);
		for (MarketListing listing : all.subList(from, to)) {
			String line = "#" + listing.id() + " — " + listing.stack().getCount() + "x "
					+ listing.stack().getHoverName().getString() + " — " + listing.price();
			source.sendSuccess(() -> Component.literal(line), false);
		}
	}

	private static int buy(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer buyer = context.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(context, "id");

		String error = tryBuy(context.getSource().getServer(), buyer, id);
		if (error != null) {
			context.getSource().sendFailure(PisoText.failure(error));
			return 0;
		}

		context.getSource().sendSuccess(() -> PisoText.success("Bought ").append(PisoText.name("#" + id)), false);
		return 1;
	}

	// Shared by the /market buy command and the shop block's clickable
	// grid (PisoShopMenu) — one code path, so both stay consistent. Returns
	// null on success, or a player-facing error message on failure.
	public static String tryBuy(final MinecraftServer server, final ServerPlayer buyer, final int id) {
		PisoMarketListings listingStore = listings(server);
		MarketListing listing = listingStore.get(id);
		if (listing == null) {
			return "No listing #" + id;
		}

		PisoVault vault = vault(server);
		if (!vault.transfer(buyer.getUUID(), listing.seller(), listing.price())) {
			return "Insufficient balance";
		}

		if (!InventoryUtil.giveItem(buyer, listing.stack())) {
			// Couldn't fit the item — undo the payment so no money vanishes.
			vault.transfer(listing.seller(), buyer.getUUID(), listing.price());
			VaultSync.sync(buyer);
			return "No inventory space";
		}

		// Buying a bound Land Deed transfers the land itself, not just the
		// paper — the claim (and everything built on it) becomes the
		// buyer's. This is the only supported way to hand land over, since
		// bound deeds can't be dropped or traded any other way.
		if (com.pisomarket.claims.DeedProtection.isBoundDeed(listing.stack())) {
			int claimId = com.pisomarket.claims.DeedProtection.claimIdOf(listing.stack());
			server.getDataStorage().computeIfAbsent(com.pisomarket.claims.PisoClaims.TYPE)
					.transferOwnership(claimId, buyer.getUUID());
			buyer.sendSystemMessage(PisoText.success("You now own claim ").append(PisoText.name("#" + claimId))
					.append(PisoText.plain(" and everything built on it.")));
			ServerPlayer previousOwner = server.getPlayerList().getPlayer(listing.seller());
			if (previousOwner != null) {
				previousOwner.sendSystemMessage(PisoText.warning("Claim ").append(PisoText.name("#" + claimId))
						.append(PisoText.plain(" was sold and is no longer yours.")));
			}
		}

		listingStore.remove(id);
		VaultSync.sync(buyer);
		ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(listing.seller());
		if (sellerPlayer != null) {
			VaultSync.sync(sellerPlayer);
		}
		return null;
	}

	private static int cancelPrompt(final CommandContext<CommandSourceStack> context, final int id) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		MarketListing listing = listings(context.getSource().getServer()).get(id);
		if (listing == null || !listing.seller().equals(player.getUUID())) {
			context.getSource().sendFailure(PisoText.failure("No listing #" + id + " of yours"));
			return 0;
		}

		MutableComponent confirm = Component.literal("[Confirm cancel #" + id + "]")
				.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/market cancel " + id + " confirm")));
		context.getSource().sendSuccess(() -> confirm, false);
		return 1;
	}

	private static int cancelConfirmed(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(context, "id");

		PisoMarketListings listingStore = listings(context.getSource().getServer());
		MarketListing listing = listingStore.get(id);
		if (listing == null || !listing.seller().equals(player.getUUID())) {
			context.getSource().sendFailure(PisoText.failure("No listing #" + id + " of yours"));
			return 0;
		}

		if (!InventoryUtil.giveItem(player, listing.stack())) {
			context.getSource().sendFailure(PisoText.failure("No inventory space — listing left active"));
			return 0;
		}

		listingStore.remove(id);
		context.getSource().sendSuccess(() -> PisoText.success("Cancelled ").append(PisoText.name("#" + id)), false);
		return 1;
	}

}
