package net.ochibo.wishlist.core.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;

/** Client-thread cache shared across calculations and screens; provider changes invalidate misses too. */
public final class CachingRecipeResolver implements RecipeResolver {
    private record Lookup(RecipeKey key, String target) {}
    private final RecipeResolver delegate;
    private final Supplier<?> generation;
    private Object stamp;
    private final Map<Lookup, Optional<ResolvedRecipe>> recipes = new HashMap<>();
    private final Map<String, List<ResolvedRecipe>> outputs = new HashMap<>();
    private final Map<String, Boolean> availability = new HashMap<>();

    public CachingRecipeResolver(RecipeResolver delegate, Supplier<?> generation) {
        this.delegate = Objects.requireNonNull(delegate);
        this.generation = Objects.requireNonNull(generation);
    }

    private void validate() {
        Object current = generation.get();
        if (!Objects.equals(current, stamp)) {
            recipes.clear(); outputs.clear(); availability.clear(); stamp = current;
        }
    }

    @Override public Optional<ResolvedRecipe> findByKey(RecipeKey key) {
        validate();
        return recipes.computeIfAbsent(new Lookup(key, null), ignored -> delegate.findByKey(key));
    }

    @Override public Optional<ResolvedRecipe> findByKey(RecipeKey key, String target) {
        if (target == null || target.isBlank()) return findByKey(key);
        validate();
        return recipes.computeIfAbsent(new Lookup(key, target), ignored -> delegate.findByKey(key, target));
    }

    @Override public List<ResolvedRecipe> findForOutput(String itemId) {
        validate();
        List<ResolvedRecipe> result = outputs.computeIfAbsent(itemId, id -> List.copyOf(delegate.findForOutput(id)));
        availability.put(itemId, !result.isEmpty());
        return result;
    }

    @Override public boolean hasRecipeForOutput(String itemId) {
        validate();
        return availability.computeIfAbsent(itemId, delegate::hasRecipeForOutput);
    }

    @Override public boolean isAuthoritativeForOutput(String itemId) { return delegate.isAuthoritativeForOutput(itemId); }
}
