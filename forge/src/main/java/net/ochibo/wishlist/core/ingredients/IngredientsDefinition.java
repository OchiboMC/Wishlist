package net.ochibo.wishlist.core.ingredients;

import java.util.List;
import java.util.Objects;

public record IngredientsDefinition(
        String namespace,
        int resourceOrder,
        boolean replace,
        List<String> values,
        List<String> remove) {
    public IngredientsDefinition {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(remove, "remove");
        if (namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        values = List.copyOf(values);
        remove = List.copyOf(remove);
    }
}
