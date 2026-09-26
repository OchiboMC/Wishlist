package net.ochibo.wishlist.api.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * A non-consumed tool, catalyst or machine needed for one attempt.
 * Alternatives are OR choices. The positive long amount is authoritative;
 * stack counts describe the supplied snapshots and do not scale this amount.
 */
public final class AuxiliaryItemRequirement {
    private final List<ItemStack> alternatives;
    private final long amount;

    private AuxiliaryItemRequirement(List<ItemStack> alternatives, long amount) {
        this.alternatives = Snapshots.items(alternatives);
        this.amount = Snapshots.positive(amount);
    }

    public static AuxiliaryItemRequirement of(List<ItemStack> alternatives, long amount) {
        return new AuxiliaryItemRequirement(alternatives, amount);
    }

    /** Returns an immutable list of fresh deep stack copies. */
    public List<ItemStack> alternatives() { return Snapshots.items(alternatives); }
    public long amount() { return amount; }
}
