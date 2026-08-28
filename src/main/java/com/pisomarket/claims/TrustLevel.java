package com.pisomarket.claims;

// What a trusted (non-owner) player may do inside a claim. Separate from
// chest-lock access (see PisoChestLocks) — someone can be trusted to build
// without being able to touch the owner's chests.
public enum TrustLevel {
	PLACE,
	DESTROY,
	BOTH;

	public boolean allowsPlace() {
		return this == PLACE || this == BOTH;
	}

	public boolean allowsDestroy() {
		return this == DESTROY || this == BOTH;
	}
}
