package net.ochibo.wishlist.core.material;

import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.tree.RecipeNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MaterialCalculator {
    private final RecipeResolver resolver;
    private final InventoryAllocator allocator = new InventoryAllocator();

    public MaterialCalculator(RecipeResolver resolver) { this.resolver = resolver; }

    public MaterialCalculation calculate(List<Wishlist> wishlists, InventorySnapshot snapshot) {
        List<WishlistEntry> active = new ArrayList<>();
        List<WishlistEntry> completed = new ArrayList<>();
        for (Wishlist wishlist : wishlists) {
            for (WishlistEntry entry : wishlist.entries()) {
                (entry.completed() ? completed : active).add(entry);
            }
        }
        MaterialCalculation result = calculateEntries(active, snapshot, true);
        if (completed.isEmpty()) return result;
        // Completed trees remain visible, so calculate their labels separately without
        // allowing them to consume inventory or contribute to the material list.
        Map<UUID, NodeQuantity> quantities = new LinkedHashMap<>(result.nodeQuantities());
        quantities.putAll(calculateEntries(completed, snapshot, false).nodeQuantities());
        return new MaterialCalculation(result.rows(), Map.copyOf(quantities));
    }

    private MaterialCalculation calculateEntries(List<WishlistEntry> entries, InventorySnapshot snapshot,
                                                 boolean includeRows) {
        Map<String, Long> remaining = new LinkedHashMap<>(snapshot.counts());
        List<Need> frontier = new ArrayList<>();
        for (WishlistEntry entry : entries) {
                if (entry.requestedCount() == 0) continue;
                ResolvedRecipe root = entry.itemOnly() ? null
                        : resolver.findByKey(entry.rootRecipe(), entry.outputItemId()).orElse(null);
                if (root == null || !root.supported()) {
                    frontier.add(Need.unexpandedRoot(entry));
                } else {
                    frontier.add(Need.root(entry, root, entry.requestedCount()));
                }
        }

        Map<UUID, NodeQuantity> nodeQuantities = new LinkedHashMap<>();
        List<Leaf> leaves = new ArrayList<>();
        int guard = 0;
        while (!frontier.isEmpty() && guard++ < 256) {
            List<InventoryAllocator.Request> requests = new ArrayList<>();
            for (int i = 0; i < frontier.size(); i++) {
                Need need = frontier.get(i);
                // An expanded recipe is a production target. Owned output items do not
                // replace its displayed ingredient plan; only marked crafts reduce it.
                long allocationTarget = need.shouldExpand() ? 0 : Math.max(0, need.required - need.craftedCount());
                requests.add(new InventoryAllocator.Request(i, need.ingredient, allocationTarget));
            }
            InventoryAllocator.Result allocation = allocator.allocate(requests, remaining);
            for (var e : allocation.usedByItem().entrySet()) remaining.computeIfPresent(e.getKey(), (k,v) -> Math.max(0L, v - e.getValue()));

            List<Need> next = new ArrayList<>();
            for (int i = 0; i < frontier.size(); i++) {
                Need need = frontier.get(i);
                long marked = Math.min(need.required, need.craftedCount());
                long allocated = allocation.allocatedByRequest().getOrDefault(i, 0L);
                long remainingRequired = need.required - marked;
                long owned = Math.min(need.required, allocated + marked);
                if (need.node != null) nodeQuantities.put(need.node.id(), new NodeQuantity(owned, need.required));
                if (need.shouldExpand()) {
                    long missing = Math.max(0, remainingRequired - allocated);
                    if (missing > 0) {
                        ResolvedRecipe recipe = need.recipe;
                        long crafts = ceilDiv(missing, recipe.outputCount());
                        List<RecipeNode> states = need.childStates();
                        for (int childIndex = 0; childIndex < recipe.ingredients().size(); childIndex++) {
                            IngredientChoice base = recipe.ingredients().get(childIndex);
                            long required = Math.multiplyExact(base.count(), crafts);
                            IngredientChoice scaled = new IngredientChoice(base.displayKey(), base.candidates(), required);
                            RecipeNode state = childIndex < states.size() ? states.get(childIndex) : null;
                            if (required == 0) continue;
                            scaled = new IngredientChoice(base.displayKey(), base.candidates(), required);
                            String selectedCandidate = state == null ? null : state.selectedCandidateItemId();
                            String childTarget = selectedCandidate;
                            if (childTarget == null && base.candidates().size() == 1) {
                                childTarget = base.candidates().get(0);
                            }
                            ResolvedRecipe childRecipe = state != null && state.selectedRecipe() != null
                                    ? (childTarget == null
                                        ? resolver.findByKey(state.selectedRecipe()).orElse(null)
                                        : resolver.findByKey(state.selectedRecipe(), childTarget).orElse(null))
                                    : null;
                            if (selectedCandidate == null && childRecipe != null
                                    && base.candidates().stream().anyMatch(candidate -> ResourceIdentity.matches(candidate, childRecipe.outputItemId()))) {
                                selectedCandidate = childRecipe.outputItemId();
                            }
                            if (selectedCandidate != null) {
                                scaled = IngredientChoice.exact(selectedCandidate, required);
                            }
                            next.add(new Need(state, scaled, required, childRecipe, null));
                        }
                    }
                } else if (remainingRequired > 0) {
                    leaves.add(new Leaf(need.ingredient, remainingRequired, allocated));
                }
            }
            frontier = next;
        }
        if (guard >= 256) throw new IllegalStateException("recipe tree depth exceeded safety limit");
        if (!includeRows) return new MaterialCalculation(List.of(), Map.copyOf(nodeQuantities));

        Map<String, LeafGroup> grouped = new LinkedHashMap<>();
        for (Leaf leaf : leaves) {
            String key = candidateSignature(leaf.ingredient.candidates());
            grouped.computeIfAbsent(key, k -> new LeafGroup(leaf.ingredient))
                    .add(leaf.ingredient, leaf.required, leaf.owned);
        }

        // Display real surplus only when a group's candidate set does not overlap another leaf group.
        List<LeafGroup> groups = new ArrayList<>(grouped.values());
        MaterialOverlapIndex overlap = new MaterialOverlapIndex();
        for (int i = 0; i < groups.size(); i++) overlap.add(i, groups.get(i).ingredient.candidates());
        for (int i = 0; i < groups.size(); i++) {
            LeafGroup group = groups.get(i);
            if (!overlap.overlaps(i, group.ingredient.candidates())) {
                long residual = remaining.entrySet().stream()
                        .filter(entry -> group.ingredient.candidates().stream()
                                .anyMatch(candidate -> ResourceIdentity.matches(candidate, entry.getKey())))
                        .mapToLong(Map.Entry::getValue).sum();
                group.owned += residual;
            }
        }

        List<MaterialSummaryRow> rows = groups.stream()
                .map(g -> new MaterialSummaryRow(g.ingredient.displayKey(), g.ingredient.candidates(), g.owned, g.required))
                .sorted(Comparator.comparing(MaterialSummaryRow::displayKey))
                .toList();
        return new MaterialCalculation(rows, Map.copyOf(nodeQuantities));
    }

    private static String candidateSignature(List<String> candidates) {
        return String.join("\u0000", candidates.stream().distinct().sorted().toList());
    }

    private static boolean betterDisplayKey(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) return false;
        if (current == null || current.isBlank()) return true;
        return candidate.startsWith("#") && !current.startsWith("#");
    }

    private static long ceilDiv(long a, long b) { return a == 0 ? 0 : 1 + ((a - 1) / b); }

    private record Need(RecipeNode node, IngredientChoice ingredient, long required, ResolvedRecipe recipe, WishlistEntry rootEntry) {
        static Need root(WishlistEntry entry, ResolvedRecipe recipe, long required) {
            return new Need(null, IngredientChoice.exact(entry.outputItemId(), required), required, recipe, entry);
        }
        static Need unexpandedRoot(WishlistEntry entry) {
            return new Need(null, IngredientChoice.exact(entry.outputItemId(), entry.requestedCount()),
                    entry.requestedCount(), null, entry);
        }
        static Need leaf(RecipeNode node, IngredientChoice ingredient, long required) { return new Need(node, ingredient, required, null, null); }
        boolean shouldExpand() { return (rootEntry != null && recipe != null && recipe.supported())
                || (node != null && node.expanded() && recipe != null && recipe.supported()); }
        List<RecipeNode> childStates() { return rootEntry != null ? rootEntry.children() : (node == null ? List.of() : node.children()); }
        long craftedCount() { return rootEntry != null ? rootEntry.craftedCount() : node == null ? 0 : node.craftedCount(); }
    }
    private record Leaf(IngredientChoice ingredient, long required, long owned) {}
    private static final class LeafGroup {
        IngredientChoice ingredient; long required; long owned;
        LeafGroup(IngredientChoice ingredient) { this.ingredient=ingredient; }
        void add(IngredientChoice source, long r, long o) {
            if (betterDisplayKey(source.displayKey(), ingredient.displayKey())) {
                ingredient = new IngredientChoice(source.displayKey(), ingredient.candidates(), ingredient.count());
            }
            required = Math.addExact(required, r);
            owned = Math.addExact(owned, o);
        }
    }
}
