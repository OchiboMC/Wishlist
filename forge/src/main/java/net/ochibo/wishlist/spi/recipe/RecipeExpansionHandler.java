package net.ochibo.wishlist.spi.recipe;

import net.minecraft.world.item.crafting.Recipe;

/**
 * Interprets a known recipe class synchronously on the logical client thread.
 * Return a nonnull decision and do not retain request, recipe, registry or world
 * references. Runtime recipe snapshots become invalid on reload or disconnect.
 */
@FunctionalInterface
public interface RecipeExpansionHandler<R extends Recipe<?>> {
    RecipeExpansionDecision expand(RecipeExpansionRequest<R> request);
}
