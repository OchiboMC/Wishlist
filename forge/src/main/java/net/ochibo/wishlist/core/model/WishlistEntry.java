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
    private final RecipeKey rootRecipe;
    private final List<RecipeNode> children;

    private WishlistEntry(UUID id, String outputItemId, long requestedCount, RecipeKey rootRecipe, List<RecipeNode> children) {
        this.id = Objects.requireNonNull(id, "id");
        this.outputItemId = Objects.requireNonNull(outputItemId, "outputItemId");
        if (requestedCount <= 0) throw new IllegalArgumentException("requestedCount must be positive");
        this.requestedCount = requestedCount;
        this.rootRecipe = Objects.requireNonNull(rootRecipe, "rootRecipe");
        this.children = new ArrayList<>(Objects.requireNonNull(children, "children"));
    }

    public static WishlistEntry fromRecipe(ResolvedRecipe recipe, long requestedCount) {
        if (!recipe.supported()) throw new IllegalArgumentException("root recipe must be supported");
        List<RecipeNode> children = recipe.ingredients().stream().map(RecipeNode::new).toList();
        return new WishlistEntry(UUID.randomUUID(), recipe.outputItemId(), requestedCount, recipe.key(), children);
    }

    public static WishlistEntry restore(UUID id, String outputItemId, long requestedCount, RecipeKey rootRecipe, List<RecipeNode> children) {
        return new WishlistEntry(id, outputItemId, requestedCount, rootRecipe, children);
    }

    public UUID id() { return id; }
    public String outputItemId() { return outputItemId; }
    public long requestedCount() { return requestedCount; }
    public RecipeKey rootRecipe() { return rootRecipe; }
    public int getChildrenCount(){ return children.size(); }
    public List<RecipeNode> children() { return Collections.unmodifiableList(children); }
    public void addRequestedCount(long delta) {
        if (delta <= 0) throw new IllegalArgumentException("delta must be positive");
        requestedCount = Math.addExact(requestedCount, delta);
    }

    public void setRequestedCount(long value) {
        if (value <= 0) throw new IllegalArgumentException("requestedCount must be positive");
        requestedCount = value;
    }
}
