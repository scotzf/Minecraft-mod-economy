package com.pisomarket.claims;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.network.Filterable;

// A purchasable claim of a fixed size (see CLAUDE.md "Territory claims").
// State lives directly on the ItemStack via the vanilla CUSTOM_DATA
// component (a small NBT tag) rather than a custom-registered
// DataComponentType — simpler, and this data never needs to be queried
// outside the item itself.
//
// Unbound (just bought): width/length/height set, no claimId.
// Bound (activated): claimId also set. Right-clicking again opens a
// clickable written-book management screen — but only while standing
// inside the claim itself; otherwise just a reminder message.
public class LandDeedItem extends Item {
	// A claim starts this many blocks BELOW the block you activate on, with
	// the remaining height going up. Without this, digging a basement under
	// your own house left you outside your own claim and grief-able.
	private static final int DEPTH_BELOW_GROUND = 4;

	public LandDeedItem(final Properties properties) {
		super(properties);
	}

	// price is carried on the deed so the claim it creates knows what rent
	// to charge (see RentCollector) — the claim itself has no other way to
	// know which size it came from once activated.
	public static ItemStack createUnbound(final Item item, final String label, final int width, final int length, final int height, final long price) {
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = new CompoundTag();
		tag.putInt("width", width);
		tag.putInt("length", length);
		tag.putInt("height", height);
		tag.putLong("price", price);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Land Deed — " + label + " (" + width + "x" + length + "x" + height + ")"));
		return stack;
	}

