package net.ochibo.wishlist.core.recipe;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;

import java.util.List;
import java.util.Optional;

public interface RecipeResolver {
    Optional<ResolvedRecipe> findByKey(RecipeKey key);

    /**
     * Resolve a recipe for a known output item. Implementations that support
     * multi-output recipes may use the target to select the correct output.
     */
    default Optional<ResolvedRecipe> findByKey(RecipeKey key, String targetItemId) {
        return findByKey(key);
    }

    List<ResolvedRecipe> findForOutput(String itemId);

    /** This source owns the complete interpretation, including terminal outputs. */
    default boolean isAuthoritativeForOutput(String itemId) { return false; }

    default boolean hasRecipeForOutput(String itemId) {
        return !findForOutput(itemId).isEmpty();
    }
}
