package com.pisomarket.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.pisomarket.claims.DeedProtection;

// Blocks dropping a bound Land Deed (see DeedProtection for why). Vanilla
// has no event for this, so it takes a mixin on Player.drop — the method
// the Q-key drop path ends up calling.
@Mixin(Player.class)
public abstract class PlayerDropMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void pisomarket$blockBoundDeedDrop(final ItemStack itemStack, final boolean thrownFromHand, final CallbackInfoReturnable<ItemEntity> cir) {
		if (!DeedProtection.isBoundDeed(itemStack)) {
			return;
		}
		if ((Object) this instanceof ServerPlayer serverPlayer) {
			DeedProtection.warnCannotDrop(serverPlayer);
		}
		// Returning null means "nothing was dropped"; the stack stays in
		// the player's inventory untouched.
		cir.setReturnValue(null);
	}
}
