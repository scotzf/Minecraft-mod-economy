package com.pisomarket.economy.harvest;

import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;

// The only faucet in the economy (see CLAUDE.md "Economy: one faucet, one
// set of sinks"). Fires on every block break; only mature potato crops do
// anything. Deliberately no daily cap right now — see CLAUDE.md for why.
public final class HarvestFaucet {
	private static final double DROP_CHANCE = 0.025;

	private HarvestFaucet() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}
			if (!state.is(Blocks.POTATOES)) {
				return;
			}
			if (!(state.getBlock() instanceof CropBlock cropBlock) || !cropBlock.isMaxAge(state)) {
				return;
			}

			if (ThreadLocalRandom.current().nextDouble() < DROP_CHANCE) {
				PisoVault vault = serverPlayer.level().getServer().getDataStorage().computeIfAbsent(PisoVault.TYPE);
				vault.deposit(serverPlayer.getUUID(), 1);
				VaultSync.sync(serverPlayer);
				serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("A poisonous potato slipped into your vault (+1)"));
			}
		});
	}
}
