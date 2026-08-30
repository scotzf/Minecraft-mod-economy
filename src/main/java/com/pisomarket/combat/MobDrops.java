package com.pisomarket.combat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.economy.PisoCurrency;
import com.pisomarket.economy.harvest.PisoLuck;

// The second faucet: Shards and custom weapons from mob kills. Rarest mobs
// drop the rarest weapons.
//
// THREE SAFEGUARDS, all load-bearing. Without them this table is an
// infinite money loop, so none of them are optional:
//
// 1. PLAYER-KILL ONLY. The drop fires only when a player landed the killing
//    blow — not fall damage, not suffocation, not an iron golem, not a
//    wolf. Same rule the crop faucet uses (player-break only), and for the
//    same reason: it makes classic AFK mob-grinder farms mint nothing.
// 2. PLAYER-BUILDABLE MOBS DROP NOTHING. Iron Golem and Snow Golem are
//    craftable from blocks, so any drop on them is literally an
//    iron-for-money printer.
// 3. PASSIVE MOBS DROP NOTHING. They breed infinitely and cost nothing to
//    kill. Handled by omission — anything not in the table below pays out
//    zero.
public final class MobDrops {
	// Weapon rarity pools, matching the tiers in CLAUDE.md's combat spec.
	private static final List<Item> TIER_1 = List.of(
			ElementalWeapons.SOUL_COLLECTOR, ElementalWeapons.SOUL_DEVOURER);
	private static final List<Item> TIER_2 = List.of(
			ElementalWeapons.DIVINEAXERHITTA, ElementalWeapons.ABOMINABLESCYTHE,
			ElementalWeapons.DIVINE_REAPER, ElementalWeapons.ABOMINABLEGREATSABER);
	private static final List<Item> TIER_3 = List.of(
			ElementalWeapons.FROSTBLADE, ElementalWeapons.FROSTSCYTHE, ElementalWeapons.MOLTENBLADE);
	private static final List<Item> TIER_4 = List.of(
			ElementalWeapons.ABOMINABLEBLADE, ElementalWeapons.MOLTENSWORD, ElementalWeapons.FROSTAXE);

	// One row of the drop table. shardChance <= 0 means "always pay
	// shardMin..shardMax" (used for bosses and guaranteed rare drops);
	// otherwise it is rolled per kill.
	private record Drop(double shardChance, int shardMin, int shardMax,
			double weaponChance, List<Item> weaponPool,
			double secondWeaponChance, List<Item> secondWeaponPool) {

		static Drop shards(final double chance, final int min, final int max) {
			return new Drop(chance, min, max, 0.0, List.of(), 0.0, List.of());
		}

		static Drop guaranteed(final int shards, final double weaponChance, final List<Item> pool) {
			return new Drop(-1.0, shards, shards, weaponChance, pool, 0.0, List.of());
		}

		static Drop boss(final int shards, final double weaponChance, final List<Item> pool,
				final double rareChance, final List<Item> rarePool) {
			return new Drop(-1.0, shards, shards, weaponChance, pool, rareChance, rarePool);
		}

		static Drop rare(final double chance, final int min, final int max,
				final double weaponChance, final List<Item> pool) {
			return new Drop(chance, min, max, weaponChance, pool, 0.0, List.of());
		}
	}

	private MobDrops() {
	}

