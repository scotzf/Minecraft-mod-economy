package com.pisomarket.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;

import com.pisomarket.claims.ClaimProtection;

// Stops fire destroying blocks inside a claim.
//
// This is the other half of the lava fix (see FluidSpreadProtectionMixin).
// Blocking the fluid alone still leaves the arson route open: pour lava
// just outside the boundary and let the fire it starts eat the wooden
// house on the other side. Fire spread is a block tick, not a player
// action, so no player-facing event sees it either.
//
// checkBurnOut is the method that actually removes a burning block, so
// cancelling it protects the build while leaving fire's visuals and its
// behaviour outside claims completely untouched.
@Mixin(FireBlock.class)
public abstract class FireSpreadProtectionMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void pisomarket$protectClaimedBlocks(final Level level, final BlockPos pos, final int chance,
			final RandomSource random, final int age, final CallbackInfo ci) {
		if (level instanceof ServerLevel serverLevel && ClaimProtection.blocksFireDamage(serverLevel, pos)) {
			ci.cancel();
		}
	}
}
