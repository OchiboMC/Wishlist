package net.ochibo.wishlist.core.tree;

import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RecipeNode {
    private final UUID id;
    private final IngredientChoice ingredient;
    private RecipeKey selectedRecipe;
    private String selectedCandidateItemId;
    private boolean expanded;
    private TerminalReason terminalReason = TerminalReason.NONE;
    private final List<RecipeNode> children = new ArrayList<>();
    private long craftedCount;

    public RecipeNode(IngredientChoice ingredient) {
        this(UUID.randomUUID(), ingredient);
    }

    public RecipeNode(UUID id, IngredientChoice ingredient) {
        this.id = Objects.requireNonNull(id, "id");
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
    }

    public UUID id() { return id; }
    public IngredientChoice ingredient() { return ingredient; }
    public RecipeKey selectedRecipe() { return selectedRecipe; }
    public String selectedCandidateItemId() { return selectedCandidateItemId; }
    public boolean expanded() { return expanded; }
    public TerminalReason terminalReason() { return terminalReason; }
    public List<RecipeNode> children() { return Collections.unmodifiableList(children); }
    public long craftedCount() { return craftedCount; }
    public void setCraftedCount(long crafted, long requested) {
        if (requested < 0) throw new IllegalArgumentException("requested count must be nonnegative");
        craftedCount = Math.max(0, Math.min(crafted, requested));
        if (craftedCount == requested && expanded) collapse();
    }
    public void addCrafted(long count, long calculatedRequested) {
        if (count <= 0) return;
        long requested = calculatedRequested;
        craftedCount = Math.min(requested, count > Long.MAX_VALUE - craftedCount ? Long.MAX_VALUE : craftedCount + count);
        if (craftedCount == requested && expanded) collapse();
    }
    public void restoreProgress(long crafted) {
        if (crafted < 0) throw new IllegalArgumentException("invalid progress");
        craftedCount = crafted;
    }

    public void applyExpansion(ResolvedRecipe recipe) {
        selectedRecipe = recipe.key();
        children.clear();
        for (IngredientChoice ingredient : recipe.ingredients()) {
            children.add(new RecipeNode(ingredient));
        }
        expanded = true;
        terminalReason = TerminalReason.NONE;
    }

    public void selectRecipe(RecipeKey recipeKey) {
        selectedRecipe = Objects.requireNonNull(recipeKey, "recipeKey");
    }

    public void selectCandidate(String itemId) {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId must not be blank");
        if (ingredient.candidates().stream().noneMatch(candidate -> ResourceIdentity.matches(candidate, itemId))) {
            throw new IllegalArgumentException("candidate is not accepted by this ingredient node: " + itemId);
        }
        selectedCandidateItemId = itemId;
    }

    public void collapse() {
        expanded = false;
        terminalReason = TerminalReason.NONE;
    }

    public void markTerminal(TerminalReason reason) {
        terminalReason = Objects.requireNonNull(reason, "reason");
        expanded = false;
    }

    public void changeRecipe(RecipeKey key) {
        selectedRecipe = Objects.requireNonNull(key, "key");
        expanded = false;
        terminalReason = TerminalReason.NONE;
        children.clear();
    }

    public void restoreState(RecipeKey selectedRecipe, String selectedCandidateItemId, boolean expanded, TerminalReason terminalReason, List<RecipeNode> children) {
        this.selectedRecipe = selectedRecipe;
        this.selectedCandidateItemId = selectedCandidateItemId;
        this.expanded = expanded;
        this.terminalReason = terminalReason == null ? TerminalReason.NONE : terminalReason;
        this.children.clear();
        this.children.addAll(children);
    }
}
