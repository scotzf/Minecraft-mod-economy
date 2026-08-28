package com.pisomarket.claims.lock;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Wraps the chest's REAL Container directly (not a copy) so placing items
// actually persists into the chest. Only ever opened for a player with
// PUT_ONLY access (see ChestAccessGuard) — allows filling empty slots,
// blocks anything that would take an item out. Known simplification: can't
// top up an already-partial stack, only fill fully-empty slots — simpler
// and unambiguously safe (no click-type-by-click-type simulation needed to
// guarantee nothing can be removed).
public class RestrictedChestMenu extends AbstractContainerMenu {
	private static final int ROWS = 3;
	private static final int CHEST_SIZE = ROWS * 9;

	private final Container chest;

	public RestrictedChestMenu(final int syncId, final Inventory playerInventory) {
		this(syncId, playerInventory, new net.minecraft.world.SimpleContainer(CHEST_SIZE));
	}

	public RestrictedChestMenu(final int syncId, final Inventory playerInventory, final Container chest) {
		super(LockContent.RESTRICTED_MENU_TYPE, syncId);
		this.chest = chest;

		for (int y = 0; y < ROWS; y++) {
			for (int x = 0; x < 9; x++) {
				addSlot(new Slot(chest, x + y * 9, 8 + x * 18, 18 + y * 18));
			}
		}
		addStandardInventorySlots(playerInventory, 8, 18 + ROWS * 18 + 13);
	}

	@Override
	public void clicked(final int slotId, final int button, final ContainerInput containerInput, final Player player) {
		if (slotId >= 0 && slotId < CHEST_SIZE) {
			// Only allow placing into a currently-empty chest slot while
			// holding something on the cursor — anything else touching a
			// chest slot (taking from it, swapping, etc.) is blocked.
			ItemStack carried = getCarried();
			if (chest.getItem(slotId).isEmpty() && !carried.isEmpty()) {
				chest.setItem(slotId, carried.copyWithCount(1));
				carried.shrink(1);
			}
			return;
		}
		super.clicked(slotId, button, containerInput, player);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		// No shift-click transfers at all — keeps the safety guarantee
		// simple (see class comment) instead of reasoning through every
		// shift-click edge case.
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		return true;
	}
}
