package net.ochibo.wishlist.internal.recipe;

import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.recipe.RecipeIngredientAggregator;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

/** Projects consumed resources into the Wishlist graph. */
public final class ExpansionGraphAdapter {
    private ExpansionGraphAdapter() {}

    public static ResolvedRecipe adapt(RecipeKey key, ItemStack target, RecipeExpansionDecision decision) {
        String targetId = itemId(target);
        return switch (decision.kind()) {
            case DECLINE -> ResolvedRecipe.unsupported(key, targetId, 1, "No semantic handler accepted the recipe");
            case BLOCKED -> ResolvedRecipe.blocked(key, targetId, 1,
                    decision.problem().orElseThrow().code() + ": " + decision.problem().orElseThrow().message());
            case RESOLVED -> adaptPlan(key, decision.plan().orElseThrow());
        };
    }

    private static ResolvedRecipe adaptPlan(RecipeKey key, ExpansionPlan plan) {
        String output = itemId(plan.targetOutput());
        var ingredients = new ArrayList<IngredientChoice>();
        for (var requirement : plan.consumedItems()) {
            var alternatives = requirement.alternatives();
            var ids = alternatives.stream().map(ExpansionGraphAdapter::itemId).distinct().sorted().toList();
            ingredients.add(new IngredientChoice(ids.get(0), ids, requirement.amount()));
        }
        for (var requirement : plan.consumedFluids()) {
            var ids = requirement.alternatives().stream().map(StackResolver::fluidId).distinct().sorted().toList();
            ingredients.add(new IngredientChoice(ids.get(0), ids, requirement.amount()));
        }
        return ResolvedRecipe.supported(key, output, plan.targetAmount(), RecipeIngredientAggregator.aggregate(ingredients));
    }

    private static String itemId(ItemStack stack) { return StackResolver.id(stack); }
}
