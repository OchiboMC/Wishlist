package net.ochibo.wishlist.core.inventory;

import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InventorySnapshot {
    private final Map<String, Long> counts;

    private InventorySnapshot(Map<String, Long> counts) {
        LinkedHashMap<String, Long> clean = new LinkedHashMap<>();
        counts.forEach((k, v) -> { if (k != null && !k.isBlank() && v != null && v > 0) clean.put(k, v); });
        this.counts = Map.copyOf(clean);
    }

    public static InventorySnapshot of(Map<String, Long> counts) { return new InventorySnapshot(counts); }
    public static InventorySnapshot empty() { return new InventorySnapshot(Map.of()); }
    public Map<String, Long> counts() { return counts; }
    public long count(String resourceKey) {
        return counts.entrySet().stream()
                .filter(entry -> ResourceIdentity.matches(resourceKey, entry.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
    }
}
