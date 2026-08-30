package com.pisomarket.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pisomarket.PisoMarket;
import com.pisomarket.claims.lock.LockContent;
import com.pisomarket.economy.VaultBalancePayload;
import com.pisomarket.shop.PisoShopContent;

import com.pisomarket.economy.PisoCurrency;

// Client-only entrypoint. Registers the HUD and every custom menu's screen.
//
// Currency is the Sunstone Shard (see PisoCurrency).
public class PisoMarketClient implements ClientModInitializer {
	private static final Identifier SHARD_BALANCE_HUD =
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "shard_balance");

	// The HUD shows vault balance, not held inventory count — the vault is
	// server-only data (PisoVault, a SavedData), so it has to be pushed
	// over the network (see VaultSync) and cached here for the HUD to read
	// every frame. Starts at 0 until the first sync packet arrives (join,
	// or shortly after).
	private static long cachedBalance = 0;

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(SHARD_BALANCE_HUD, PisoMarketClient::renderShardBalance);
		MenuScreens.register(PisoShopContent.MENU_TYPE, PisoShopScreen::new);
		MenuScreens.register(LockContent.RESTRICTED_MENU_TYPE, RestrictedChestScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(
				VaultBalancePayload.TYPE, (payload, context) -> cachedBalance = payload.balance()
		);
	}

	private static void renderShardBalance(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		// Built here, not as a static field: constructing an ItemStack at
		// class-load time crashes with "Components not bound yet" because
		// mod entrypoints run before Minecraft finishes binding item data
		// components. By render time the game is fully loaded, so it's safe.
		ItemStack shardIcon = new ItemStack(PisoCurrency.SUNSTONE_SHARD);

		int margin = 4;
		int iconSize = 16;
		int x = graphics.guiWidth() - margin - iconSize;
		int y = margin;

		graphics.item(shardIcon, x, y);
		graphics.itemDecorations(minecraft.font, shardIcon, x, y, String.valueOf(cachedBalance));
	}
}
