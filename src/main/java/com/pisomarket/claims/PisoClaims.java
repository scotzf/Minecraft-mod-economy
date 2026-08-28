package com.pisomarket.claims;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

public class PisoClaims extends SavedData {
	private static final int OVERLAP_BUFFER = 1;

	private record State(int nextId, List<Claim> claims) {
		private static final Codec<State> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("nextId").forGetter(State::nextId),
						Claim.CODEC.listOf().fieldOf("claims").forGetter(State::claims)
				).apply(instance, State::new)
		);
	}

	public static final Codec<PisoClaims> CODEC = State.CODEC.xmap(PisoClaims::new, PisoClaims::toState);

	public static final SavedDataType<PisoClaims> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "claims"), PisoClaims::new, CODEC, DataFixTypes.LEVEL
	);

	private int nextId;
	private final Map<Integer, Claim> claims;

	public PisoClaims() {
		this(new State(1, List.of()));
	}

	private PisoClaims(final State state) {
		this.nextId = state.nextId();
		this.claims = new LinkedHashMap<>();
		for (Claim claim : state.claims()) {
			claims.put(claim.id(), claim);
		}
	}

	private State toState() {
		return new State(nextId, List.copyOf(claims.values()));
	}

	public Claim get(final int id) {
		return claims.get(id);
	}

	public List<Claim> all() {
		return List.copyOf(claims.values());
	}

	public List<Claim> byOwner(final UUID owner) {
		return claims.values().stream().filter(c -> c.owner().equals(owner)).toList();
	}

	// Null if the box (expanded by the overlap buffer) doesn't conflict
	// with anything — the caller is then free to create it.
	public Claim findOverlap(final ResourceKey<Level> dimension, final int minX, final int minY, final int minZ,
			final int maxX, final int maxY, final int maxZ) {
		for (Claim claim : claims.values()) {
			if (claim.dimension().equals(dimension) && claim.overlapsWithBuffer(minX, minY, minZ, maxX, maxY, maxZ, OVERLAP_BUFFER)) {
				return claim;
			}
		}
		return null;
	}

	public Claim findAt(final ResourceKey<Level> dimension, final int x, final int y, final int z) {
		for (Claim claim : claims.values()) {
			if (claim.dimension().equals(dimension) && x >= claim.minX() && x <= claim.maxX()
					&& y >= claim.minY() && y <= claim.maxY() && z >= claim.minZ() && z <= claim.maxZ()) {
				return claim;
			}
		}
		return null;
	}

	public int create(final UUID owner, final ResourceKey<Level> dimension,
			final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {
		int id = nextId++;
		claims.put(id, new Claim(id, owner, dimension, minX, minY, minZ, maxX, maxY, maxZ, new HashMap<>(), ChestAccess.OWNER_ONLY));
		setDirty();
		return id;
	}

	// Used when a bound deed changes hands (e.g. sold on the market) — the
	// land and everything built on it goes with the deed. Trust entries are
	// cleared: they were the previous owner's arrangements, not the new
	// owner's, and silently inheriting them would hand strangers build
	// access to land they just bought.
	public void transferOwnership(final int claimId, final UUID newOwner) {
		Claim claim = claims.get(claimId);
		if (claim == null) {
			return;
		}
		claims.put(claimId, new Claim(
				claim.id(), newOwner, claim.dimension(), claim.minX(), claim.minY(), claim.minZ(),
				claim.maxX(), claim.maxY(), claim.maxZ(), new HashMap<>(), claim.chestAccess()
		));
		setDirty();
	}

	public void setChestAccess(final int claimId, final ChestAccess access) {
		Claim claim = claims.get(claimId);
		if (claim == null) {
			return;
		}
		claims.put(claimId, new Claim(
				claim.id(), claim.owner(), claim.dimension(), claim.minX(), claim.minY(), claim.minZ(),
				claim.maxX(), claim.maxY(), claim.maxZ(), claim.trusted(), access
		));
		setDirty();
	}

	public void remove(final int id) {
		if (claims.remove(id) != null) {
			setDirty();
		}
	}

	// null trustLevel revokes trust entirely.
	public void setTrust(final int claimId, final UUID player, final TrustLevel trustLevel) {
		Claim claim = claims.get(claimId);
		if (claim == null) {
			return;
		}
		Map<UUID, TrustLevel> trusted = new HashMap<>(claim.trusted());
		if (trustLevel == null) {
			trusted.remove(player);
		} else {
			trusted.put(player, trustLevel);
		}
		claims.put(claimId, new Claim(
				claim.id(), claim.owner(), claim.dimension(), claim.minX(), claim.minY(), claim.minZ(),
				claim.maxX(), claim.maxY(), claim.maxZ(), trusted, claim.chestAccess()
		));
		setDirty();
	}
}
