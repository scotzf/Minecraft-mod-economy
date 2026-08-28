package com.pisomarket.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

import com.pisomarket.claims.ClaimProtection;

// Stops lava and water flowing across a claim boundary from outside.
//
// Placing a bucket inside a claim was already blocked by ClaimProtection,
// but that only covered the deliberate act. Standing one block outside the
// line and pouring lava in is not a player action at all — the fluid
// spreads on its own, on a block tick, with no player attached — so nothing
// in the event-based protection could ever see it.
//
// spreadTo is the single chokepoint: both the downward path and the
// sideways path in FlowingFluid.spread funnel through it, so hooking it
// once covers every way a fluid can advance. `pos` is the DESTINATION, and
// `direction` is the way the fluid moved — which is what lets us recover
// the origin block and allow an owner's own fluid to keep flowing within
// their own claim.
@Mixin(FlowingFluid.class)
public abstract class FluidSpreadProtectionMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void pisomarket$blockSpreadIntoClaims(final LevelAccessor level, final BlockPos pos, final BlockState state,
			final Direction direction, final FluidState target, final CallbackInfo ci) {
		// LevelAccessor covers the client copy too; claims only exist server-side.
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		BlockPos origin = pos.relative(direction.getOpposite());
		if (ClaimProtection.blocksFluidFrom(serverLevel, pos, origin)) {
			ci.cancel();
		}
	}
}
