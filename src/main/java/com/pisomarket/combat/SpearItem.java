package com.pisomarket.combat;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Divine Justice's spear behaviour: hold right-click to wind up, release to
// thrust. Damage and reach both scale with how long it was charged.
//
// Built on the same use/releaseUsing pair vanilla's own trident and bow use,
// and it reports ItemUseAnimation.SPEAR — a real animation that exists in
// 26.2 — so the held pose is the game's own spear pose rather than something
// approximated.
//
// The thrust is NOT a projectile. It sweeps every entity in a line in front
// of the player out to the charged reach, which is what makes "spear" mean
// extended melee range here rather than a throw.
public class SpearItem extends ElementalBladeItem {
	// Ticks to reach a full charge. 20 ticks = 1 second — deliberately
	// quick, since a spear that takes as long as a bow to ready would never
	// be worth using in a real fight.
	private static final int FULL_CHARGE_TICKS = 20;

	// Reach in blocks at zero charge and at full charge. Vanilla melee is
	// ~3, so even an uncharged thrust already out-ranges a sword.
	private static final double MIN_REACH = 3.5;
	private static final double MAX_REACH = 6.0;

	// Damage multiplier at zero charge and at full charge, applied to the
	// weapon's own base damage.
	private static final float MIN_DAMAGE_SCALE = 0.4F;
	private static final float MAX_DAMAGE_SCALE = 2.0F;

	// How wide the thrust is. Small on purpose: a spear should reward
	// aiming, not act as a cone that hits everything in front of you.
	private static final double THRUST_WIDTH = 1.0;

	private final float spearBaseDamage;

	public SpearItem(
			final Element element, final float effectMagnitude, final int effectDurationTicks,
			final float baseDamage, final Item.Properties properties
	) {
		super(element, effectMagnitude, effectDurationTicks, 0.0F, 1.0F, false, baseDamage, properties);
		this.spearBaseDamage = baseDamage;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(final ItemStack stack) {
		return ItemUseAnimation.SPEAR;
	}

	// Effectively indefinite, same value vanilla's bow uses — the player
	// decides when to release, we just cap the useful charge at
	// FULL_CHARGE_TICKS.
	@Override
	public int getUseDuration(final ItemStack stack, final LivingEntity user) {
		return 72000;
	}

	@Override
	public boolean releaseUsing(final ItemStack stack, final Level level, final LivingEntity entity, final int remainingTime) {
		if (!(entity instanceof Player player) || !(level instanceof ServerLevel serverLevel)) {
			return false;
		}

		int heldTicks = this.getUseDuration(stack, entity) - remainingTime;
		float charge = Math.min(1.0F, heldTicks / (float) FULL_CHARGE_TICKS);

		double reach = MIN_REACH + (MAX_REACH - MIN_REACH) * charge;
		float damage = this.spearBaseDamage * (MIN_DAMAGE_SCALE + (MAX_DAMAGE_SCALE - MIN_DAMAGE_SCALE) * charge);

		this.thrust(serverLevel, player, reach, damage, charge);
		return true;
	}

	private void thrust(final ServerLevel level, final Player player, final double reach, final float damage, final float charge) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 tip = eye.add(look.scale(reach));

		// A thin box along the line of the thrust rather than a sphere
		// around the player, so this genuinely reads as a directional stab.
		AABB path = new AABB(eye, tip).inflate(THRUST_WIDTH);

		List<LivingEntity> hit = level.getEntitiesOfClass(
				LivingEntity.class, path,
				other -> other != player && other.isAlive() && !other.isAlliedTo(player)
		);

		// Nearest first: a spear should hit what it actually reached, and
		// this also makes the single-target case predictable.
		hit.sort(Comparator.comparingDouble(other -> other.distanceToSqr(eye)));

		for (LivingEntity target : hit) {
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			// Reuse the shared on-hit effect so the spear's element behaves
			// exactly like every other weapon carrying it.
			this.element().onHit(target, player, this.effectMagnitudeValue(), this.effectDurationValue());
		}

		// Visual/audio feedback scaled to the charge, so a full-power thrust
		// is obviously different from a tap.
		level.sendParticles(
				ParticleTypes.SWEEP_ATTACK,
				tip.x, tip.y, tip.z,
				0, look.x, 0.0, look.z, 0.0
		);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0F, 0.8F + 0.4F * charge);

		player.getCooldowns().addCooldown(player.getMainHandItem(), 10);
	}
}
