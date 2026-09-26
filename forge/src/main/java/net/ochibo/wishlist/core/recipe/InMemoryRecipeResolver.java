package net.ochibo.wishlist.core.recipe;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryRecipeResolver implements RecipeResolver {
    private final Map<RecipeKey, ResolvedRecipe> recipes = new LinkedHashMap<>();

    public void add(ResolvedRecipe recipe) { recipes.put(recipe.key(), recipe); }
    @Override public Optional<ResolvedRecipe> findByKey(RecipeKey key) { return Optional.ofNullable(recipes.get(key)); }
    @Override public List<ResolvedRecipe> findForOutput(String itemId) {
        List<ResolvedRecipe> out = new ArrayList<>();
        for (ResolvedRecipe recipe : recipes.values()) if (ResourceIdentity.matches(itemId, recipe.outputItemId())) out.add(recipe);
        return List.copyOf(out);
    }
}
