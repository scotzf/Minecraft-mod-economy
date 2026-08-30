package com.pisomarket.combat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

import com.pisomarket.PisoMarket;

// First pass at "wings on boots" — a real 3D wing model attached at the
// ankle (see WingedBootsLayer, client-only), not a flat texture decal. A
// decal was tried first and rejected: at 16x16 it read as disconnected
// pixel flecks, not a wing (same lesson the 15 elemental weapons already
// taught this project about flat art next to real geometry).
//
// A brand-new item rather than reskinning vanilla diamond boots, so normal
// diamond boots stay untouched for anyone not wearing this one specifically.
// Armor stats are identical to vanilla diamond boots (same material, same
// slot) — this pass is about the visual, not new combat numbers.
public final class WingedBootsContent {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots");

	public static final Item WINGED_BOOTS = new Item(
			new Item.Properties()
					.setId(ResourceKey.create(Registries.ITEM, ID))
					.humanoidArmor(ArmorMaterials.DIAMOND, ArmorType.BOOTS)
	);

	private WingedBootsContent() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, ID, WINGED_BOOTS);
	}
}
