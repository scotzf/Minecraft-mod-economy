package com.pisomarket.shop.system;

import net.minecraft.world.item.Item;

// One catalog entry. id is stable across a server's lifetime (used by
// /shop buy <id> and by the BlackMarket grid) — never reorder existing
// entries in ShopCatalog, only append.
//
// Scope note: only Tier 3 (consumables) and Tier 4 (prestige) from
// CLAUDE.md's "System shop catalog" are implemented as real purchasable
// items here. Tier 1 (cosmetics — chat colors, titles, particle trails) and
// Tier 2 (convenience — extra /sethome slots, etc.) need systems that don't
// exist yet (a title/prefix system, particle tick handling, /sethome
// itself), so they're not in this catalog. Weekly rotation isn't
// implemented either — it needs the day-tracking system also not built yet
// (see CLAUDE.md's daily-cap design). Stock here is a fixed pool that
// depletes and never refills until that's added.
public record ShopEntry(int id, Item item, int tier, long price, int stock) {
}
