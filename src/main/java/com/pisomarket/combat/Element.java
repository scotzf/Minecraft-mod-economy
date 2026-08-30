package com.pisomarket.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

// What each of the five weapon lines does when it lands a hit.
//
// Durations and magnitudes are deliberately small. These are flavour and
// utility, not a damage increase — a long Slowness or a big heal would
// quietly make the weapon stronger than diamond, which is exactly the power
// creep CLAUDE.md's shop rules exist to prevent.
public enum Element {
	// Sets the target alight. Fire damage over time is vanilla's own, so
	// this needs no effect instance — just the burn, in seconds.
	EMBER(4),

	// Slows the target briefly. Amplifier 0 is Slowness I.
	FROST(0),

	// Poisons the target. Poison never kills in vanilla (it floors the
	// target at half a heart), so this cannot become a one-shot.
	VENOM(0),

	// Heals the attacker a small flat amount per hit. Magnitude is in half-
	// hearts, matching vanilla's own float health scale — 2 here means one
	// full heart back per successful hit.
	LIFESTEAL(2),

	// Deals a small burst of bonus true damage, but only against targets
	// vanilla's own Smite enchantment already considers undead
	// (EntityTypeTags.SENSITIVE_TO_SMITE — skeletons, zombies, the wither,
	// the phantom). Against anything else this is a no-op, same as the
	// enchantment it borrows the name and the tag from.
	SMITE(3);

	private final int magnitude;

	Element(final int magnitude) {
		this.magnitude = magnitude;
	}

	// Applied on every successful hit. Kept in one place so the blade and
	// the scythe of a line can never drift apart.
	public void onHit(final LivingEntity target, final LivingEntity attacker) {
		switch (this) {
			case EMBER -> target.igniteForSeconds(this.magnitude);
			case FROST -> target.addEffect(
					new MobEffectInstance(MobEffects.SLOWNESS, 60, this.magnitude)
			);
			case VENOM -> target.addEffect(
					new MobEffectInstance(MobEffects.POISON, 80, this.magnitude)
			);
			case LIFESTEAL -> attacker.heal(this.magnitude);
			case SMITE -> {
				// hurt(DamageSource, float) is deprecated in favour of
				// hurtServer(ServerLevel, ...) — callers must supply the
				// level themselves now instead of it being resolved
				// internally. Caller (ElementalBladeItem) already
				// guarantees this only runs server-side.
				if (target.is(EntityTypeTags.SENSITIVE_TO_SMITE)
						&& target.level() instanceof ServerLevel serverLevel) {
					target.hurtServer(serverLevel, target.damageSources().generic(), this.magnitude);
				}
			}
		}
	}
}
