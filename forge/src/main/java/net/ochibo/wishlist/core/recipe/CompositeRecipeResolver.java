package net.ochibo.wishlist.core.recipe;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class CompositeRecipeResolver implements RecipeResolver {
    private final RecipeResolver primary;
    private final Supplier<Optional<RecipeResolver>> fallbackSupplier;

    public CompositeRecipeResolver(RecipeResolver primary, Supplier<Optional<RecipeResolver>> fallbackSupplier) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallbackSupplier = Objects.requireNonNull(fallbackSupplier, "fallbackSupplier");
    }

    @Override
    public Optional<ResolvedRecipe> findByKey(RecipeKey key) {
        Optional<ResolvedRecipe> primaryResult = primary.findByKey(key);
        if (primaryResult.filter(r -> !r.fallbackAllowed()).isPresent()) return primaryResult;

        Optional<RecipeResolver> fallback = fallbackSupplier.get();
        if (fallback.isEmpty()) return primaryResult;
        Optional<ResolvedRecipe> fallbackResult = fallback.orElseThrow().findByKey(key);
        if (fallbackResult.filter(ResolvedRecipe::supported).isPresent()) return fallbackResult;
        return primaryResult.isPresent() ? primaryResult : fallbackResult;
    }

    @Override
    public Optional<ResolvedRecipe> findByKey(RecipeKey key, String targetItemId) {
        Optional<ResolvedRecipe> primaryResult = primary.findByKey(key, targetItemId);
        if (primaryResult.filter(r -> !r.fallbackAllowed()).isPresent()) return primaryResult;

        Optional<RecipeResolver> fallback = fallbackSupplier.get();
        if (fallback.isEmpty()) return primaryResult;
        Optional<ResolvedRecipe> fallbackResult = fallback.orElseThrow().findByKey(key, targetItemId);
        if (fallbackResult.filter(ResolvedRecipe::supported).isPresent()) return fallbackResult;
        return primaryResult.isPresent() ? primaryResult : fallbackResult;
    }

    @Override
    public List<ResolvedRecipe> findForOutput(String itemId) {
        Map<RecipeKey, ResolvedRecipe> merged = new LinkedHashMap<>();
        for (ResolvedRecipe recipe : primary.findForOutput(itemId)) merged.put(recipe.key(), recipe);

        Optional<RecipeResolver> fallback = fallbackSupplier.get();
        if (fallback.isPresent()) {
            for (ResolvedRecipe recipe : fallback.orElseThrow().findForOutput(itemId)) {
                ResolvedRecipe existing = merged.get(recipe.key());
                if (existing == null || (existing.fallbackAllowed() && !recipe.fallbackAllowed())) {
                    merged.put(recipe.key(), recipe);
                }
            }
        }
        return List.copyOf(new ArrayList<>(merged.values()));
    }
}
