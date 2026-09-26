package net.ochibo.wishlist.integration.create;

import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.api.recipe.OutputOption;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionHandler;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionRequest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Exact Create 6.0.x semantics for Mechanical Crafting ingredient consumption. */
public final class MechanicalCraftingHandler implements RecipeExpansionHandler<MechanicalCraftingRecipe> {
    @Override
    public RecipeExpansionDecision expand(RecipeExpansionRequest<MechanicalCraftingRecipe> request) {
        MechanicalCraftingRecipe recipe = request.recipe();
        // Do not silently inherit Create semantics into addon subclasses that may
        // override crafting behavior. A more specific external handler may own them.
        if (recipe.getClass() != MechanicalCraftingRecipe.class) return RecipeExpansionDecision.decline();

        ItemStack output = recipe.getResultItem(request.registryAccess());
        if (output.isEmpty() || !CreateExpansionSupport.sameRequestedItem(request.targetOutput(), output)) {
            return RecipeExpansionDecision.decline();
        }

        ExpansionPlan.Builder plan = ExpansionPlan.builder(output, output.getCount())
                .addOutput(OutputOption.of(output, output.getCount(), 1));
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            var alternatives = CreateExpansionSupport.candidates(ingredient);
            if (alternatives.isEmpty()) {
                return CreateExpansionSupport.blocked("create_mechanical_custom_ingredient",
                        "Mechanical Crafting contains an ingredient that cannot be represented losslessly");
            }
            if (alternatives.stream().anyMatch(ItemStack::hasCraftingRemainingItem)) {
                return CreateExpansionSupport.blocked("create_mechanical_remainder",
                        "Mechanical Crafting ingredient has a crafting remainder");
            }
            if (!CreateExpansionSupport.addConsumed(plan, ingredient, 1)) {
                return CreateExpansionSupport.blocked("create_mechanical_ingredient",
                        "Mechanical Crafting ingredient has no usable alternatives");
            }
        }
        return RecipeExpansionDecision.resolved(plan.build());
    }
}