	// Handles the bound (management book) case for a plain right-click,
	// i.e. NOT aiming at a block within reach. useOn (below) only fires
	// when a block is actually targeted, which is fine for "activate on
	// the ground you're pointing at" but meant the book silently never
	// opened at all if you right-clicked while just looking straight
	// ahead into open air instead of down at the ground.
	@Override
	public InteractionResult use(final net.minecraft.world.level.Level level, final net.minecraft.world.entity.player.Player genericPlayer, final InteractionHand hand) {
		if (level.isClientSide() || !(genericPlayer instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = player.getItemInHand(hand);
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (tag.getInt("claimId").isEmpty()) {
			// Unbound — activation needs an actual ground block target, so
			// it's handled by useOn, not here. Say so out loud rather than
			// returning PASS silently: a silent no-op is indistinguishable
			// from the mod being broken, which is exactly how this looked
			// when reported ("total silence" while standing in a claim).
			int w = tag.getInt("width").orElse(0);
			int l = tag.getInt("length").orElse(0);
			int h = tag.getInt("height").orElse(0);
			player.sendSystemMessage(Component.literal(
					"This deed (" + w + "x" + l + "x" + h + ") isn't activated yet — point at the ground and right-click to claim there."
			));
			return InteractionResult.SUCCESS;
		}

		return useBound(player, tag.getInt("claimId").orElse(-1), stack, hand);
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = context.getItemInHand();
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

		if (tag.getInt("claimId").isPresent()) {
			return useBound(player, tag.getInt("claimId").orElse(-1), stack, context.getHand());
		}

		int width = tag.getInt("width").orElse(0);
		int length = tag.getInt("length").orElse(0);
		int height = tag.getInt("height").orElse(0);
		if (width <= 0 || length <= 0 || height <= 0) {
			player.sendSystemMessage(Component.literal("This deed's size is invalid — it can't be activated."));
			return InteractionResult.FAIL;
		}

		BlockPos ground = context.getClickedPos();
		int minX = ground.getX() - width / 2;
		int minZ = ground.getZ() - length / 2;
		int maxX = minX + width - 1;
		int maxZ = minZ + length - 1;
		int minY = ground.getY() - DEPTH_BELOW_GROUND;
		int maxY = minY + height - 1;

		PisoClaims claims = player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
		var dimension = player.level().dimension();
		Claim overlap = claims.findOverlap(dimension, minX, minY, minZ, maxX, maxY, maxZ);
		if (overlap != null) {
			player.sendSystemMessage(Component.literal(
					"Can't activate here — too close to claim #" + overlap.id() + " (owned by someone else). Try a different spot."
			));
			return InteractionResult.FAIL;
		}

		// Don't claim immediately — claiming is a big, hard-to-undo action,
		// so remember the target spot on the deed and ask first. Confirmed
		// via /deed confirm (see DeedCommands.confirmClaim).
		tag.putInt("pendingX", ground.getX());
		tag.putInt("pendingY", ground.getY());
		tag.putInt("pendingZ", ground.getZ());
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);

		MutableComponent page = Component.literal(
				"Claim this land?\n\n"
						+ "Size: " + width + "x" + length + "x" + height + "\n"
						+ "Corner: " + minX + ", " + minY + ", " + minZ + "\n"
						+ "to: " + maxX + ", " + maxY + ", " + maxZ + "\n\n"
		);
		page.append(Component.literal("[Yes, claim here]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/deed confirm"))))
				.append(Component.literal("\n\n"))
				.append(Component.literal("[Cancel]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/deed cancel"))));

		openBook(player, stack, context.getHand(), "Confirm claim", page);
		return InteractionResult.SUCCESS;
	}

	// Actually creates the claim from the pending position stored above.
	// Returns a player-facing error, or null on success.
	public static String confirmPendingClaim(final ServerPlayer player, final ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (tag.getInt("claimId").isPresent()) {
			return "That deed is already bound to a claim.";
		}
		if (tag.getInt("pendingX").isEmpty()) {
			return "Right-click the ground with the deed first to pick a spot.";
		}

		int width = tag.getInt("width").orElse(0);
		int length = tag.getInt("length").orElse(0);
		int height = tag.getInt("height").orElse(0);
		int gx = tag.getInt("pendingX").orElse(0);
		int gy = tag.getInt("pendingY").orElse(0);
		int gz = tag.getInt("pendingZ").orElse(0);

		int minX = gx - width / 2;
		int minZ = gz - length / 2;
		int maxX = minX + width - 1;
		int maxZ = minZ + length - 1;
		int minY = gy - DEPTH_BELOW_GROUND;
		int maxY = minY + height - 1;

		PisoClaims claims = player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
		var dimension = player.level().dimension();
		Claim overlap = claims.findOverlap(dimension, minX, minY, minZ, maxX, maxY, maxZ);
		if (overlap != null) {
			return "Can't claim here — too close to claim #" + overlap.id() + ".";
		}

		long deedPrice = tag.getLong("price").orElse(0L);
		long rent = deedPrice > 0 ? RentCollector.rentForDeedPrice(deedPrice) : 0L;
		int claimId = claims.create(player.getUUID(), dimension, minX, minY, minZ, maxX, maxY, maxZ, rent);
		tag.putInt("claimId", claimId);
		tag.remove("pendingX");
		tag.remove("pendingY");
		tag.remove("pendingZ");
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Land Deed — claim #" + claimId + " (bound)"));
		player.sendSystemMessage(Component.literal("Claim #" + claimId + " activated: " + width + "x" + length + "x" + height));
		return null;
	}

	public static void cancelPendingClaim(final ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.remove("pendingX");
		tag.remove("pendingY");
		tag.remove("pendingZ");
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
	}

	// Shared book-opening helper. The content MUST go on the stack actually
	// in the player's hand — openItemGui only sends the hand, and the client
	// reads WRITTEN_BOOK_CONTENT off whatever it finds there.
	private static void openBook(final ServerPlayer player, final ItemStack heldStack, final InteractionHand hand,
			final String title, final MutableComponent page) {
		openBook(player, heldStack, hand, title, List.of(page));
	}

	// Multi-page overload. A written book page renders only what physically
	// fits and silently CLIPS the rest — it doesn't scroll or wrap onto the
	// next page. So anything that can grow (the trust list) gets its own
	// page, otherwise a claim with several trusted players would push the
	// chest settings and [Unclaim] button off the bottom where they can't
	// be clicked at all.
	private static void openBook(final ServerPlayer player, final ItemStack heldStack, final InteractionHand hand,
			final String title, final List<MutableComponent> pages) {
		WrittenBookContent content = new WrittenBookContent(
				Filterable.passThrough(title), "Piso Market", 0,
				pages.stream().map(p -> Filterable.passThrough((Component) p)).toList(), true
		);
		heldStack.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
		player.openItemGui(heldStack, hand);
	}

	private InteractionResult useBound(final ServerPlayer player, final int claimId, final ItemStack heldStack, final InteractionHand hand) {
		PisoClaims claims = player.level().getServer().getDataStorage().computeIfAbsent(PisoClaims.TYPE);
		Claim claim = claims.get(claimId);
		if (claim == null) {
			player.sendSystemMessage(Component.literal("This deed's claim no longer exists."));
			return InteractionResult.SUCCESS;
		}

		boolean inside = claim.dimension().equals(player.level().dimension()) && claim.contains(player.blockPosition());
		if (!inside) {
			// Spell out both the claim's bounds and where the player
			// actually is — if this check is ever wrong (off-by-one, Y
			// mismatch, wrong dimension), the message itself makes it
			// obvious instead of just looking like the book is broken.
			BlockPos at = player.blockPosition();
			player.sendSystemMessage(Component.literal(
					"Stand inside claim #" + claimId + " to manage it. Claim is X " + claim.minX() + ".." + claim.maxX()
							+ ", Y " + claim.minY() + ".." + claim.maxY() + ", Z " + claim.minZ() + ".." + claim.maxZ()
							+ " — you are at " + at.getX() + ", " + at.getY() + ", " + at.getZ() + "."
			));
			return InteractionResult.SUCCESS;
		}

		openClaimBook(player, claim, heldStack, hand);
		return InteractionResult.SUCCESS;
	}

	private static void openClaimBook(final ServerPlayer player, final Claim claim, final ItemStack heldStack, final InteractionHand hand) {
		MinecraftServer server = player.level().getServer();
		int width = claim.maxX() - claim.minX() + 1;
		int length = claim.maxZ() - claim.minZ() + 1;
		int height = claim.maxY() - claim.minY() + 1;

		// PAGE 1 — the claim itself and its rent standing.
		MutableComponent overview = Component.literal(
				"Claim #" + claim.id() + "\n" + width + "x" + length + "x" + height + "\n"
						+ "at " + claim.minX() + ", " + claim.minZ() + "\n\n"
		);
		appendRentSection(overview, claim);

		// PAGE 2 — trust management (the part that grows).
		MutableComponent page = Component.literal("");

		if (claim.trusted().isEmpty()) {
			page.append(Component.literal("No one trusted yet\n\n"));
		} else {
			page.append(Component.literal("Trusted:\n"));
			for (Map.Entry<UUID, TrustLevel> entry : claim.trusted().entrySet()) {
				boolean online = server.getPlayerList().getPlayer(entry.getKey()) != null;
				String levelLabel = entry.getValue().name().toLowerCase();
				// The name was recorded when trust was granted, so offline
				// players get working buttons too — this used to fall back
				// to a raw UUID with no usable [Remove].
				String name = claim.nameFor(entry.getKey());
				page.append(Component.literal(name + " (" + levelLabel + (online ? "" : ", offline") + ") "));
				appendLevelLinks(page, name);
				page.append(Component.literal(" "))
						.append(Component.literal("[Remove]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/untrust " + name))));
				page.append(Component.literal("\n"));
			}
			page.append(Component.literal("\n"));
		}

		page.append(Component.literal("Online players:\n"));
		boolean anyOnline = false;
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (online.getUUID().equals(player.getUUID()) || claim.trusted().containsKey(online.getUUID())) {
				continue;
			}
			anyOnline = true;
			String name = online.getGameProfile().name();
			page.append(Component.literal(name + " "));
			appendLevelLinks(page, name);
			page.append(Component.literal("\n"));
		}
		if (!anyOnline) {
			page.append(Component.literal("(no one else online)\n"));
		}

		// PAGE 3 — chest access and the destructive button, kept clear of the
		// trust list so they can never be clipped off the page.
		// Chest access replaces the old Lock item entirely. One setting for
		// every chest in the claim; the owner is never restricted.
		MutableComponent settings = Component.literal("Chests: " + claim.chestAccess().label() + "\n");
		appendChestLinks(settings);

		settings.append(Component.literal("\n\n"))
				.append(Component.literal("[Unclaim this land]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/unclaim"))));

		// Content goes on the held stack — see openBook for why.
		openBook(player, heldStack, hand, "Claim #" + claim.id(), List.of(overview, page, settings));
	}

	// The rent countdown. This is the part players actually need to see:
	// rent is charged silently in the background by RentCollector, so
	// without it the first sign anything was happening was a "protection is
	// OFF" chat message.
	//
	// The clock only advances while the owner is logged in (see
	// RentCollector), so "days" here means days of PLAY, not calendar days.
	// Both units are shown because neither alone is meaningful on its own:
	// in-game days are what the rent is defined in, but real minutes are
	// what a player can actually plan around.
	private static void appendRentSection(final MutableComponent page, final Claim claim) {
		if (claim.rentPerPeriod() <= 0) {
			// Claims created before rent existed carry 0 and stay free.
			page.append(Component.literal("Rent: none\nThis land is free to keep.\n"));
			return;
		}

		page.append(Component.literal(
				"Rent: " + claim.rentPerPeriod()
						+ "\nevery " + Claim.RENT_PERIOD_DAYS + " days played\n\n"
		));

		page.append(Component.literal("Next due in:\n" + RentCollector.timeUntilDue(claim) + "\n\n"));

		if (claim.rentUnpaid()) {
			int periodsLeft = Claim.RENT_GRACE_PERIODS - claim.unpaidPeriods();
			page.append(Component.literal(
					"PROTECTION IS OFF\nYou missed " + claim.unpaidPeriods() + " payment"
							+ (claim.unpaidPeriods() == 1 ? "" : "s") + ". Anyone can build here until rent is paid.\n"
							+ "Land is released after " + periodsLeft + " more.\n\n"
							+ "Put money in your vault — it pays itself.\n"
			));
		} else {
			page.append(Component.literal("Paid up. Protection on.\n"));
		}
	}

	private static void appendChestLinks(final MutableComponent page) {
		page.append(Component.literal("[Only me]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/claim chest owneronly"))))
				.append(Component.literal(" "))
				.append(Component.literal("[Put only]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/claim chest putonly"))))
				.append(Component.literal("\n"))
				.append(Component.literal("[Put+Get]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/claim chest putandget"))))
				.append(Component.literal(" "))
				.append(Component.literal("[Open]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/claim chest open"))));
	}

	// Yes/no confirmation page for unclaiming — a destructive action needs
	// a real button, not just a chat line.
	public static void openUnclaimConfirm(final ServerPlayer player, final Claim claim, final ItemStack heldStack, final InteractionHand hand) {
		MutableComponent page = Component.literal(
				"Unclaim claim #" + claim.id() + "?\n\nThis removes your protection. Blocks you built stay, but anyone can change them.\n\n"
		);
		page.append(Component.literal("[Yes, unclaim]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/unclaim confirm"))))
				.append(Component.literal("\n\n"))
				.append(Component.literal("[No, keep it]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/claims"))));
		openBook(player, heldStack, hand, "Confirm unclaim", page);
	}

	// [Place] [Destroy] [Both] — one click each to set a player's trust
	// level directly, instead of needing to type /trust <name> <level>.
	// Works the same whether the player is already trusted (changes their
	// level) or not yet trusted (adds them at that level) — /trust handles
	// both cases identically.
	private static void appendLevelLinks(final MutableComponent page, final String name) {
		page.append(Component.literal("[Place]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/trust " + name + " place"))))
				.append(Component.literal(" "))
				.append(Component.literal("[Destroy]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/trust " + name + " destroy"))))
				.append(Component.literal(" "))
				.append(Component.literal("[Both]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/trust " + name + " both"))));
	}
}
