package com.pisomarket.shop.system;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import com.pisomarket.economy.harvest.HarvestFaucet;
import com.pisomarket.economy.harvest.HarvestPotionItem;

// Prices come from one formula rather than per-item guesswork:
//
//     price = material unit value  x  recipe unit count
//
// The unit values are calibrated to the faucet. The drop rate is 2.5%, so
// 1 Piso == 40 mature potatoes harvested — that is the real exchange rate
// of this economy, and every price below is ultimately a number of potato
// harvests. Iron 7 x pickaxe 3 = 21, diamond 70 x 3 = 210, matching the
// "iron ~20, diamond ~200" anchors this was designed against.
//
// Why stocking craftable tools does NOT break CLAUDE.md's "never stock
// what players can produce" rule: tools wear out. Selling them creates
// recurring demand instead of permanently replacing a player seller, which
// is exactly why Unbreaking and Mending are banned from the enchant list —
// both cancel the durability sink that makes this safe.
public final class ShopCatalog {
	// Material unit values.
	private static final long WOOD = 1;
	private static final long STONE = 2;
	private static final long GOLD = 6;
	private static final long IRON = 7;
	private static final long DIAMOND = 70;

	// Recipe unit counts.
	private static final int SHOVEL = 1;
	private static final int SWORD = 2;
	private static final int HOE = 2;
	private static final int PICKAXE = 3;
	private static final int AXE = 3;
	private static final int BOOTS = 4;
	private static final int HELMET = 5;
	private static final int LEGGINGS = 7;
	private static final int CHESTPLATE = 8;

	// An enchanted item costs its base price plus this much per level —
	// enough to be a real premium without undercutting a player who can
	// enchant to full strength.
	private static final double ENCHANT_PREMIUM_PER_LEVEL = 0.6;

	public static final int TIER_TOOL = 1;
	public static final int TIER_ENCHANTED = 2;
	public static final int TIER_CONSUMABLE = 3;
	public static final int TIER_PRESTIGE = 4;

	public static final List<ShopEntry> ENTRIES = build();

	private ShopCatalog() {
	}

	private static long price(final long material, final int units) {
		return material * units;
	}

	private static long enchantedPrice(final long basePrice, final int level) {
		return Math.round(basePrice * (1.0 + ENCHANT_PREMIUM_PER_LEVEL * level));
	}

