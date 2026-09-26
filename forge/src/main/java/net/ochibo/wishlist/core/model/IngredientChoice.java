package net.ochibo.wishlist.core.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record IngredientChoice(String displayKey, List<String> candidates, long count) {
    public IngredientChoice(String displayKey, List<String> candidates, long count) {
        this.displayKey = Objects.requireNonNull(displayKey, "displayKey");
        if (displayKey.isBlank()) throw new IllegalArgumentException("displayKey must not be blank");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        Set<String> unique = new LinkedHashSet<>(Objects.requireNonNull(candidates, "candidates"));
        unique.removeIf(s -> s == null || s.isBlank());
        if (unique.isEmpty()) throw new IllegalArgumentException("ingredient must have candidates");
        this.candidates = List.copyOf(new ArrayList<>(unique));
        this.count = count;
    }

    public static IngredientChoice exact(String itemId, long count) {
        return new IngredientChoice(itemId, List.of(itemId), count);
    }

    public boolean isExact() {
        return candidates.size() == 1;
    }

    public IngredientChoice withCount(long newCount) {
        return new IngredientChoice(displayKey, candidates, newCount);
    }

    public String signature() {
        return displayKey + "|" + String.join(";", candidates);
    }

    @Override
    public String toString() {
        return displayKey + " x" + count;
    }
}
