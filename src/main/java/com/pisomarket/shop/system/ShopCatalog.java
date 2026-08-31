package com.pisomarket.shop.system;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import com.pisomarket.combat.ElementalWeapons;
import com.pisomarket.economy.harvest.FortunePotionItem;
import com.pisomarket.travel.WaypointContent;

// The system shop catalog, restructured for v2.
//
// The old wood -> stone -> iron -> gold -> diamond material ladder is GONE,
// along with the `material unit value x recipe unit count` formula that
// only made sense spanning five materials. Tier 1 is diamond-only and
// priced at a flat rate per diamond in the vanilla recipe.
//
// Everything is denominated in Sunstone Shards. The calibration anchor is
// a 9x9 carrot plot at 2.5%, which yields roughly 2 Shards per full harvest
// cycle — so the 20-Shard price floor is about ten cycles of a small farm.
//
// Hard rule, unchanged from v1: never stock anything players can produce
// THAT DOES NOT WEAR OUT. Tools wearing out is what makes selling them
// create recurring demand instead of permanently replacing a player seller,
// and it is exactly why Unbreaking and Mending stay banned from the enchant
// list — both cancel the durability sink that makes this safe.
public final class ShopCatalog {
	// Tier 1: 10 Shards per diamond in the item's vanilla recipe.
	private static final long PER_DIAMOND = 10;

	// Vanilla recipe diamond counts, confirmed against the real recipes.
	private static final int SHOVEL = 1;
	private static final int SWORD = 2;
	private static final int HOE = 2;
	private static final int PICKAXE = 3;
	private static final int AXE = 3;
	private static final int BOOTS = 4;
	private static final int HELMET = 5;
	private static final int LEGGINGS = 7;
	private static final int CHESTPLATE = 8;

	// An enchanted item costs its base price plus this much per level.
	// Carried over unchanged from v1.
	private static final double ENCHANT_PREMIUM_PER_LEVEL = 0.6;

	public static final int TIER_TOOL = 1;
	public static final int TIER_ENCHANTED = 2;
	public static final int TIER_CONSUMABLE = 3;
	public static final int TIER_PRESTIGE = 4;

	public static final List<ShopEntry> ENTRIES = build();

	private ShopCatalog() {
	}

	private static long price(final int diamonds) {
		return PER_DIAMOND * diamonds;
	}

	private static long enchantedPrice(final long basePrice, final int level) {
		return Math.round(basePrice * (1.0 + ENCHANT_PREMIUM_PER_LEVEL * level));
	}

