package net.ochibo.wishlist.client.integration.jei;

import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.recipe.RecipeIngredientAggregator;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static net.ochibo.wishlist.core.utils.StackResolver.targetOrAir;

/** Conservative Item and Fluid interpretation of a public JEI recipe layout. */
final class JeiGenericRecipeInterpreter {
    private JeiGenericRecipeInterpreter() {}

    static ResolvedRecipe resolve(JeiRecipeSnapshot snapshot, String requestedTarget) {
        if (snapshot.hasUnmodeledInvisibleIngredients()) {
            return unsupported(snapshot, targetOrAir(requestedTarget),
                    "JEI recipe contains invisible ingredients whose material semantics cannot be proven");
        }

        List<IngredientChoice> inputs = new ArrayList<>();
        List<NormalizedSlot> outputs = new ArrayList<>();
        for (JeiRecipeSlot slot : snapshot.slots()) {
            if (slot.role() == JeiIngredientRole.RENDER_ONLY) continue;
            if (slot.allIngredientCount() == 0 && slot.candidates().isEmpty()) continue;
            NormalizedSlot normalized = normalize(slot);
            if (normalized.reason() != null) {
                return unsupported(snapshot, targetOrAir(requestedTarget), normalized.reason());
            }
            if (normalized.choice() == null) continue;
            switch (slot.role()) {
                case INPUT -> inputs.add(normalized.choice());
                case OUTPUT -> outputs.add(normalized);
                case CATALYST, RENDER_ONLY -> { /* non-consumed by definition */ }
            }
        }

        if (outputs.isEmpty()) return unsupported(snapshot, targetOrAir(requestedTarget), "JEI recipe has no item output");

        String target = requestedTarget == null ? "" : requestedTarget;
        String requested = target;
        TreeSet<String> matchingOutputs = new TreeSet<>();
        for (NormalizedSlot output : outputs) output.choice().candidates().stream()
                .filter(key -> ResourceIdentity.parse(key).kind().equals("item"))
                .filter(key -> requested.isBlank() || ResourceIdentity.matches(requested, key))
                .forEach(matchingOutputs::add);
        if (matchingOutputs.size() != 1)
            return unsupported(snapshot, targetOrAir(target), "JEI recipe has no unique matching item output");
        target = matchingOutputs.first();

        long outputAmount = 0;
        boolean matched = false;
        for (NormalizedSlot output : outputs) {
            IngredientChoice choice = output.choice();
            if (!choice.candidates().contains(target)) continue;
            if (choice.candidates().size() != 1) {
                return unsupported(snapshot, target, "Target output slot contains alternative outputs");
            }
            outputAmount = Math.addExact(outputAmount, choice.count());
            matched = true;
        }
        if (!matched || outputAmount <= 0) {
            return unsupported(snapshot, target, "Target output is not present in the JEI recipe layout");
        }
        if (!ResourceIdentity.parse(target).kind().equals("item"))
            return unsupported(snapshot, target, "Wishlist roots must be items");

        // Aggregation identity is the normalized candidate set, not displayKey.
        // This is what merges e.g. #balm:iron_ingots and minecraft:iron_ingot
        // when both resolve to the same concrete candidate set.
        return ResolvedRecipe.supported(snapshot.key(), target, outputAmount,
                RecipeIngredientAggregator.aggregate(inputs));
    }

    private static NormalizedSlot normalize(JeiRecipeSlot slot) {
        if (slot.blankIngredientCount() > 0)
            return new NormalizedSlot(null, "Blank/optional ingredient alternatives are not modeled");
        if (!slot.itemOnly())
            return new NormalizedSlot(null, "Ingredient type has no Wishlist resource adapter");
        if (slot.candidates().isEmpty())
            return new NormalizedSlot(null, "Item ingredient slot has no usable item candidates");
        if (slot.candidates().stream().map(candidate -> ResourceIdentity.parse(candidate.itemId()).kind())
                .distinct().count() != 1)
            return new NormalizedSlot(null, "Mixed resource kinds in one slot are not modeled");

        long amount = slot.candidates().get(0).amount();
        if (slot.candidates().stream().anyMatch(candidate -> candidate.amount() != amount))
            return new NormalizedSlot(null, "Alternative ingredients have different amounts");

        List<String> candidates = slot.candidates().stream()
                .map(JeiStackCandidate::itemId).distinct().sorted().toList();
        String display = candidates.size() == 1
                ? candidates.get(0)
                : (slot.displayKey().isBlank() ? candidates.get(0) : slot.displayKey());
        return new NormalizedSlot(new IngredientChoice(display, candidates, amount), null);
    }

    private static ResolvedRecipe unsupported(JeiRecipeSnapshot snapshot, String target, String reason) {
        return ResolvedRecipe.unsupported(snapshot.key(), target, 1, reason);
    }

    private record NormalizedSlot(IngredientChoice choice, String reason) { }
}
