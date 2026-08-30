package com.pisomarket.combat;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

// What each of the three weapon lines does when it lands a hit.
//
// Durations are deliberately short. These are flavour and utility, not a
// damage increase — a long Slowness or Poison would quietly make the weapon
// stronger than diamond, which is exactly the power creep CLAUDE.md's shop
// rules exist to prevent.
public enum Element {
	// Sets the target alight. Fire damage over time is vanilla's own, so
	// this needs no effect instance — just the burn, in seconds.
	EMBER(4),

	// Slows the target briefly. Amplifier 0 is Slowness I.
	FROST(0),

	// Poisons the target. Poison never kills in vanilla (it floors the
	// target at half a heart), so this cannot become a one-shot.
	VENOM(0);

	private final int magnitude;

	Element(final int magnitude) {
		this.magnitude = magnitude;
	}

	// Applied on every successful hit. Kept in one place so the blade and
	// the scythe of a line can never drift apart.
	public void onHit(final LivingEntity target) {
		switch (this) {
			case EMBER -> target.igniteForSeconds(this.magnitude);
			case FROST -> target.addEffect(
					new MobEffectInstance(MobEffects.SLOWNESS, 60, this.magnitude)
			);
			case VENOM -> target.addEffect(
					new MobEffectInstance(MobEffects.POISON, 80, this.magnitude)
			);
		}
	}
}
