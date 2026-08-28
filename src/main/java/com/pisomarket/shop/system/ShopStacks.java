package com.pisomarket.shop.system;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

// Builds the actual ItemStack for a catalog entry. Enchantments live in a
// registry, so an enchanted stack can only be created with server registry
// access — it can't be a constant on the entry itself.
public final class ShopStacks {
	private ShopStacks() {
	}

	public static ItemStack build(final MinecraftServer server, final ShopEntry entry, final int count) {
		ItemStack stack = new ItemStack(entry.item(), count);
		if (!entry.isEnchanted()) {
			return stack;
		}

		Holder<Enchantment> enchantment = server.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(entry.enchantment());
		EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, entry.enchantLevel()));
		return stack;
	}
}
