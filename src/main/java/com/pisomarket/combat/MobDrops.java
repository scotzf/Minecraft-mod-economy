package com.pisomarket.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.item.equipment.ArmorType;

import com.pisomarket.economy.harvest.PisoEffects;
import com.pisomarket.economy.harvest.PisoLuck;
import com.pisomarket.shop.system.PisoShopStock;

// The second faucet: Shards, custom weapons and custom armor from mob kills.
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
//    kill. Handled by omission — anything not in the table below pays zero.
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

	// Boss payout cooldown. A full payout is available once per this many
	// in-game days PER PLAYER PER BOSS TYPE; repeat kills inside the window
	// pay REPEAT_PAYOUT_FRACTION instead. 7 in-game days is roughly 2.3
	// real hours. Both numbers are tunable — the point is to break the
	// respawn loop, not to make repeat boss fights worthless.
	public static final int BOSS_COOLDOWN_DAYS = 7;
	public static final double REPEAT_PAYOUT_FRACTION = 0.01;

	private static final ArmorType[] PIECES = {
			ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

	// Which armor set a drop comes from. Armor always drops as ONE random
	// piece, never a full set — a whole set from a single kill would make
	// the hardest gear in the game a one-fight reward.
	private enum ArmorSet {
		SENTINEL, AEGIS, BULWARK;

		Item randomPiece() {
			ArmorType type = PIECES[ThreadLocalRandom.current().nextInt(PIECES.length)];
			return switch (this) {
				case SENTINEL -> CustomArmorContent.gold(type);
				case AEGIS -> CustomArmorContent.diamond(type);
				case BULWARK -> CustomArmorContent.netherite(type);
			};
		}
	}

	// One row of the drop table. shardChance < 0 means "always pay
	// shardMin"; otherwise it is rolled per kill.
	private record Drop(
			double shardChance, int shardMin, int shardMax,
			double weaponChance, List<Item> weaponPool,
			double secondWeaponChance, List<Item> secondWeaponPool,
			double armorChance, ArmorSet armorSet) {

		static Drop shards(final double chance, final int min, final int max) {
			return new Drop(chance, min, max, 0.0, List.of(), 0.0, List.of(), 0.0, null);
		}

		static Drop flat(final int shards) {
			return new Drop(-1.0, shards, shards, 0.0, List.of(), 0.0, List.of(), 0.0, null);
		}

		Drop withWeapon(final double chance, final List<Item> pool) {
			return new Drop(shardChance, shardMin, shardMax, chance, pool,
					secondWeaponChance, secondWeaponPool, armorChance, armorSet);
		}

		Drop withRareWeapon(final double chance, final List<Item> pool) {
			return new Drop(shardChance, shardMin, shardMax, weaponChance, weaponPool,
					chance, pool, armorChance, armorSet);
		}

		Drop withArmor(final double chance, final ArmorSet set) {
			return new Drop(shardChance, shardMin, shardMax, weaponChance, weaponPool,
					secondWeaponChance, secondWeaponPool, chance, set);
		}
	}

	private MobDrops() {
	}

	// Armor chance for the structure-gated "rare" band, derived from the
	// mob's own buffed max health rather than hand-set per mob: a tougher
	// fight is worth better odds, and one formula stays consistent if mob
	// HP is ever retuned. Capped at 15% — these are one-off structure
	// fights, not farmable the way Blaze and Wither Skeleton are.
	private static double armorChanceFromHealth(final LivingEntity mob) {
		double hp = mob.getMaxHealth();
		return Math.min(0.15, hp / 1000.0);
	}

	// Which set a rare mob drops, also scaled by how hard it hits back.
	private static ArmorSet armorSetFromHealth(final LivingEntity mob) {
		double hp = mob.getMaxHealth();
		if (hp >= 100) {
			return ArmorSet.BULWARK;
		}
		if (hp >= 50) {
			return ArmorSet.AEGIS;
		}
		return ArmorSet.SENTINEL;
	}

	// Built once at registration rather than evaluated as an if-chain on
	// every mob death. The old form walked up to ~30 EntityType comparisons
	// for the most common case (a mob with no drops at all), which is the
	// single hottest path in this class.
	private static final Map<EntityType<?>, Drop> TABLE = new HashMap<>();
	private static final Set<EntityType<?>> HEALTH_DERIVED_ARMOR = new HashSet<>();

	private static void buildTable() {
		// --- Bosses. Tier 1 mobs drop armor at 100% alongside their weapon,
		// and pay across a 500k-1m band scaled by how hard the fight is.
		// These are deliberately the only income at this magnitude — see the
		// economy note in CLAUDE.md about what that does to farming.
		TABLE.put(EntityTypes.WARDEN,
				Drop.flat(1_000_000).withWeapon(1.0, TIER_1).withArmor(1.0, ArmorSet.BULWARK));
		TABLE.put(EntityTypes.ENDER_DRAGON,
				Drop.flat(750_000).withWeapon(0.50, TIER_2).withRareWeapon(0.05, TIER_1).withArmor(1.0, ArmorSet.BULWARK));
		TABLE.put(EntityTypes.WITHER,
				Drop.flat(500_000).withWeapon(0.40, TIER_2).withRareWeapon(0.03, TIER_1).withArmor(1.0, ArmorSet.AEGIS));

		// --- Very rare / structure-gated. Armor chance is HP-derived and
		// applied at award time (see awardArmor).
		TABLE.put(EntityTypes.ELDER_GUARDIAN, Drop.flat(500).withWeapon(0.15, TIER_2));
		TABLE.put(EntityTypes.RAVAGER, Drop.flat(300).withWeapon(0.10, TIER_3));
		TABLE.put(EntityTypes.EVOKER, Drop.flat(100).withWeapon(0.08, TIER_3));
		TABLE.put(EntityTypes.PIGLIN_BRUTE, Drop.flat(80).withWeapon(0.05, TIER_4));
		TABLE.put(EntityTypes.BREEZE, Drop.flat(60).withWeapon(0.05, TIER_3));
		TABLE.put(EntityTypes.SHULKER, Drop.flat(60));
		TABLE.put(EntityTypes.GUARDIAN, Drop.flat(40).withWeapon(0.03, TIER_4));
		// Illusioner deliberately absent: it has no natural spawn in
		// survival, so a table entry for it could never fire.
		TABLE.put(EntityTypes.VINDICATOR, Drop.flat(30));
		TABLE.put(EntityTypes.PILLAGER, Drop.flat(30));

		HEALTH_DERIVED_ARMOR.addAll(Set.of(
				EntityTypes.ELDER_GUARDIAN, EntityTypes.RAVAGER, EntityTypes.EVOKER,
				EntityTypes.PIGLIN_BRUTE, EntityTypes.BREEZE, EntityTypes.SHULKER,
				EntityTypes.GUARDIAN, EntityTypes.VINDICATOR, EntityTypes.PILLAGER));

		// --- Uncommon. Three of these carry hand-set armor chances.
		// Blaze and Wither Skeleton sit at 1%, NOT 10%: both are
		// spawner-farmable in fortresses, and at 10% a full four-piece set
		// was about forty kills — an afternoon. At 1% it is a few hundred.
		TABLE.put(EntityTypes.WITCH, Drop.shards(0.20, 1, 3));
		TABLE.put(EntityTypes.ENDERMAN, Drop.shards(0.20, 1, 3));
		TABLE.put(EntityTypes.BLAZE, Drop.shards(0.15, 1, 2).withArmor(0.01, ArmorSet.AEGIS));
		TABLE.put(EntityTypes.WITHER_SKELETON, Drop.shards(0.15, 1, 2).withArmor(0.01, ArmorSet.BULWARK));
		TABLE.put(EntityTypes.GHAST, Drop.shards(0.15, 1, 2));
		TABLE.put(EntityTypes.HOGLIN, Drop.shards(0.12, 1, 2));
		TABLE.put(EntityTypes.ZOGLIN, Drop.shards(0.12, 1, 2));
		TABLE.put(EntityTypes.PHANTOM, Drop.shards(0.10, 1, 1).withArmor(0.01, ArmorSet.SENTINEL));
		for (EntityType<?> type : new EntityType<?>[] {
				EntityTypes.PIGLIN, EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.CAVE_SPIDER,
				EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.HUSK, EntityTypes.DROWNED}) {
			TABLE.put(type, Drop.shards(0.10, 1, 1));
		}

		// --- Endermite: paid flat for RARITY, not difficulty. Most players
		// never see one (it only spawns from ender pearl misthrows), so a
		// 5% chance of 1 shard meant it effectively never paid at all.
		TABLE.put(EntityTypes.ENDERMITE, Drop.flat(5));

		// --- Common. No armor, ever.
		for (EntityType<?> type : new EntityType<?>[] {
				EntityTypes.ZOMBIE, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.SKELETON,
				EntityTypes.CREEPER, EntityTypes.SPIDER, EntityTypes.SILVERFISH}) {
			TABLE.put(type, Drop.shards(0.05, 1, 1));
		}

		// Anything absent — passive animals, villagers, and CRUCIALLY Iron
		// Golem and Snow Golem (safeguard 2) — pays nothing.
	}

	// Rare/structure mobs get HP-derived armor odds; everything else uses
	// whatever the table row set explicitly.
	private static boolean isBoss(final EntityType<?> type) {
		return type == EntityTypes.WARDEN || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WITHER;
	}



	public static void register() {
		buildTable();
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

			Drop drop = TABLE.get(entity.getType());
			if (drop == null) {
				return;
			}

			awardShards(level, entity, killer, drop);
			rollWeapon(level, entity, drop.weaponChance(), drop.weaponPool());
			rollWeapon(level, entity, drop.secondWeaponChance(), drop.secondWeaponPool());
			awardArmor(level, entity, drop);
		});
	}

	private static void awardArmor(final ServerLevel level, final LivingEntity victim, final Drop drop) {
		double chance;
		ArmorSet set;

		if (HEALTH_DERIVED_ARMOR.contains(victim.getType())) {
			chance = armorChanceFromHealth(victim);
			set = armorSetFromHealth(victim);
		} else {
			chance = drop.armorChance();
			set = drop.armorSet();
		}

		if (chance <= 0.0 || set == null) {
			return;
		}
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return;
		}
		spawnAt(level, victim, new ItemStack(set.randomPiece()));
	}

	private static void awardShards(final ServerLevel level, final LivingEntity victim,
			final ServerPlayer killer, final Drop drop) {
		int count;
		if (drop.shardChance() < 0.0) {
			// Guaranteed payout. Luck still doubles it, but there is no
			// chance roll to boost, so Fortune does nothing here by design —
			// a potion should not multiply a boss payout.
			count = drop.shardMin();

			// Boss cooldown. Wither and Ender Dragon are re-summonable, so
			// without this a player can loop respawns for an unbounded
			// income far beyond every other activity combined.
			if (isBoss(victim.getType())) {
				int today = PisoShopStock.currentDay(level.getServer());
				String bossId = victim.getType().toString();
				BossPayoutState payouts = level.getServer().getDataStorage().computeIfAbsent(BossPayoutState.TYPE);

				if (payouts.isFullPayoutDue(killer.getUUID(), bossId, today, BOSS_COOLDOWN_DAYS)) {
					payouts.recordFullPayout(killer.getUUID(), bossId, today);
				} else {
					count = (int) Math.max(1, count * REPEAT_PAYOUT_FRACTION);
					killer.sendSystemMessage(com.pisomarket.util.PisoText.hint("Reduced payout — you already claimed this boss recently."));
				}
			}

			if (killer.hasEffect(PisoEffects.FORTUNE_LUCK)) {
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
			// Straight to the vault, like XP. Weapons and armor below still
			// drop as real items — those are gear, not currency.
			com.pisomarket.economy.harvest.HarvestFaucet.credit(killer, count);
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
