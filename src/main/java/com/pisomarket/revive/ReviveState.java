package com.pisomarket.revive;

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

// Persisted consecutive-death count per player, so the escalating revive
// cooldown survives a server restart instead of quietly forgiving whoever
// was mid-streak. Only the COUNT and the day of the last death are stored
// here — the active hold itself (whether a player is frozen right now, and
// when they're released) is transient, held in ReviveManager, since a
// server restart clearing an in-progress cooldown is an acceptable edge
// case but silently forgetting how many times someone already died this
// window is not (it would let repeat deaths dodge the whole point of the
// escalation by timing them around a restart).
public class ReviveState extends SavedData {
	private record DeathRecord(int consecutiveDeaths, int lastDeathDay) {
		private static final Codec<DeathRecord> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("consecutiveDeaths").forGetter(DeathRecord::consecutiveDeaths),
						Codec.INT.fieldOf("lastDeathDay").forGetter(DeathRecord::lastDeathDay)
				).apply(instance, DeathRecord::new)
		);
	}

	private static final Codec<Map<UUID, DeathRecord>> RECORDS_CODEC =
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, DeathRecord.CODEC);

	public static final Codec<ReviveState> CODEC =
			RECORDS_CODEC.xmap(ReviveState::new, ReviveState::getRecords);

	public static final SavedDataType<ReviveState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "revive"), ReviveState::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<UUID, DeathRecord> records;

	public ReviveState() {
		this(new HashMap<>());
	}

	// MUST copy into a mutable map — Codec.unboundedMap decodes into an
	// ImmutableMap. See the identical note on PisoVault; this bit PisoVault
	// once already and isn't worth relearning per SavedData.
	private ReviveState(final Map<UUID, DeathRecord> records) {
		this.records = new HashMap<>(records);
	}

	private Map<UUID, DeathRecord> getRecords() {
		return records;
	}

	// Records a death for this player against today's in-game day, and
	// returns the resulting consecutive-death count (1 for a fresh streak).
	// The reset rule is a SLIDING window measured from the last death, not
	// a fixed calendar boundary: if 3 or more in-game days have passed
	// since the last death, this one starts a new streak at 1; otherwise
	// it extends the existing streak. Paying to skip a cooldown does NOT
	// reset this — only the elapsed-time gap does, per the design.
	public int recordDeath(final int today, final UUID player) {
		DeathRecord previous = records.get(player);
		int consecutive = (previous == null || today - previous.lastDeathDay() >= ReviveManager.RESET_WINDOW_DAYS)
				? 1
				: previous.consecutiveDeaths() + 1;
		records.put(player, new DeathRecord(consecutive, today));
		setDirty();
		return consecutive;
	}
}
