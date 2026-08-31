package com.pisomarket.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// When each player last collected a FULL boss payout, per boss type.
//
// This exists because the Wither and the Ender Dragon are both
// re-summonable. At 500,000 and 750,000 Shards a kill, and with a dragon
// respawn costing only four end crystals, an uncapped payout is a
// straightforward money printer — one loop out-earns every other activity
// in the game combined. The Warden is not summonable but can be farmed in
// a deep dark by a strong enough player.
//
// Keyed by "<player uuid>|<boss id>" rather than a nested map, so the codec
// stays a flat string->int and there is no second level of decoding to get
// wrong.
public class BossPayoutState extends SavedData {
	public static final Codec<BossPayoutState> CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(BossPayoutState::new, BossPayoutState::getDays);

	public static final SavedDataType<BossPayoutState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "boss_payouts"), BossPayoutState::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<String, Integer> lastFullPayoutDay;

	public BossPayoutState() {
		this(new HashMap<>());
	}

	// Mutable copy — Codec.unboundedMap decodes into an ImmutableMap, so
	// using it directly makes every write throw once loaded from disk.
	// Same trap PisoVault hit.
	private BossPayoutState(final Map<String, Integer> days) {
		this.lastFullPayoutDay = new HashMap<>(days);
	}

	private Map<String, Integer> getDays() {
		return lastFullPayoutDay;
	}

	private static String key(final UUID player, final String bossId) {
		return player + "|" + bossId;
	}

	// True if this player is due a full payout for this boss type today.
	// A player who has never killed it has no entry and is always due.
	public boolean isFullPayoutDue(final UUID player, final String bossId, final int today, final int cooldownDays) {
		Integer last = lastFullPayoutDay.get(key(player, bossId));
		return last == null || today - last >= cooldownDays;
	}

	public void recordFullPayout(final UUID player, final String bossId, final int today) {
		lastFullPayoutDay.put(key(player, bossId), today);
		setDirty();
	}
}
