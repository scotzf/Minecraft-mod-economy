package com.pisomarket.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// Vault balance: a long per player UUID, whole units only. Never floating
// point here — fractional rounding is a duplication exploit (see CLAUDE.md).
// This is server-wide (via MinecraftServer.getDataStorage(), not a specific
// ServerLevel's) since a player's money isn't tied to which dimension
// they're in.
public class PisoVault extends SavedData {
	public static final Codec<PisoVault> CODEC =
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).xmap(PisoVault::new, PisoVault::getBalances);

	public static final SavedDataType<PisoVault> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "vault"), PisoVault::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<UUID, Long> balances;

	public PisoVault() {
		this(new HashMap<>());
	}

	// MUST copy into a mutable map. Codec.unboundedMap decodes into an
	// ImmutableMap, so taking the decoded map directly meant every vault
	// LOADED FROM DISK was unmodifiable and the first deposit/withdraw
	// threw UnsupportedOperationException. Fresh worlds worked (the no-arg
	// constructor makes a HashMap), which is why this only showed up as
	// "/deposit failed and ate my potatoes" on an existing save.
	private PisoVault(final Map<UUID, Long> balances) {
		this.balances = new HashMap<>(balances);
	}

	private Map<UUID, Long> getBalances() {
		return balances;
	}

	public long getBalance(final UUID player) {
		return balances.getOrDefault(player, 0L);
	}

	// Validates before mutating, per CLAUDE.md's rule: amounts are long,
	// validated positive before touching storage.
	public void deposit(final UUID player, final long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Deposit amount must be positive");
		}
		balances.merge(player, amount, Long::sum);
		setDirty();
	}

	// Returns false (and changes nothing) if the balance is insufficient,
	// instead of throwing — callers use this to report a normal "not enough
	// money" failure to the player rather than a crash.
	public boolean withdraw(final UUID player, final long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Withdraw amount must be positive");
		}
		long current = getBalance(player);
		if (current < amount) {
			return false;
		}
		balances.put(player, current - amount);
		setDirty();
		return true;
	}

	// Atomic vault-to-vault transfer: either both sides update, or neither
	// does (insufficient funds), so money can never be created or destroyed
	// by a failed /pay.
	public boolean transfer(final UUID from, final UUID to, final long amount) {
		if (!withdraw(from, amount)) {
			return false;
		}
		deposit(to, amount);
		return true;
	}

	// Admin-only mint/burn to an exact value — unlike deposit/withdraw this
	// is not conserving (it can create or destroy money), which is fine
	// here because /eco set is explicitly an admin override, not a player
	// transaction. Negative amounts are rejected the same as everywhere
	// else money touches storage (CLAUDE.md's "Rules for all commands").
	public void setBalance(final UUID player, final long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Balance cannot be set negative");
		}
		balances.put(player, amount);
		setDirty();
	}

	// Sum of every vault balance. NOT total money in circulation — cash
	// sitting in inventories and chests isn't tracked anywhere (see
	// CLAUDE.md's "Data model" note). This is the floor, not the total.
	public long totalVaultBalance() {
		long total = 0;
		for (long balance : balances.values()) {
			total += balance;
		}
		return total;
	}
}
