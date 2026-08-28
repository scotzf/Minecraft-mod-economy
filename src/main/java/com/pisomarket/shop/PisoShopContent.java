package com.pisomarket.shop;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.pisomarket.PisoMarket;

public final class PisoShopContent {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "shop");
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, ID);

	// Blocks.register bakes the registry key into Properties before
	// construction (BlockBehaviour needs it internally) and registers the
	// result in one call — the same helper vanilla's own Blocks class uses
	// for every block, access-widened by Fabric for mod use.
	//
	// strength(50.0F, 1200.0F) matches vanilla Obsidian's hardness/blast
	// resistance exactly (slow to break by hand, same as obsidian) — but
	// deliberately without .requiresCorrectToolForDrops(), so unlike
	// obsidian it always drops itself no matter what breaks it, including
	// bare hands.
	public static final Block SHOP_BLOCK = Blocks.register(
			BLOCK_KEY, PisoShopBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(50.0f, 1200.0f).sound(SoundType.STONE)
	);

	// No Fabric API helper needed for a plain menu type with no extra sync
	// data — this is the same constructor vanilla itself uses internally
	// for MenuType.GENERIC_9x1 etc. (public via Fabric's transitive access
	// widener).
	public static final MenuType<PisoShopMenu> MENU_TYPE = new MenuType<>(PisoShopMenu::new, FeatureFlags.VANILLA_SET);

	// Display-only red arrow used inside the shop menu to point at the two
	// real item slots (Sell, Vault deposit). Not obtainable, not craftable,
	// no behavior — it only ever exists in a menu slot. A vanilla ARROW was
	// tried first but reads as ammunition, not a pointer.
	private static final Identifier POINTER_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "pointer");
	public static final Item POINTER = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, POINTER_ID)));

	private PisoShopContent() {
	}

	public static void register() {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ID);
		Registry.register(BuiltInRegistries.ITEM, ID, new BlockItem(SHOP_BLOCK, new Item.Properties().setId(itemKey)));
		Registry.register(BuiltInRegistries.ITEM, POINTER_ID, POINTER);
		Registry.register(BuiltInRegistries.MENU, ID, MENU_TYPE);
	}
}
