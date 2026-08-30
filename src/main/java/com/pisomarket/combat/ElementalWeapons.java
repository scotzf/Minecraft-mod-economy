package com.pisomarket.combat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import com.pisomarket.PisoMarket;

// SAMPLE — one weapon only, to prove the pipeline end to end before the full
// five-line set goes in.
//
// Frostblade was chosen deliberately: it is the only strong candidate with no
// animated overlay texture, so what renders in game is exactly what was
// previewed. Anything that looks wrong is a real problem, not an artefact of
// a preview we couldn't reproduce.
//
// Balance rule from CLAUDE.md holds: IRON material, never diamond, so
// player-enchanted gear stays strictly better in a straight fight. The
// Slowness on hit is the reason to carry it, not the damage number.
public final class ElementalWeapons {
	private static final Identifier FROSTBLADE_ID =
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "frostblade");

	public static final Item FROSTBLADE = new ElementalBladeItem(
			Element.FROST,
			new Item.Properties()
					.setId(ResourceKey.create(Registries.ITEM, FROSTBLADE_ID))
					.sword(ToolMaterial.IRON, 3.0F, -2.4F)
	);

	private ElementalWeapons() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, FROSTBLADE_ID, FROSTBLADE);
	}
}
