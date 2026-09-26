package net.ochibo.wishlist.core.model;

import java.util.Objects;

public record RecipeKey(String id) {
    public RecipeKey {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("recipe id must not be blank");
    }
}
