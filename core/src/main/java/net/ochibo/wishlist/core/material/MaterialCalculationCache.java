package net.ochibo.wishlist.core.material;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.model.Wishlist;

/** Shared by all views. Validation visits selected lists, never their entries or recipe trees. */
public final class MaterialCalculationCache {
    private record Version(Wishlist wishlist, long revision) {}
    private List<Version> versions;
    private Map<String, Long> inventoryCounts;
    private Object resolver;
    private MaterialCalculation calculation;

    public MaterialCalculation get(List<Wishlist> selected, InventorySnapshot inventory, Object resolver,
                                   Supplier<MaterialCalculation> calculate) {
        List<Version> current = versions(selected);
        if (calculation == null || !current.equals(versions) || !Objects.equals(this.resolver, resolver)
                || !inventory.counts().equals(inventoryCounts)) {
            MaterialCalculation result = calculate.get();
            // Normalizing saved progress may touch a list while calculating.
            versions = versions(selected);
            inventoryCounts = inventory.counts();
            this.resolver = resolver;
            calculation = result;
        }
        return calculation;
    }

    private static List<Version> versions(List<Wishlist> selected) {
        return selected.stream().map(wishlist -> new Version(wishlist, wishlist.revision())).toList();
    }

    public void invalidate() {
        calculation = null;
        versions = null;
        inventoryCounts = null;
        resolver = null;
    }
}
