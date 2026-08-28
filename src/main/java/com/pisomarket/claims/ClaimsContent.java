package com.pisomarket.claims;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.pisomarket.PisoMarket;

public final class ClaimsContent {
	private static final Identifier DEED_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "land_deed");

	public static final Item LAND_DEED = new LandDeedItem(
			new Item.Properties().setId(ResourceKey.create(Registries.ITEM, DEED_ID)).stacksTo(1)
	);

	private ClaimsContent() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, DEED_ID, LAND_DEED);
	}
}
