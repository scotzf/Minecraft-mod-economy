package com.pisomarket.claims.lock;

import net.fabricmc.fabric.api.event.player.BlockEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Blocks;

import com.pisomarket.claims.Claim;
import com.pisomarket.claims.ChestAccess;
import com.pisomarket.claims.PisoClaims;

// Enforces the claim's chest policy (see ChestAccess) on every chest inside
// that claim. Replaces the old per-chest Lock item: the setting now lives on
// the claim and is edited from the Land Deed book, so there's nothing to
// craft, place or lose.
//
// Hooks the same BlockState.useWithoutItem path PisoShopBlock overrides
// directly for its own block — chests are vanilla, so this goes through the
// Fabric event instead.
public final class ChestAccessGuard {
	private ChestAccessGuard() {
	}

	public static void register() {
		BlockEvents.USE_WITHOUT_ITEM.register((state, level, pos, player, hitResult) -> {
			if (level.isClientSide() || !state.is(Blocks.CHEST) || !(player instanceof ServerPlayer serverPlayer)) {
				return null;
			}

			PisoClaims claims = serverPlayer.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
			Claim claim = claims.findAt(serverPlayer.level().dimension(), pos.getX(), pos.getY(), pos.getZ());
			if (claim == null) {
				// Unclaimed land — chests there are nobody's business.
				return null;
			}

			// The owner is never restricted by their own setting.
			if (claim.owner().equals(serverPlayer.getUUID())) {
				return null;
			}

			ChestAccess access = claim.chestAccess();
			if (access == ChestAccess.OPEN) {
				return null;
			}

			boolean trusted = claim.trusted().containsKey(serverPlayer.getUUID());
			if (!trusted || access == ChestAccess.OWNER_ONLY) {
				serverPlayer.sendSystemMessage(Component.literal("This chest is locked"));
				return InteractionResult.FAIL;
			}

			if (access == ChestAccess.TRUSTED_PUT_AND_GET) {
				return null;
			}

			// TRUSTED_PUT_ONLY — open a restricted view of the real chest.
			if (level.getBlockEntity(pos) instanceof Container chestContainer) {
				serverPlayer.openMenu(new MenuProvider() {
					@Override
					public Component getDisplayName() {
						return Component.literal("Chest (put only)");
					}

					@Override
					public AbstractContainerMenu createMenu(final int syncId, final net.minecraft.world.entity.player.Inventory inventory, final Player menuPlayer) {
						return new RestrictedChestMenu(syncId, inventory, chestContainer);
					}
				});
			}
			return InteractionResult.SUCCESS;
		});
	}
}
