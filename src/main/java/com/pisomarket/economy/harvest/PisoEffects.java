package com.pisomarket.economy.harvest;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import com.pisomarket.PisoMarket;

// The two harvest buffs sold in the BlackMarket, as real vanilla status
// effects rather than a custom timer of our own.
//
// This choice is entirely about network and memory cost, which was a stated
// requirement. Using MobEffect means:
//   - Vanilla syncs the effect to the client itself, in ONE packet when it
//     is applied and one when it expires. A hand-rolled buff would have
//     needed a custom payload plus a repeating "time left" packet to show
//     any countdown at all.
//   - Duration, expiry and persistence across relog are handled by the game.
//     No SavedData, no per-player map that grows, nothing to clean up.
//   - The player gets the normal effect icon and countdown in their
//     inventory for free.
//
// Reading the buff during a harvest is a single lookup on the player's own
// effect map, so the faucet's hot path stays O(1).
public final class PisoEffects {
	// Increases the chance a harvested potato pays out. Amplifier 0 is the
	// weaker potion, amplifier 1 the stronger — one effect with two
	// strengths, which is what makes them non-stackable: drinking either
	// replaces whatever was active rather than adding to it.
	public static final Holder<MobEffect> FORTUNE_BOOST = register(
			"fortune_boost", new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xC8A24B)
	);

	// Rolls a second chance to pay out again on the same crop, doubling a
	// successful harvest.
	public static final Holder<MobEffect> FORTUNE_LUCK = register(
			"fortune_luck", new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x4BC86E)
	);

	// MobEffect's constructor is protected, so a subclass is the only way to
	// build a plain effect. Neither of ours needs any behaviour of its own:
	// they carry no attribute modifiers and do nothing on tick. They are
	// pure flags that HarvestFaucet reads when a crop is broken.
	private static final class SimpleEffect extends MobEffect {
		private SimpleEffect(final MobEffectCategory category, final int color) {
			super(category, color);
		}
	}

	private PisoEffects() {
	}

	private static Holder<MobEffect> register(final String name, final MobEffect effect) {
		return Registry.registerForHolder(
				BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, name), effect
		);
	}

	// Registration happens in the static initialisers above; this exists so
	// PisoMarket.onInitialize has something to call that forces the class to
	// load at a predictable point in startup.
	public static void register() {
	}
}
