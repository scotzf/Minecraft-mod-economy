package com.pisomarket.claims;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.util.PisoText;

// Registers /claims, /trust, /untrust, /unclaim — see CLAUDE.md "In-game
// command surface". All four act on whichever claim the player is
// currently standing in, not by id — simpler than tracking ids for a
// player who may own several claims. Activating a claim itself is done by
// right-clicking the ground with a Land Deed (LandDeedItem), not a command.
public final class ClaimCommands {
	private ClaimCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(Commands.literal("claims").executes(ClaimCommands::listMine));

			dispatcher.register(
					Commands.literal("trust")
							.then(
									Commands.argument("player", GameProfileArgument.gameProfile())
											.then(
													Commands.argument("level", StringArgumentType.word())
															.executes(ClaimCommands::trust)
											)
							)
			);

			dispatcher.register(
					Commands.literal("untrust")
							.then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(ClaimCommands::untrust))
			);

			dispatcher.register(
					Commands.literal("unclaim")
							.executes(ClaimCommands::unclaimPrompt)
							.then(Commands.literal("confirm").executes(ClaimCommands::unclaimConfirmed))
			);

			// Chest policy for the claim you're standing in — the deed
			// book's [Only me]/[Put only]/[Put+Get]/[Open] buttons run this.
			dispatcher.register(
					Commands.literal("claim").then(
							Commands.literal("chest").then(
									Commands.argument("mode", StringArgumentType.word()).executes(ClaimCommands::setChestAccess)
							)
					)
			);
		});
	}

	private static PisoClaims claims(final ServerPlayer player) {
		return player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
	}

	private static Claim claimHereOwnedBy(final ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		Claim claim = claims(player).findAt(player.level().dimension(), pos.getX(), pos.getY(), pos.getZ());
		return claim != null && claim.owner().equals(player.getUUID()) ? claim : null;
	}

	private static int listMine(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		List<Claim> mine = claims(player).byOwner(player.getUUID());
		if (mine.isEmpty()) {
			context.getSource().sendSuccess(() -> PisoText.body("You don't own any claims"), false);
			return 1;
		}
		context.getSource().sendSuccess(() -> PisoText.body("Your claims:"), false);
		for (Claim claim : mine) {
			int w = claim.maxX() - claim.minX() + 1;
			int l = claim.maxZ() - claim.minZ() + 1;
			int h = claim.maxY() - claim.minY() + 1;
			String rent;
			if (claim.rentPerPeriod() <= 0) {
				rent = " — no rent";
			} else if (claim.rentUnpaid()) {
				int left = Claim.RENT_GRACE_PERIODS - claim.unpaidPeriods();
				rent = " — RENT UNPAID, protection OFF, released after " + left + " more missed";
			} else {
				rent = " — rent " + claim.rentPerPeriod() + " every " + Claim.RENT_PERIOD_DAYS
						+ " days played, next due in " + RentCollector.timeUntilDue(claim);
			}
			String line = "#" + claim.id() + " — " + w + "x" + l + "x" + h
					+ " at (" + claim.minX() + "," + claim.minY() + "," + claim.minZ() + ")" + rent;
			context.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int trust(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Claim claim = claimHereOwnedBy(player);
		if (claim == null) {
			context.getSource().sendFailure(PisoText.failure("Stand inside a claim you own to use this"));
			return 0;
		}

		String levelArg = StringArgumentType.getString(context, "level").toUpperCase();
		TrustLevel level;
		try {
			level = TrustLevel.valueOf(levelArg);
		} catch (IllegalArgumentException e) {
			context.getSource().sendFailure(PisoText.failure("Level must be place, destroy, or both"));
			return 0;
		}

		NameAndId target = GameProfileArgument.getGameProfiles(context, "player").iterator().next();
		claims(player).setTrust(claim.id(), target.id(), target.name(), level);
		String name = target.name();
		context.getSource().sendSuccess(() -> PisoText.success("Trusted ").append(PisoText.name(name))
				.append(PisoText.plain(" (" + levelArg.toLowerCase() + ") on claim "))
				.append(PisoText.name("#" + claim.id())), false);
		return 1;
	}

	private static int untrust(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Claim claim = claimHereOwnedBy(player);
		if (claim == null) {
			context.getSource().sendFailure(PisoText.failure("Stand inside a claim you own to use this"));
			return 0;
		}

		NameAndId target = GameProfileArgument.getGameProfiles(context, "player").iterator().next();
		claims(player).setTrust(claim.id(), target.id(), target.name(), null);
		String name = target.name();
		context.getSource().sendSuccess(() -> PisoText.success("Untrusted ").append(PisoText.name(name))
				.append(PisoText.plain(" on claim ")).append(PisoText.name("#" + claim.id())), false);
		return 1;
	}

	private static int unclaimPrompt(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Claim claim = claimHereOwnedBy(player);
		if (claim == null) {
			context.getSource().sendFailure(PisoText.failure("Stand inside a claim you own to use this"));
			return 0;
		}

		// If they're holding the deed, show a real Yes/No book page. Falling
		// back to a clickable chat line covers running /unclaim by hand
		// without the deed in hand.
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof LandDeedItem) {
			LandDeedItem.openUnclaimConfirm(player, claim, held, net.minecraft.world.InteractionHand.MAIN_HAND);
			return 1;
		}

		MutableComponent confirm = Component.literal("[Confirm unclaim #" + claim.id() + " — this cannot be undone]")
				.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/unclaim confirm")));
		context.getSource().sendSuccess(() -> confirm, false);
		return 1;
	}

	private static int setChestAccess(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Claim claim = claimHereOwnedBy(player);
		if (claim == null) {
			context.getSource().sendFailure(PisoText.failure("Stand inside a claim you own to use this"));
			return 0;
		}

		String mode = StringArgumentType.getString(context, "mode").toLowerCase();
		ChestAccess access = switch (mode) {
			case "owneronly" -> ChestAccess.OWNER_ONLY;
			case "putonly" -> ChestAccess.TRUSTED_PUT_ONLY;
			case "putandget" -> ChestAccess.TRUSTED_PUT_AND_GET;
			case "open" -> ChestAccess.OPEN;
			default -> null;
		};
		if (access == null) {
			context.getSource().sendFailure(PisoText.failure("Mode must be owneronly, putonly, putandget, or open"));
			return 0;
		}

		claims(player).setChestAccess(claim.id(), access);
		context.getSource().sendSuccess(() -> PisoText.body("Chests in claim ").append(PisoText.name("#" + claim.id()))
				.append(PisoText.plain(": " + access.label())), false);
		return 1;
	}

	private static int unclaimConfirmed(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Claim claim = claimHereOwnedBy(player);
		if (claim == null) {
			context.getSource().sendFailure(PisoText.failure("Stand inside a claim you own to use this"));
			return 0;
		}

		claims(player).remove(claim.id());
		context.getSource().sendSuccess(() -> PisoText.success("Claim ").append(PisoText.name("#" + claim.id()))
				.append(PisoText.plain(" released")), false);
		return 1;
	}
}
