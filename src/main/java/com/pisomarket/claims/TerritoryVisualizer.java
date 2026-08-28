package com.pisomarket.claims;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// While holding a Land Deed (bound or unbound — an unbound one is exactly
// the "where's it safe to claim" case; a bound one doubles as "where does
// my own claim end"), draws each nearby claim's box outline as particles
// visible only to that player: green for claims they own or are trusted
// on, red for everyone else's. No particles at all means unclaimed ground.
public final class TerritoryVisualizer {
	private static final int TICK_INTERVAL = 10; // ~0.5s at 20 tps
	private static final int RADIUS = 48;
	// Only draw an edge point if it's actually near the player. A 20x20x10
	// claim has ~200 edge points; drawing all of them for every nearby
	// claim, twice a second, is a lot of individual particle packets and
	// was reported as lag. Culling to what's visible cuts that hard.
	private static final double DRAW_DISTANCE = 24.0;
	private static final double DRAW_DISTANCE_SQ = DRAW_DISTANCE * DRAW_DISTANCE;
	// Every Nth block along an edge instead of every block — a dotted
	// outline reads just as clearly and costs a fraction of the packets.
	private static final int STEP = 2;
	// Hard ceiling per player per update, so a cluster of overlapping
	// claims can never spike into thousands of packets.
	private static final int MAX_PARTICLES_PER_PLAYER = 220;
	private static final DustParticleOptions GREEN = new DustParticleOptions(0x00FF00, 1.0f);
	private static final DustParticleOptions RED = new DustParticleOptions(0xFF0000, 1.0f);

	private static int tickCounter;

	private TerritoryVisualizer() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (tickCounter % TICK_INTERVAL != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (isHoldingDeed(player)) {
					showNearbyClaims(player);
				}
			}
		});
	}

	private static boolean isHoldingDeed(final ServerPlayer player) {
		return isDeed(player.getMainHandItem()) || isDeed(player.getOffhandItem());
	}

	private static boolean isDeed(final ItemStack stack) {
		return stack.getItem() instanceof LandDeedItem;
	}

	private static void showNearbyClaims(final ServerPlayer player) {
		PisoClaims claims = player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
		var dimension = player.level().dimension();
		var pos = player.position();
		int budget = MAX_PARTICLES_PER_PLAYER;

		for (Claim claim : claims.all()) {
			if (!claim.dimension().equals(dimension)) {
				continue;
			}
			double dx = Math.max(0, Math.max(claim.minX() - pos.x, pos.x - claim.maxX()));
			double dz = Math.max(0, Math.max(claim.minZ() - pos.z, pos.z - claim.maxZ()));
			if (dx > RADIUS || dz > RADIUS) {
				continue;
			}

			boolean isMine = claim.owner().equals(player.getUUID()) || claim.trusted().containsKey(player.getUUID());
			budget = drawCube(player, claim, isMine ? GREEN : RED, budget);
			if (budget <= 0) {
				return;
			}
		}
	}

	// Full 3D box outline (12 edges), not just a flat square at ground
	// level — protection already covers the deed's whole height
	// (Claim.contains checks Y too), the visual just wasn't matching that.
	//
	// Returns the remaining particle budget so a cluster of claims can't
	// collectively blow past MAX_PARTICLES_PER_PLAYER.
	private static int drawCube(final ServerPlayer player, final Claim claim, final DustParticleOptions color, int budget) {
		double bottomY = claim.minY() + 0.1;
		double topY = claim.maxY() + 0.9;
		double minX = claim.minX();
		double maxX = claim.maxX() + 1.0;
		double minZ = claim.minZ();
		double maxZ = claim.maxZ() + 1.0;
		var eye = player.position();

		// Bottom and top horizontal edges.
		for (int x = claim.minX(); x <= claim.maxX() && budget > 0; x += STEP) {
			budget = tryParticle(player, color, x + 0.5, bottomY, minZ, eye, budget);
			budget = tryParticle(player, color, x + 0.5, bottomY, maxZ, eye, budget);
			budget = tryParticle(player, color, x + 0.5, topY, minZ, eye, budget);
			budget = tryParticle(player, color, x + 0.5, topY, maxZ, eye, budget);
		}
		for (int z = claim.minZ(); z <= claim.maxZ() && budget > 0; z += STEP) {
			budget = tryParticle(player, color, minX, bottomY, z + 0.5, eye, budget);
			budget = tryParticle(player, color, maxX, bottomY, z + 0.5, eye, budget);
			budget = tryParticle(player, color, minX, topY, z + 0.5, eye, budget);
			budget = tryParticle(player, color, maxX, topY, z + 0.5, eye, budget);
		}

		// Four vertical corner edges connecting bottom to top.
		for (int y = claim.minY(); y <= claim.maxY() + 1 && budget > 0; y += STEP) {
			budget = tryParticle(player, color, minX, y, minZ, eye, budget);
			budget = tryParticle(player, color, minX, y, maxZ, eye, budget);
			budget = tryParticle(player, color, maxX, y, minZ, eye, budget);
			budget = tryParticle(player, color, maxX, y, maxZ, eye, budget);
		}

		return budget;
	}

	// Skips anything the player couldn't meaningfully see anyway — the
	// single biggest win, since most of a large claim's outline is far
	// behind/away from wherever they're standing.
	private static int tryParticle(final ServerPlayer player, final DustParticleOptions color,
			final double x, final double y, final double z, final net.minecraft.world.phys.Vec3 eye, final int budget) {
		if (budget <= 0) {
			return 0;
		}
		double ddx = x - eye.x;
		double ddy = y - eye.y;
		double ddz = z - eye.z;
		if (ddx * ddx + ddy * ddy + ddz * ddz > DRAW_DISTANCE_SQ) {
			return budget;
		}
		((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(player, color, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		return budget - 1;
	}
}
