package com.pisomarket.level;

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

// The HIGHEST vanilla XP level each player has ever reached.
//
// Stats key off vanilla's own XP level (the green number in the HUD) —
// there is no separate Piso level any more. But they key off the PEAK, not
// the current value, and that distinction is the whole reason this class
// exists: spending XP at an enchanting table drops your level, and losing
// hearts because you enchanted a sword would be a miserable mechanic.
//
// So: reaching level 30 grants those stats permanently. Enchanting back
// down to 0 keeps them.
public class PlayerLevels extends SavedData {
	// Stats stop accruing past this. Vanilla XP levels are unbounded, and
	// an uncapped +HP per level eventually makes a player unkillable.
	public static final int MAX_LEVEL = 50;

	public static final Codec<PlayerLevels> CODEC =
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).xmap(PlayerLevels::new, PlayerLevels::getPeaks);

	public static final SavedDataType<PlayerLevels> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "levels"), PlayerLevels::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<UUID, Integer> peakLevel;

	public PlayerLevels() {
		this(new HashMap<>());
	}

	// Mutable copy — Codec.unboundedMap decodes into an ImmutableMap, so
	// using it directly makes every write throw once loaded from disk.
	private PlayerLevels(final Map<UUID, Integer> peaks) {
		this.peakLevel = new HashMap<>(peaks);
	}

	private Map<UUID, Integer> getPeaks() {
		return peakLevel;
	}

	// The level stats are computed from: highest ever reached, capped.
	public int effectiveLevel(final UUID player) {
		return Math.min(peakLevel.getOrDefault(player, 0), MAX_LEVEL);
	}

	// Records a new peak if this beats the old one. Returns true when it
	// actually moved, so the caller knows to reapply stats and announce.
	public boolean raisePeak(final UUID player, final int currentLevel) {
		int capped = Math.min(currentLevel, MAX_LEVEL);
		if (capped <= peakLevel.getOrDefault(player, 0)) {
			return false;
		}
		peakLevel.put(player, capped);
		setDirty();
		return true;
	}
}
