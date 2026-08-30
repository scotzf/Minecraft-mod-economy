package com.pisomarket.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;

import com.pisomarket.PisoMarket;

// Buffs mob health so the rebalanced custom weapons (23-44 damage) have
// something to bite. Applied on spawn as an attribute modifier rather than
// by setting health directly, so it composes correctly with anything else
// touching max health and persists for the entity's lifetime.
//
// Uses MULTIPLY_TOTAL so one modifier covers every mob regardless of its
// base health — a flat addition would be trivial on a Warden and lethal on
// a silverfish.
public final class MobScaling {
	private static final Identifier HEALTH_BOOST_ID =
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "mob_health_scaling");

	// +50% health across the board.
	private static final double STANDARD_MULTIPLIER = 0.5;
	// The Warden is already terrifying at 500; +30% is enough.
	private static final double WARDEN_MULTIPLIER = 0.3;

	private MobScaling() {
	}

	private static double multiplierFor(final EntityType<?> type) {
		if (type == EntityTypes.WARDEN) {
			return WARDEN_MULTIPLIER;
		}
		// Player-buildable golems are deliberately untouched: buffing them
		// only makes a free, craftable mob tankier for no design reason.
		if (type == EntityTypes.IRON_GOLEM || type == EntityTypes.SNOW_GOLEM) {
			return 0.0;
		}
		return STANDARD_MULTIPLIER;
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Hostile mobs only — buffing cows and villagers achieves
			// nothing and makes villager mechanics feel broken.
			if (!(entity instanceof Mob mob)) {
				return;
			}

			double multiplier = multiplierFor(mob.getType());
			if (multiplier <= 0.0) {
				return;
			}

			AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
			if (maxHealth == null || maxHealth.getModifier(HEALTH_BOOST_ID) != null) {
				return; // already scaled (entity reloaded from disk)
			}

			maxHealth.addPermanentModifier(new AttributeModifier(
					HEALTH_BOOST_ID, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

			// Top the entity up, otherwise a freshly buffed mob spawns at
			// its OLD health value and looks pre-damaged.
			mob.setHealth(mob.getMaxHealth());
		});
	}
}
