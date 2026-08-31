package com.pisomarket.economy;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// Pushes a player's current vault balance to their own client, so the HUD
// (client-side, no access to server SavedData) can show the real number
// instead of raw inventory count. Call this after anything that changes a
// player's own balance — see call sites across PisoCommands, MarketCommands,
// ShopCommands, DeedCommands, LockCommands, HarvestFaucet, and PisoShopMenu.
public final class VaultSync {
	// Players whose balance changed since the last flush.
	//
	// This exists because sync() used to be called once per Shard earned,
	// which meant a network packet for every crop a player broke. Harvesting
	// a field is a burst of hundreds of those. Coalescing to one packet per
	// FLUSH_INTERVAL_TICKS collapses that to at most five a second while
	// staying far below what anyone perceives as delay.
	private static final Set<UUID> pending = ConcurrentHashMap.newKeySet();
	private static final int FLUSH_INTERVAL_TICKS = 4;
	private static int tickCounter;

	private VaultSync() {
	}

	// Preferred entry point for anything in a hot path. Marks the balance
	// stale; the next flush sends it.
	public static void markDirty(final ServerPlayer player) {
		pending.add(player.getUUID());
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(VaultBalancePayload.TYPE, VaultBalancePayload.CODEC);
		// Initial sync on join — without this the HUD would show 0 (or the
		// last cached value from a previous session) until the player does
		// something that happens to trigger a sync.
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> sync(listener.player));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % FLUSH_INTERVAL_TICKS != 0 || pending.isEmpty()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (pending.remove(player.getUUID())) {
					sync(player);
				}
			}
			// Anything left belongs to players who logged out before the
			// flush; their HUD re-syncs on next join anyway.
			pending.clear();
		});
	}

	public static void sync(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long balance = server.getDataStorage().computeIfAbsent(PisoVault.TYPE).getBalance(player.getUUID());
		ServerPlayNetworking.send(player, new VaultBalancePayload(balance));
	}
}
