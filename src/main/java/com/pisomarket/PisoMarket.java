package com.pisomarket;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pisomarket.claims.ClaimCommands;
import com.pisomarket.claims.ClaimProtection;
import com.pisomarket.claims.ClaimsContent;
import com.pisomarket.claims.DeedCommands;
import com.pisomarket.claims.TerritoryVisualizer;
import com.pisomarket.claims.lock.ChestAccessGuard;
import com.pisomarket.claims.lock.LockContent;
import com.pisomarket.economy.EcoCommands;
import com.pisomarket.economy.PisoCommands;
import com.pisomarket.economy.VaultSync;
import com.pisomarket.economy.harvest.HarvestFaucet;
import com.pisomarket.market.MarketCommands;
import com.pisomarket.shop.PisoShopContent;
import com.pisomarket.shop.system.ShopCommands;

// Server + client entrypoint (runs on both). Registration for items, commands,
// and SavedData will attach here as the build order (see CLAUDE.md) adds
// each system: balance/vault, market listings, shop, claims.
public class PisoMarket implements ModInitializer {
	public static final String MOD_ID = "pisomarket";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		com.pisomarket.economy.PisoCurrency.register();
		PisoCommands.register();
		EcoCommands.register();
		com.pisomarket.economy.DeathPenalty.register();
		VaultSync.register();
		com.pisomarket.economy.LeaderboardCommands.register();
		com.pisomarket.economy.LeaderboardTracker.register();
		HarvestFaucet.register();
		com.pisomarket.economy.harvest.PisoEffects.register();
		com.pisomarket.economy.harvest.FortunePotionItem.register();
		MarketCommands.register();
		ShopCommands.register();
		PisoShopContent.register();
		com.pisomarket.shop.PisoUiItems.register();
		ClaimsContent.register();
		ClaimCommands.register();
		ClaimProtection.register();
		DeedCommands.register();
		TerritoryVisualizer.register();
		com.pisomarket.claims.RentCollector.register();
		LockContent.register();
		ChestAccessGuard.register();
		com.pisomarket.combat.ElementalWeapons.register();
		com.pisomarket.combat.CustomArmorContent.register();
		com.pisomarket.combat.MobDrops.register();
		com.pisomarket.combat.MobScaling.register();
		com.pisomarket.level.LevelManager.register();
		com.pisomarket.travel.WaypointContent.register();
		com.pisomarket.travel.WarpCommand.register();
		PisoCreativeTabs.register();
		LOGGER.info("Piso Market initialized");
	}
}
