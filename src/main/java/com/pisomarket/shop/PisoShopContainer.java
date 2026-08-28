package com.pisomarket.shop;

import net.minecraft.world.SimpleContainer;

// Marker type for the shop menu's own content grid. ContainerDeedGuardMixin
// blocks bound Land Deeds from every container except the player's own
// inventory and ender chest — but the Sell slot lives here, and selling a
// deed is the one sanctioned way to transfer land, so this container has to
// be explicitly allowed.
public class PisoShopContainer extends SimpleContainer {
	public PisoShopContainer(final int size) {
		super(size);
	}
}
