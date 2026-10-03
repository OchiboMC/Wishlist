package net.ochibo.wishlist.integration.fabric;

import net.ochibo.wishlist.internal.recipe.HandlerTable;

/** Optional Fabric entrypoint `wishlist:recipe_expansion` for typed recipe handlers. */
@FunctionalInterface
public interface RecipeExpansionHandlerRegistration {
    void register(HandlerTable.Builder handlers);
}
