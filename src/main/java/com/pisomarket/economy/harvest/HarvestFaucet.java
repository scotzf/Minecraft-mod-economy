package com.pisomarket.economy.harvest;

import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.pisomarket.economy.PisoCurrency;

// The farming faucet — one of two places new money enters the world (mob
// drops are the other; see MobDrops).
//
// Two deliberate properties, both load-bearing:
//
// PLAYER-BREAK ONLY. Hooked on PlayerBlockBreakEvents, so piston and
// villager farms mint nothing no matter how large they are. This is the
// single reason it is safe to have no daily cap.
//
// PAYS A PHYSICAL DROP, not a vault deposit. The currency is an item, so
// earning it should put an item on the ground. The old behaviour deposited
// straight into the vault, which made money invisible and contradicted the
// whole point of item currency. Losing a payout to lava is an accepted
// passive sink.
public final class HarvestFaucet {
	// Per-crop drop chance. Nether wart is highest because reaching the
	// Nether at all is its own barrier, already priced in.
	public static final double CHANCE_WHEAT = 0.025;
	public static final double CHANCE_POTATO = 0.025;
	public static final double CHANCE_CARROT = 0.025;
	public static final double CHANCE_BEETROOT = 0.05;
	public static final double CHANCE_NETHER_WART = 0.10;

	private HarvestFaucet() {
	}

	// Returns the base drop chance for a fully grown crop, or 0 if this
	// block is not a currency-bearing crop (or is not yet mature).
	//
	// Melon/pumpkin/sugar cane/cocoa/berries are deliberately absent: they
	// are the renewable crops that regrow without replanting, so an
	// unbounded automated farm would dominate the faucet. Adding them needs
	// a lower rate or a cap decided first.
	public static double baseChanceFor(final BlockState state) {
		if (state.is(Blocks.NETHER_WART)) {
			// NetherWartBlock is NOT a CropBlock and has its own 0-3 age
			// property, so isMaxAge() is unavailable here.
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? CHANCE_NETHER_WART : 0.0;
		}

		if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
			return 0.0;
		}

		if (state.is(Blocks.WHEAT)) {
			return CHANCE_WHEAT;
		}
		if (state.is(Blocks.POTATOES)) {
			return CHANCE_POTATO;
		}
		if (state.is(Blocks.CARROTS)) {
			return CHANCE_CARROT;
		}
		if (state.is(Blocks.BEETROOTS)) {
			return CHANCE_BEETROOT;
		}
		return 0.0;
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			// Cheapest checks first — this runs on EVERY block any player
			// breaks, so the overwhelmingly common case (not a crop) must
			// cost almost nothing.
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}

			double base = baseChanceFor(state);
			if (base <= 0.0) {
				return;
			}

			int payout = PisoLuck.rollPayout(serverPlayer, base);
			if (payout <= 0) {
				return;
			}

			dropShards(level, pos, payout);
		});
	}

	// Pops the reward on the ground the same way a broken crop pops its own
	// seeds, so it behaves like any other harvest yield.
	public static void dropShards(final Level level, final BlockPos pos, final int count) {
		Block.popResource(level, pos, new ItemStack(PisoCurrency.SUNSTONE_SHARD, count));
	}
}
