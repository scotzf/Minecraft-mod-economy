package com.pisomarket.claims;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

// Enforces TrustLevel.canDestroy / canPlace for anyone inside a claim who
// isn't the owner or explicitly trusted. Doesn't touch claims the acting
// player owns or is trusted on — only ever cancels, never grants anything
// extra.
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
			// Only relevant when the player is holding a block to place —
			// anything else (opening a door, right-clicking a lever) isn't
			// build protection's concern.
			if (!(player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem)) {
				return InteractionResult.PASS;
			}

			BlockPos targetPos = hitResult.getBlockPos().relative(hitResult.getDirection());
			return checkPlace(serverPlayer, targetPos) ? InteractionResult.PASS : InteractionResult.FAIL;
		});
	}

	private static PisoClaims claims(final ServerPlayer player) {
		return player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
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