	private static Drop tableFor(final EntityType<?> type) {
		// --- Bosses
		if (type == EntityTypes.WARDEN) {
			return Drop.guaranteed(10000, 1.0, TIER_1);
		}
		if (type == EntityTypes.ENDER_DRAGON) {
			return Drop.boss(5000, 0.50, TIER_2, 0.05, TIER_1);
		}
		if (type == EntityTypes.WITHER) {
			return Drop.boss(3000, 0.40, TIER_2, 0.03, TIER_1);
		}

		// --- Very rare / structure-gated
		if (type == EntityTypes.ELDER_GUARDIAN) {
			return Drop.guaranteed(500, 0.15, TIER_2);
		}
		if (type == EntityTypes.RAVAGER) {
			return Drop.guaranteed(300, 0.10, TIER_3);
		}
		if (type == EntityTypes.EVOKER) {
			return Drop.guaranteed(100, 0.08, TIER_3);
		}
		if (type == EntityTypes.PIGLIN_BRUTE) {
			return Drop.guaranteed(80, 0.05, TIER_4);
		}
		if (type == EntityTypes.BREEZE) {
			return Drop.guaranteed(60, 0.05, TIER_3);
		}
		if (type == EntityTypes.SHULKER) {
			return Drop.guaranteed(60, 0.0, List.of());
		}
		if (type == EntityTypes.GUARDIAN) {
			return Drop.guaranteed(40, 0.03, TIER_4);
		}
		if (type == EntityTypes.VINDICATOR || type == EntityTypes.PILLAGER || type == EntityTypes.ILLUSIONER) {
			return Drop.guaranteed(30, 0.0, List.of());
		}

		// --- Uncommon
		if (type == EntityTypes.WITCH || type == EntityTypes.ENDERMAN) {
			return Drop.shards(0.20, 1, 3);
		}
		if (type == EntityTypes.BLAZE || type == EntityTypes.WITHER_SKELETON || type == EntityTypes.GHAST) {
			return Drop.shards(0.15, 1, 2);
		}
		if (type == EntityTypes.HOGLIN || type == EntityTypes.ZOGLIN) {
			return Drop.shards(0.12, 1, 2);
		}
		if (type == EntityTypes.PHANTOM || type == EntityTypes.PIGLIN || type == EntityTypes.SLIME
				|| type == EntityTypes.MAGMA_CUBE || type == EntityTypes.CAVE_SPIDER || type == EntityTypes.STRAY
				|| type == EntityTypes.BOGGED || type == EntityTypes.HUSK || type == EntityTypes.DROWNED) {
			return Drop.shards(0.10, 1, 1);
		}

		// --- Common
		if (type == EntityTypes.ZOMBIE || type == EntityTypes.ZOMBIE_VILLAGER || type == EntityTypes.SKELETON
				|| type == EntityTypes.CREEPER || type == EntityTypes.SPIDER
				|| type == EntityTypes.SILVERFISH || type == EntityTypes.ENDERMITE) {
			return Drop.shards(0.05, 1, 1);
		}

		// Everything else — passive animals, villagers, and CRUCIALLY
		// Iron Golem and Snow Golem (safeguard 2) — pays nothing.
		return null;
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// Safeguard 1: a player must have landed the killing blow.
			if (!(damageSource.getEntity() instanceof ServerPlayer killer)) {
				return;
			}
			// Never pay out for killing another player — PvP has its own
			// rewards, and this would otherwise be an alt-account loop.
			if (entity instanceof ServerPlayer) {
				return;
			}

			Drop drop = tableFor(entity.getType());
			if (drop == null) {
				return;
			}

			awardShards(level, entity, killer, drop);
			rollWeapon(level, entity, drop.weaponChance(), drop.weaponPool());
			rollWeapon(level, entity, drop.secondWeaponChance(), drop.secondWeaponPool());
		});
	}

	private static void awardShards(final ServerLevel level, final LivingEntity victim,
			final ServerPlayer killer, final Drop drop) {
		int count;
		if (drop.shardChance() < 0.0) {
			// Guaranteed payout (bosses, rare mobs). Luck still doubles it,
			// but there is no chance roll to boost, so Fortune does nothing
			// here by design — a potion should not multiply a boss payout.
			count = drop.shardMin();
			if (killer.hasEffect(com.pisomarket.economy.harvest.PisoEffects.HARVEST_LUCK)) {
				count *= 2;
			}
		} else {
			// Chance-based: routed through the SAME roll the crop faucet
			// uses, which is what makes one potion buff both activities.
			int payout = PisoLuck.rollPayout(killer, drop.shardChance());
			if (payout <= 0) {
				return;
			}
			int range = drop.shardMax() - drop.shardMin() + 1;
			int rolled = drop.shardMin() + ThreadLocalRandom.current().nextInt(Math.max(1, range));
			count = rolled * payout;
		}

		if (count > 0) {
			spawnAt(level, victim, new ItemStack(PisoCurrency.SUNSTONE_SHARD, count));
		}
	}

	private static void rollWeapon(final ServerLevel level, final LivingEntity victim,
			final double chance, final List<Item> pool) {
		if (chance <= 0.0 || pool.isEmpty()) {
			return;
		}
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return;
		}
		Item weapon = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
		spawnAt(level, victim, new ItemStack(weapon));
	}

	private static void spawnAt(final ServerLevel level, final Entity at, final ItemStack stack) {
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
				level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}
}
