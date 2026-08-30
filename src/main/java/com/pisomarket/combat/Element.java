package com.pisomarket.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

// What each of the five weapon lines does when it lands a hit.
//
// Magnitude and duration are NOT stored here any more — they are passed in
// per weapon (see ElementalWeapons). That change was forced by the combat
// rebalance: Divine Axe Rhitta needs Smite +25 while Divine Reaper needs
// +6, and Frostblade needs an 8-second slow while every other Frost weapon
// gets 3. One constant per element could not express that.
public enum Element {
	// Sets the target alight. Fire damage over time is vanilla's own, so
	// this needs no effect instance — just the burn.
	EMBER,

	// Slows the target. Amplifier 0 is Slowness I; the weapons differentiate
	// on DURATION rather than strength, which is what makes Frostblade's
	// 8 seconds feel distinct without making it hit harder.
	FROST,

	// Poisons the target. Poison never kills in vanilla (it floors the
	// target at half a heart), so this cannot become a one-shot.
	VENOM,

	// Heals the attacker. Magnitude is in half-hearts, matching vanilla's
	// own float health scale.
	LIFESTEAL,

	// Bonus true damage, but only against targets vanilla's own Smite
	// enchantment already considers undead. Against anything else this is
	// a no-op, same as the enchantment it borrows the name and tag from.
	SMITE;

	// magnitude: effect strength (heal amount / bonus damage / ignite
	//            seconds, depending on the element)
	// durationTicks: how long the status effect lasts; ignored by
	//            LIFESTEAL and SMITE, which are instant
	public void onHit(final LivingEntity target, final LivingEntity attacker, final float magnitude, final int durationTicks) {
		switch (this) {
			case EMBER -> target.igniteForSeconds((int) magnitude);
			case FROST -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, 0));
			case VENOM -> target.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, 0));
			case LIFESTEAL -> attacker.heal(magnitude);
			case SMITE -> {
				// hurt(DamageSource, float) is deprecated in favour of
				// hurtServer(ServerLevel, ...). Caller (ElementalBladeItem)
				// already guarantees this only runs server-side.
				if (target.is(EntityTypeTags.SENSITIVE_TO_SMITE) && target.level() instanceof ServerLevel serverLevel) {
					target.hurtServer(serverLevel, target.damageSources().generic(), magnitude);
				}
			}
		}
	}
}
