package com.pisomarket.claims;

import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

// A box-shaped claim (see CLAUDE.md "Territory claims") — not tied to the
// 16x16 chunk grid. minX/minY/minZ to maxX/maxY/maxZ are inclusive bounds.
// Rent (see CLAUDE.md "Territory claims"): charged per period of in-game
// days the OWNER is actually logged in — never while they're offline, so a
// player can take a break without coming back to an unprotected base.
//
// rentPerPeriod is 1% of the deed's purchase price, charged every
// RENT_PERIOD_DAYS. That is deliberately equivalent to 0.25%/day but in
// whole units: at 0.25% a Small claim would cost 0.5/day, and rounding
// fractional currency is exactly the duplication exploit CLAUDE.md warns
// about.
//
// rentProgressTicks accumulates ONLY while the owner is connected, and
// persists across logouts. It must not be a "last charged on day N"
// timestamp: an earlier version did that and reset it on login, which meant
// a payment required 4 in-game days inside one unbroken session — so anyone
// playing in sessions shorter than ~80 real minutes, or simply relogging,
// never paid rent at all.
//
// unpaidPeriods counts consecutive failed charges: >0 means protection is
// off right now, and at RENT_GRACE_PERIODS the claim is released entirely.
// Blocks are never touched either way.
public record Claim(
		int id, UUID owner, ResourceKey<Level> dimension,
		int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
		Map<UUID, TrustLevel> trusted, Map<UUID, String> trustedNames, ChestAccess chestAccess,
		long rentPerPeriod, long rentProgressTicks, int unpaidPeriods
) {
	// The player's name is recorded when trust is granted. There is no
	// reliable UUID->name lookup for someone who has never been online this
	// session, so without this an offline trusted player showed in the deed
	// book as a raw UUID with no working [Remove] button.
	public String nameFor(final UUID player) {
		String name = trustedNames.get(player);
		return name != null ? name : player.toString();
	}
	public static final int RENT_PERIOD_DAYS = 4;
	public static final int RENT_GRACE_PERIODS = 4;
	public static final long TICKS_PER_DAY = 24000L;
	public static final long RENT_PERIOD_TICKS = RENT_PERIOD_DAYS * TICKS_PER_DAY;

	public boolean rentUnpaid() {
		return unpaidPeriods > 0;
	}
	private static final Codec<TrustLevel> TRUST_LEVEL_CODEC = Codec.STRING.xmap(TrustLevel::valueOf, Enum::name);
	private static final Codec<ChestAccess> CHEST_ACCESS_CODEC = Codec.STRING.xmap(ChestAccess::valueOf, Enum::name);

	public static final Codec<Claim> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.INT.fieldOf("id").forGetter(Claim::id),
					UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Claim::owner),
					ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Claim::dimension),
					Codec.INT.fieldOf("minX").forGetter(Claim::minX),
					Codec.INT.fieldOf("minY").forGetter(Claim::minY),
					Codec.INT.fieldOf("minZ").forGetter(Claim::minZ),
					Codec.INT.fieldOf("maxX").forGetter(Claim::maxX),
					Codec.INT.fieldOf("maxY").forGetter(Claim::maxY),
					Codec.INT.fieldOf("maxZ").forGetter(Claim::maxZ),
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, TRUST_LEVEL_CODEC).fieldOf("trusted").forGetter(Claim::trusted),
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("trustedNames", Map.of()).forGetter(Claim::trustedNames),
					// optionalFieldOf so claims saved before chest access
					// existed still load instead of failing to parse.
					CHEST_ACCESS_CODEC.optionalFieldOf("chestAccess", ChestAccess.OWNER_ONLY).forGetter(Claim::chestAccess),
					// Same for the rent fields — claims created before rent
					// existed load as "no rent, paid up" rather than
					// failing, and start being charged from their next
					// login. rentPerPeriod 0 means free forever.
					Codec.LONG.optionalFieldOf("rentPerPeriod", 0L).forGetter(Claim::rentPerPeriod),
					Codec.LONG.optionalFieldOf("rentProgressTicks", 0L).forGetter(Claim::rentProgressTicks),
					Codec.INT.optionalFieldOf("unpaidPeriods", 0).forGetter(Claim::unpaidPeriods)
			).apply(instance, Claim::new)
	);

	public boolean contains(final BlockPos pos) {
		return pos.getX() >= minX && pos.getX() <= maxX
				&& pos.getY() >= minY && pos.getY() <= maxY
				&& pos.getZ() >= minZ && pos.getZ() <= maxZ;
	}

	// True if this claim's box, expanded by `buffer` in every direction,
	// intersects the given box at all — used to enforce "can't claim
	// touching someone else's claim" (see CLAUDE.md).
	public boolean overlapsWithBuffer(final int otherMinX, final int otherMinY, final int otherMinZ,
			final int otherMaxX, final int otherMaxY, final int otherMaxZ, final int buffer) {
		return (minX - buffer) <= otherMaxX && (maxX + buffer) >= otherMinX
				&& (minY - buffer) <= otherMaxY && (maxY + buffer) >= otherMinY
				&& (minZ - buffer) <= otherMaxZ && (maxZ + buffer) >= otherMinZ;
	}

	// Unpaid rent switches protection off entirely — anyone may build here
	// until it's paid. Blocks are never removed; only the shield drops.
	public boolean canPlace(final UUID player) {
		if (rentUnpaid()) {
			return true;
		}
		return owner.equals(player) || trusted.getOrDefault(player, null) != null && trusted.get(player).allowsPlace();
	}

	public boolean canDestroy(final UUID player) {
		if (rentUnpaid()) {
			return true;
		}
		return owner.equals(player) || trusted.getOrDefault(player, null) != null && trusted.get(player).allowsDestroy();
	}
}
