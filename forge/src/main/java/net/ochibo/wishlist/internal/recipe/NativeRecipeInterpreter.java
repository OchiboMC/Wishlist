package net.ochibo.wishlist.internal.recipe;

import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.api.recipe.ExpansionProblem;
import net.ochibo.wishlist.api.recipe.ItemRequirement;
import net.ochibo.wishlist.api.recipe.OutputOption;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionRequest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.SimpleContainer;
import io.netty.buffer.Unpooled;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Only exact vanilla classes carry the vanilla consumption contract. */
public final class NativeRecipeInterpreter {
    private static final Set<Class<?>> EXACT = Set.of(ShapedRecipe.class, ShapelessRecipe.class,
            SmeltingRecipe.class, BlastingRecipe.class, SmokingRecipe.class,
            CampfireCookingRecipe.class, StonecutterRecipe.class,
            SmithingTransformRecipe.class, SmithingTrimRecipe.class);

    public RecipeExpansionDecision expand(RecipeExpansionRequest<?> request) {
        Recipe<?> recipe = request.recipe();
        if (!EXACT.contains(recipe.getClass())) return RecipeExpansionDecision.decline();
        if (recipe.isSpecial()) return blocked("Special recipe semantics require a handler");
        if (recipe instanceof SmithingTrimRecipe trim) return expandTrim(request, trim);
        ItemStack output = recipe.getResultItem(request.registryAccess());
        ItemStack requested = request.targetOutput();
        boolean smithingInheritedTag = recipe instanceof SmithingTransformRecipe
                && !requested.isEmpty() && requested.hasTag() && !output.isEmpty()
                && requested.getItem() == output.getItem();
        if (output.isEmpty() || (!smithingInheritedTag && !matchesRequestedTarget(requested, output))) {
            return blocked("Recipe does not produce the requested target");
        }
        if (smithingInheritedTag) output = requested.copy();
        var plan = ExpansionPlan.builder(output, output.getCount())
                .addOutput(OutputOption.of(output, output.getCount(), 1));
        List<Ingredient> recipeIngredients;
        try {
            recipeIngredients = recipe instanceof SmithingTransformRecipe smithing
                    ? smithingIngredients(smithing) : recipe.getIngredients();
        } catch (RuntimeException error) {
            return blocked("Smithing ingredients could not be read");
        }
        for (int index = 0; index < recipeIngredients.size(); index++) {
            Ingredient ingredient = recipeIngredients.get(index);
            final int slotIndex = index;
            if (ingredient.isEmpty()) continue;
            // Custom predicates may encode conditions beyond their display candidates.
            if (ingredient.getClass() != Ingredient.class) return blocked("Custom ingredient requires a semantic handler");
            var candidates = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                    .map(stack -> {
                        if (!smithingInheritedTag || slotIndex != 1) return stack;
                        ItemStack inherited = stack.copy();
                        inherited.setTag(requested.getTag().copy());
                        return inherited;
                    }).toList();
            if (candidates.isEmpty()) return blocked("Ingredient has no usable alternatives");
            if (candidates.stream().anyMatch(ItemStack::hasCraftingRemainingItem)) {
                return blocked("Crafting remainder consumption requires a semantic handler");
            }
            plan.addItem(ItemRequirement.of(candidates, 1));
        }
        return RecipeExpansionDecision.resolved(plan.build());
    }

    private static List<Ingredient> smithingIngredients(SmithingTransformRecipe recipe) {
        // Vanilla's smithing transform stores these three fields but does not expose
        // them through Recipe#getIngredients. Its network codec exposes their order.
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeSerializer.SMITHING_TRANSFORM.toNetwork(buffer, recipe);
            return List.of(Ingredient.fromNetwork(buffer), Ingredient.fromNetwork(buffer),
                    Ingredient.fromNetwork(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RecipeExpansionDecision expandTrim(RecipeExpansionRequest<?> request, SmithingTrimRecipe recipe) {
        ItemStack requested = request.targetOutput();
        if (requested.isEmpty() || !recipe.isBaseIngredient(requested))
            return blocked("Smithing trim cannot produce the requested target");
        List<Ingredient> ingredients;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeSerializer.SMITHING_TRIM.toNetwork(buffer, recipe);
            ingredients = List.of(Ingredient.fromNetwork(buffer), Ingredient.fromNetwork(buffer),
                    Ingredient.fromNetwork(buffer));
        } catch (RuntimeException error) {
            return blocked("Smithing trim ingredients could not be read");
        } finally {
            buffer.release();
        }
        if (ingredients.stream().anyMatch(ingredient -> ingredient.getClass() != Ingredient.class))
            return blocked("Custom smithing trim ingredient requires a semantic handler");
        List<ItemStack> templates = usable(ingredients.get(0));
        List<ItemStack> bases = usable(ingredients.get(1));
        List<ItemStack> additions = usable(ingredients.get(2));
        for (ItemStack template : templates) {
            for (ItemStack originalBase : bases) {
                if (originalBase.getItem() != requested.getItem()) continue;
                ItemStack base = originalBase.copy();
                if (requested.hasTag()) {
                    var originalTag = requested.getTag().copy();
                    originalTag.remove("Trim");
                    base.setTag(originalTag.isEmpty() ? null : originalTag);
                }
                for (ItemStack addition : additions) {
                    SimpleContainer container = new SimpleContainer(template, base, addition);
                    ItemStack produced = recipe.assemble(container, request.registryAccess());
                    if (produced.isEmpty() || !matchesRequestedTarget(requested, produced)) continue;
                    if (template.hasCraftingRemainingItem() || base.hasCraftingRemainingItem()
                            || addition.hasCraftingRemainingItem())
                        return blocked("Crafting remainder consumption requires a semantic handler");
                    var plan = ExpansionPlan.builder(requested, 1)
                            .addOutput(OutputOption.of(requested, 1, 1));
                    plan.addItem(ItemRequirement.of(List.of(template), 1));
                    plan.addItem(ItemRequirement.of(List.of(base), 1));
                    plan.addItem(ItemRequirement.of(List.of(addition), 1));
                    return RecipeExpansionDecision.resolved(plan.build());
                }
            }
        }
        return blocked("No smithing trim combination produces the requested target");
    }

    private static List<ItemStack> usable(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty()).toList();
    }

    private static boolean matchesRequestedTarget(ItemStack requested, ItemStack actual) {
        if (requested.isEmpty() || actual.isEmpty() || requested.getItem() != actual.getItem()) return false;
        return !requested.hasTag() || ItemStack.isSameItemSameTags(requested, actual);
    }

    private static RecipeExpansionDecision blocked(String message) {
        return RecipeExpansionDecision.blocked(ExpansionProblem.of(
                ResourceLocation.fromNamespaceAndPath("wishlist", "unsupported_native_semantics"), message));
    }
}
