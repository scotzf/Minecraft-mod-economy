package com.pisomarket.shop.system;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// Remaining stock per ShopEntry.id. Starts at ShopEntry.stock() the first
// time an entry is checked; decrements on purchase; never refills (no
// rotation system yet — see ShopEntry's scope note).
public class PisoShopStock extends SavedData {
	public static final Codec<PisoShopStock> CODEC =
			Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.INT)
					.xmap(PisoShopStock::new, PisoShopStock::getRemaining);

	public static final SavedDataType<PisoShopStock> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "shop_stock"), PisoShopStock::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<Integer, Integer> remaining;

	public PisoShopStock() {
		this(new HashMap<>());
	}

	private PisoShopStock(final Map<Integer, Integer> remaining) {
		this.remaining = remaining;
	}

	private Map<Integer, Integer> getRemaining() {
		return remaining;
	}

	public int remainingFor(final ShopEntry entry) {
		return remaining.getOrDefault(entry.id(), entry.stock());
	}

	// Returns false without changing anything if there isn't enough stock.
	public boolean take(final ShopEntry entry, final int quantity) {
		int have = remainingFor(entry);
		if (have < quantity) {
			return false;
		}
		remaining.put(entry.id(), have - quantity);
		setDirty();
		return true;
	}
}
