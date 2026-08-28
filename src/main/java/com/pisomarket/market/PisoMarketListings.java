package com.pisomarket.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// Active market listings, server-wide (same reasoning as PisoVault — a
// listing isn't tied to which dimension it was posted from).
public class PisoMarketListings extends SavedData {
	private record State(int nextId, List<MarketListing> listings) {
		private static final Codec<State> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("nextId").forGetter(State::nextId),
						MarketListing.CODEC.listOf().fieldOf("listings").forGetter(State::listings)
				).apply(instance, State::new)
		);
	}

	public static final Codec<PisoMarketListings> CODEC = State.CODEC.xmap(PisoMarketListings::new, PisoMarketListings::toState);

	public static final SavedDataType<PisoMarketListings> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "market"), PisoMarketListings::new, CODEC, DataFixTypes.LEVEL
	);

	private int nextId;
	private final Map<Integer, MarketListing> listings;

	public PisoMarketListings() {
		this(new State(1, List.of()));
	}

	private PisoMarketListings(final State state) {
		this.nextId = state.nextId();
		this.listings = new LinkedHashMap<>();
		for (MarketListing listing : state.listings()) {
			listings.put(listing.id(), listing);
		}
	}

	private State toState() {
		return new State(nextId, List.copyOf(listings.values()));
	}

	// Posts a stack for sale and returns the new listing's id.
	public int list(final UUID seller, final ItemStack stack, final long price) {
		int id = nextId++;
		listings.put(id, new MarketListing(id, seller, stack, price));
		setDirty();
		return id;
	}

	public MarketListing get(final int id) {
		return listings.get(id);
	}

	// Removes and returns a listing, or null if it doesn't exist. Callers
	// are responsible for the actual money/item movement — this only
	// touches storage.
	public MarketListing remove(final int id) {
		MarketListing removed = listings.remove(id);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	// Read-only VIEW in id order, not a sorted copy.
	//
	// This used to copy every listing into a new list and sort it on each
	// call, which happens on every /market browse AND every page click in
	// the shop GUI — to then display 18 of them. Since listings are never
	// expired (a deliberate design decision) that cost grows forever.
	//
	// The sort was redundant: ids come from nextId++ and are never reused,
	// and a LinkedHashMap preserves insertion order, so the map's own
	// iteration order is already ascending id.
	public List<MarketListing> all() {
		return List.copyOf(listings.values());
	}

	public List<MarketListing> bySeller(final UUID seller) {
		return listings.values().stream().filter(listing -> listing.seller().equals(seller)).collect(Collectors.toList());
	}
}