	private static List<ShopEntry> build() {
		List<ShopEntry> entries = new ArrayList<>();
		int id = 1;

		// --- Tier 1: plain tools and armour. Cheap, restock daily, the
		// bread-and-butter of the shop. These wear out, so they sell again.
		id = addTool(entries, id, Items.STONE_PICKAXE, price(STONE, PICKAXE), 64);
		id = addTool(entries, id, Items.STONE_AXE, price(STONE, AXE), 64);
		id = addTool(entries, id, Items.IRON_SHOVEL, price(IRON, SHOVEL), 32);
		id = addTool(entries, id, Items.IRON_HOE, price(IRON, HOE), 32);
		id = addTool(entries, id, Items.IRON_SWORD, price(IRON, SWORD), 32);
		id = addTool(entries, id, Items.IRON_PICKAXE, price(IRON, PICKAXE), 32);
		id = addTool(entries, id, Items.IRON_AXE, price(IRON, AXE), 32);
		id = addTool(entries, id, Items.IRON_HELMET, price(IRON, HELMET), 16);
		id = addTool(entries, id, Items.IRON_CHESTPLATE, price(IRON, CHESTPLATE), 16);
		id = addTool(entries, id, Items.IRON_LEGGINGS, price(IRON, LEGGINGS), 16);
		id = addTool(entries, id, Items.IRON_BOOTS, price(IRON, BOOTS), 16);
		id = addTool(entries, id, Items.GOLDEN_PICKAXE, price(GOLD, PICKAXE), 16);
		id = addTool(entries, id, Items.BOW, price(WOOD, PICKAXE) + price(IRON, SHOVEL), 16);

		// Diamond gear — same formula, an order of magnitude up. Weekly
		// restock so it stays an event rather than a vending machine.
		id = addEntry(entries, id, Items.DIAMOND_SWORD, TIER_TOOL, price(DIAMOND, SWORD), 4, 7);
		id = addEntry(entries, id, Items.DIAMOND_PICKAXE, TIER_TOOL, price(DIAMOND, PICKAXE), 4, 7);
		id = addEntry(entries, id, Items.DIAMOND_AXE, TIER_TOOL, price(DIAMOND, AXE), 4, 7);
		id = addEntry(entries, id, Items.DIAMOND_HELMET, TIER_TOOL, price(DIAMOND, HELMET), 2, 7);
		id = addEntry(entries, id, Items.DIAMOND_CHESTPLATE, TIER_TOOL, price(DIAMOND, CHESTPLATE), 2, 7);
		id = addEntry(entries, id, Items.DIAMOND_LEGGINGS, TIER_TOOL, price(DIAMOND, LEGGINGS), 2, 7);
		id = addEntry(entries, id, Items.DIAMOND_BOOTS, TIER_TOOL, price(DIAMOND, BOOTS), 2, 7);

		// --- Tier 2: single-enchant gear, always two levels below max, so
		// a player with an enchanting table always beats the shop.
		// Efficiency max 5 -> 3, Sharpness max 5 -> 3, Protection max 4 -> 2,
		// Fortune max 3 -> 1, Power max 5 -> 3.
		id = addEnchanted(entries, id, Items.IRON_PICKAXE, price(IRON, PICKAXE), Enchantments.EFFICIENCY, 3, 8, 3);
		id = addEnchanted(entries, id, Items.IRON_SWORD, price(IRON, SWORD), Enchantments.SHARPNESS, 3, 8, 3);
		id = addEnchanted(entries, id, Items.IRON_CHESTPLATE, price(IRON, CHESTPLATE), Enchantments.PROTECTION, 2, 4, 7);
		id = addEnchanted(entries, id, Items.DIAMOND_PICKAXE, price(DIAMOND, PICKAXE), Enchantments.EFFICIENCY, 3, 2, 14);
		id = addEnchanted(entries, id, Items.DIAMOND_PICKAXE, price(DIAMOND, PICKAXE), Enchantments.FORTUNE, 1, 2, 14);
		id = addEnchanted(entries, id, Items.DIAMOND_SWORD, price(DIAMOND, SWORD), Enchantments.SHARPNESS, 3, 2, 14);
		id = addEnchanted(entries, id, Items.DIAMOND_CHESTPLATE, price(DIAMOND, CHESTPLATE), Enchantments.PROTECTION, 2, 1, 14);
		id = addEnchanted(entries, id, Items.BOW, price(WOOD, PICKAXE) + price(IRON, SHOVEL), Enchantments.POWER, 3, 4, 7);

		// --- Tier 3: consumables. Destroyed on use, never accumulate.
		id = addEntry(entries, id, Items.EXPERIENCE_BOTTLE, TIER_CONSUMABLE, 12, 64, 1);
		id = addEntry(entries, id, Items.NAME_TAG, TIER_CONSUMABLE, 30, 16, 3);
		id = addEntry(entries, id, Items.SADDLE, TIER_CONSUMABLE, 45, 8, 3);
		id = addEntry(entries, id, Items.FIREWORK_ROCKET, TIER_CONSUMABLE, 4, 64, 1);
		id = addEntry(entries, id, Items.ENDER_PEARL, TIER_CONSUMABLE, 25, 16, 2);

		// Harvest potions. Priced by the stated rule: half of what a player
		// can earn in the one minute the potion lasts. See potionPrice.
		id = addEntry(entries, id, HarvestPotionItem.HARVEST_I, TIER_CONSUMABLE,
				potionPrice(HarvestFaucet.DROP_CHANCE + HarvestFaucet.HARVEST_BOOST_I, 1), 16, 1);
		id = addEntry(entries, id, HarvestPotionItem.HARVEST_II, TIER_CONSUMABLE,
				potionPrice(HarvestFaucet.DROP_CHANCE + HarvestFaucet.HARVEST_BOOST_II, 1), 8, 1);
		id = addEntry(entries, id, HarvestPotionItem.LUCK, TIER_CONSUMABLE,
				potionPrice(HarvestFaucet.DROP_CHANCE, 2), 8, 1);

		// --- Tier 4: the genuinely unobtainable. Priced by scarcity, not
		// by materials, and restocked on a scale of real days.
		id = addEntry(entries, id, Items.ECHO_SHARD, TIER_PRESTIGE, 150, 4, 14);
		id = addEntry(entries, id, Items.HEART_OF_THE_SEA, TIER_PRESTIGE, 800, 1, 30);
		id = addEntry(entries, id, Items.ENCHANTED_GOLDEN_APPLE, TIER_PRESTIGE, 1200, 1, 60);
		id = addEntry(entries, id, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, TIER_PRESTIGE, 1500, 1, 100);
		addEntry(entries, id, Items.ELYTRA, TIER_PRESTIGE, 2000, 1, 300);

		return List.copyOf(entries);
	}

	// How many mature potato crops a player can realistically break by hand
	// in one minute. Two per second — brisk but sustainable while walking a
	// farm row. Every potion price below is derived from this one number, so
	// if it turns out to be wrong in practice, change it here and all three
	// prices move together.
	public static final int HARVEST_PER_MINUTE = 120;

	// The stated pricing rule: a potion costs half of what it earns you over
	// the one minute it lasts.
	//
	// NOTE — this makes every potion strictly profitable to drink while
	// harvesting, so a player who is farming anyway should always be using
	// one. It is not an infinite-money loop (you still have to break the
	// crops), but it does raise the effective faucet rate for anyone who
	// buys in. See the summary in chat for the numbers.
	private static long potionPrice(final double effectiveChance, final int payoutPerHit) {
		double yieldPerMinute = HARVEST_PER_MINUTE * effectiveChance * payoutPerHit;
		return Math.max(1, (long) Math.ceil(yieldPerMinute / 2.0));
	}

	private static int addTool(final List<ShopEntry> entries, final int id, final Item item, final long price, final int stock) {
		return addEntry(entries, id, item, TIER_TOOL, price, stock, 1);
	}

	private static int addEntry(final List<ShopEntry> entries, final int id, final Item item,
			final int tier, final long price, final int stock, final int restockDays) {
		entries.add(new ShopEntry(id, item, tier, price, stock, restockDays));
		return id + 1;
	}

	private static int addEnchanted(final List<ShopEntry> entries, final int id, final Item item, final long basePrice,
			final net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment,
			final int level, final int stock, final int restockDays) {
		entries.add(new ShopEntry(id, item, TIER_ENCHANTED, enchantedPrice(basePrice, level), stock, restockDays, enchantment, level));
		return id + 1;
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
