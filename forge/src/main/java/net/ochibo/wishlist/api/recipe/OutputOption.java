package net.ochibo.wishlist.api.recipe;

import net.minecraft.world.item.ItemStack;

/** One possible output of one attempt. Probability never scales required inputs. */
public final class OutputOption {
    private final ItemStack output;
    private final long amount;
    private final double probability;

    private OutputOption(ItemStack output, long amount, double probability) {
        this.output = Snapshots.item(output);
        this.amount = Snapshots.positive(amount);
        this.probability = Snapshots.probability(probability);
    }
    public static OutputOption of(ItemStack output, long amount, double probability) {
        return new OutputOption(output, amount, probability);
    }
    /** Returns a deep copy, including NBT. */
    public ItemStack output() { return output.copy(); }
    /** Authoritative output quantity, independent of the snapshot stack count. */
    public long amount() { return amount; }
    /** Probability in the closed interval [0, 1]. */
    public double probability() { return probability; }
}
