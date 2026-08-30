package com.pisomarket.level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// Persistent per-player level and XP.
//
// Deliberately a SEPARATE track from vanilla's XP bar. Sharing vanilla XP
// would make levelling compete with enchanting for the same resource —
// spend on Sharpness V and you delay your next stat point, which punishes
// players for using a core vanilla system.
public class PlayerLevels extends SavedData {
	// Max level. Stats are granted on the way up; past this nothing more
	// is awarded.
	public static final int MAX_LEVEL = 50;

	// XP to go from level N to N+1 is XP_PER_LEVEL_STEP * N, so each level
	// costs more than the last. Linear-increasing rather than exponential:
	// an exponential curve puts the last few levels out of reach entirely
	// on a server this size.
	public static final int XP_PER_LEVEL_STEP = 100;

	private record Progress(int level, int xp) {
		private static final Codec<Progress> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("level").forGetter(Progress::level),
						Codec.INT.fieldOf("xp").forGetter(Progress::xp)
				).apply(instance, Progress::new)
		);
	}

	public static final Codec<PlayerLevels> CODEC =
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Progress.CODEC).xmap(PlayerLevels::new, PlayerLevels::getProgress);

	public static final SavedDataType<PlayerLevels> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "levels"), PlayerLevels::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<UUID, Progress> progress;

	public PlayerLevels() {
		this(new HashMap<>());
	}

	// MUST copy into a mutable map — Codec.unboundedMap decodes into an
	// ImmutableMap, so using it directly makes every write throw once the
	// data has been loaded from disk. Same trap PisoVault hit.
	private PlayerLevels(final Map<UUID, Progress> progress) {
		this.progress = new HashMap<>(progress);
	}

	private Map<UUID, Progress> getProgress() {
		return progress;
	}

	// XP required to advance FROM this level to the next.
	public static int xpToNext(final int level) {
		return XP_PER_LEVEL_STEP * Math.max(1, level);
	}

	public int levelOf(final UUID player) {
		Progress p = progress.get(player);
		return p == null ? 1 : p.level();
	}

	public int xpOf(final UUID player) {
		Progress p = progress.get(player);
		return p == null ? 0 : p.xp();
	}

	// Adds XP and returns how many levels were gained (0 if none). Loops
	// rather than dividing, so a single huge XP award still only ever
	// advances one level at a time and cannot skip past MAX_LEVEL.
	public int addXp(final UUID player, final int amount) {
		if (amount <= 0) {
			return 0;
		}
		Progress current = progress.getOrDefault(player, new Progress(1, 0));
		int level = current.level();
		int xp = current.xp() + amount;
		int gained = 0;

		while (level < MAX_LEVEL && xp >= xpToNext(level)) {
			xp -= xpToNext(level);
			level++;
			gained++;
		}

		if (level >= MAX_LEVEL) {
			xp = 0; // nothing left to spend it on
		}

		progress.put(player, new Progress(level, xp));
		setDirty();
		return gained;
	}
}
