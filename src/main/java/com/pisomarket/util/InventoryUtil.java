package com.pisomarket.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtil {
	private InventoryUtil() {
	}

	// Tries to give the exact stack to the player. Returns false (and
	// leaves their inventory untouched) if it doesn't fully fit — callers
	// are expected to not charge, or to refund, on false.
	public static boolean giveItem(final ServerPlayer player, final ItemStack stack) {
		ItemStack copy = stack.copy();
		boolean added = player.getInventory().add(copy);
		return added && copy.isEmpty();
	}
}
