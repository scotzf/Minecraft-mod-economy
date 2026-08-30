package com.pisomarket.combat;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;

import com.pisomarket.PisoMarket;

// Wings on boots, second pass. First pass used a hand-drawn icon and a
// custom wing texture, both rejected — this pass keeps the wing geometry
// (WingedBootsLayer, unchanged) but swaps the BOOT's own look for the
// CarroRateXMods pack's real diamond/netherite/gold art instead, across
// three separate items so each keeps that material's real armor stats.
//
// The trick: .humanoidArmor(material, type) sets up durability, attribute
// values (defense/toughness) AND the default visual asset all at once —
// there's no way to ask for "diamond's numbers, someone else's texture"
// in one call. So it's called first for the correct stats, then a second
// .component(DataComponents.EQUIPPABLE, ...) call overwrites just the
// asset reference with our own equipment JSON
// (assets/pisomarket/equipment/winged_boots_<material>.json), which points
// at the copied CarroRateXMods textures instead of vanilla's.
public final class WingedBootsContent {
	public static final Item WINGED_BOOTS_DIAMOND = create("diamond", ArmorMaterials.DIAMOND);
	public static final Item WINGED_BOOTS_NETHERITE = create("netherite", ArmorMaterials.NETHERITE);
	public static final Item WINGED_BOOTS_GOLD = create("gold", ArmorMaterials.GOLD);

	public static final List<Item> ALL = List.of(WINGED_BOOTS_DIAMOND, WINGED_BOOTS_NETHERITE, WINGED_BOOTS_GOLD);

	private WingedBootsContent() {
	}

	private static Item create(final String name, final ArmorMaterial material) {
		Identifier itemId = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots_" + name);
		Identifier assetId = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots_" + name);
		ResourceKey<EquipmentAsset> customAsset = ResourceKey.create(EquipmentAssets.ROOT_ID, assetId);

		return new Item(
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, itemId))
						.humanoidArmor(material, ArmorType.BOOTS)
						.component(
								DataComponents.EQUIPPABLE,
								Equippable.builder(EquipmentSlot.FEET)
										.setEquipSound(material.equipSound())
										.setAsset(customAsset)
										.build()
						)
		);
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots_diamond"), WINGED_BOOTS_DIAMOND);
		Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots_netherite"), WINGED_BOOTS_NETHERITE);
		Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots_gold"), WINGED_BOOTS_GOLD);
	}
}
