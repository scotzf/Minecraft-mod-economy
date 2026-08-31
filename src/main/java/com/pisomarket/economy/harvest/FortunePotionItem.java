package com.pisomarket.economy.harvest;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.pisomarket.PisoMarket;

// The three Fortune potions. Drinking one applies a status
// effect for HarvestFaucet to read (see PisoEffects).
//
// All three are the same class with different parameters, because the only
// thing that varies is which effect is applied and how strong it is.
public class FortunePotionItem extends Item {
	// One minute, as specified. Effect durations are in ticks; 20 ticks is
	// one real second.
	public static final int DURATION_TICKS = 60 * 20;

	public static final FortunePotionItem FORTUNE_I = create("fortune_potion_i", PisoEffects.FORTUNE_BOOST, 0);
	public static final FortunePotionItem FORTUNE_II = create("fortune_potion_ii", PisoEffects.FORTUNE_BOOST, 1);
	public static final FortunePotionItem LUCK = create("luck_potion", PisoEffects.FORTUNE_LUCK, 0);

	private static final String[] NAMES = {"fortune_potion_i", "fortune_potion_ii", "luck_potion"};
	private static final Item[] ITEMS = {FORTUNE_I, FORTUNE_II, LUCK};

	private final Holder<MobEffect> effect;
	private final int amplifier;

	protected FortunePotionItem(final Properties properties, final Holder<MobEffect> effect, final int amplifier) {
		super(properties);
		this.effect = effect;
		this.amplifier = amplifier;
	}

	private static FortunePotionItem create(final String name, final Holder<MobEffect> effect, final int amplifier) {
		Identifier id = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, name);
		// stacksTo(8) keeps a pocketful reasonable without letting someone
		// carry a stack of 64 minutes of buff into one harvesting session.
		return new FortunePotionItem(
				new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, id))
						.stacksTo(8)
						// Vanilla's own drink consumable: gives the real
						// drinking animation, the standard drink duration and
						// the gulp sounds for free, instead of the instant
						// right-click with a one-off sound this had before.
						.component(net.minecraft.core.component.DataComponents.CONSUMABLE,
								net.minecraft.world.item.component.Consumables.DEFAULT_DRINK),
				effect, amplifier
		);
	}

	public static void register() {
		for (int i = 0; i < NAMES.length; i++) {
			Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, NAMES[i]), ITEMS[i]);
		}
	}

	// Applied when the drink completes, so the effect lands with the
	// animation rather than instantly on click.
	@Override
	public ItemStack finishUsingItem(final ItemStack stack, final Level level, final net.minecraft.world.entity.LivingEntity entity) {
		if (!level.isClientSide() && entity instanceof Player player) {
			// addEffect REPLACES an existing instance of the same effect
			// rather than adding to it, which is exactly the "not stackable"
			// rule: drinking a second Harvest potion re-starts the minute (or
			// upgrades I to II) instead of stacking two bonuses together.
			player.addEffect(new MobEffectInstance(effect, DURATION_TICKS, amplifier, false, true, true), player);
			// Creative players keep the item, same as vanilla consumables.
			stack.consume(1, player);
		}
		return stack;
	}
}
