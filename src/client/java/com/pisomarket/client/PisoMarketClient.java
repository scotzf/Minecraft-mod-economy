package com.pisomarket.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pisomarket.PisoMarket;
import com.pisomarket.claims.lock.LockContent;
import com.pisomarket.economy.VaultBalancePayload;
import com.pisomarket.shop.PisoShopContent;

// Client-only entrypoint. Registers the HUD and every custom menu's screen.
//
// Currency is the vanilla poisonous potato — decided, final (see CLAUDE.md).
public class PisoMarketClient implements ClientModInitializer {
	private static final Identifier POTATO_BALANCE_HUD =
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "potato_balance");

	private static final ModelLayerLocation WINGED_BOOTS_LAYER =
			new ModelLayerLocation(Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "winged_boots"), "wings");

	// The HUD shows vault balance, not held inventory count — the vault is
	// server-only data (PisoVault, a SavedData), so it has to be pushed
	// over the network (see VaultSync) and cached here for the HUD to read
	// every frame. Starts at 0 until the first sync packet arrives (join,
	// or shortly after).
	private static long cachedBalance = 0;

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(POTATO_BALANCE_HUD, PisoMarketClient::renderPotatoBalance);
		MenuScreens.register(PisoShopContent.MENU_TYPE, PisoShopScreen::new);
		MenuScreens.register(LockContent.RESTRICTED_MENU_TYPE, RestrictedChestScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(
				VaultBalancePayload.TYPE, (payload, context) -> cachedBalance = payload.balance()
		);

		ModelLayerRegistry.registerModelLayer(WINGED_BOOTS_LAYER, WingedBootsLayer::createLayer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityType == EntityTypes.PLAYER && entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				registrationHelper.register(new WingedBootsLayer(avatarRenderer, context.bakeLayer(WINGED_BOOTS_LAYER)));
			}
		});
	}

	private static void renderPotatoBalance(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		// Built here, not as a static field: constructing an ItemStack at
		// class-load time crashes with "Components not bound yet" because
		// mod entrypoints run before Minecraft finishes binding item data
		// components. By render time the game is fully loaded, so it's safe.
		ItemStack potatoIcon = new ItemStack(Items.POISONOUS_POTATO);

		int margin = 4;
		int iconSize = 16;
		int x = graphics.guiWidth() - margin - iconSize;
		int y = margin;

		graphics.item(potatoIcon, x, y);
		graphics.itemDecorations(minecraft.font, potatoIcon, x, y, String.valueOf(cachedBalance));
	}
}
