package com.pisomarket.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// The craftable shop block from CLAUDE.md's "Shop UI" section. Right-click
// opens PisoShopMenu, a chest-style menu whose slots are named books acting
// as navigation buttons — no custom client Screen class needed, since the
// menu renders through vanilla's existing generic-container screen.
public class PisoShopBlock extends Block {
	public PisoShopBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(
			final BlockState state, final net.minecraft.world.level.Level level, final BlockPos pos, final Player player, final net.minecraft.world.phys.BlockHitResult hitResult
	) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new MenuProvider() {
				@Override
				public Component getDisplayName() {
					return Component.literal("Piso Market");
				}

				@Override
				public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
						final int syncId, final net.minecraft.world.entity.player.Inventory inventory, final Player menuPlayer
				) {
					return new PisoShopMenu(syncId, inventory);
				}
			});
		}
		return InteractionResult.SUCCESS;
	}
}
