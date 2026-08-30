package com.pisomarket.combat;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import com.pisomarket.PisoMarket;

// Custom Diamond/Netherite/Gold armor sets, CarroRateXMods art, built to
// survive the v2 weapon rebalance (30-40 dmg per hit is now normal — see
// CLAUDE.md's combat rebalance notes). Vanilla diamond/netherite/gold
// armor are completely untouched; these are separate items layered on top.
//
// Why toughness and not armor points: verified against the real game code
// (CombatRules.getDamageAfterAbsorb) that damage reduction is hard-capped
// at 20 armor points / 80% no matter how high the raw number goes — vanilla
// diamond/netherite already sit at that cap, so more "armor" on a custom
// set would do literally nothing. Toughness has no such ceiling and is
// what actually keeps that 80% closer to true against a big hit, so that's
// the only stat these sets change.
//
// Durability, base defense-per-piece, enchantability, equip sound and
// knockback resistance are all copied directly from the real
// ArmorMaterials constants (not retyped by hand) so there's no risk of
// silently drifting from vanilla's own numbers on anything except the one
// stat this is deliberately changing.
public final class CustomArmorContent {
	// Total toughness across a full 4-piece set — matches the numbers
	// discussed and confirmed: vanilla diamond is 8, netherite 12, gold 0.
	// These divide by 4 for the per-piece value ArmorMaterial actually
	// takes, since each equipped piece contributes the same amount
	// (see ArmorMaterial.createAttributes — one ADD_VALUE modifier per
	// piece worn, so a full set sums to 4x the per-piece figure).
	private static final float DIAMOND_TOUGHNESS_PER_PIECE = 18.0F / 4.0F;
	private static final float NETHERITE_TOUGHNESS_PER_PIECE = 28.0F / 4.0F;
	private static final float GOLD_TOUGHNESS_PER_PIECE = 10.0F / 4.0F;

	private static final Map<Identifier, Item> ITEMS = new LinkedHashMap<>();

	private static final Map<ArmorType, Item> DIAMOND_ITEMS = new EnumMap<>(ArmorType.class);
	private static final Map<ArmorType, Item> NETHERITE_ITEMS = new EnumMap<>(ArmorType.class);
	private static final Map<ArmorType, Item> GOLD_ITEMS = new EnumMap<>(ArmorType.class);

	static {
		ArmorMaterial customDiamond = rebuilt(ArmorMaterials.DIAMOND, DIAMOND_TOUGHNESS_PER_PIECE, "custom_diamond");
		ArmorMaterial customNetherite = rebuilt(ArmorMaterials.NETHERITE, NETHERITE_TOUGHNESS_PER_PIECE, "custom_netherite");
		ArmorMaterial customGold = rebuilt(ArmorMaterials.GOLD, GOLD_TOUGHNESS_PER_PIECE, "custom_gold");

		for (ArmorType type : new ArmorType[] {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
			DIAMOND_ITEMS.put(type, create("custom_diamond_" + type.getName(), customDiamond, type));
			NETHERITE_ITEMS.put(type, create("custom_netherite_" + type.getName(), customNetherite, type));
			GOLD_ITEMS.put(type, create("custom_gold_" + type.getName(), customGold, type));
		}
	}

	private CustomArmorContent() {
	}

	public static Item diamond(final ArmorType type) {
		return DIAMOND_ITEMS.get(type);
	}

	public static Item netherite(final ArmorType type) {
		return NETHERITE_ITEMS.get(type);
	}

	public static Item gold(final ArmorType type) {
		return GOLD_ITEMS.get(type);
	}

	// Same durability/defense/enchantability/equip sound/knockback as the
	// real vanilla material — only toughness and the visual asset change.
	private static ArmorMaterial rebuilt(final ArmorMaterial base, final float toughnessPerPiece, final String assetName) {
		ResourceKey<EquipmentAsset> customAsset =
				ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, assetName));
		return new ArmorMaterial(
				base.durability(),
				base.defense(),
				base.enchantmentValue(),
				base.equipSound(),
				toughnessPerPiece,
				base.knockbackResistance(),
				base.repairIngredient(),
				customAsset
		);
	}

	private static Item create(final String name, final ArmorMaterial material, final ArmorType type) {
		Identifier itemId = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, name);
		Item item = new Item(
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, itemId))
						.humanoidArmor(material, type)
						.component(
								DataComponents.EQUIPPABLE,
								Equippable.builder(type.getSlot())
										.setEquipSound(material.equipSound())
										.setAsset(material.assetId())
										.build()
						)
		);
		ITEMS.put(itemId, item);
		return item;
	}

	public static void register() {
		for (Map.Entry<Identifier, Item> entry : ITEMS.entrySet()) {
			Registry.register(BuiltInRegistries.ITEM, entry.getKey(), entry.getValue());
		}
	}
}
