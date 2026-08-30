package com.pisomarket.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// One class for all six weapons — the only thing that varies between them is
// which Element they carry and which tool profile they were built with, both
// passed in at construction (see ElementalWeapons).
public class ElementalBladeItem extends Item {
	private final Element element;

	public ElementalBladeItem(final Element element, final Item.Properties properties) {
		super(properties);
		this.element = element;
	}

	@Override
	public void hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		// Server side only: applying an effect client-side desyncs the
		// target's effect list and the status icon flickers.
		if (!target.level().isClientSide()) {
			this.element.onHit(target, attacker);
		}
	}

	public Element element() {
		return this.element;
	}
}
