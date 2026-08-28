package com.pisomarket.claims.lock;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import com.pisomarket.PisoMarket;

// Only the restricted-chest menu remains here. The Lock item was removed:
// chest access is now a claim-wide setting edited in the Land Deed book
// (see ChestAccess / ChestAccessGuard), so there's no item to buy or place.
public final class LockContent {
	private static final Identifier RESTRICTED_MENU_ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "restricted_chest");

	public static final MenuType<RestrictedChestMenu> RESTRICTED_MENU_TYPE =
			new MenuType<>(RestrictedChestMenu::new, FeatureFlags.VANILLA_SET);

	private LockContent() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.MENU, RESTRICTED_MENU_ID, RESTRICTED_MENU_TYPE);
	}
}
