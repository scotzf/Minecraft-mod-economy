package com.pisomarket.shop.system;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.pisomarket.PisoMarket;

// Remaining stock per ShopEntry.id, plus the in-game day each entry was
// last refilled. Each entry restocks on its own schedule (ShopEntry
// .restockDays) — cheap tools daily, an elytra once every 300 in-game days.
//
// Restocking is lazy: nothing is scheduled or ticked. Any read checks
// whether enough days have passed and tops the entry back up first. That
// survives restarts and long periods with nobody online, with no
// background work at all.
public class PisoShopStock extends SavedData {
	// Vanilla day length in ticks — the same divisor vanilla's own day
	// counter uses.
	private static final long TICKS_PER_DAY = 24000L;

	private record Slot(int remaining, int lastRestockDay) {
		private static final Codec<Slot> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("remaining").forGetter(Slot::remaining),
						Codec.INT.fieldOf("lastRestockDay").forGetter(Slot::lastRestockDay)
				).apply(instance, Slot::new)
		);
	}

	public static final Codec<PisoShopStock> CODEC =
			Codec.unboundedMap(Codec.STRING, Slot.CODEC).xmap(PisoShopStock::new, PisoShopStock::getSlots);

	public static final SavedDataType<PisoShopStock> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "shop_stock"), PisoShopStock::new, CODEC, DataFixTypes.LEVEL
	);

	private final Map<String, Slot> slots;

	public PisoShopStock() {
		this(new HashMap<>());
	}

	// Copy into a mutable map — Codec.unboundedMap decodes into an
	// ImmutableMap, so using it directly makes every write throw once the
	// data has been loaded from disk. See the same note on PisoVault.
	private PisoShopStock(final Map<String, Slot> slots) {
		this.slots = new HashMap<>(slots);
	}

	private Map<String, Slot> getSlots() {
		return slots;
	}

	public static int currentDay(final MinecraftServer server) {
		return (int) (server.overworld().getGameTime() / TICKS_PER_DAY);
	}

	// Applies any restock that's come due, then returns what's left.
	public int remainingFor(final MinecraftServer server, final ShopEntry entry) {
		int today = currentDay(server);
		Slot slot = slots.get(String.valueOf(entry.id()));

		if (slot == null) {
			slots.put(String.valueOf(entry.id()), new Slot(entry.stock(), today));
			setDirty();
			return entry.stock();
		}

		if (today - slot.lastRestockDay() >= entry.restockDays()) {
			slots.put(String.valueOf(entry.id()), new Slot(entry.stock(), today));
			setDirty();
			return entry.stock();
		}

		return slot.remaining();
	}

	// In-game days until this entry next refills; 0 if it's already full or
	// due. Shown in the shop so the wait is visible rather than a mystery.
	public int daysUntilRestock(final MinecraftServer server, final ShopEntry entry) {
		Slot slot = slots.get(String.valueOf(entry.id()));
		if (slot == null || slot.remaining() >= entry.stock()) {
			return 0;
		}
		int elapsed = currentDay(server) - slot.lastRestockDay();
		return Math.max(0, entry.restockDays() - elapsed);
	}

	// Returns false without changing anything if there isn't enough stock.
	// A negative quantity puts stock back (used to undo a failed purchase).
	public boolean take(final MinecraftServer server, final ShopEntry entry, final int quantity) {
		int have = remainingFor(server, entry);
		if (have < quantity) {
			return false;
		}
		Slot slot = slots.get(String.valueOf(entry.id()));
		int lastDay = slot == null ? currentDay(server) : slot.lastRestockDay();
		slots.put(String.valueOf(entry.id()), new Slot(have - quantity, lastDay));
		setDirty();
		return true;
	}
}
