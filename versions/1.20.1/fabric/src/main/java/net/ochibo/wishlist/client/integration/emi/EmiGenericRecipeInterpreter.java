package net.ochibo.wishlist.client.integration.emi;

import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.recipe.RecipeIngredientAggregator;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

final class EmiGenericRecipeInterpreter {
    private EmiGenericRecipeInterpreter() {}

    static ResolvedRecipe resolve(EmiRecipeSnapshot snapshot, String requestedTarget) {
        String target = requestedTarget == null ? "" : requestedTarget;
        if (!snapshot.supportsRecipeTree()) return unsupported(snapshot, target, "recipe tree is unsupported");

        List<IngredientChoice> inputs = new ArrayList<>();
        for (EmiIngredientSnapshot input : snapshot.inputs()) {
            String problem = problem(input);
            if (problem != null) return unsupported(snapshot, target, problem);
            inputs.add(new IngredientChoice(input.candidates().get(0), input.candidates(), input.amount()));
        }

        TreeSet<String> outputIds = new TreeSet<>();
        for (EmiIngredientSnapshot output : snapshot.outputs()) {
            String problem = problem(output);
            if (problem != null) return unsupported(snapshot, target, problem);
            if (output.candidates().size() != 1)
                return unsupported(snapshot, target, "alternative outputs are not modeled");
            if (ResourceIdentity.parse(output.candidates().get(0)).kind().equals("item"))
                outputIds.add(output.candidates().get(0));
        }
        String requested = target;
        outputIds.removeIf(key -> !requested.isBlank() && !ResourceIdentity.matches(requested, key));
        if (outputIds.size() != 1)
            return unsupported(snapshot, target, "recipe has no unique matching item output");
        target = outputIds.first();

        long amount = 0;
        for (EmiIngredientSnapshot output : snapshot.outputs()) {
            if (output.candidates().contains(target)) amount = Math.addExact(amount, output.amount());
        }
        if (amount <= 0) return unsupported(snapshot, target, "requested output is absent");
        if (!ResourceIdentity.parse(target).kind().equals("item"))
            return unsupported(snapshot, target, "Wishlist roots must be items");
        return ResolvedRecipe.supported(snapshot.key(), target, amount,
                RecipeIngredientAggregator.aggregate(inputs));
    }

    private static String problem(EmiIngredientSnapshot ingredient) {
        if (!ingredient.itemOnly() || ingredient.candidates().isEmpty()) return "ingredient type has no Wishlist resource adapter";
        if (ingredient.candidates().stream().map(key -> ResourceIdentity.parse(key).kind()).distinct().count() != 1)
            return "mixed resource kinds in one slot";
        if (!ingredient.certain()) return "probabilistic ingredient";
        if (ingredient.hasRemainder()) return "ingredient remainder is not modeled";
        if (ingredient.amount() <= 0) return "non-positive ingredient amount";
        return null;
    }

    private static ResolvedRecipe unsupported(EmiRecipeSnapshot snapshot, String target, String reason) {
        return ResolvedRecipe.unsupported(snapshot.key(), target.isBlank() ? "minecraft:air" : target, 1, reason);
    }
}
