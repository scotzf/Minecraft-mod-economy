package com.pisomarket.economy.harvest;

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

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;

// The farming faucet — one of two places new money enters the world (mob
// drops are the other; see MobDrops).
//
// Two deliberate properties, both load-bearing:
//
// PLAYER-BREAK ONLY. Hooked on PlayerBlockBreakEvents, so piston and
// villager farms mint nothing no matter how large they are. This is the
// single reason it is safe to have no daily cap.
//
// PAYS STRAIGHT INTO THE VAULT, the way vanilla XP does. Shards are a
// balance, not an item you carry: there is nothing to pick up, nothing to
// lose to lava, and nothing to leave behind by walking away from a farm.
// The sink that replaces "lost to lava" is the death penalty — see
// DeathPenalty.
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
	// Melon, pumpkin, sugar cane, cocoa, sweet berries and glow berries are
	// DECIDED-OUT (2026-08-31), not merely unimplemented. They are the
	// renewable crops: harvesting them does not consume the plant, so one
	// large farm produces forever at no ongoing cost. Only replant-required
	// crops pay, which keeps the faucet tied to real effort per harvest.
	// Do not "complete the set" by adding them.
	public static double baseChanceFor(final BlockState state) {
		// CropBlock first: this runs on EVERY block any player breaks, and
		// the overwhelmingly common case is stone or dirt, which this single
		// instanceof rejects outright.
		if (!(state.getBlock() instanceof CropBlock crop)) {
			// Nether wart is the one payer that is NOT a CropBlock — it has
			// its own 0-3 age property, so isMaxAge() is unavailable for it.
			if (state.is(Blocks.NETHER_WART)) {
				return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? CHANCE_NETHER_WART : 0.0;
			}
			return 0.0;
		}

		if (!crop.isMaxAge(state)) {
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

			// XP is awarded here rather than from a second block-break
			// listener: both handlers needed the same baseChanceFor(state)
			// result, so a separate listener meant doing the crop lookup
			// twice for every block any player broke.
			serverPlayer.giveExperiencePoints(com.pisomarket.level.LevelManager.XP_FARMING);

			int payout = PisoLuck.rollPayout(serverPlayer, base);
			if (payout <= 0) {
				return;
			}

			credit(serverPlayer, payout);
		});
	}

	// Credits the vault and pushes the new balance to the HUD, so the
	// number visibly ticks up the moment a crop pays out.
	public static void credit(final ServerPlayer player, final int amount) {
		player.level().getServer().getDataStorage().computeIfAbsent(PisoVault.TYPE)
				.deposit(player.getUUID(), amount);
		VaultSync.sync(player);
	}
}
