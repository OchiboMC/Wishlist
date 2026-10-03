package net.ochibo.wishlist.internal.recipe;

import net.fabricmc.loader.api.FabricLoader;
import net.ochibo.wishlist.integration.create.CreateRecipeHandlers;
import net.ochibo.wishlist.integration.fabric.RecipeExpansionHandlerRegistration;

/** Initializes Fabric recipe expansion handlers during client setup. */
public final class ClientRecipeExpansion {
    private static HandlerTable handlers;

    private ClientRecipeExpansion() {}

    public static void registerHandlers() {
        if (handlers != null) return;
        HandlerTable.Builder builder = new HandlerTable.Builder();
        if (FabricLoader.getInstance().isModLoaded("create")) {
            CreateRecipeHandlers.register(builder);
        }
        for (RecipeExpansionHandlerRegistration registration : FabricLoader.getInstance()
                .getEntrypoints("wishlist:recipe_expansion", RecipeExpansionHandlerRegistration.class)) {
            registration.register(builder);
        }
        handlers = builder.freeze();
    }

    public static RecipeExpansionEngine newEngine() {
        if (handlers == null) registerHandlers();
        return new RecipeExpansionEngine(handlers);
    }
}
