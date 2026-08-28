package com.pisomarket.claims;

// Claim-wide chest policy, set from the Land Deed book. Replaces the old
// per-chest Lock item: every chest inside the claim follows this one
// setting, so there's nothing to place, carry, or lose track of.
//
// The claim OWNER always has full access regardless — the point is
// controlling what other people can do, never locking yourself out.
public enum ChestAccess {
	// Nobody but the owner. Trusted build access does not grant chest access.
	OWNER_ONLY,
	// Trusted players may insert items but never take any out.
	TRUSTED_PUT_ONLY,
	// Trusted players get normal, full chest access.
	TRUSTED_PUT_AND_GET,
	// Anyone at all, trusted or not, gets normal chest access.
	OPEN;

	public ChestAccess next() {
		return switch (this) {
			case OWNER_ONLY -> TRUSTED_PUT_ONLY;
			case TRUSTED_PUT_ONLY -> TRUSTED_PUT_AND_GET;
			case TRUSTED_PUT_AND_GET -> OPEN;
			case OPEN -> OWNER_ONLY;
		};
	}

	public String label() {
		return switch (this) {
			case OWNER_ONLY -> "Only me";
			case TRUSTED_PUT_ONLY -> "Trusted: put only";
			case TRUSTED_PUT_AND_GET -> "Trusted: put and get";
			case OPEN -> "Open to everyone";
		};
	}
}
