package com.pisomarket.economy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// Pushes a player's current vault balance to their own client, so the HUD
// (client-side, no access to server SavedData) can show the real number
// instead of raw inventory count. Call this after anything that changes a
// player's own balance — see call sites across PisoCommands, MarketCommands,
// ShopCommands, DeedCommands, LockCommands, HarvestFaucet, and PisoShopMenu.
public final class VaultSync {
	private VaultSync() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(VaultBalancePayload.TYPE, VaultBalancePayload.CODEC);
		// Initial sync on join — without this the HUD would show 0 (or the
		// last cached value from a previous session) until the player does
		// something that happens to trigger a sync.
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> sync(listener.player));
	}

	public static void sync(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long balance = server.getDataStorage().computeIfAbsent(PisoVault.TYPE).getBalance(player.getUUID());
		ServerPlayNetworking.send(player, new VaultBalancePayload(balance));
	}
}
