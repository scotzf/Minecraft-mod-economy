package com.pisomarket.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

// TNT is disabled server-wide on this server, by decision — it was causing
// noticeable lag and, at the edge of a claim, a hard server crash.
//
// This kills the primed entity on its very first tick, BEFORE the fuse ever
// runs. That is deliberately earlier than cancelling the explosion itself:
//   - No fuse countdown, no per-tick entity movement, no smoke particles
//     broadcast to every nearby player for four seconds.
//   - No ServerExplosion is ever constructed, so no ray-casting over
//     hundreds of block positions and no claim filtering work either.
// Lighting a TNT block now simply makes it vanish.
//
// This covers EVERY way TNT can be primed — flint & steel, redstone, fire,
// a dispenser, or another explosion setting off a chain — because all of
// them ultimately spawn a PrimedTnt entity, and all of them tick.
//
// Deliberately NOT touched: creepers, beds/respawn anchors in the wrong
// dimension, and end crystals. Those are still normal explosions and are
// still filtered by ExplosionProtectionMixin. Only TNT is removed.
@Mixin(PrimedTnt.class)
public abstract class TntRemovalMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void pisomarket$removeTnt(final CallbackInfo ci) {
		// discard() is Entity's, not PrimedTnt's — the cast through Object is
		// the standard mixin way to reach an inherited method without
		// declaring the whole superclass hierarchy.
		((Entity) (Object) this).discard();
		ci.cancel();
	}
}
