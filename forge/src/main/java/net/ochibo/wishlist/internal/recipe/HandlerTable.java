package net.ochibo.wishlist.internal.recipe;

import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionHandler;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionRequest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable runtime registrations; never stores recipes or requests. */
public final class HandlerTable {
    private final List<Entry<?>> entries;

    private HandlerTable(List<Entry<?>> entries) {
        this.entries = entries.stream().sorted(Comparator.comparing(entry -> entry.id().toString())).toList();
    }

    List<Entry<?>> entries() { return entries; }

    record Entry<R extends Recipe<?>>(ResourceLocation id, Class<R> recipeClass, RecipeExpansionHandler<R> handler) {
        RecipeExpansionDecision expand(RecipeExpansionRequest<?> request) {
            return handler.expand(RecipeExpansionRequest.of(recipeClass.cast(request.recipe()), request.recipeId(),
                    request.targetOutput(), request.registryAccess()));
        }
    }

    /** Construction is limited to the client registration lifecycle. */
    public static final class Builder {
        private final List<Entry<?>> entries = new ArrayList<>();
        private final Set<ResourceLocation> ids = new HashSet<>();
        private HandlerTable frozen;

        public <R extends Recipe<?>> void register(ResourceLocation id, Class<R> recipeClass, RecipeExpansionHandler<R> handler) {
            if (frozen != null) throw new IllegalStateException("Recipe expansion registration is frozen");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(recipeClass, "recipeClass");
            Objects.requireNonNull(handler, "handler");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate recipe expansion handler: " + id);
            entries.add(new Entry<>(id, recipeClass, handler));
        }

        public HandlerTable freeze() {
            if (frozen == null) frozen = new HandlerTable(entries);
            return frozen;
        }
    }
}
