package net.ochibo.wishlist.core.recipe;

import java.util.List;

public final class DeterministicRecipePolicy {
    private DeterministicRecipePolicy() {}

    public static boolean supportsIngredientCandidates(List<Boolean> hasCraftingRemainder) {
        for (boolean remainder : hasCraftingRemainder) {
            if (remainder) return false;
        }
        return true;
    }

    public static boolean allChancesCertain(List<Double> chances) {
        for (double chance : chances) {
            if (Double.compare(chance, 1.0D) != 0) return false;
        }
        return true;
    }
}
