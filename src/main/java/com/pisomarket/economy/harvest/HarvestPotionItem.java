package com.pisomarket.economy.harvest;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.pisomarket.PisoMarket;

// The three BlackMarket harvest potions. Drinking one applies a status
// effect for HarvestFaucet to read (see PisoEffects).
//
// All three are the same class with different parameters, because the only
// thing that varies is which effect is applied and how strong it is.
public class HarvestPotionItem extends Item {
	// One minute, as specified. Effect durations are in ticks; 20 ticks is
	// one real second.
	public static final int DURATION_TICKS = 60 * 20;

	public static final HarvestPotionItem HARVEST_I = create("harvest_potion_i", PisoEffects.HARVEST_BOOST, 0);
	public static final HarvestPotionItem HARVEST_II = create("harvest_potion_ii", PisoEffects.HARVEST_BOOST, 1);
	public static final HarvestPotionItem LUCK = create("luck_potion", PisoEffects.HARVEST_LUCK, 0);

	private static final String[] NAMES = {"harvest_potion_i", "harvest_potion_ii", "luck_potion"};
	private static final Item[] ITEMS = {HARVEST_I, HARVEST_II, LUCK};

	private final Holder<MobEffect> effect;
	private final int amplifier;

	protected HarvestPotionItem(final Properties properties, final Holder<MobEffect> effect, final int amplifier) {
		super(properties);
		this.effect = effect;
		this.amplifier = amplifier;
	}

	private static HarvestPotionItem create(final String name, final Holder<MobEffect> effect, final int amplifier) {
		Identifier id = Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, name);
		// stacksTo(8) keeps a pocketful reasonable without letting someone
		// carry a stack of 64 minutes of buff into one harvesting session.
		return new HarvestPotionItem(
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(8), effect, amplifier
		);
	}

	public static void register() {
		for (int i = 0; i < NAMES.length; i++) {
			Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, NAMES[i]), ITEMS[i]);
		}
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!level.isClientSide()) {
			// addEffect REPLACES an existing instance of the same effect
			// rather than adding to it, which is exactly the "not stackable"
			// rule: drinking a second Harvest potion re-starts the minute (or
			// upgrades I to II) instead of stacking two bonuses together.
			player.addEffect(new MobEffectInstance(effect, DURATION_TICKS, amplifier, false, true, true), player);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0F, 1.0F);

		// Creative players keep the item, same as vanilla consumables.
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
