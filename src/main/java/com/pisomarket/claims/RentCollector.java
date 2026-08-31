package com.pisomarket.claims;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.pisomarket.economy.PisoVault;
import com.pisomarket.economy.VaultSync;

import com.pisomarket.util.PisoText;

// Charges claim rent (see CLAUDE.md "Territory claims" and the notes on
// Claim). Rent is only ever charged while the OWNER is online, so time
// spent away is free — a player can take a two-week break and come back to
// a base that is still protected.
//
// The clock is an accumulator, not a date: every check adds the elapsed
// ticks to each owned claim, but only for players who are currently
// connected. Progress persists across logouts, so short sessions add up
// instead of resetting. (Comparing against a "last charged on day N" that
// got reset at login meant a payment needed 4 in-game days inside ONE
// session — anyone playing in shorter bursts never paid at all.)
public final class RentCollector {
	// Rent is 1% of the deed price per period. Claims made before rent
	// existed carry rentPerPeriod 0 and stay free.
	public static final double RENT_FRACTION_OF_PRICE = 0.01;

	private static final int CHECK_INTERVAL_TICKS = 200; // ~10s
	private static int tickCounter;

	private RentCollector() {
	}

	public static long rentForDeedPrice(final long deedPrice) {
		return Math.max(1, Math.round(deedPrice * RENT_FRACTION_OF_PRICE));
	}

	// How long until this claim's next rent charge, as player-facing text.
	// Shared by the deed book and /claims so the two can never disagree.
	//
	// The clock only advances while the owner is logged in, so these are
	// days of PLAY, not calendar days — hence the real-minutes figure
	// alongside it, which is what someone can actually plan around.
	// 1000 ticks is one in-game hour; 20 ticks is one real second.
	public static String timeUntilDue(final Claim claim) {
		long remaining = Math.max(0, Claim.RENT_PERIOD_TICKS - claim.rentProgressTicks());
		long days = remaining / Claim.TICKS_PER_DAY;
		long hours = (remaining % Claim.TICKS_PER_DAY) / 1000L;
		long realMinutes = remaining / 20L / 60L;
		return days + "d " + hours + "h (about " + realMinutes + " min played)";
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter % CHECK_INTERVAL_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				accrueFor(server, player);
			}
		});
	}

	private static PisoClaims claims(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(PisoClaims.TYPE);
	}

	private static void accrueFor(final MinecraftServer server, final ServerPlayer player) {
		PisoClaims claims = claims(server);

		// Deliberately a COPY, not a live view: the loop below calls
		// setRentState and, on the release path, remove — mutating the
		// backing map while iterating it would throw. The allocation is
		// irrelevant here since this runs once every 200 ticks per player.
		for (Claim claim : claims.byOwner(player.getUUID())) {
			if (claim.rentPerPeriod() <= 0) {
				continue; // pre-rent claim, free forever
			}

			long progress = claim.rentProgressTicks() + CHECK_INTERVAL_TICKS;
			if (progress < Claim.RENT_PERIOD_TICKS) {
				claims.setRentState(claim.id(), progress, claim.unpaidPeriods());
				continue;
			}

			// A payment has come due. Carry the remainder so long sessions
			// don't lose partial progress.
			long carried = progress - Claim.RENT_PERIOD_TICKS;
			PisoVault vault = server.getDataStorage().computeIfAbsent(PisoVault.TYPE);

			if (vault.withdraw(player.getUUID(), claim.rentPerPeriod())) {
				boolean wasUnpaid = claim.unpaidPeriods() > 0;
				claims.setRentState(claim.id(), carried, 0);
				VaultSync.sync(player);
				player.sendSystemMessage(wasUnpaid
						? PisoText.success("Rent paid for claim ").append(PisoText.name("#" + claim.id()))
								.append(PisoText.plain(" — protection is back on."))
						: PisoText.success("Rent ").append(PisoText.money(claim.rentPerPeriod()))
								.append(PisoText.plain(" paid for claim ")).append(PisoText.name("#" + claim.id())));
				continue;
			}

			int unpaid = claim.unpaidPeriods() + 1;
			if (unpaid >= Claim.RENT_GRACE_PERIODS) {
				claims.remove(claim.id());
				player.sendSystemMessage(PisoText.warning("Claim ").append(PisoText.name("#" + claim.id()))
						.append(PisoText.plain(" was released — rent went unpaid too long. "
								+ "Everything you built is still there, but the land is no longer yours.")));
				continue;
			}

			claims.setRentState(claim.id(), carried, unpaid);
			int periodsLeft = Claim.RENT_GRACE_PERIODS - unpaid;
			player.sendSystemMessage(PisoText.warning("Can't pay ").append(PisoText.money(claim.rentPerPeriod()))
					.append(PisoText.plain(" rent for claim ")).append(PisoText.name("#" + claim.id()))
					.append(PisoText.plain(" — protection is OFF until you do. Released after " + periodsLeft
							+ " more missed payment" + (periodsLeft == 1 ? "" : "s") + ".")));
		}
	}
}
