package com.pisomarket.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// Which waypoint each player is bound to, and when they last warped.
//
// The dimension is stored alongside the position because a BlockPos alone
// is ambiguous across dimensions — the same coordinates exist in the
// Overworld, Nether and End, and warping to the wrong one would drop a
// player into solid rock.
public class WaypointState extends SavedData {
	private record Bound(String dimension, int x, int y, int z, long lastWarpTick) {
		private static final Codec<Bound> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.fieldOf("dimension").forGetter(Bound::dimension),
						Codec.INT.fieldOf("x").forGetter(Bound::x),
						Codec.INT.fieldOf("y").forGetter(Bound::y),
						Codec.INT.fieldOf("z").forGetter(Bound::z),
						Codec.LONG.fieldOf("lastWarpTick").forGetter(Bound::lastWarpTick)
				).apply(instance, Bound::new)
		);
	}

	public static final Codec<WaypointState> CODEC =
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bound.CODEC).xmap(WaypointState::new, WaypointState::getBounds);

	public static final SavedDataType<WaypointState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "waypoints"), WaypointState::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<UUID, Bound> bounds;

	public WaypointState() {
		this(new HashMap<>());
	}

	// Mutable copy — see the identical note on PisoVault.
	private WaypointState(final Map<UUID, Bound> bounds) {
		this.bounds = new HashMap<>(bounds);
	}

	private Map<UUID, Bound> getBounds() {
		return bounds;
	}

	public void bind(final UUID player, final String dimension, final BlockPos pos) {
		Bound existing = bounds.get(player);
		long lastWarp = existing == null ? 0L : existing.lastWarpTick();
		bounds.put(player, new Bound(dimension, pos.getX(), pos.getY(), pos.getZ(), lastWarp));
		setDirty();
	}

	public boolean isBound(final UUID player) {
		return bounds.containsKey(player);
	}

	public String dimensionOf(final UUID player) {
		Bound b = bounds.get(player);
		return b == null ? null : b.dimension();
	}

	public BlockPos posOf(final UUID player) {
		Bound b = bounds.get(player);
		return b == null ? null : new BlockPos(b.x(), b.y(), b.z());
	}

	public long lastWarpTick(final UUID player) {
		Bound b = bounds.get(player);
		return b == null ? 0L : b.lastWarpTick();
	}

	public void markWarped(final UUID player, final long tick) {
		Bound b = bounds.get(player);
		if (b == null) {
			return;
		}
		bounds.put(player, new Bound(b.dimension(), b.x(), b.y(), b.z(), tick));
		setDirty();
	}

	// Called when a waypoint block is destroyed — anyone bound to it is
	// unbound, so /warp fails with a clear message instead of teleporting
	// them into empty air.
	public void unbindAllAt(final String dimension, final BlockPos pos) {
		bounds.entrySet().removeIf(e -> {
			Bound b = e.getValue();
			return b.dimension().equals(dimension) && b.x() == pos.getX() && b.y() == pos.getY() && b.z() == pos.getZ();
		});
		setDirty();
	}
}
