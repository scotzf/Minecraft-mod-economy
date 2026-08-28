package com.pisomarket.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import com.pisomarket.claims.ClaimProtection;

// Explosions bypass every player-facing protection: TNT and creepers don't
// fire PlayerBlockBreakEvents, so a protected base could simply be blown
// up. This filters protected positions out of the list of blocks an
// explosion is about to destroy — the blast still happens, damages
// entities, and destroys anything outside a claim; it just can't remove
// claimed blocks.
//
// Hooking calculateExplodedPositions (rather than the block removal
// itself) means drops, sounds and particles all stay consistent with the
// smaller block list.
@Mixin(ServerExplosion.class)
public abstract class ExplosionProtectionMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void pisomarket$filterProtectedBlocks(final CallbackInfoReturnable<List<BlockPos>> cir) {
		List<BlockPos> positions = cir.getReturnValue();
		if (positions == null || positions.isEmpty()) {
			return;
		}

		// One batched pass — see ClaimProtection.filterProtected for why
		// this must not be done per-position.
		List<BlockPos> allowed = ClaimProtection.filterProtected(this.level, positions);
		if (allowed != positions) {
			cir.setReturnValue(allowed);
		}
	}
}
