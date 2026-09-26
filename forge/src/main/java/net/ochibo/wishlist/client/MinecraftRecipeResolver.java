package net.ochibo.wishlist.client;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.internal.recipe.ClientRecipeExpansion;
import net.ochibo.wishlist.internal.recipe.ExpansionGraphAdapter;
import net.ochibo.wishlist.internal.recipe.NativeRecipeInterpreter;
import net.ochibo.wishlist.internal.recipe.RecipeExpansionEngine;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionRequest;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.ochibo.wishlist.core.utils.StackResolver.stack;

/**
 * Recipe-manager resolver using typed semantic handlers first and exact vanilla
 * interpretation second. JEI remains a separate composite fallback.
 */
public final class MinecraftRecipeResolver implements RecipeResolver {
    private final RecipeManager manager;
    private final RegistryAccess registryAccess;
    private final RecipeExpansionEngine semanticEngine;
    private final NativeRecipeInterpreter nativeInterpreter = new NativeRecipeInterpreter();

    public MinecraftRecipeResolver(RecipeManager manager, RegistryAccess registryAccess) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.registryAccess = Objects.requireNonNull(registryAccess, "registryAccess");
        this.semanticEngine = ClientRecipeExpansion.newEngine();
    }

    @Override
    public Optional<ResolvedRecipe> findByKey(RecipeKey key) {
        ResourceLocation id = ResourceLocation.tryParse(key.id());
        if (id == null) return Optional.empty();
        return manager.byKey(id).map(this::convert);
    }

    @Override
    public Optional<ResolvedRecipe> findByKey(RecipeKey key, String targetItemId) {
        ResourceLocation id = ResourceLocation.tryParse(key.id());
        if (id == null) return Optional.empty();
        ItemStack target = stack(targetItemId);
        if (target.isEmpty()) return findByKey(key);
        return manager.byKey(id).map(recipe -> convert(recipe, target));
    }

    @Override
    public List<ResolvedRecipe> findForOutput(String itemId) {
        ItemStack target = stack(itemId);
        if (target.isEmpty()) return List.of();
        List<ResolvedRecipe> result = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            RecipeExpansionRequest<?> request = request(recipe, target);
            RecipeExpansionDecision semantic = semanticEngine.expand(request);
            if (semantic.kind() != RecipeExpansionDecision.Kind.DECLINE) {
                ResolvedRecipe converted = ExpansionGraphAdapter.adapt(
                        new RecipeKey(recipe.getId().toString()), target, semantic);
                if (ResourceIdentity.matches(itemId, converted.outputItemId())) result.add(converted);
                continue;
            }

            // Do not invoke exact-native semantics for every unrelated recipe.
            // This prefilter prevents a wrong-target semantic rejection from
            // becoming a false candidate for the requested output.
            ItemStack nativeOutput = recipe.getResultItem(registryAccess);
            if (!matchesItemTarget(target, nativeOutput)
                    && !(recipe.getClass() == SmithingTransformRecipe.class
                    && !nativeOutput.isEmpty() && target.getItem() == nativeOutput.getItem())
                    && !(recipe.getClass() == SmithingTrimRecipe.class
                    && ((SmithingTrimRecipe) recipe).isBaseIngredient(target))) continue;
            RecipeExpansionDecision nativeDecision = nativeInterpreter.expand(request);
            if (nativeDecision.kind() == RecipeExpansionDecision.Kind.DECLINE) continue;
            ResolvedRecipe converted = ExpansionGraphAdapter.adapt(
                    new RecipeKey(recipe.getId().toString()), target, nativeDecision);
            if (ResourceIdentity.matches(itemId, converted.outputItemId())) result.add(converted);
        }
        return List.copyOf(result);
    }

    public ResolvedRecipe convert(Recipe<?> recipe) {
        Objects.requireNonNull(recipe, "recipe");
        ItemStack target = recipe.getResultItem(registryAccess);
        if (target.isEmpty()) {
            RecipeKey key = new RecipeKey(recipe.getId().toString());
            return ResolvedRecipe.unsupported(key, "minecraft:air", 1,
                    "Recipe has no stable primary item output");
        }
        return convert(recipe, target);
    }

    private ResolvedRecipe convert(Recipe<?> recipe, ItemStack target) {
        RecipeKey key = new RecipeKey(recipe.getId().toString());
        RecipeExpansionRequest<?> request = request(recipe, target);
        RecipeExpansionDecision decision = semanticEngine.expand(request);
        if (decision.kind() == RecipeExpansionDecision.Kind.DECLINE) {
            decision = nativeInterpreter.expand(request);
        }
        return ExpansionGraphAdapter.adapt(key, target, decision);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RecipeExpansionRequest<?> request(Recipe<?> recipe, ItemStack target) {
        // Generic Recipe container type is irrelevant to interpretation; the
        // typed handler table re-casts only after checking the registered class.
        return RecipeExpansionRequest.of((Recipe) recipe, recipe.getId(), target, registryAccess);
    }

    private static boolean matchesItemTarget(ItemStack requested, ItemStack output) {
        if (requested.isEmpty() || output.isEmpty() || requested.getItem() != output.getItem()) return false;
        return !requested.hasTag() || ItemStack.isSameItemSameTags(requested, output);
    }
}
