package net.ochibo.wishlist.api.recipe;

import java.util.List;

/**
 * Fluid consumed by one attempt; amount is measured in millibuckets.
 * Alternatives are OR choices. The positive long amount is authoritative;
 * stack counts describe the supplied snapshots and do not scale this amount.
 */
public final class FluidRequirement {
    private final List<FluidStack> alternatives;
    private final long amount;

    private FluidRequirement(List<FluidStack> alternatives, long amount) {
        this.alternatives = Snapshots.fluids(alternatives);
        this.amount = Snapshots.positive(amount);
    }

    public static FluidRequirement of(List<FluidStack> alternatives, long amount) {
        return new FluidRequirement(alternatives, amount);
    }

    /** Returns an immutable list of fresh deep stack copies. */
    public List<FluidStack> alternatives() { return Snapshots.fluids(alternatives); }
    public long amount() { return amount; }
}
