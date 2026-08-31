package com.pisomarket.economy;

import java.util.Collection;
import java.util.UUID;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import com.pisomarket.util.PisoText;

// Registers /balance and /donate. See CLAUDE.md's "In-game
// command surface" for the full documented behavior of each.
// /donate replaces /pay as of the v2 redesign (same behavior, new name).
public final class PisoCommands {
	private PisoCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(Commands.literal("balance").executes(PisoCommands::balance));

			dispatcher.register(
					Commands.literal("donate")
							.then(
									Commands.argument("player", GameProfileArgument.gameProfile())
											.then(
													Commands.argument("amount", LongArgumentType.longArg(1))
															.executes(PisoCommands::donate)
											)
							)
			);

			// /deposit and /withdraw are deliberately NOT registered.
			//
			// Moving money between hand and vault must happen at a Shop
			// block, so the block is a real place players have to go rather
			// than decoration. The equivalent logic lives in the menu's Vault
			// view (PisoShopMenu.depositFromInventory, and the withdraw
			// branch of clickedVault), which was always a separate
			// implementation — it never called these commands — so removing
			// them changes nothing for the GUI.
		});
	}

	private static PisoVault vault(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoVault.TYPE);
	}

	private static int balance(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long amount = vault(context.getSource().getServer()).getBalance(player.getUUID());
		VaultSync.sync(player); // cheap self-heal if the HUD ever drifts
		context.getSource().sendSuccess(() -> PisoText.body("Balance: ").append(PisoText.money(amount)), false);
		return 1;
	}

	private static int donate(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer sender = context.getSource().getPlayerOrException();
		long amount = LongArgumentType.getLong(context, "amount");

		// GameProfileArgument resolves by name even if the target has never
		// been online this session (it goes through the server's persistent
		// name->UUID cache), matching CLAUDE.md's "recipient may be offline".
		Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "player");
		NameAndId target = targets.iterator().next();
		UUID targetId = target.id();

		if (targetId.equals(sender.getUUID())) {
			context.getSource().sendFailure(PisoText.failure("You can't donate to yourself"));
			return 0;
		}

		MinecraftServer server = context.getSource().getServer();
		boolean success = vault(server).transfer(sender.getUUID(), targetId, amount);
		if (!success) {
			context.getSource().sendFailure(PisoText.failure("Insufficient balance"));
			return 0;
		}

		VaultSync.sync(sender);
		ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetId);
		if (targetPlayer != null) {
			VaultSync.sync(targetPlayer);
		}

		String targetName = target.name();
		context.getSource().sendSuccess(() -> PisoText.success("Donated ").append(PisoText.money(amount)).append(PisoText.plain(" to ")).append(PisoText.name(targetName)), false);
		return 1;
	}

}
