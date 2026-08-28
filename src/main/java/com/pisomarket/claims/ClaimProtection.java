package com.pisomarket.claims;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Enforces TrustLevel.canDestroy / canPlace for anyone inside a claim who
// isn't the owner or explicitly trusted. Only ever cancels — never grants
// anything extra.
//
// Covers: breaking blocks, placing blocks, world-changing items (buckets,
// flint & steel), explosions (see ExplosionProtectionMixin), and the
// decoration entities that aren't blocks at all (item frames, paintings,
// armor stands) — breaking one of those is an *attack*, not a block break,
// so it needs its own hook.
public final class ClaimProtection {
	private ClaimProtection() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return true;
			}
			return checkDestroy(serverPlayer, pos);
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			// Anything that changes the world when right-clicked. A plain
			// BlockItem check was not enough: a lava bucket and flint &
			// steel are not BlockItems, so both sailed straight through
			// and could burn down a claimed base.
			if (!changesTheWorld(player.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}

			BlockPos targetPos = hitResult.getBlockPos().relative(hitResult.getDirection());
			return checkPlace(serverPlayer, targetPos) ? InteractionResult.PASS : InteractionResult.FAIL;
		});

		// Breaking an item frame / painting / armor stand is an attack on an
		// entity, so none of the block hooks above see it.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !isProtectedDecoration(entity)) {
				return InteractionResult.PASS;
			}
			return checkDestroy(serverPlayer, entity.blockPosition()) ? InteractionResult.PASS : InteractionResult.FAIL;
		});

		// Rotating an item frame or restyling an armor stand counts as
		// changing someone's build too.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !isProtectedDecoration(entity)) {
				return InteractionResult.PASS;
			}
			return checkPlace(serverPlayer, entity.blockPosition()) ? InteractionResult.PASS : InteractionResult.FAIL;
		});
	}

	private static boolean isProtectedDecoration(final Entity entity) {
		// HangingEntity covers item frames, glow item frames and paintings.
		return entity instanceof HangingEntity || entity instanceof ArmorStand;
	}

	// Items that modify terrain on use. Buckets place lava/water, flint &
	// steel and fire charges start fires, and all three are trivially
	// effective griefing tools — none of them are BlockItems.
	private static boolean changesTheWorld(final ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof BlockItem
				|| item instanceof BucketItem
				|| item == Items.FLINT_AND_STEEL
				|| item == Items.FIRE_CHARGE;
	}

	private static PisoClaims claims(final ServerPlayer player) {
		return player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
	}

	// Used by ExplosionProtectionMixin. An explosion has no player behind
	// it that we can meaningfully trust-check (a creeper isn't anybody), so
	// any claimed block is simply immune — including the owner's own, which
	// is the point: you don't want your own base creeper-holed either.
	public static boolean isExplosionProtected(final ServerLevel level, final BlockPos pos) {
		PisoClaims claims = level.getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
		Claim claim = claims.findAt(level.dimension(), pos.getX(), pos.getY(), pos.getZ());
		return claim != null && !claim.rentUnpaid();
	}

	private static boolean checkDestroy(final ServerPlayer player, final BlockPos pos) {
		Claim claim = claims(player).findAt(player.level().dimension(), pos.getX(), pos.getY(), pos.getZ());
		if (claim == null || claim.canDestroy(player.getUUID())) {
			return true;
		}
		player.sendSystemMessage(Component.literal("This is claimed land — you can't break blocks here"));
		return false;
	}

	private static boolean checkPlace(final ServerPlayer player, final BlockPos pos) {
		Claim claim = claims(player).findAt(player.level().dimension(), pos.getX(), pos.getY(), pos.getZ());
		if (claim == null || claim.canPlace(player.getUUID())) {
			return true;
		}
		player.sendSystemMessage(Component.literal("This is claimed land — you can't place blocks here"));
		return false;
	}
}
