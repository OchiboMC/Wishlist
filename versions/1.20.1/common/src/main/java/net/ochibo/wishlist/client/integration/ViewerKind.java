package net.ochibo.wishlist.client.integration;

import java.util.ArrayList;
import java.util.List;

public enum ViewerKind {
    JEI,
    EMI;

    public static List<ViewerKind> availableOrder(boolean jei, boolean emi, ViewerKind lastUsed) {
        List<ViewerKind> result = new ArrayList<>(2);
        ViewerKind first = lastUsed == null ? JEI : lastUsed;
        ViewerKind second = first == JEI ? EMI : JEI;
        if (first == JEI && jei || first == EMI && emi) result.add(first);
        if (second == JEI && jei || second == EMI && emi) result.add(second);
        return List.copyOf(result);
    }
}
