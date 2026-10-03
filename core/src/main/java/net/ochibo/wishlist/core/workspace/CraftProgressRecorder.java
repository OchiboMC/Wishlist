package net.ochibo.wishlist.core.workspace;

import net.ochibo.wishlist.core.material.NodeQuantity;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.tree.RecipeNode;

import java.util.Map;
import java.util.UUID;

/** Allocates one actual crafting output to at most one unfinished tree target. */
public final class CraftProgressRecorder {
    private CraftProgressRecorder() {}

    public static boolean record(Wishlist wishlist, Map<UUID, NodeQuantity> quantities,
                                 String outputId, long count) {
        if (count <= 0) return false;
        long remaining = count;
        for (WishlistEntry entry : wishlist.entries()) {
            if (entry.completed()) continue;
            if (ResourceIdentity.matches(entry.outputItemId(), outputId)) {
                long taken = Math.min(remaining, entry.requestedCount() - entry.craftedCount());
                if (taken > 0) {
                    entry.setCraftedCount(entry.craftedCount() + taken);
                    remaining -= taken;
                }
            }
            if (remaining == 0) break;
            for (RecipeNode node : entry.children()) {
                remaining = recordNode(node, quantities, outputId, remaining);
                if (remaining == 0) break;
            }
            if (remaining == 0) break;
        }
        if (remaining != count) wishlist.touch();
        return remaining != count;
    }

    private static long recordNode(RecipeNode node, Map<UUID, NodeQuantity> quantities,
                                   String outputId, long remaining) {
        NodeQuantity quantity = quantities.get(node.id());
        if (quantity == null) return remaining;
        long requested = quantity.required();
        boolean matches = node.selectedCandidateItemId() != null
                ? ResourceIdentity.matches(node.selectedCandidateItemId(), outputId)
                : node.ingredient().candidates().stream().anyMatch(candidate -> ResourceIdentity.matches(candidate, outputId));
        if (matches) {
            long taken = Math.min(remaining, Math.max(0, requested - node.craftedCount()));
            if (taken > 0) {
                node.addCrafted(taken, requested);
                remaining -= taken;
            }
        }
        if (!node.expanded()) return remaining;
        for (RecipeNode child : node.children()) {
            remaining = recordNode(child, quantities, outputId, remaining);
            if (remaining == 0) break;
        }
        return remaining;
    }
}
