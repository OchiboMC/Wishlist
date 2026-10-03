package net.ochibo.wishlist.client.integration.emi;

import java.util.List;

record EmiIngredientSnapshot(List<String> candidates, long amount, boolean itemOnly,
                             boolean hasNbt, boolean certain, boolean hasRemainder) {
    EmiIngredientSnapshot {
        candidates = List.copyOf(candidates);
    }
}
