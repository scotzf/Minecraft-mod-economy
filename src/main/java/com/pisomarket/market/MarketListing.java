package com.pisomarket.market;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

// One posted item stack in world storage, waiting to be bought. Seller and
// buyer never need to be online together (CLAUDE.md: "asynchronous item
// listings").
public record MarketListing(int id, UUID seller, ItemStack stack, long price) {
	public static final Codec<MarketListing> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.INT.fieldOf("id").forGetter(MarketListing::id),
					UUIDUtil.STRING_CODEC.fieldOf("seller").forGetter(MarketListing::seller),
					ItemStack.CODEC.fieldOf("stack").forGetter(MarketListing::stack),
					Codec.LONG.fieldOf("price").forGetter(MarketListing::price)
			).apply(instance, MarketListing::new)
	);
}
