package com.pisomarket.shop.system;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

// One catalog entry. `id` is stable across a server's lifetime (used by
// /shop buy <id> and the BlackMarket grid) — never reorder or renumber
// existing entries, only append.
//
// restockDays: how many in-game days until this entry's stock refills to
// `stock`. 1 in-game day = 20 real minutes, so 300 days is roughly 100
// real hours — deliberately once-per-server for the legendary items.
//
// enchantment/enchantLevel are optional (null/0 = plain item). House rules,
// set by the server owner:
//   - exactly one enchantment per item, never a stack of them
//   - never Unbreaking or Mending: gear wearing out IS the money sink, and
//     both of those cancel it
//   - never max level — always two below, so player-enchanted gear stays
//     strictly better than anything the system sells
public record ShopEntry(
		int id, Item item, int tier, long price, int stock, int restockDays,
		ResourceKey<Enchantment> enchantment, int enchantLevel
) {
	public ShopEntry(final int id, final Item item, final int tier, final long price, final int stock, final int restockDays) {
		this(id, item, tier, price, stock, restockDays, null, 0);
	}

	public boolean isEnchanted() {
		return enchantment != null && enchantLevel > 0;
	}
}
