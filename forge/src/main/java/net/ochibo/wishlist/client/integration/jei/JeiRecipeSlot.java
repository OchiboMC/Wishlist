package net.ochibo.wishlist.client.integration.jei;

import java.util.List;
import java.util.Objects;

record JeiRecipeSlot(
        JeiIngredientRole role,
        String displayKey,
        List<JeiStackCandidate> candidates,
        int allIngredientCount,
        int itemIngredientCount,
        int blankIngredientCount) {
    JeiRecipeSlot {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(displayKey, "displayKey");
        Objects.requireNonNull(candidates, "candidates");
        if (itemIngredientCount < 0 || itemIngredientCount > allIngredientCount) {
            throw new IllegalArgumentException("invalid ingredient counts");
        }
        if (blankIngredientCount < 0) {
            throw new IllegalArgumentException("blankIngredientCount must not be negative");
        }
        candidates = List.copyOf(candidates);
    }

    boolean itemOnly() {
        return allIngredientCount == itemIngredientCount;
    }
}
