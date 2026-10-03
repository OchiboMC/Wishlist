package net.ochibo.wishlist.client.integration.jei;

import net.ochibo.wishlist.core.model.RecipeKey;

import java.util.List;
import java.util.Objects;

/** Internal JEI-only normalized view. Never part of the public recipe API. */
record JeiRecipeSnapshot(RecipeKey key, List<JeiRecipeSlot> slots, boolean hasUnmodeledInvisibleIngredients) {
    JeiRecipeSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(slots, "slots");
        slots = List.copyOf(slots);
    }
}
