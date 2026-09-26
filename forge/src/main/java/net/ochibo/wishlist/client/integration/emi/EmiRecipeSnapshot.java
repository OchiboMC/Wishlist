package net.ochibo.wishlist.client.integration.emi;

import net.ochibo.wishlist.core.model.RecipeKey;

import java.util.List;

record EmiRecipeSnapshot(RecipeKey key, List<EmiIngredientSnapshot> inputs,
                         List<EmiIngredientSnapshot> outputs, boolean hasCatalysts,
                         boolean supportsRecipeTree) {
    EmiRecipeSnapshot {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
