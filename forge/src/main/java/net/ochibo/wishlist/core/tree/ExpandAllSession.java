package net.ochibo.wishlist.core.tree;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.recipe.RecipeResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ExpandAllSession {
    public record PendingSelection(RecipeNode node, List<RecipeKey> ancestors) {}

    private final Wishlist wishlist;
    private final RecipeResolver resolver;
    private final Set<String> ingredients;
    private final RecipeTreeService tree = new RecipeTreeService();
    private final Map<RecipeNode, PendingSelection> pending = new IdentityHashMap<>();
    private final Set<UUID> skipped = new LinkedHashSet<>();
    private boolean cancelled;

    public ExpandAllSession(Wishlist wishlist, RecipeResolver resolver, Set<String> ingredients) {
        this.wishlist = Objects.requireNonNull(wishlist, "wishlist");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.ingredients = Set.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
    }

    public void expandAvailable() {
        if (cancelled) return;
        pending.clear();
        for (WishlistEntry entry : wishlist.entries()) {
            ResolvedRecipe root = resolver.findByKey(entry.rootRecipe(), entry.outputItemId()).orElse(null);
            if (root == null) continue;
            List<RecipeKey> ancestors = List.of(root.key());
            for (RecipeNode node : entry.children()) expandRecursively(node, ancestors);
        }
    }

    private void expandRecursively(RecipeNode node, List<RecipeKey> ancestors) {
        if (cancelled) return;
        if (skipped.contains(node.id())) return;
        if (!node.expanded()) {
            if (isIngredientsStop(node)) return;
            TerminalReason result = tree.expandOne(node, resolver, ancestors);
            if (result == TerminalReason.MULTIPLE_RECIPES) {
                pending.put(node, new PendingSelection(node, ancestors));
                return;
            }
            if (result != TerminalReason.NONE) return;
        }
        if (!node.expanded()) return;
        List<RecipeKey> childAncestors = tree.childAncestors(ancestors, node);
        for (RecipeNode child : node.children()) expandRecursively(child, childAncestors);
    }

    private boolean isIngredientsStop(RecipeNode node) {
        return !node.ingredient().candidates().isEmpty()
                && node.ingredient().candidates().stream().allMatch(ingredients::contains);
    }

    public TerminalReason manualExpand(RecipeNode node, List<RecipeKey> ancestors) {
        return tree.expandOne(node, resolver, ancestors);
    }

    public void selectRecipe(RecipeNode node, ResolvedRecipe recipe) {
        PendingSelection selected = pending.get(node);
        List<RecipeKey> ancestors = selected == null ? List.of() : selected.ancestors();
        tree.selectAndExpand(node, recipe, ancestors);
        pending.remove(node);
    }

    public void skipSelection(RecipeNode node) {
        if (pending.remove(node) != null) skipped.add(node.id());
    }

    public List<PendingSelection> pendingSelections() {
        return pending.values().stream()
                .sorted(Comparator.comparing(p -> p.node().ingredient().displayKey()))
                .toList();
    }

    public void cancel() { cancelled = true; }
    public boolean cancelled() { return cancelled; }

    public void collapseAll() {
        for (WishlistEntry entry : wishlist.entries()) for (RecipeNode node : entry.children()) collapseRecursively(node);
        pending.clear();
    }

    private void collapseRecursively(RecipeNode node) {
        if (node.expanded()) node.collapse();
        for (RecipeNode child : node.children()) collapseRecursively(child);
    }
}
