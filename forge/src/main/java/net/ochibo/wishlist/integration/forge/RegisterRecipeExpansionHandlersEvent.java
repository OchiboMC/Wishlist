package net.ochibo.wishlist.integration.forge;

import net.ochibo.wishlist.internal.recipe.HandlerTable;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.eventbus.api.Event;

/**
 * One-shot, non-cancellable client setup event posted on MinecraftForge.EVENT_BUS.
 * Register handlers synchronously during delivery. Retaining this event does not permit later registration.
 */
public final class RegisterRecipeExpansionHandlersEvent extends Event {
    private final HandlerTable.Builder registrations = new HandlerTable.Builder();

    /** Registers one typed semantic handler. IDs must be unique across all mods. */
    public <R extends Recipe<?>> void register(ResourceLocation id, Class<R> recipeClass,
                                               RecipeExpansionHandler<R> handler) {
        registrations.register(id, recipeClass, handler);
    }

    /** Internal lifecycle operation, excluded from the supported external registration API. */
    public HandlerTable freeze() {
        return registrations.freeze();
    }
}
