package com.pisomarket.economy.harvest;

import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;

// The only faucet in the economy — the single place new money enters the
// world (see CLAUDE.md "Economy: one faucet, one set of sinks").
//
// It fires on every player block break. If the broken block was a fully
// grown potato crop, it rolls DROP_CHANCE; on a hit, one unit is added
// straight to the player's VAULT rather than dropped on the ground, so a
// payout can't be lost to lava or a despawn the instant it is earned.
//
// It fires on PLAYER breaks only, which is load-bearing: automated potato
// farms harvested by pistons or villagers create no money at all. That is
// what makes it safe to have no daily cap right now.
//
// Deliberately no daily cap — see CLAUDE.md for the reasoning and for when
// to revisit it.
public final class HarvestFaucet {
	// Base payout chance per mature potato harvested by hand.
	public static final double DROP_CHANCE = 0.01;

	// Extra chance added by the Harvest potions (see HarvestPotionItem).
	// Amplifier 0 is the weaker potion, amplifier 1 the stronger.
	public static final double HARVEST_BOOST_I = 0.015;
	public static final double HARVEST_BOOST_II = 0.04;

	private HarvestFaucet() {
	}

	// The effective drop chance for this player right now, base plus any
	// active Harvest potion. Public so the shop can price the potions
	// against the real numbers instead of hardcoded duplicates.
	public static double chanceFor(final ServerPlayer player) {
		MobEffectInstance boost = player.getEffect(PisoEffects.HARVEST_BOOST);
		if (boost == null) {
			return DROP_CHANCE;
		}
		return DROP_CHANCE + (boost.getAmplifier() >= 1 ? HARVEST_BOOST_II : HARVEST_BOOST_I);
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			// Cheapest checks first — this runs on EVERY block any player
			// breaks, so the overwhelmingly common case (not a potato) must
			// cost almost nothing and must not touch storage at all.
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}
			if (!state.is(Blocks.POTATOES)) {
				return;
			}
			if (!(state.getBlock() instanceof CropBlock cropBlock) || !cropBlock.isMaxAge(state)) {
				return;
			}

			// Only now do we look at effects — two O(1) map lookups on the
			// player's own effect list, and only for a mature potato.
			if (ThreadLocalRandom.current().nextDouble() >= chanceFor(serverPlayer)) {
				return;
			}

			// Luck: a successful payout gets rolled a second time, so the
			// potion doubles a hit rather than making hits more likely.
			int payout = player.hasEffect(PisoEffects.HARVEST_LUCK) ? 2 : 1;

			PisoVault vault = serverPlayer.level().getServer().getDataStorage().computeIfAbsent(PisoVault.TYPE);
			vault.deposit(serverPlayer.getUUID(), payout);
			VaultSync.sync(serverPlayer);
			serverPlayer.sendSystemMessage(Component.literal(
					payout > 1
							? "Lucky harvest — two potatoes slipped into your vault (+" + payout + ")"
							: "A poisonous potato slipped into your vault (+1)"
			));
		});
	}
}
