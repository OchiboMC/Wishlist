package net.ochibo.wishlist.api.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Items consumed by one attempt.
 * Alternatives are OR choices. The positive long amount is authoritative;
 * stack counts describe the supplied snapshots and do not scale this amount.
 */
public final class ItemRequirement {
    private final List<ItemStack> alternatives;
    private final long amount;

    private ItemRequirement(List<ItemStack> alternatives, long amount) {
        this.alternatives = Snapshots.items(alternatives);
        this.amount = Snapshots.positive(amount);
    }

    public static ItemRequirement of(List<ItemStack> alternatives, long amount) {
        return new ItemRequirement(alternatives, amount);
    }

    /** Returns an immutable list of fresh deep stack copies. */
    public List<ItemStack> alternatives() { return Snapshots.items(alternatives); }
    public long amount() { return amount; }
}
