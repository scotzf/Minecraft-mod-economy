package com.pisomarket.claims;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// While holding a Land Deed (bound or unbound — an unbound one is exactly
// the "where's it safe to claim" case; a bound one doubles as "where does
// my own claim end"), draws each nearby claim's box outline as particles
// visible only to that player: green for claims they own or are trusted
// on, red for everyone else's. No particles at all means unclaimed ground.
//
// PERFORMANCE — this was reported as noticeable lag, and the cause was the
// packet count, not the maths. ServerLevel.sendParticles with count=1 emits
// one ClientboundLevelParticlesPacket PER PARTICLE. The old version drew a
// point every 2 blocks along all 12 edges, twice a second: roughly 104
// packets per claim per update, ~208/second, each also spawning a particle
// the client has to simulate and render.
//
// Three multiplicative fixes:
//   1. SEGMENTS — one packet now covers a whole run of edge using count>1
//      with a positional spread, instead of one packet per point. Same
//      visual density, a fraction of the packets.
//   2. Claims are pre-filtered by DRAW_DISTANCE before their edges are
//      walked at all. Previously a claim 40 blocks away still had every
//      edge point generated and distance-tested one by one.
//   3. Half the update rate, a tighter draw distance, and a much smaller
//      hard ceiling.
public final class TerritoryVisualizer {
	private static final int TICK_INTERVAL = 20; // once a second
	// Only claims with an edge within this range are considered at all.
	private static final double DRAW_DISTANCE = 16.0;
	private static final double DRAW_DISTANCE_SQ = DRAW_DISTANCE * DRAW_DISTANCE;
	// One packet covers this many blocks of edge...
	private static final int SEGMENT = 4;
	// ...scattering this many particles across it.
	private static final int PARTICLES_PER_SEGMENT = 3;
	// Hard ceiling on PACKETS (not particles) per player per update.
	private static final int MAX_PACKETS_PER_PLAYER = 40;

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

			PisoClaims claims = server.getDataStorage().computeIfAbsent(PisoClaims.TYPE);
			if (claims.all().isEmpty()) {
				return; // nothing claimed anywhere — skip every player
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (isHoldingDeed(player)) {
					showNearbyClaims(player, claims);
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

	private static void showNearbyClaims(final ServerPlayer player, final PisoClaims claims) {
		var dimension = player.level().dimension();
		Vec3 eye = player.position();
		int budget = MAX_PACKETS_PER_PLAYER;

		for (Claim claim : claims.all()) {
			if (!claim.dimension().equals(dimension)) {
				continue;
			}

			// Distance from the player to the claim's box on each axis.
			// Cheap rejection BEFORE walking any edges — this is what stops
			// far-away claims costing anything at all.
			double dx = Math.max(0, Math.max(claim.minX() - eye.x, eye.x - (claim.maxX() + 1)));
			double dy = Math.max(0, Math.max(claim.minY() - eye.y, eye.y - (claim.maxY() + 1)));
			double dz = Math.max(0, Math.max(claim.minZ() - eye.z, eye.z - (claim.maxZ() + 1)));
			if (dx * dx + dy * dy + dz * dz > DRAW_DISTANCE_SQ) {
				continue;
			}

			boolean isMine = claim.owner().equals(player.getUUID()) || claim.trusted().containsKey(player.getUUID());
			budget = drawCube(player, claim, isMine ? GREEN : RED, eye, budget);
			if (budget <= 0) {
				return;
			}
		}
	}

	// Full 3D box outline (12 edges) — protection covers the deed's whole
	// height, so the visual should too.
	private static int drawCube(final ServerPlayer player, final Claim claim, final DustParticleOptions color,
			final Vec3 eye, int budget) {
		ServerLevel level = (ServerLevel) player.level();
		double bottomY = claim.minY() + 0.1;
		double topY = claim.maxY() + 0.9;
		double minX = claim.minX();
		double maxX = claim.maxX() + 1.0;
		double minZ = claim.minZ();
		double maxZ = claim.maxZ() + 1.0;

		// Edges running along X, at both Z walls and both heights.
		for (double x = minX; x < maxX && budget > 0; x += SEGMENT) {
			double len = Math.min(SEGMENT, maxX - x);
			double cx = x + len / 2.0;
			budget = segment(level, player, color, cx, bottomY, minZ, len / 2.0, 0, 0, eye, budget);
			budget = segment(level, player, color, cx, bottomY, maxZ, len / 2.0, 0, 0, eye, budget);
			budget = segment(level, player, color, cx, topY, minZ, len / 2.0, 0, 0, eye, budget);
			budget = segment(level, player, color, cx, topY, maxZ, len / 2.0, 0, 0, eye, budget);
		}

		// Edges running along Z.
		for (double z = minZ; z < maxZ && budget > 0; z += SEGMENT) {
			double len = Math.min(SEGMENT, maxZ - z);
			double cz = z + len / 2.0;
			budget = segment(level, player, color, minX, bottomY, cz, 0, 0, len / 2.0, eye, budget);
			budget = segment(level, player, color, maxX, bottomY, cz, 0, 0, len / 2.0, eye, budget);
			budget = segment(level, player, color, minX, topY, cz, 0, 0, len / 2.0, eye, budget);
			budget = segment(level, player, color, maxX, topY, cz, 0, 0, len / 2.0, eye, budget);
		}

		// The four vertical corner posts.
		for (double y = bottomY; y < topY && budget > 0; y += SEGMENT) {
			double len = Math.min(SEGMENT, topY - y);
			double cy = y + len / 2.0;
			budget = segment(level, player, color, minX, cy, minZ, 0, len / 2.0, 0, eye, budget);
			budget = segment(level, player, color, minX, cy, maxZ, 0, len / 2.0, 0, eye, budget);
			budget = segment(level, player, color, maxX, cy, minZ, 0, len / 2.0, 0, eye, budget);
			budget = segment(level, player, color, maxX, cy, maxZ, 0, len / 2.0, 0, eye, budget);
		}

		return budget;
	}

	// One packet, several particles scattered along the segment's axis.
	// Skips anything the player can't see anyway.
	private static int segment(final ServerLevel level, final ServerPlayer player, final DustParticleOptions color,
			final double x, final double y, final double z,
			final double spreadX, final double spreadY, final double spreadZ,
			final Vec3 eye, final int budget) {
		if (budget <= 0) {
			return 0;
		}
		double ddx = x - eye.x;
		double ddy = y - eye.y;
		double ddz = z - eye.z;
		if (ddx * ddx + ddy * ddy + ddz * ddz > DRAW_DISTANCE_SQ) {
			return budget;
		}
		// speed 0 keeps them static so the outline reads as a line, not a puff.
		level.sendParticles(player, color, true, true, x, y, z, PARTICLES_PER_SEGMENT, spreadX, spreadY, spreadZ, 0.0);
		return budget - 1;
	}
}
