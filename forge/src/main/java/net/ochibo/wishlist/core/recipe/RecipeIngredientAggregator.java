package net.ochibo.wishlist.core.recipe;

import net.ochibo.wishlist.core.model.IngredientChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeIngredientAggregator {
    private RecipeIngredientAggregator() {}

    public static List<IngredientChoice> aggregate(List<IngredientChoice> ingredients) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (IngredientChoice ingredient : ingredients) {
            List<String> candidates = ingredient.candidates().stream().distinct().sorted().toList();
            if (candidates.isEmpty()) continue;
            String signature = String.join("\u0000", candidates);
            Group group = groups.computeIfAbsent(signature, k -> new Group(candidates, ingredient.displayKey()));
            if (isBetterDisplayKey(ingredient.displayKey(), group.displayKey)) {
                group.displayKey = ingredient.displayKey();
            }
            group.count = Math.addExact(group.count, ingredient.count());
        }
        List<IngredientChoice> result = new ArrayList<>();
        for (Group group : groups.values()) {
            result.add(new IngredientChoice(group.displayKey, group.candidates, group.count));
        }
        result.sort(Comparator.comparing(i -> i.candidates().get(0)));
        return List.copyOf(result);
    }

    private static boolean isBetterDisplayKey(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) return false;
        if (current == null || current.isBlank()) return true;
        return candidate.startsWith("#") && !current.startsWith("#");
    }

    private static final class Group {
        final List<String> candidates;
        String displayKey;
        long count;

        Group(List<String> candidates, String displayKey) {
            this.candidates = candidates;
            this.displayKey = displayKey;
        }
    }
}
