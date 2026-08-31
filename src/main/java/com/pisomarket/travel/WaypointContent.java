package com.pisomarket.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import com.pisomarket.PisoMarket;

import com.pisomarket.util.PisoText;

// The Waypoint block — fast travel infrastructure.
//
// PUBLIC BY DESIGN. Anyone who can physically reach a waypoint can bind to
// it, including inside someone else's claim. The intent is that players
// build travel hubs other people use, which makes placement a real
// decision. Deliberately NOT gated on claim trust.
//
// Implemented as a single block rather than the originally-sketched
// two-block door-style structure: the two-block version needs paired
// placement, paired breaking and a half-tracking blockstate, all of which
// is real complexity buying nothing mechanically. Flag if the visual of a
// tall structure actually matters.
public final class WaypointContent {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "waypoint");
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, ID);

	public static final Block WAYPOINT_BLOCK = Blocks.register(
			BLOCK_KEY,
			WaypointBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.strength(3.0f, 6.0f)
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> 7)
	);

	private WaypointContent() {
	}

	public static void register() {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ID);
		Registry.register(BuiltInRegistries.ITEM, ID,
				new BlockItem(WAYPOINT_BLOCK, new Item.Properties().setId(itemKey)));
	}

	// Right-click binds. Breaking unbinds anyone who was bound here.
	public static class WaypointBlock extends Block {
		public WaypointBlock(final Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
				final Player player, final BlockHitResult hit) {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}

			WaypointState waypoints = level.getServer().getDataStorage().computeIfAbsent(WaypointState.TYPE);
			waypoints.bind(serverPlayer.getUUID(), level.dimension().identifier().toString(), pos);
			serverPlayer.sendSystemMessage(PisoText.success("Waypoint bound. ").append(PisoText.hint("/warp returns you here.")));
			return InteractionResult.SUCCESS;
		}

		// Renamed from onRemove in 26.2 — it now takes a ServerLevel
		// directly and fires only on the server, so no side check needed.
		@Override
		protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level,
				final BlockPos pos, final boolean movedByPiston) {
			level.getServer().getDataStorage().computeIfAbsent(WaypointState.TYPE)
					.unbindAllAt(level.dimension().identifier().toString(), pos);
			super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		}
	}
}
