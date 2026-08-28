package com.pisomarket.shop.system;

import java.util.List;

import net.minecraft.world.item.Items;

// Static catalog data — ids are permanent once shipped (see ShopEntry).
// Prices follow CLAUDE.md's ratio (consumable 2 : prestige 200) times a
// starting multiplier of 5; both the multiplier and every price here are
// explicitly "tune after the systems run" per CLAUDE.md, not final.
public final class ShopCatalog {
	private static final long MULTIPLIER = 5;
	private static final long CONSUMABLE_PRICE = 2 * MULTIPLIER;
	private static final long PRESTIGE_PRICE = 200 * MULTIPLIER;

	public static final int TIER_CONSUMABLE = 3;
	public static final int TIER_PRESTIGE = 4;

	public static final List<ShopEntry> ENTRIES = List.of(
			new ShopEntry(1, Items.EXPERIENCE_BOTTLE, TIER_CONSUMABLE, CONSUMABLE_PRICE, 64),
			new ShopEntry(2, Items.NAME_TAG, TIER_CONSUMABLE, CONSUMABLE_PRICE, 32),
			new ShopEntry(3, Items.SADDLE, TIER_CONSUMABLE, CONSUMABLE_PRICE, 16),
			new ShopEntry(4, Items.IRON_HORSE_ARMOR, TIER_CONSUMABLE, CONSUMABLE_PRICE, 8),
			new ShopEntry(5, Items.FIREWORK_ROCKET, TIER_CONSUMABLE, CONSUMABLE_PRICE, 64),
			new ShopEntry(6, Items.ELYTRA, TIER_PRESTIGE, PRESTIGE_PRICE, 3),
			new ShopEntry(7, Items.ENCHANTED_GOLDEN_APPLE, TIER_PRESTIGE, PRESTIGE_PRICE, 5),
			new ShopEntry(8, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, TIER_PRESTIGE, PRESTIGE_PRICE, 5),
			new ShopEntry(9, Items.HEART_OF_THE_SEA, TIER_PRESTIGE, PRESTIGE_PRICE, 3),
			new ShopEntry(10, Items.ECHO_SHARD, TIER_PRESTIGE, PRESTIGE_PRICE, 10)
	);

	private ShopCatalog() {
	}

	public static ShopEntry byId(final int id) {
		for (ShopEntry entry : ENTRIES) {
			if (entry.id() == id) {
				return entry;
			}
		}
		return null;
	}
}
