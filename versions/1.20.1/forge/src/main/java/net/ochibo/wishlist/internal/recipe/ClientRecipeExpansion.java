package net.ochibo.wishlist.internal.recipe;

import net.ochibo.wishlist.integration.create.CreateRecipeHandlers;
import net.ochibo.wishlist.integration.forge.RegisterRecipeExpansionHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

/** Client lifecycle owner; never exposed as an extension service. */
public final class ClientRecipeExpansion {
    private static HandlerTable handlers;
    private ClientRecipeExpansion() {}

    public static void registerHandlers() {
        if (handlers != null) throw new IllegalStateException("Recipe handlers already registered");
        var event = new RegisterRecipeExpansionHandlersEvent();
        if (ModList.get().isLoaded("create")) CreateRecipeHandlers.register(event);
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } finally {
            handlers = event.freeze();
        }
    }

    public static RecipeExpansionEngine newEngine() {
        if (handlers == null) throw new IllegalStateException("Recipe handler registration has not completed");
        return new RecipeExpansionEngine(handlers);
    }
}
