package net.ochibo.wishlist.core.model;

import java.util.List;
import java.util.Objects;

public final class ResolvedRecipe {
    private final RecipeKey key;
    private final String outputItemId;
    private final long outputCount;
    private final List<IngredientChoice> ingredients;
    private final boolean supported;
    private final String unsupportedReason;
    private final boolean fallbackAllowed;

    private ResolvedRecipe(RecipeKey key, String outputItemId, long outputCount,
                           List<IngredientChoice> ingredients, boolean supported, String unsupportedReason, boolean fallbackAllowed) {
        this.key = Objects.requireNonNull(key, "key");
        this.outputItemId = Objects.requireNonNull(outputItemId, "outputItemId");
        if (outputItemId.isBlank()) throw new IllegalArgumentException("outputItemId must not be blank");
        if (outputCount <= 0) throw new IllegalArgumentException("outputCount must be positive");
        this.outputCount = outputCount;
        this.ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        this.supported = supported;
        this.unsupportedReason = unsupportedReason == null ? "" : unsupportedReason;
        this.fallbackAllowed = fallbackAllowed;
    }

    public static ResolvedRecipe supported(RecipeKey key, String outputItemId, long outputCount, List<IngredientChoice> ingredients) {
        return new ResolvedRecipe(key, outputItemId, outputCount, ingredients, true, "", false);
    }

    public static ResolvedRecipe unsupported(RecipeKey key, String outputItemId, long outputCount, String reason) {
        return new ResolvedRecipe(key, outputItemId, outputCount, List.of(), false, reason, true);
    }

    /** A semantic rejection; a viewer must not replace this result with an inferred recipe. */
    public static ResolvedRecipe blocked(RecipeKey key, String outputItemId, long outputCount, String reason) {
        return new ResolvedRecipe(key, outputItemId, outputCount, List.of(), false, reason, false);
    }

    public RecipeKey key() { return key; }
    public String outputItemId() { return outputItemId; }
    public long outputCount() { return outputCount; }
    public List<IngredientChoice> ingredients() { return ingredients; }
    public boolean supported() { return supported; }
    public String unsupportedReason() { return unsupportedReason; }
    public boolean fallbackAllowed() { return fallbackAllowed; }
}
