package com.pisomarket.claims;

import java.util.List;

// Fixed deed sizes/prices — placeholders, explicitly "tune after the
// systems run" per CLAUDE.md, same as everything else pricing-related.
public final class DeedCatalog {
	public record DeedSize(int id, String label, int width, int length, int height, long price) {
	}

	public static final List<DeedSize> SIZES = List.of(
			new DeedSize(1, "Small", 20, 20, 10, 200),
			new DeedSize(2, "Medium", 40, 40, 15, 500),
			new DeedSize(3, "Large", 60, 60, 20, 1000)
	);

	private DeedCatalog() {
	}

	public static DeedSize byId(final int id) {
		for (DeedSize size : SIZES) {
			if (size.id() == id) {
				return size;
			}
		}
		return null;
	}
}
