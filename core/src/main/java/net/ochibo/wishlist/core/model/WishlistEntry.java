package net.ochibo.wishlist.core.model;

import net.ochibo.wishlist.core.tree.RecipeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WishlistEntry {
    private final UUID id;
    private final String outputItemId;
    private long requestedCount;
    public static final RecipeKey ITEM_ONLY_RECIPE = new RecipeKey("wishlist:unexpanded_item");
    private RecipeKey rootRecipe;
    private final List<RecipeNode> children;
    private boolean completed;
    private long craftedCount;

    private WishlistEntry(UUID id, String outputItemId, long requestedCount, RecipeKey rootRecipe,
                          List<RecipeNode> children, boolean completed, long craftedCount) {
        this.id = Objects.requireNonNull(id, "id");
        this.outputItemId = Objects.requireNonNull(outputItemId, "outputItemId");
        if (requestedCount < 0) throw new IllegalArgumentException("requestedCount must be nonnegative");
        this.requestedCount = requestedCount;
        this.rootRecipe = Objects.requireNonNull(rootRecipe, "rootRecipe");
        this.children = new ArrayList<>(Objects.requireNonNull(children, "children"));
        this.completed = completed;
        this.craftedCount = Math.max(0, Math.min(craftedCount, requestedCount));
    }

    public static WishlistEntry fromRecipe(ResolvedRecipe recipe, long requestedCount) {
        if (!recipe.supported()) throw new IllegalArgumentException("root recipe must be supported");
        List<RecipeNode> children = recipe.ingredients().stream().map(RecipeNode::new).toList();
        return new WishlistEntry(UUID.randomUUID(), recipe.outputItemId(), requestedCount, recipe.key(), children, false, 0);
    }

    public static WishlistEntry fromItem(String itemId, long requestedCount) {
        if (requestedCount <= 0) throw new IllegalArgumentException("requestedCount must be positive");
        ResourceIdentity.parse(itemId);
        return new WishlistEntry(UUID.randomUUID(), itemId, requestedCount, ITEM_ONLY_RECIPE,
                List.of(), false, 0);
    }

    public static WishlistEntry restore(UUID id, String outputItemId, long requestedCount, RecipeKey rootRecipe, List<RecipeNode> children) {
        return restore(id, outputItemId, requestedCount, rootRecipe, children, false);
    }

    public static WishlistEntry restore(UUID id, String outputItemId, long requestedCount, RecipeKey rootRecipe,
                                        List<RecipeNode> children, boolean completed) {
        return restore(id, outputItemId, requestedCount, rootRecipe, children, completed, 0);
    }

    public static WishlistEntry restore(UUID id, String outputItemId, long requestedCount, RecipeKey rootRecipe,
                                        List<RecipeNode> children, boolean completed, long craftedCount) {
        return new WishlistEntry(id, outputItemId, requestedCount, rootRecipe, children, completed, craftedCount);
    }

    public UUID id() { return id; }
    public String outputItemId() { return outputItemId; }
    public long requestedCount() { return requestedCount; }
    public RecipeKey rootRecipe() { return rootRecipe; }
    public boolean itemOnly() { return ITEM_ONLY_RECIPE.equals(rootRecipe); }
    public void selectRootRecipe(ResolvedRecipe recipe) {
        if (!itemOnly()) throw new IllegalStateException("root already has a recipe");
        if (!recipe.supported() || !ResourceIdentity.matches(outputItemId, recipe.outputItemId()))
            throw new IllegalArgumentException("recipe output does not match root item");
        rootRecipe = recipe.key();
        children.clear();
        recipe.ingredients().stream().map(RecipeNode::new).forEach(children::add);
    }
    public int getChildrenCount(){ return children.size(); }
    public List<RecipeNode> children() { return Collections.unmodifiableList(children); }
    public boolean completed() { return completed; }
    public void setCompleted(boolean value) { completed = value; }
    public long craftedCount() { return craftedCount; }
    public void setCraftedCount(long value) {
        craftedCount = Math.max(0, Math.min(value, requestedCount));
        if (craftedCount == requestedCount) completed = true;
    }
    public void addRequestedCount(long delta) {
        if (delta <= 0) throw new IllegalArgumentException("delta must be positive");
        requestedCount = Math.addExact(requestedCount, delta);
    }

    public void setRequestedCount(long value) {
        if (value < 0) throw new IllegalArgumentException("requestedCount must be nonnegative");
        requestedCount = value;
        craftedCount = Math.min(craftedCount, requestedCount);
        if (craftedCount == requestedCount) completed = true;
    }
}
