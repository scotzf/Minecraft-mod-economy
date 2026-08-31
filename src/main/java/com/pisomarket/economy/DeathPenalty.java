package com.pisomarket.economy;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.pisomarket.util.PisoText;

// Dying costs Shards, the way dying costs XP in vanilla.
//
// This is the sink that replaces the old "money can be lost to lava"
// property. Once currency became a balance rather than an item, nothing
// could destroy it any more — every faucet was pure inflation with no
// counterweight. A death tax puts that back and makes carrying a large
// balance into a fight a real decision.
//
// On a PvP kill the lost amount TRANSFERS to the killer rather than
// vanishing, which mirrors how vanilla XP orbs drop for whoever is
// standing there. On any other death it is destroyed.
public final class DeathPenalty {
	// Fraction of the victim's vault balance lost on death.
	public static final double DEATH_LOSS_FRACTION = 0.10;

	private DeathPenalty() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer victim)) {
				return;
			}

			MinecraftServer server = victim.level().getServer();
			PisoVault vault = server.getDataStorage().computeIfAbsent(PisoVault.TYPE);

			long balance = vault.getBalance(victim.getUUID());
			// Rounded DOWN, so a balance small enough to round to zero costs
			// nothing rather than silently taking the last Shard.
			long lost = (long) (balance * DEATH_LOSS_FRACTION);
			if (lost <= 0) {
				return;
			}

			if (!vault.withdraw(victim.getUUID(), lost)) {
				return;
			}
			VaultSync.sync(victim);

			// A player killer collects it; anything else destroys it.
			ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer p && p != victim ? p : null;

			if (killer != null) {
				vault.deposit(killer.getUUID(), lost);
				VaultSync.sync(killer);
				victim.sendSystemMessage(PisoText.failure("Lost ").append(PisoText.money(lost))
						.append(PisoText.plain(" to ")).append(PisoText.name(killer.getGameProfile().name())));
				killer.sendSystemMessage(PisoText.success("Claimed ").append(PisoText.money(lost))
						.append(PisoText.plain(" from ")).append(PisoText.name(victim.getGameProfile().name())));
			} else {
				victim.sendSystemMessage(PisoText.failure("Lost ").append(PisoText.money(lost))
						.append(PisoText.plain(" on death")));
			}
		});
	}
}
