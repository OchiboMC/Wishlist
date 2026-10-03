package net.ochibo.wishlist.core.tree;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.recipe.RecipeResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeTreeService {
    public TerminalReason expandOne(RecipeNode node, RecipeResolver resolver, List<RecipeKey> ancestorRecipes) {
        if (node.selectedRecipe() != null) {
            String target = node.selectedCandidateItemId();
            if (target == null && node.ingredient().candidates().size() == 1) {
                target = node.ingredient().candidates().get(0);
            }
            ResolvedRecipe selected = target == null
                    ? resolver.findByKey(node.selectedRecipe()).orElse(null)
                    : resolver.findByKey(node.selectedRecipe(), target).orElse(null);
            if (selected == null) { node.markTerminal(TerminalReason.NO_RECIPE); return TerminalReason.NO_RECIPE; }
            return selectAndExpand(node, selected, ancestorRecipes, resolver);
        }

        if (node.selectedCandidateItemId() != null) {
            return expandOneForCandidate(node, resolver, ancestorRecipes, node.selectedCandidateItemId());
        }

        if (node.ingredient().candidates().size() > 1) {
            node.markTerminal(TerminalReason.ALTERNATIVE_INGREDIENT);
            return TerminalReason.ALTERNATIVE_INGREDIENT;
        }
        return expandOneForCandidate(node, resolver, ancestorRecipes, node.ingredient().candidates().get(0));
    }

    public TerminalReason expandOneForCandidate(RecipeNode node, RecipeResolver resolver,
                                                 List<RecipeKey> ancestorRecipes, String candidateItemId) {
        if (node.ingredient().candidates().stream().noneMatch(candidate -> ResourceIdentity.matches(candidate, candidateItemId))) {
            throw new IllegalArgumentException("candidate is not accepted by this ingredient node: " + candidateItemId);
        }
        node.selectCandidate(candidateItemId);
        if (repeatsAncestorOutput(candidateItemId, resolver, ancestorRecipes)) {
            node.markTerminal(TerminalReason.CYCLE);
            return TerminalReason.CYCLE;
        }
        Map<RecipeKey, ResolvedRecipe> unique = new LinkedHashMap<>();
        for (ResolvedRecipe recipe : resolver.findForOutput(candidateItemId)) unique.put(recipe.key(), recipe);
        if (unique.isEmpty()) { node.markTerminal(TerminalReason.NO_RECIPE); return TerminalReason.NO_RECIPE; }
        if (unique.size() > 1) { node.markTerminal(TerminalReason.MULTIPLE_RECIPES); return TerminalReason.MULTIPLE_RECIPES; }
        return selectAndExpand(node, unique.values().iterator().next(), ancestorRecipes, resolver);
    }

    public TerminalReason selectAndExpand(RecipeNode node, ResolvedRecipe recipe, List<RecipeKey> ancestorRecipes) {
        return selectAndExpand(node, recipe, ancestorRecipes, null);
    }

    public TerminalReason selectAndExpand(RecipeNode node, ResolvedRecipe recipe,
                                          List<RecipeKey> ancestorRecipes, RecipeResolver resolver) {
        if (node.ingredient().candidates().stream().noneMatch(candidate -> ResourceIdentity.matches(candidate, recipe.outputItemId()))) {
            throw new IllegalArgumentException("recipe output does not satisfy this ingredient node");
        }
        node.selectCandidate(recipe.outputItemId());
        if (ancestorRecipes.contains(recipe.key()) || resolver != null
                && repeatsAncestorOutput(recipe.outputItemId(), resolver, ancestorRecipes)) {
            node.selectRecipe(recipe.key());
            node.markTerminal(TerminalReason.CYCLE);
            return TerminalReason.CYCLE;
        }
        if (!recipe.supported()) {
            node.selectRecipe(recipe.key());
            node.markTerminal(TerminalReason.UNSUPPORTED);
            return TerminalReason.UNSUPPORTED;
        }
        node.applyExpansion(recipe);
        return TerminalReason.NONE;
    }

    private static boolean repeatsAncestorOutput(String candidate, RecipeResolver resolver,
                                                 List<RecipeKey> ancestorRecipes) {
        ResourceIdentity target = ResourceIdentity.parse(candidate);
        for (RecipeKey ancestor : ancestorRecipes) {
            ResolvedRecipe recipe = resolver.findByKey(ancestor).orElse(null);
            if (recipe == null) continue;
            ResourceIdentity output = ResourceIdentity.parse(recipe.outputItemId());
            if (target.equals(output))
                return true;
        }
        return false;
    }

    public void collapse(RecipeNode node) { node.collapse(); }

    public void changeRecipe(RecipeNode node, RecipeKey key) { node.changeRecipe(key); }

    public List<RecipeKey> childAncestors(List<RecipeKey> ancestors, RecipeNode parent) {
        List<RecipeKey> result = new ArrayList<>(ancestors);
        if (parent.selectedRecipe() != null) result.add(parent.selectedRecipe());
        return List.copyOf(result);
    }
}
