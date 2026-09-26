package net.ochibo.wishlist.spi.recipe;

import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * One synchronous interpretation invocation on the logical client thread.
 * Recipe and registry access are borrowed read-only and must not be retained
 * beyond the invocation. Target stacks are defensively copied on both boundaries.
 */
public final class RecipeExpansionRequest<R extends Recipe<?>> {
    private final R recipe;
    private final ResourceLocation recipeId;
    private final ItemStack targetOutput;
    private final RegistryAccess registryAccess;
    private RecipeExpansionRequest(R recipe, ResourceLocation recipeId, ItemStack targetOutput,
                                   RegistryAccess registryAccess) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(targetOutput, "targetOutput");
        if (targetOutput.isEmpty()) throw new IllegalArgumentException("targetOutput must not be empty");
        this.targetOutput = targetOutput.copy();
        this.registryAccess = Objects.requireNonNull(registryAccess, "registryAccess");
    }
    public static <R extends Recipe<?>> RecipeExpansionRequest<R> of(
            R recipe, ResourceLocation recipeId, ItemStack targetOutput, RegistryAccess registryAccess) {
        return new RecipeExpansionRequest<>(recipe, recipeId, targetOutput, registryAccess);
    }
    public R recipe() { return recipe; }
    public ResourceLocation recipeId() { return recipeId; }
    public ItemStack targetOutput() { return targetOutput.copy(); }
    public RegistryAccess registryAccess() { return registryAccess; }
}
