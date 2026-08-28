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
public record Claim(
		int id, UUID owner, ResourceKey<Level> dimension,
		int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
		Map<UUID, TrustLevel> trusted, ChestAccess chestAccess
) {
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
					// optionalFieldOf so claims saved before chest access
					// existed still load instead of failing to parse.
					CHEST_ACCESS_CODEC.optionalFieldOf("chestAccess", ChestAccess.OWNER_ONLY).forGetter(Claim::chestAccess)
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

	public boolean canPlace(final UUID player) {
		return owner.equals(player) || trusted.getOrDefault(player, null) != null && trusted.get(player).allowsPlace();
	}

	public boolean canDestroy(final UUID player) {
		return owner.equals(player) || trusted.getOrDefault(player, null) != null && trusted.get(player).allowsDestroy();
	}
}
