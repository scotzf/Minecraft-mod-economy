package com.pisomarket.economy;

import java.util.HashMap;
import java.util.List;
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

// The wealth leaderboard, published as a SNAPSHOT every 2 in-game days.
//
// WHY A SNAPSHOT AND NOT A LIVE RANKING — this is the important design
// point, and it follows directly from the currency being a physical item
// (see CLAUDE.md "Data model"). There is no single place that knows a
// player's total wealth:
//   - the vault balance is known exactly, it's stored right here;
//   - potatoes in a player's INVENTORY can only be counted while they are
//     online, so the last seen count is remembered in `carried`;
//   - potatoes in CHESTS are invisible to us entirely. Counting them would
//     mean scanning every block entity in every loaded chunk, and would
//     still miss anything in an unloaded one.
//
// So this ranks vault + last-known-carried, and is honestly a "richest on
// the books" board rather than true total wealth. Hoarding in chests hides
// money from it, which is a reasonable thing to let players do on purpose.
//
// The 2-day cadence is what makes this defensible: a snapshot that is
// openly a little stale reads as intended behaviour, where a "live" board
// that quietly missed chest contents would just look broken.
public class PisoLeaderboard extends SavedData {
	// Narrowed from 10 to 3 for v2: the board is broadcast to everyone
	// every 2 in-game days, and a 10-line wall of chat every 40 real
	// minutes is noise rather than information.
	public static final int TOP_N = 3;

	public record Entry(UUID player, String name, long total) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Entry::player),
						Codec.STRING.fieldOf("name").forGetter(Entry::name),
						Codec.LONG.fieldOf("total").forGetter(Entry::total)
				).apply(instance, Entry::new)
		);
	}

	private record State(int lastSnapshotDay, Map<UUID, Long> carried, Map<UUID, String> names, List<Entry> snapshot) {
		private static final Codec<State> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("lastSnapshotDay").forGetter(State::lastSnapshotDay),
						Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("carried").forGetter(State::carried),
						Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).fieldOf("names").forGetter(State::names),
						Entry.CODEC.listOf().fieldOf("snapshot").forGetter(State::snapshot)
				).apply(instance, State::new)
		);
	}

	public static final Codec<PisoLeaderboard> CODEC = State.CODEC.xmap(PisoLeaderboard::new, PisoLeaderboard::toState);

	public static final SavedDataType<PisoLeaderboard> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "leaderboard"), PisoLeaderboard::new, CODEC, DataFixTypes.LEVEL
	);

	// Far enough in the past that the very first check always fires,
	// whatever the snapshot interval is set to, so a new world gets a board
	// as soon as anyone has money rather than after a two-day wait.
	//
	// Not simply -1: the check is `today >= lastSnapshotDay + INTERVAL`, so
	// on a brand new world (day 0) a value of -1 needs `0 >= 1`, which is
	// false — it delayed the first board by two days instead of skipping the
	// wait. Halving MIN_VALUE keeps the `+ INTERVAL` from overflowing.
	private int lastSnapshotDay = Integer.MIN_VALUE / 2;
	private final Map<UUID, Long> carried;
	private final Map<UUID, String> names;
	private List<Entry> snapshot;

	public PisoLeaderboard() {
		this.carried = new HashMap<>();
		this.names = new HashMap<>();
		this.snapshot = List.of();
	}

	// Both maps MUST be copied into mutable ones. Codec.unboundedMap decodes
	// into an ImmutableMap, so a board loaded from disk would throw on the
	// first write — the exact bug that made /deposit destroy items before
	// (see CLAUDE.md). The snapshot list stays immutable on purpose: it is
	// only ever replaced wholesale, never appended to.
	private PisoLeaderboard(final State state) {
		this.lastSnapshotDay = state.lastSnapshotDay();
		this.carried = new HashMap<>(state.carried());
		this.names = new HashMap<>(state.names());
		this.snapshot = List.copyOf(state.snapshot());
	}

	private State toState() {
		return new State(lastSnapshotDay, carried, names, snapshot);
	}

	public int lastSnapshotDay() {
		return lastSnapshotDay;
	}

	public List<Entry> snapshot() {
		return snapshot;
	}

	public Map<UUID, Long> carried() {
		return carried;
	}

	public String nameFor(final UUID player) {
		return names.getOrDefault(player, player.toString());
	}

	// Remembers how many potatoes a player was last seen holding. Called
	// while they're online and again as they disconnect, so an offline
	// player still has a fair (if frozen) figure on the next board.
	public void recordCarried(final UUID player, final String name, final long amount) {
		Long previous = carried.put(player, amount);
		names.put(player, name);
		if (previous == null || previous != amount) {
			setDirty();
		}
	}

	public void publish(final int day, final List<Entry> entries) {
		this.lastSnapshotDay = day;
		this.snapshot = List.copyOf(entries);
		setDirty();
	}
}