	private static List<ShopEntry> build() {
		List<ShopEntry> entries = new ArrayList<>();
		int id = 1;

		// --- Tier 1: plain diamond tools and armour. These wear out, so
		// they sell again. Note the shovel lands at 10, below the stated
		// 20-Shard floor — accepted as the single exception rather than
		// breaking the clean per-diamond formula for one item.
		id = addEntry(entries, id, Items.DIAMOND_SHOVEL, TIER_TOOL, price(SHOVEL), 8, 1);
		id = addEntry(entries, id, Items.DIAMOND_SWORD, TIER_TOOL, price(SWORD), 8, 1);
		id = addEntry(entries, id, Items.DIAMOND_HOE, TIER_TOOL, price(HOE), 8, 1);
		id = addEntry(entries, id, Items.DIAMOND_PICKAXE, TIER_TOOL, price(PICKAXE), 8, 1);
		id = addEntry(entries, id, Items.DIAMOND_AXE, TIER_TOOL, price(AXE), 8, 1);
		id = addEntry(entries, id, Items.DIAMOND_BOOTS, TIER_TOOL, price(BOOTS), 4, 2);
		id = addEntry(entries, id, Items.DIAMOND_HELMET, TIER_TOOL, price(HELMET), 4, 2);
		id = addEntry(entries, id, Items.DIAMOND_LEGGINGS, TIER_TOOL, price(LEGGINGS), 4, 2);
		id = addEntry(entries, id, Items.DIAMOND_CHESTPLATE, TIER_TOOL, price(CHESTPLATE), 4, 2);

		// --- Tier 2: single-enchant diamond gear, always two levels below
		// max, so a player with an enchanting table always beats the shop.
		// Efficiency max 5 -> 3, Sharpness 5 -> 3, Protection 4 -> 2,
		// Fortune 3 -> 1, Power 5 -> 3.
		id = addEnchanted(entries, id, Items.DIAMOND_PICKAXE, price(PICKAXE), Enchantments.EFFICIENCY, 3, 4, 7);
		id = addEnchanted(entries, id, Items.DIAMOND_PICKAXE, price(PICKAXE), Enchantments.FORTUNE, 1, 2, 7);
		id = addEnchanted(entries, id, Items.DIAMOND_SWORD, price(SWORD), Enchantments.SHARPNESS, 3, 4, 7);
		id = addEnchanted(entries, id, Items.DIAMOND_CHESTPLATE, price(CHESTPLATE), Enchantments.PROTECTION, 2, 2, 14);
		id = addEnchanted(entries, id, Items.DIAMOND_HELMET, price(HELMET), Enchantments.PROTECTION, 2, 2, 14);
		id = addEnchanted(entries, id, Items.DIAMOND_LEGGINGS, price(LEGGINGS), Enchantments.PROTECTION, 2, 2, 14);
		id = addEnchanted(entries, id, Items.DIAMOND_BOOTS, price(BOOTS), Enchantments.PROTECTION, 2, 2, 14);

		// --- Tier 3: rare items. Prices locked by hand, not formula-derived
		// — these have no recipe to derive from.
		id = addEntry(entries, id, Items.TOTEM_OF_UNDYING, TIER_CONSUMABLE, 45, 4, 7);
		id = addEntry(entries, id, Items.SHULKER_BOX, TIER_CONSUMABLE, 100, 2, 14);
		id = addEntry(entries, id, Items.NETHERITE_INGOT, TIER_CONSUMABLE, 50, 4, 14);
		id = addEntry(entries, id, Items.ELYTRA, TIER_CONSUMABLE, 120, 1, 30);
		// Flat prices, NOT the old "half of one minute's yield" formula.
		id = addEntry(entries, id, FortunePotionItem.FORTUNE_I, TIER_CONSUMABLE, 30, 16, 1);
		id = addEntry(entries, id, FortunePotionItem.FORTUNE_II, TIER_CONSUMABLE, 40, 8, 1);
		id = addEntry(entries, id, FortunePotionItem.LUCK, TIER_CONSUMABLE, 30, 8, 1);

		// The Waypoint block. Sold rather than craftable, so fast travel is
		// a money sink a player has to earn rather than a free convenience.
		id = addEntry(entries, id, WaypointContent.WAYPOINT_BLOCK.asItem(), TIER_CONSUMABLE, 5000, 2, 7);

		// --- Tier 4: ONLY the weakest weapon group is sold. Everything
		// stronger (Souls, Divine, Abominable heavy/scythe, Frost/Molten
		// blade) is mob-drop-only — see MobDrops. The shop deliberately
		// cannot sell you a top-tier weapon at any price.
		//
		// NOTE, flagged rather than silently accepted: at the current faucet
		// rates these prices are effectively unreachable by farming (100k
		// shards is ~20,000 hours at ~5/hour from mob grinding). They are
		// only payable out of BOSS income — Warden pays 10,000, so 100k is
		// ten Wardens. But a single Warden already drops a Tier 1 weapon at
		// 100%, which is strictly better than the Tier 4 weapon these prices
		// buy. As written, nobody has a reason to ever buy one. See
		// CLAUDE.md's pricing note.
		id = addEntry(entries, id, ElementalWeapons.ABOMINABLEBLADE, TIER_PRESTIGE, 200000, 1, 14);
		id = addEntry(entries, id, ElementalWeapons.FROSTAXE, TIER_PRESTIGE, 150000, 1, 14);
		addEntry(entries, id, ElementalWeapons.MOLTENSWORD, TIER_PRESTIGE, 100000, 1, 14);

		return List.copyOf(entries);
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
