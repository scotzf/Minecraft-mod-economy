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

// The 13 custom weapons, carrying the combat rebalance numbers from
// CLAUDE.md's "Combat — FULL REBALANCE SPEC" section.
//
// The old "IRON material, never diamond, so player-enchanted gear stays
// strictly better" rule is REVERSED as of that spec. These are deliberately
// stronger than diamond; the counterweight is that only the three custom
// armor sets (CustomArmorContent) can survive them.
//
// HOW THE DAMAGE NUMBERS WORK. Item.Properties.sword/axe take a *baseline*
// that vanilla then adds things to — the final in-game damage is
//
//     1.0 (player base) + baseline + material.attackDamageBonus
//
// and ToolMaterial.IRON's bonus is 2.0 (confirmed by decompiling
// ToolMaterial). Attack speed works the same way against a base of 4.0.
// Rather than leave that arithmetic implicit in a pile of magic numbers,
// the helpers below take the FINAL damage and speed straight from the spec
// table and do the conversion themselves — so the constants in this file
// read exactly like the design doc, and there is no second place for the
// two to drift apart.
//
// Material stays IRON purely for durability/enchantability/repair-item
// (250 durability, repairs with iron). That is low for endgame gear and is
// worth revisiting, but it is not what sets the damage any more.
public final class ElementalWeapons {
	private static final float PLAYER_BASE_DAMAGE = 1.0F;
	private static final float IRON_DAMAGE_BONUS = 2.0F;
	private static final float PLAYER_BASE_SPEED = 4.0F;

	// Shape identity, tuned so all three land at ~46-48 DPS with completely
	// different feel. See the spec table in CLAUDE.md.
	private static final float SWORD_SPEED = 1.6F;
	private static final float HEAVY_SPEED = 1.0F;
	private static final float SCYTHE_SPEED = 1.05F;

	// Only the Heavy/axe shape crits.
	private static final float HEAVY_CRIT_CHANCE = 0.30F;
	private static final float HEAVY_CRIT_MULTIPLIER = 1.5F;
	private static final float NO_CRIT = 0.0F;

	private static final boolean CLEAVE = true;
	private static final boolean NO_CLEAVE = false;

	private static final int SECONDS = 20; // ticks

	private static final Map<Identifier, Item> ITEMS = new LinkedHashMap<>();

	private ElementalWeapons() {
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, path);
	}

	// Sword and Scythe both use vanilla's sword profile — Minecraft has no
	// scythe tool type. They differ in the damage/speed numbers passed in,
	// which is the whole of the shape distinction.
	private static Item sword(final String path, final Element element, final float damage, final float speed,
			final float magnitude, final int durationTicks, final boolean cleave) {
		Identifier itemId = id(path);
		Item item = new ElementalBladeItem(
				element, magnitude, durationTicks, NO_CRIT, 1.0F, cleave, damage,
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, itemId))
						.sword(ToolMaterial.IRON, damageBaseline(damage), speedBaseline(speed))
		);
		ITEMS.put(itemId, item);
		return item;
	}

	private static Item heavy(final String path, final Element element, final float damage,
			final float magnitude, final int durationTicks, final boolean cleave) {
		Identifier itemId = id(path);
		Item item = new ElementalBladeItem(
				element, magnitude, durationTicks, HEAVY_CRIT_CHANCE, HEAVY_CRIT_MULTIPLIER, cleave, damage,
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, itemId))
						.axe(ToolMaterial.IRON, damageBaseline(damage), speedBaseline(HEAVY_SPEED))
		);
		ITEMS.put(itemId, item);
		return item;
	}

	private static float damageBaseline(final float finalDamage) {
		return finalDamage - PLAYER_BASE_DAMAGE - IRON_DAMAGE_BONUS;
	}

	private static float speedBaseline(final float finalSpeed) {
		return finalSpeed - PLAYER_BASE_SPEED;
	}

	// --- Tier 1: Souls. Slightly under Tier 2 on raw damage; lifesteal is
	// what makes the comeback. Magnitude is in half-hearts.
	public static final Item SOUL_COLLECTOR =
			sword("soul_collector", Element.LIFESTEAL, 40.0F, SCYTHE_SPEED, 4.0F, 0, NO_CLEAVE);
	public static final Item SOUL_DEVOURER =
			heavy("soul_devourer", Element.LIFESTEAL, 37.0F, 3.0F, 0, CLEAVE);

	// --- Tier 2: the top of the raw-damage ladder. Divine sits just above
	// Abominable so it stays the PvP pick, while Abominable's poison is the
	// better farming effect.
	public static final Item DIVINE_REAPER =
			sword("divine_reaper", Element.SMITE, 44.0F, SCYTHE_SPEED, 6.0F, 0, NO_CLEAVE);
	// Smite +25 is Divine Axe Rhitta's entire identity: 65 total against
	// undead one-shots every common buffed undead (30 HP). Against anything
	// NOT undead it is a plain 40 — the most specialised weapon in the set,
	// deliberately.
	public static final Item DIVINEAXERHITTA =
			heavy("divineaxerhitta", Element.SMITE, 40.0F, 25.0F, 0, NO_CLEAVE);
	public static final Item ABOMINABLESCYTHE =
			sword("abominablescythe", Element.VENOM, 42.0F, SCYTHE_SPEED, 0.0F, 6 * SECONDS, NO_CLEAVE);
	public static final Item ABOMINABLEGREATSABER =
			heavy("abominablegreatsaber", Element.VENOM, 38.0F, 0.0F, 6 * SECONDS, CLEAVE);

	// --- Tier 3. Divine Justice sits at the 30 floor; its charge/spear
	// mechanic is NOT implemented yet (see CLAUDE.md's open questions), so
	// today it is simply a fast Smite sword.
	public static final Item DIVINE_JUSTICE =
			sword("divine_justice", Element.SMITE, 30.0F, SWORD_SPEED, 6.0F, 0, NO_CLEAVE);
	public static final Item FROSTSCYTHE =
			sword("frostscythe", Element.FROST, 39.0F, SCYTHE_SPEED, 0.0F, 3 * SECONDS, NO_CLEAVE);
	public static final Item MOLTENBLADE =
			heavy("moltenblade", Element.EMBER, 35.0F, 5.0F, 0, CLEAVE);
	// The deliberate exception: lowest damage of any non-Molten weapon, in
	// exchange for an 8-second slow (more than double anyone else's). A
	// control weapon, not a damage weapon.
	public static final Item FROSTBLADE =
			sword("frostblade", Element.FROST, 24.0F, SWORD_SPEED, 0.0F, 8 * SECONDS, CLEAVE);

	// --- Tier 4: the entry tier, still comfortably above vanilla diamond.
	public static final Item ABOMINABLEBLADE =
			sword("abominableblade", Element.VENOM, 30.0F, SWORD_SPEED, 0.0F, 4 * SECONDS, CLEAVE);
	public static final Item MOLTENSWORD =
			sword("moltensword", Element.EMBER, 23.0F, SWORD_SPEED, 4.0F, 0, NO_CLEAVE);
	public static final Item FROSTAXE =
			heavy("frostaxe", Element.FROST, 31.0F, 0.0F, 3 * SECONDS, NO_CLEAVE);

	public static void register() {
		for (Map.Entry<Identifier, Item> entry : ITEMS.entrySet()) {
			Registry.register(BuiltInRegistries.ITEM, entry.getKey(), entry.getValue());
		}
	}
}
