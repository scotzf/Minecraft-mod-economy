package com.pisomarket.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;

import com.pisomarket.claims.DeedProtection;

// A bound Land Deed may only live in the player's own inventory or their
// ender chest — never a normal chest, barrel, shulker, hopper, or any other
// container someone else could reach. Combined with PlayerDropMixin (can't
// drop it), that leaves selling it on the market as the only way to hand
// land over, which is exactly the intent.
//
// The ender chest is allowed because it's private per player and travels
// with them, so it's storage without being a theft vector.
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerDeedGuardMixin {
	@Shadow
	public NonNullList<Slot> slots;

	private static boolean pisomarket$isSafeHome(final Container container) {
		// The shop's own grid is allowed because its Sell slot is the one
		// sanctioned route for transferring land (see PisoShopContainer).
		return container instanceof Inventory
				|| container instanceof PlayerEnderChestContainer
				|| container instanceof com.pisomarket.shop.PisoShopContainer;
	}

	// Everything funnels through clicked() — including shift-click, which
	// arrives as ContainerInput.QUICK_MOVE. quickMoveStack itself is
	// abstract on AbstractContainerMenu, so it cannot be injected into
	// (mixin fails at load with "insnNode is null"); handling both cases
	// here is both necessary and sufficient.
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void pisomarket$blockDeedIntoContainer(final int slotId, final int button, final ContainerInput containerInput,
			final Player player, final CallbackInfo ci) {
		if (slotId < 0 || slotId >= this.slots.size()) {
			return;
		}
		Slot slot = this.slots.get(slotId);

		if (containerInput == ContainerInput.QUICK_MOVE) {
			// Shift-click: vanilla decides the destination, so refuse if
			// the deed could land anywhere unsafe in this menu at all.
			if (!DeedProtection.isBoundDeed(slot.getItem())) {
				return;
			}
			for (Slot candidate : this.slots) {
				if (!pisomarket$isSafeHome(candidate.container)) {
					pisomarket$refuse(player, ci);
					return;
				}
			}
			return;
		}

		if (pisomarket$isSafeHome(slot.container)) {
			return;
		}

		// Blocked if they're carrying a bound deed onto this slot, or the
		// slot already holds one (so it can't be shuffled around either).
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		if (DeedProtection.isBoundDeed(self.getCarried()) || DeedProtection.isBoundDeed(slot.getItem())) {
			pisomarket$refuse(player, ci);
		}
	}

	private static void pisomarket$refuse(final Player player, final CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			DeedProtection.warnCannotStore(serverPlayer);
		}
		ci.cancel();
	}
}
