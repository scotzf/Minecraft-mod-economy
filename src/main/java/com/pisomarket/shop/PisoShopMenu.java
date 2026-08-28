package com.pisomarket.shop;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pisomarket.claims.ClaimsContent;
import com.pisomarket.claims.DeedCatalog;
import com.pisomarket.claims.DeedCommands;
import com.pisomarket.claims.LandDeedItem;
import com.pisomarket.market.MarketCommands;
import com.pisomarket.market.MarketListing;
import com.pisomarket.shop.system.PisoShopStock;
import com.pisomarket.shop.system.ShopCatalog;
import com.pisomarket.shop.system.ShopCommands;
import com.pisomarket.shop.system.ShopEntry;

// Chest-style menu for the shop block (see CLAUDE.md "Shop UI"). One
// AbstractContainerMenu instance, three "screens" swapped in place by
// repopulating the same 27-slot content grid — simpler and more robust
// than juggling separate MenuType/Screen pairs per screen:
//
//  MAIN        — three named books: Buy, Sell, BlackMarket
//  MARKET      — a real grid of player-listed items; click one to buy it
//                (same code path as /market buy — see MarketCommands.tryBuy)
//  BLACKMARKET — a real grid of the system shop catalog; click one to buy
//                (same code path as /shop buy — see ShopCommands.tryBuy)
//
// Only the server ever populates real data (client instances start empty
// and pick up the real contents through the normal Slot-sync vanilla
// already does for every container, the same way a real chest's contents
// sync to anyone looking at it — no custom networking needed here).
public class PisoShopMenu extends AbstractContainerMenu {
	private static final int ROWS = 3;
	private static final int CONTENT_SIZE = ROWS * 9;
	private static final int ITEMS_PER_PAGE = ROWS * 9 - 9; // top two rows

	private static final int MAIN_VAULT = 9;
	private static final int MAIN_BUY = 11;
	private static final int MAIN_SELL = 13;
	private static final int MAIN_BLACKMARKET = 15;

	private static final int NAV_BACK = 18;
	private static final int NAV_PREV = 21;
	private static final int NAV_NEXT = 23;

	// Sell screen: a real, draggable slot for the item (unlike every other
	// slot in this menu, which is a display-only button) plus +/- buttons
	// to dial in a price without needing any text-input widget.
	//
	// SELL_ITEM_SLOT and VAULT_DEPOSIT_SLOT (below) MUST stay outside
	// 0..ITEMS_PER_PAGE-1 (currently 0-17) — that range is where Buy and
	// BlackMarket place real, valuable items for display. clicked() makes
	// these two slots always-real regardless of which screen is active
	// (see the comment there for why), so if one of them ever lands inside
	// the display grid's range again, whatever item Buy/BlackMarket is
	// showing at that exact index becomes a free, draggable pickup the
	// instant that screen is open — this happened for real (see git
	// history/conversation): browsing BlackMarket with a Firework Rocket
	// entry landing on slot 4 let it just be taken, and the item-loss
	// safety net in clearContent() made it worse by auto-*handing over*
	// whatever was sitting there on every screen change, even items that
	// were never the player's to take.
	private static final int SELL_ITEM_HINT = 19;
	private static final int SELL_ITEM_SLOT = 20;
	private static final int SELL_MINUS_100 = 9;
	private static final int SELL_MINUS_10 = 10;
	private static final int SELL_MINUS_1 = 11;
	private static final int SELL_PRICE_DISPLAY = 13;
	private static final int SELL_PLUS_1 = 15;
	private static final int SELL_PLUS_10 = 16;
	private static final int SELL_PLUS_100 = 17;
	private static final int SELL_CONFIRM = 22;

	// Vault screen: a real slot to drop potatoes in for deposit (same
	// pattern as Sell's item slot), plus +/- buttons to dial in a withdraw
	// amount (same pattern as Sell's price picker). See the big comment on
	// SELL_ITEM_SLOT above — same constraint applies here.
	private static final int VAULT_DEPOSIT_HINT = 24;
	private static final int VAULT_DEPOSIT_SLOT = 25;
	private static final int VAULT_DEPOSIT_CONFIRM = 3;
	private static final int VAULT_BALANCE_DISPLAY = 4;
	private static final int VAULT_WITHDRAW_DISPLAY = 5;
	private static final int VAULT_WITHDRAW_CONFIRM = 7;
	private static final int VAULT_MINUS_100 = 9;
	private static final int VAULT_MINUS_10 = 10;
	private static final int VAULT_MINUS_1 = 11;
	private static final int VAULT_PLUS_1 = 15;
	private static final int VAULT_PLUS_10 = 16;
	private static final int VAULT_PLUS_100 = 17;

