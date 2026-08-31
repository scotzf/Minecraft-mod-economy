package com.pisomarket;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.claims.ClaimsContent;
import com.pisomarket.claims.DeedCatalog;
import com.pisomarket.claims.LandDeedItem;
import com.pisomarket.shop.PisoShopContent;

// Puts this mod's items into the vanilla creative menu so they're findable
// (and searchable) without commands. The Pointer is deliberately excluded —
// it's a display-only decoration for menu slots, not a real item anyone
// should be able to obtain.
public final class PisoCreativeTabs {
	private PisoCreativeTabs() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
				output.accept(new ItemStack(PisoShopContent.SHOP_BLOCK), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)
		);

		// Potions in the food/drink tab so they can be grabbed for testing
		// without going through the BlackMarket every time.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(new ItemStack(com.pisomarket.economy.harvest.FortunePotionItem.FORTUNE_I), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.economy.harvest.FortunePotionItem.FORTUNE_II), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.economy.harvest.FortunePotionItem.LUCK), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		});

		// The full elemental weapon set, in the combat tab so it can be
		// grabbed for testing without the shop.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.MOLTENSWORD), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.MOLTENBLADE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.FROSTBLADE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.FROSTAXE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.FROSTSCYTHE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.ABOMINABLEBLADE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.ABOMINABLEGREATSABER), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.ABOMINABLESCYTHE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.SOUL_DEVOURER), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.SOUL_COLLECTOR), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.DIVINEAXERHITTA), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(new ItemStack(com.pisomarket.combat.ElementalWeapons.DIVINE_REAPER), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			for (net.minecraft.world.item.equipment.ArmorType type : new net.minecraft.world.item.equipment.ArmorType[] {
					net.minecraft.world.item.equipment.ArmorType.HELMET, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE,
					net.minecraft.world.item.equipment.ArmorType.LEGGINGS, net.minecraft.world.item.equipment.ArmorType.BOOTS
			}) {
				output.accept(new ItemStack(com.pisomarket.combat.CustomArmorContent.diamond(type)), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
				output.accept(new ItemStack(com.pisomarket.combat.CustomArmorContent.netherite(type)), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
				output.accept(new ItemStack(com.pisomarket.combat.CustomArmorContent.gold(type)), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			}
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			// One entry per deed size, each already carrying its
			// width/length/height so it's usable straight out of creative.
			for (DeedCatalog.DeedSize size : DeedCatalog.SIZES) {
				output.accept(
						LandDeedItem.createUnbound(ClaimsContent.LAND_DEED, size.label(), size.width(), size.length(), size.height(), size.price()),
						CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
				);
			}
		});
	}
}
