package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.material.MaterialCalculation;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Reuses the expensive tree calculation while its inputs are unchanged. */
final class MaterialCalculationCache {
    private Map<String, Long> inventoryCounts;
    private Object resolver;
    private MaterialCalculation calculation;

    MaterialCalculation get(InventorySnapshot inventory, Object resolver,
                            Supplier<MaterialCalculation> calculate) {
        if (calculation == null || !Objects.equals(this.resolver, resolver)
                || !inventory.counts().equals(inventoryCounts)) {
            calculation = calculate.get();
            inventoryCounts = inventory.counts();
            this.resolver = resolver;
        }
        return calculation;
    }

    void invalidate() {
        calculation = null;
        inventoryCounts = null;
        resolver = null;
    }
}
