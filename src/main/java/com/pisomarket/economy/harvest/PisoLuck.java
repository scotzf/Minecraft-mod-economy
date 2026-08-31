package com.pisomarket.economy.harvest;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

// The single place a Shard payout is rolled, shared by BOTH faucets — crop
// harvesting (HarvestFaucet) and mob kills (MobDrops).
//
// That sharing is the entire point of the potion rework: the boost used to
// live inside the crop faucet and only ever applied to breaking a potato.
// Now any activity that can pay out Shards routes through here, so one
// potion buffs everything.
//
// The boost is a MULTIPLIER on the base rate, not a flat addition. A flat
// +1.5 percentage points was +60% on a 2.5% crop but only +15% on a 10%
// nether wart and would be near-worthless on a 20% Enderman. A multiplier
// feels like the same item everywhere.
public final class PisoLuck {
	// Relative increase to drop chance while the effect is active.
	public static final double BOOST_I = 0.50;   // +50%
	public static final double BOOST_II = 1.50;  // +150%

	private PisoLuck() {
	}

	// Effective drop chance for this player right now, base plus any active
	// Fortune effect. Public so the shop can price the potions against the
	// real numbers rather than hardcoded duplicates.
	public static double effectiveChance(final ServerPlayer player, final double baseChance) {
		MobEffectInstance boost = player.getEffect(PisoEffects.FORTUNE_BOOST);
		if (boost == null) {
			return baseChance;
		}
		double multiplier = boost.getAmplifier() >= 1 ? BOOST_II : BOOST_I;
		return Math.min(1.0, baseChance * (1.0 + multiplier));
	}

	// Rolls once against the boosted chance and returns how many Shards to
	// award — 0 on a miss, 1 normally, 2 while Luck is active.
	//
    // Luck deliberately doubles the PAYOUT rather than the chance, so it
	// stacks multiplicatively with Fortune instead of competing with it.
	public static int rollPayout(final ServerPlayer player, final double baseChance) {
		double chance = effectiveChance(player, baseChance);
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return 0;
		}
		return player.hasEffect(PisoEffects.FORTUNE_LUCK) ? 2 : 1;
	}
}
