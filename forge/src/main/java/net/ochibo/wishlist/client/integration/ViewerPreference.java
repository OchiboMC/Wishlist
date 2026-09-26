package net.ochibo.wishlist.client.integration;

import java.util.ArrayList;
import java.util.List;

public enum ViewerPreference {
    AUTO,
    JEI,
    EMI;

    public List<ViewerKind> order(boolean jeiAvailable, boolean emiAvailable) {
        return order(jeiAvailable, emiAvailable, null);
    }

    public List<ViewerKind> order(boolean jeiAvailable, boolean emiAvailable, ViewerKind lastUsed) {
        List<ViewerKind> result = new ArrayList<>(2);
        ViewerKind first = this == EMI ? ViewerKind.EMI : (this == AUTO && lastUsed != null ? lastUsed : ViewerKind.JEI);
        ViewerKind second = first == ViewerKind.JEI ? ViewerKind.EMI : ViewerKind.JEI;
        addIfAvailable(result, first, jeiAvailable, emiAvailable);
        addIfAvailable(result, second, jeiAvailable, emiAvailable);
        return List.copyOf(result);
    }

    private static void addIfAvailable(List<ViewerKind> result, ViewerKind kind, boolean jei, boolean emi) {
        if ((kind == ViewerKind.JEI && jei) || (kind == ViewerKind.EMI && emi)) result.add(kind);
    }
}
