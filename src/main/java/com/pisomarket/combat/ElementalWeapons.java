package com.pisomarket.combat;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import com.pisomarket.PisoMarket;

// The full five-line set from the "Custom weapons" table in CLAUDE.md.
// Frostblade was the sample that proved the import pipeline end to end; the
// other fourteen followed the same procedure once it compiled clean.
//
// Balance rule from CLAUDE.md holds everywhere here: IRON material, never
// diamond, so player-enchanted gear stays strictly better in a straight
// fight. The on-hit effect is the reason to carry one, never the damage
// number.
//
// Tool profile per column, applied uniformly across all five lines:
// - Sword column: .sword(IRON, 3.0F, -2.4F) — vanilla iron sword stats.
// - Heavy column: .axe(IRON, 6.0F, -3.1F) — vanilla iron axe stats. This is
//   cosmetic weight, not a mining-speed grant anyone asked for; it just
//   matches what a "heavy" weapon should feel like in hand.
// - Reach column: also .sword(IRON, 3.0F, -2.4F). Minecraft has no scythe
//   tool type, and CLAUDE.md never asked for an actual extended hit range —
//   "Reach" here is the art category the pack ships these models under, not
//   a mechanical difference from the Sword column. Flag this if a real
//   reach bonus is ever wanted; it isn't implemented.
public final class ElementalWeapons {
	// Registration needs each item's Identifier back, and Item carries no
	// public getter for the key it was built with — so the map is the
	// source of truth for both construction and register(), same as the
	// FROSTBLADE_ID local variable did before this was fifteen items.
	private static final Map<Identifier, Item> ITEMS = new LinkedHashMap<>();

	private ElementalWeapons() {
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, path);
	}

	private static Item sword(final String path, final Element element) {
		Identifier itemId = id(path);
		Item item = new ElementalBladeItem(
				element,
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, itemId))
						.sword(ToolMaterial.IRON, 3.0F, -2.4F)
		);
		ITEMS.put(itemId, item);
		return item;
	}

	private static Item heavy(final String path, final Element element) {
		Identifier itemId = id(path);
		Item item = new ElementalBladeItem(
				element,
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, itemId))
						.axe(ToolMaterial.IRON, 6.0F, -3.1F)
		);
		ITEMS.put(itemId, item);
		return item;
	}

	// Molten — Ignite. Hearthflame (the Reach column) was cut from the
	// final roster 2026-08-31 — not built, don't re-add without checking.
	public static final Item MOLTENSWORD = sword("moltensword", Element.EMBER);
	public static final Item MOLTENBLADE = heavy("moltenblade", Element.EMBER);

	// Frost — Slowness
	public static final Item FROSTBLADE = sword("frostblade", Element.FROST);
	public static final Item FROSTAXE = heavy("frostaxe", Element.FROST);
	public static final Item FROSTSCYTHE = sword("frostscythe", Element.FROST);

	// Blight — Poison
	public static final Item ABOMINABLEBLADE = sword("abominableblade", Element.VENOM);
	public static final Item ABOMINABLEGREATSABER = heavy("abominablegreatsaber", Element.VENOM);
	public static final Item ABOMINABLESCYTHE = sword("abominablescythe", Element.VENOM);

	// Soul — Lifesteal. Soul Edge (the Sword column) was cut from the
	// final roster 2026-08-31 — not built, don't re-add without checking.
	public static final Item SOUL_DEVOURER = heavy("soul_devourer", Element.LIFESTEAL);
	public static final Item SOUL_COLLECTOR = sword("soul_collector", Element.LIFESTEAL);

	// Divine — Smite
	public static final Item DIVINE_JUSTICE = sword("divine_justice", Element.SMITE);
	public static final Item DIVINEAXERHITTA = heavy("divineaxerhitta", Element.SMITE);
	public static final Item DIVINE_REAPER = sword("divine_reaper", Element.SMITE);

	public static void register() {
		for (Map.Entry<Identifier, Item> entry : ITEMS.entrySet()) {
			Registry.register(BuiltInRegistries.ITEM, entry.getKey(), entry.getValue());
		}
	}
}
