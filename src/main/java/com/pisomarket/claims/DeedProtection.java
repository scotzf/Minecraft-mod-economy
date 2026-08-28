package com.pisomarket.claims;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// A bound Land Deed is the title to real land, so it deliberately can't be
// dropped — losing it to lava or a death pit would mean losing the land with
// it. Transferring land is done by selling the deed on the market, which
// moves the claim itself to the buyer (see MarketCommands.tryBuy).
//
// Enforcement lives in PlayerDropMixin; this class holds the shared checks
// so the rule is defined in exactly one place.
public final class DeedProtection {
	private DeedProtection() {
	}

	public static boolean isBoundDeed(final ItemStack stack) {
		if (!(stack.getItem() instanceof LandDeedItem)) {
			return false;
		}
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("claimId").isPresent();
	}

	public static int claimIdOf(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("claimId").orElse(-1);
	}

	public static void warnCannotStore(final ServerPlayer player) {
		player.sendSystemMessage(Component.literal(
				"A bound Land Deed can only be kept in your own inventory or your ender chest. Sell it on the market to transfer the land."
		));
	}

	public static void warnCannotDrop(final ServerPlayer player) {
		player.sendSystemMessage(Component.literal(
				"You can't drop a bound Land Deed — keep it, store it in an ender chest, or sell it on the market to transfer the land."
		));
	}
}