	private enum View { MAIN, MARKET, BLACKMARKET, SELL, VAULT }

	private final Container content = new SimpleContainer(CONTENT_SIZE);
	private ServerPlayer owner;
	private View view = View.MAIN;
	private int page;

	// Index into these lists is the content slot index (0..ITEMS_PER_PAGE);
	// null/absent means that slot isn't a purchasable entry this page.
	private List<Integer> pageListingIds = List.of();
	// BlackMarket mixes two kinds of purchasable thing (ShopEntry and
	// DeedCatalog.DeedSize) in one grid — each page entry here is one of
	// those types, dispatched by instanceof in clickedBlackMarket.
	private List<Object> pageBlackMarketRows = List.of();

	private long sellPrice = 10;
	private long withdrawAmount = 10;

	public PisoShopMenu(final int syncId, final Inventory playerInventory) {
		super(PisoShopContent.MENU_TYPE, syncId);
		content.startOpen(playerInventory.player);

		for (int y = 0; y < ROWS; y++) {
			for (int x = 0; x < 9; x++) {
				addSlot(new Slot(content, x + y * 9, 8 + x * 18, 18 + y * 18));
			}
		}
		addStandardInventorySlots(playerInventory, 8, 18 + ROWS * 18 + 13);

		if (playerInventory.player instanceof ServerPlayer serverPlayer) {
			this.owner = serverPlayer;
			showMain();
		}
	}

	private static ItemStack namedBook(final String label) {
		ItemStack stack = new ItemStack(Items.BOOK);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
		return stack;
	}

