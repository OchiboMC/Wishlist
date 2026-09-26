package net.ochibo.wishlist.core.material;

import java.util.List;
import java.util.Set;

/** Shared visibility rules for the materials panel and HUD. */
public final class MaterialVisibility {
    private MaterialVisibility() {}

    public static List<MaterialSummaryRow> screenRows(List<MaterialSummaryRow> rows,
                                                       Set<String> completed, boolean missingOnly) {
        if (!missingOnly) return List.copyOf(rows);
        return rows.stream().filter(row -> !row.satisfied() && !completed.contains(row.identityKey())).toList();
    }

    public static List<MaterialSummaryRow> hudRows(List<MaterialSummaryRow> rows,
                                                    Set<String> completed, boolean missingOnly) {
        return rows.stream().filter(row -> !completed.contains(row.identityKey()))
                .filter(row -> !missingOnly || !row.satisfied()).toList();
    }
}
