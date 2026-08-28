package com.pisomarket.shop;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.pisomarket.PisoMarket;

// Display-only icons for the shop menu's buttons and readouts. None of
// these are obtainable, craftable, or in any creative tab — they only ever
// exist inside a menu slot. They replace the plain paper items the menu
// used to use, which all looked identical and read as labels rather than
// buttons.
public final class PisoUiItems {
	public static final Item PLUS_1 = create("ui_plus_1");
	public static final Item PLUS_10 = create("ui_plus_10");
	public static final Item PLUS_100 = create("ui_plus_100");
	public static final Item MINUS_1 = create("ui_minus_1");
	public static final Item MINUS_10 = create("ui_minus_10");
	public static final Item MINUS_100 = create("ui_minus_100");
	public static final Item BALANCE = create("ui_balance");
	public static final Item AMOUNT = create("ui_amount");
	public static final Item DEPOSIT = create("ui_deposit");
	public static final Item WITHDRAW = create("ui_withdraw");
	public static final Item DEPOSIT_ALL = create("ui_deposit_all");
	public static final Item LEADERBOARD = create("ui_leaderboard");

	private static final String[] NAMES = {
			"ui_plus_1", "ui_plus_10", "ui_plus_100",
			"ui_minus_1", "ui_minus_10", "ui_minus_100",
			"ui_balance", "ui_amount", "ui_deposit", "ui_withdraw", "ui_deposit_all", "ui_leaderboard",
	};
	private static final Item[] ITEMS = {
			PLUS_1, PLUS_10, PLUS_100, MINUS_1, MINUS_10, MINUS_100,
			BALANCE, AMOUNT, DEPOSIT, WITHDRAW, DEPOSIT_ALL, LEADERBOARD,
	};

	private PisoUiItems() {
	}

	private static Item create(final String name) {
		Identifier id = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, name);
		return new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)));
	}

	public static void register() {
		for (int i = 0; i < NAMES.length; i++) {
			Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, NAMES[i]), ITEMS[i]);
		}
	}
}
