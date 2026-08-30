package com.pisomarket.economy;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.pisomarket.PisoMarket;

// The Sunstone Shard — the mod's currency item, replacing the vanilla
// poisonous potato.
//
// Why a custom item at all: the poisonous-potato design had one unfixable
// problem, which is that every poisonous potato already sitting in an
// existing world counted as pre-existing money. A brand new item that
// nothing else in the game produces has no such backlog.
//
// No crafting recipe, deliberately. The only sources are the harvest
// faucet and mob drops, which is what keeps the money supply bounded.
public final class PisoCurrency {
	private static final Identifier SHARD_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "sunstone_shard");

	public static final Item SUNSTONE_SHARD = new Item(
			new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SHARD_ID))
	);

	private PisoCurrency() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, SHARD_ID, SUNSTONE_SHARD);
	}
}
