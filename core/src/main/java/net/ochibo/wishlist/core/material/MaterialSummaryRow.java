package net.ochibo.wishlist.core.material;

import java.util.List;

public record MaterialSummaryRow(String displayKey, List<String> candidates, long allocatedOwned, long required) {
    public boolean satisfied() { return allocatedOwned >= required; }
    public String identityKey() { return String.join("\u0000", candidates.stream().distinct().sorted().toList()); }
}
