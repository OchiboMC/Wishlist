package net.ochibo.wishlist.client.integration.jei;

import java.util.Objects;

record JeiStackCandidate(String itemId, long amount, boolean hasNbt) {
    JeiStackCandidate {
        Objects.requireNonNull(itemId, "itemId");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
