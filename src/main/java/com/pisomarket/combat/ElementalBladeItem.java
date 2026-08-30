package com.pisomarket.combat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

// One class for every custom weapon — what varies between them is passed in
// at construction (see ElementalWeapons): which Element they carry, how
// strong and how long that effect is, whether they crit, and whether they
// cleave.
public class ElementalBladeItem extends Item {
	// How far a cleave reaches past the primary target, in blocks.
	private static final double CLEAVE_RADIUS = 3.0;
	// Cleave hits for this fraction of the weapon's damage. Deliberately
	// well under 1.0: cleave is a farming convenience, and at full damage
	// a cleave weapon would simply be strictly better than a non-cleave
	// one of the same tier in every situation including duels.
	private static final float CLEAVE_DAMAGE_FRACTION = 0.5F;

	private final Element element;
	private final float effectMagnitude;
	private final int effectDurationTicks;
	private final float critChance;
	private final float critMultiplier;
	private final boolean cleave;
	// The weapon's own final damage, kept so crit and cleave can be
	// computed from it. Vanilla applies base damage through the attribute
	// system before hurtEnemy runs, so it is not otherwise available here.
	private final float baseDamage;

	public ElementalBladeItem(
			final Element element, final float effectMagnitude, final int effectDurationTicks,
			final float critChance, final float critMultiplier, final boolean cleave,
			final float baseDamage, final Item.Properties properties
	) {
		super(properties);
		this.element = element;
		this.effectMagnitude = effectMagnitude;
		this.effectDurationTicks = effectDurationTicks;
		this.critChance = critChance;
		this.critMultiplier = critMultiplier;
		this.cleave = cleave;
		this.baseDamage = baseDamage;
	}

	@Override
	public void hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);

		// Server side only: applying an effect client-side desyncs the
		// target's effect list and the status icon flickers.
		if (!(target.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		this.element.onHit(target, attacker, this.effectMagnitude, this.effectDurationTicks);

		// Crit — a flat chance on EVERY swing, not vanilla's fall-attack
		// crit. Vanilla's only fires while falling and not sprinting, which
		// most players never notice, so an axe's "better crit" identity
		// would have been invisible. Rolled the same way the harvest faucet
		// and Luck potion roll their chances.
		if (this.critChance > 0.0F && ThreadLocalRandom.current().nextFloat() < this.critChance) {
			float bonus = this.baseDamage * (this.critMultiplier - 1.0F);
			target.hurtServer(serverLevel, target.damageSources().generic(), bonus);
		}

		if (this.cleave) {
			this.applyCleave(serverLevel, target, attacker);
		}
	}

	// Hits everything living near the primary target for a fraction of the
	// weapon's damage. This is the mob-farming mechanic for the weapons that
	// aren't Divine Axe Rhitta (which farms undead by one-shotting instead).
	private void applyCleave(final ServerLevel level, final LivingEntity target, final LivingEntity attacker) {
		AABB area = target.getBoundingBox().inflate(CLEAVE_RADIUS);
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, area);
		float cleaveDamage = this.baseDamage * CLEAVE_DAMAGE_FRACTION;

		// Vanilla's own sweep arc + sound, so a cleave reads visually the
		// same way a vanilla sword sweep does instead of damage silently
		// appearing on nearby mobs. Spawned from the ATTACKER facing
		// forward, matching how Player.sweepAttack positions it.
		double dx = -Mth.sin(attacker.getYRot() * (float) (Math.PI / 180.0));
		double dz = Mth.cos(attacker.getYRot() * (float) (Math.PI / 180.0));
		level.sendParticles(
				ParticleTypes.SWEEP_ATTACK,
				attacker.getX() + dx, attacker.getY(0.5), attacker.getZ() + dz,
				0, dx, 0.0, dz, 0.0
		);
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1.0F, 1.0F);

		for (LivingEntity other : nearby) {
			// Never the attacker (cleaving yourself), never the primary
			// target (it already took a full hit), and never a teammate.
			if (other == attacker || other == target || other.isAlliedTo(attacker)) {
				continue;
			}
			// Deliberately a plain generic damage source rather than the
			// weapon's own attack path: routing cleave back through a real
			// weapon attack would re-trigger hurtEnemy on every entity hit,
			// which cascades into an effectively infinite chain.
			other.hurtServer(level, other.damageSources().generic(), cleaveDamage);
		}
	}

	public Element element() {
		return this.element;
	}

	// Exposed so SpearItem can apply the same on-hit effect through its own
	// thrust path instead of duplicating the magnitude/duration numbers.
	protected float effectMagnitudeValue() {
		return this.effectMagnitude;
	}

	protected int effectDurationValue() {
		return this.effectDurationTicks;
	}
}