	private static ItemStack namedPaper(final String label) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
		return stack;
	}

	// Used for the "drop your item here ->" pointers next to the two real
	// item slots — an arrow reads as a direction at a glance, where paper
	// looked like just another label.
	private static ItemStack namedArrow(final String label) {
		ItemStack stack = new ItemStack(PisoShopContent.POINTER);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
		return stack;
	}

	// Both real slots (Sell's item slot, Vault's deposit slot) hold actual
	// items outside of any inventory while their screen is open — always
	// return them before wiping, on every view transition, not just the
	// ones each screen's own "Back" button happens to think of. Closes off
	// the item-loss risk regardless of which path leads here.
	private void clearContent() {
		returnSlotItemIfAny(SELL_ITEM_SLOT);
		returnSlotItemIfAny(VAULT_DEPOSIT_SLOT);
		for (int i = 0; i < CONTENT_SIZE; i++) {
			content.setItem(i, ItemStack.EMPTY);
		}
	}

	private void showMain() {
		view = View.MAIN;
		clearContent();
		content.setItem(MAIN_VAULT, namedBook("Vault"));
		content.setItem(MAIN_BUY, namedBook("Buy"));
		content.setItem(MAIN_SELL, namedBook("Sell"));
		content.setItem(MAIN_BLACKMARKET, namedBook("BlackMarket"));
	}

	private void showVault() {
		view = View.VAULT;
		withdrawAmount = 10;
		clearContent();
		content.setItem(VAULT_DEPOSIT_CONFIRM, namedBook("Deposit"));
		content.setItem(VAULT_WITHDRAW_CONFIRM, namedBook("Withdraw"));
		content.setItem(VAULT_MINUS_100, namedPaper("-100"));
		content.setItem(VAULT_MINUS_10, namedPaper("-10"));
		content.setItem(VAULT_MINUS_1, namedPaper("-1"));
		content.setItem(VAULT_PLUS_1, namedPaper("+1"));
		content.setItem(VAULT_PLUS_10, namedPaper("+10"));
		content.setItem(VAULT_PLUS_100, namedPaper("+100"));
		content.setItem(VAULT_DEPOSIT_HINT, namedArrow("Drop potatoes to deposit ->"));
		content.setItem(NAV_BACK, namedBook("< Back"));
		refreshVaultDisplay();
	}

	private void refreshVaultDisplay() {
		long balance = owner.level().getServer().getDataStorage().computeIfAbsent(com.pisomarket.economy.PisoVault.TYPE).getBalance(owner.getUUID());
		content.setItem(VAULT_BALANCE_DISPLAY, namedPaper("Balance: " + balance));
		content.setItem(VAULT_WITHDRAW_DISPLAY, namedPaper("Withdraw: " + withdrawAmount));
	}

	private void showMarket(final int requestedPage) {
		view = View.MARKET;
		clearContent();

		List<MarketListing> all = MarketCommands.listings(owner.level().getServer()).all();
		int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
		page = Math.max(0, Math.min(requestedPage, totalPages - 1));
		int from = page * ITEMS_PER_PAGE;
		int to = Math.min(from + ITEMS_PER_PAGE, all.size());

		List<Integer> ids = new ArrayList<>();
		for (int i = from; i < to; i++) {
			MarketListing listing = all.get(i);
			ItemStack display = listing.stack().copy();
			display.set(
					DataComponents.CUSTOM_NAME,
					Component.literal(display.getHoverName().getString() + " — " + listing.price() + " (#" + listing.id() + ")")
			);
			content.setItem(i - from, display);
			ids.add(listing.id());
		}
		pageListingIds = ids;

		setupNav(page, totalPages);
	}

	private void showBlackMarket(final int requestedPage) {
		view = View.BLACKMARKET;
		clearContent();

		// Catalog items, then Land Deed sizes — one combined grid (see
		// pageBlackMarketRows).
		List<Object> all = new ArrayList<>();
		all.addAll(ShopCatalog.ENTRIES);
		all.addAll(DeedCatalog.SIZES);

		int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
		page = Math.max(0, Math.min(requestedPage, totalPages - 1));
		int from = page * ITEMS_PER_PAGE;
		int to = Math.min(from + ITEMS_PER_PAGE, all.size());

		PisoShopStock stock = ShopCommands.stock(owner.level().getServer());
		List<Object> rows = new ArrayList<>();
		for (int i = from; i < to; i++) {
			Object row = all.get(i);
			ItemStack display = displayFor(row, stock);
			content.setItem(i - from, display);
			rows.add(row);
		}
		pageBlackMarketRows = rows;

		setupNav(page, totalPages);
	}

	private ItemStack displayFor(final Object row, final PisoShopStock stock) {
		if (row instanceof ShopEntry entry) {
			int remaining = stock.remainingFor(entry);
			ItemStack display = new ItemStack(entry.item());
			display.set(
					DataComponents.CUSTOM_NAME,
					Component.literal(display.getHoverName().getString() + " — " + entry.price() + " (" + remaining + " left)")
			);
			return display;
		}
		DeedCatalog.DeedSize size = (DeedCatalog.DeedSize) row;
		ItemStack display = LandDeedItem.createUnbound(ClaimsContent.LAND_DEED, size.label(), size.width(), size.length(), size.height());
		display.set(DataComponents.CUSTOM_NAME, Component.literal("Land Deed — " + size.label() + " — " + size.price()));
		return display;
	}

	private void showSell() {
		view = View.SELL;
		sellPrice = 10;
		clearContent();
		content.setItem(SELL_MINUS_100, namedPaper("-100"));
		content.setItem(SELL_MINUS_10, namedPaper("-10"));
		content.setItem(SELL_MINUS_1, namedPaper("-1"));
		content.setItem(SELL_PLUS_1, namedPaper("+1"));
		content.setItem(SELL_PLUS_10, namedPaper("+10"));
		content.setItem(SELL_PLUS_100, namedPaper("+100"));
		content.setItem(SELL_CONFIRM, namedBook("List it"));
		content.setItem(SELL_ITEM_HINT, namedArrow("Drop item to sell ->"));
		content.setItem(NAV_BACK, namedBook("< Back"));
		refreshSellDisplay();
	}

	// Updates only the price-display item — never touches SELL_ITEM_SLOT,
	// so adjusting the price doesn't disturb whatever the player already
	// dragged in there.
	private void refreshSellDisplay() {
		content.setItem(SELL_PRICE_DISPLAY, namedPaper("Price: " + sellPrice));
	}

	private void setupNav(final int currentPage, final int totalPages) {
		content.setItem(NAV_BACK, namedBook("< Back"));
		if (currentPage > 0) {
			content.setItem(NAV_PREV, namedPaper("< Prev"));
		}
		if (currentPage < totalPages - 1) {
			content.setItem(NAV_NEXT, namedPaper("Next >"));
		}
	}

	@Override
	public void clicked(final int slotId, final int button, final ContainerInput containerInput, final Player player) {
		// Real slots (the player's own inventory, plus the Sell and Vault
		// deposit slots) must always run through vanilla's default handling
		// on BOTH sides — clicked() runs client-side too, for prediction,
		// before the server's authoritative copy confirms it.
		//
		// This check used to also require view == View.SELL, which was a
		// second bug on top of the ServerPlayer one fixed earlier: `view`
		// is a plain field that only ever gets mutated by the server-side
		// show*() methods (they're gated behind the constructor's
		// ServerPlayer check), so the CLIENT's own copy of this menu never
		// learns the view changed — it stays View.MAIN forever. Checking
		// `view == View.SELL` client-side was therefore always false, so
		// client-side prediction still treated the slot as a no-op button
		// and the placed item visually snapped back, even though the
		// server-side placement itself was fine. Fix: these specific slot
		// ids are unconditionally real slots regardless of view — no
		// client/server-shared state needed at all.
		if (slotId >= CONTENT_SIZE || slotId == SELL_ITEM_SLOT || slotId == VAULT_DEPOSIT_SLOT) {
			super.clicked(slotId, button, containerInput, player);
			return;
		}

		if (!(player instanceof ServerPlayer serverPlayer) || owner == null) {
			return;
		}

		switch (view) {
			case MAIN -> clickedMain(slotId, serverPlayer);
			case MARKET -> clickedMarket(slotId, serverPlayer);
			case BLACKMARKET -> clickedBlackMarket(slotId, serverPlayer);
			case SELL -> clickedSell(slotId, serverPlayer);
			case VAULT -> clickedVault(slotId, serverPlayer);
		}
	}

	private void clickedMain(final int slotId, final ServerPlayer player) {
		if (slotId == MAIN_VAULT) {
			showVault();
		} else if (slotId == MAIN_BUY) {
			showMarket(0);
		} else if (slotId == MAIN_SELL) {
			showSell();
		} else if (slotId == MAIN_BLACKMARKET) {
			showBlackMarket(0);
		}
	}

	private void clickedVault(final int slotId, final ServerPlayer player) {
		if (slotId == NAV_BACK) {
			showMain();
			return;
		}

		long delta = switch (slotId) {
			case VAULT_MINUS_100 -> -100;
			case VAULT_MINUS_10 -> -10;
			case VAULT_MINUS_1 -> -1;
			case VAULT_PLUS_1 -> 1;
			case VAULT_PLUS_10 -> 10;
			case VAULT_PLUS_100 -> 100;
			default -> 0;
		};
		if (delta != 0) {
			withdrawAmount = Math.max(1, withdrawAmount + delta);
			refreshVaultDisplay();
			return;
		}

		if (slotId == VAULT_DEPOSIT_CONFIRM) {
			ItemStack toDeposit = content.getItem(VAULT_DEPOSIT_SLOT);
			if (toDeposit.isEmpty()) {
				player.sendSystemMessage(Component.literal("Put potatoes in the slot first"));
				return;
			}
			if (toDeposit.getItem() != Items.POISONOUS_POTATO) {
				player.sendSystemMessage(Component.literal("Only Poisonous Potato can be deposited"));
				return;
			}
			// Credit first, then clear the slot — same failure-can't-lose-
			// items ordering as /deposit (see PisoCommands.deposit).
			long amount = toDeposit.getCount();
			com.pisomarket.economy.PisoVault vault =
					player.level().getServer().getDataStorage().computeIfAbsent(com.pisomarket.economy.PisoVault.TYPE);
			vault.deposit(player.getUUID(), amount);
			content.setItem(VAULT_DEPOSIT_SLOT, ItemStack.EMPTY);
			com.pisomarket.economy.VaultSync.sync(player);
			player.sendSystemMessage(Component.literal("Deposited " + amount));
			refreshVaultDisplay();
			return;
		}

		if (slotId == VAULT_WITHDRAW_CONFIRM) {
			com.pisomarket.economy.PisoVault vault =
					player.level().getServer().getDataStorage().computeIfAbsent(com.pisomarket.economy.PisoVault.TYPE);
			if (!vault.withdraw(player.getUUID(), withdrawAmount)) {
				player.sendSystemMessage(Component.literal("Insufficient balance"));
				return;
			}

			// Potatoes cap at a 64-item stack — give in chunks so amounts
			// above 64 don't get silently lost into one oversized stack.
			long remaining = withdrawAmount;
			while (remaining > 0) {
				int chunk = (int) Math.min(remaining, 64);
				ItemStack given = new ItemStack(Items.POISONOUS_POTATO, chunk);
				if (!com.pisomarket.util.InventoryUtil.giveItem(player, given)) {
					break;
				}
				remaining -= chunk;
			}

			long actuallyGiven = withdrawAmount - remaining;
			if (remaining > 0) {
				// Didn't all fit — refund the part that didn't so no money
				// is destroyed.
				vault.deposit(player.getUUID(), remaining);
			}
			com.pisomarket.economy.VaultSync.sync(player);
			if (actuallyGiven == 0) {
				player.sendSystemMessage(Component.literal("No inventory space"));
				return;
			}

			player.sendSystemMessage(Component.literal("Withdrew " + actuallyGiven));
			refreshVaultDisplay();
		}
	}

	private void clickedSell(final int slotId, final ServerPlayer player) {
		if (slotId == NAV_BACK) {
			showMain();
			return;
		}

		long delta = switch (slotId) {
			case SELL_MINUS_100 -> -100;
			case SELL_MINUS_10 -> -10;
			case SELL_MINUS_1 -> -1;
			case SELL_PLUS_1 -> 1;
			case SELL_PLUS_10 -> 10;
			case SELL_PLUS_100 -> 100;
			default -> 0;
		};
		if (delta != 0) {
			sellPrice = Math.max(1, sellPrice + delta);
			refreshSellDisplay();
			return;
		}

		if (slotId == SELL_CONFIRM) {
			ItemStack toSell = content.getItem(SELL_ITEM_SLOT);
			if (toSell.isEmpty()) {
				player.sendSystemMessage(Component.literal("Put an item in the slot first"));
				return;
			}
			ItemStack copy = toSell.copy();
			content.setItem(SELL_ITEM_SLOT, ItemStack.EMPTY);
			int id = MarketCommands.createListing(player.level().getServer(), player.getUUID(), copy, sellPrice);
			player.sendSystemMessage(Component.literal("Listed as #" + id + " for " + sellPrice));
			showMain();
		}
	}

	// Give back whatever's sitting in a real item slot (Sell's or Vault's)
	// rather than lose it — called from clearContent() on every view
	// transition, and again here on close, so there's exactly one path
	// this can ever go through instead of one per screen to remember.
	private void returnSlotItemIfAny(final int slotId) {
		ItemStack stack = content.getItem(slotId);
		if (stack.isEmpty() || owner == null) {
			return;
		}
		content.setItem(slotId, ItemStack.EMPTY);
		if (!com.pisomarket.util.InventoryUtil.giveItem(owner, stack)) {
			owner.drop(stack, false);
		}
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		returnSlotItemIfAny(SELL_ITEM_SLOT);
		returnSlotItemIfAny(VAULT_DEPOSIT_SLOT);
	}

	private void clickedMarket(final int slotId, final ServerPlayer player) {
		if (slotId == NAV_BACK) {
			showMain();
			return;
		}
		if (slotId == NAV_PREV) {
			showMarket(page - 1);
			return;
		}
		if (slotId == NAV_NEXT) {
			showMarket(page + 1);
			return;
		}
		if (slotId >= pageListingIds.size()) {
			return;
		}

		int listingId = pageListingIds.get(slotId);
		String error = MarketCommands.tryBuy(player.level().getServer(), player, listingId);
		player.sendSystemMessage(Component.literal(error == null ? "Bought #" + listingId : error));
		showMarket(page);
	}

	private void clickedBlackMarket(final int slotId, final ServerPlayer player) {
		if (slotId == NAV_BACK) {
			showMain();
			return;
		}
		if (slotId == NAV_PREV) {
			showBlackMarket(page - 1);
			return;
		}
		if (slotId == NAV_NEXT) {
			showBlackMarket(page + 1);
			return;
		}
		if (slotId >= pageBlackMarketRows.size()) {
			return;
		}

		Object row = pageBlackMarketRows.get(slotId);
		String error;
		String boughtLabel;
		if (row instanceof ShopEntry entry) {
			error = ShopCommands.tryBuy(player.level().getServer(), player, entry.id(), 1);
			boughtLabel = entry.item().getDefaultInstance().getHoverName().getString();
		} else {
			DeedCatalog.DeedSize size = (DeedCatalog.DeedSize) row;
			error = DeedCommands.tryBuy(player, size.id());
			boughtLabel = "a " + size.label() + " Land Deed";
		}

		player.sendSystemMessage(Component.literal(error == null ? "Bought " + boughtLabel : error));
		showBlackMarket(page);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		// Content slots (0..CONTENT_SIZE) never move via shift-click.
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		return true;
	}
}
