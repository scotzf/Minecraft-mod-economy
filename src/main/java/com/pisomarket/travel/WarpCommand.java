package com.pisomarket.travel;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.pisomarket.util.PisoText;

// /warp — return to the bound waypoint.
//
// Two limits, both deliberate:
//
// COMBAT LOCK. Refused if the player took damage in the last 5 seconds.
// Without this, /warp is a free escape button that trivialises every fight
// and every raid. Five seconds is short enough that it never annoys someone
// travelling peacefully.
//
// COOLDOWN. 5 minutes between uses. Free otherwise — farming plots are
// spread out by nature and travel should not be means-tested.
public final class WarpCommand {
	private static final long COOLDOWN_TICKS = 5 * 60 * 20L; // 5 minutes
	private static final int COMBAT_LOCK_TICKS = 5 * 20;     // 5 seconds

	private WarpCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal("warp").executes(WarpCommand::warp))
		);
	}

	private static int warp(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		WaypointState waypoints = context.getSource().getServer().getDataStorage().computeIfAbsent(WaypointState.TYPE);

		if (!waypoints.isBound(player.getUUID())) {
			context.getSource().sendFailure(PisoText.failure("You have no waypoint bound. Right-click one to bind it."));
			return 0;
		}

		// Combat lock. invulnerableTime counts down from the last hit, so a
		// non-zero value means very recent damage; the explicit tick
		// comparison covers the rest of the 5-second window.
		if (player.getLastHurtByMobTimestamp() > 0
				&& player.tickCount - player.getLastHurtByMobTimestamp() < COMBAT_LOCK_TICKS) {
			context.getSource().sendFailure(PisoText.failure("You took damage too recently to warp."));
			return 0;
		}

		long now = context.getSource().getServer().overworld().getGameTime();
		long last = waypoints.lastWarpTick(player.getUUID());
		if (last > 0 && now - last < COOLDOWN_TICKS) {
			long secondsLeft = (COOLDOWN_TICKS - (now - last)) / 20;
			context.getSource().sendFailure(PisoText.failure(
					"Warp is on cooldown for another " + (secondsLeft / 60) + "m " + (secondsLeft % 60) + "s."));
			return 0;
		}

		String dimensionId = waypoints.dimensionOf(player.getUUID());
		ServerLevel target = context.getSource().getServer().getLevel(
				ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
						Identifier.parse(dimensionId)));
		if (target == null) {
			context.getSource().sendFailure(PisoText.failure("That waypoint's world no longer exists."));
			return 0;
		}

		// The block is checked, not just the coordinates: if someone mined
		// the waypoint while the owner was offline the unbind hook may not
		// have run, and teleporting into empty air is worse than failing.
		BlockPos pos = waypoints.posOf(player.getUUID());
		if (!target.getBlockState(pos).is(WaypointContent.WAYPOINT_BLOCK)) {
			context.getSource().sendFailure(PisoText.failure("Your waypoint is gone."));
			return 0;
		}

		player.teleportTo(target, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		waypoints.markWarped(player.getUUID(), now);
		context.getSource().sendSuccess(
				() -> PisoText.success("Warped."), false);
		return 1;
	}
}
