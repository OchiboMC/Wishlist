package net.ochibo.wishlist.client.integration.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.client.integration.MaterialAdapterRegistry;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.utils.StackResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads registered EMI recipes through EMI's public recipe and stack APIs. */
public final class EmiGenericRecipeResolver implements RecipeResolver {
    @Override
    public Optional<ResolvedRecipe> findByKey(RecipeKey key) {
        return findByKey(key, "");
    }

    @Override
    public Optional<ResolvedRecipe> findByKey(RecipeKey key, String targetItemId) {
        ResourceLocation id = ResourceLocation.tryParse(key.id());
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (id == null || manager == null) return Optional.empty();
        try {
            EmiRecipe recipe = manager.getRecipe(id);
            return recipe == null ? Optional.empty() : resolveRecipe(recipe, targetItemId);
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<ResolvedRecipe> findForOutput(String itemId) {
        ItemStack stack = StackResolver.stack(itemId);
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (stack.isEmpty() || manager == null) return List.of();
        List<ResolvedRecipe> found = new ArrayList<>();
        try {
            for (EmiRecipe recipe : manager.getRecipesByOutput(EmiStack.of(stack))) {
                resolveRecipe(recipe, itemId).ifPresent(found::add);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A broken third-party EMI recipe must not break the Wishlist tree.
        }
        return List.copyOf(found);
    }

    public Optional<ResolvedRecipe> resolveRecipe(EmiRecipe recipe, String targetItemId) {
        ResourceLocation id = recipe.getId();
        if (id == null) return Optional.empty(); // Unidentified recipes cannot be persisted.
        List<EmiIngredientSnapshot> inputs = new ArrayList<>();
        for (EmiIngredient input : recipe.getInputs()) {
            if (!input.isEmpty()) inputs.add(snapshot(input));
        }
        List<EmiIngredientSnapshot> outputs = recipe.getOutputs().stream()
                .map(this::snapshot).toList();
        EmiRecipeSnapshot snapshot = new EmiRecipeSnapshot(new RecipeKey(id.toString()),
                inputs, outputs, !recipe.getCatalysts().isEmpty(), recipe.supportsRecipeTree());
        return Optional.of(EmiGenericRecipeInterpreter.resolve(snapshot, targetItemId));
    }

    private EmiIngredientSnapshot snapshot(EmiIngredient ingredient) {
        List<String> candidates = new ArrayList<>();
        boolean itemOnly = true;
        boolean hasNbt = false;
        boolean certain = ingredient.getChance() == 1.0f;
        boolean hasRemainder = false;
        long amount = ingredient.getAmount();
        var alternatives = ingredient.getEmiStacks();
        for (EmiStack alternative : alternatives) {
            var adapted = MaterialAdapterRegistry.read(alternative);
            if (adapted.isPresent()) {
                var material = adapted.orElseThrow();
                candidates.add(material.key());
                if (alternatives.size() == 1) amount = material.amount();
                else if (material.amount() != amount) itemOnly = false;
                certain &= alternative.getChance() == 1.0f;
                hasRemainder |= !alternative.getRemainder().isEmpty();
                continue;
            }
            ItemStack stack = alternative.getItemStack();
            if (!stack.isEmpty()) candidates.add(StackResolver.id(stack));
            else if (alternative.getKey() instanceof Fluid && alternative.getId() != null)
                candidates.add(ResourceIdentity.fluid(alternative.getId().toString(),
                        alternative.hasNbt() ? alternative.getNbt().toString() : null));
            else itemOnly = false;
            hasNbt |= alternative.hasNbt();
            certain &= alternative.getChance() == 1.0f;
            hasRemainder |= !alternative.getRemainder().isEmpty();
        }
        return new EmiIngredientSnapshot(candidates.stream().distinct().sorted().toList(),
                amount, itemOnly, hasNbt, certain, hasRemainder);
    }
}
