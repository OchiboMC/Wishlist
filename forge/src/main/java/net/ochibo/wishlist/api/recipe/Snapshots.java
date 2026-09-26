package net.ochibo.wishlist.api.recipe;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

final class Snapshots {
    private Snapshots() {}
    static long positive(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        return amount;
    }
    static double probability(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("probability must be finite and between 0 and 1");
        return value;
    }
    static ItemStack item(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
        return stack.copy();
    }
    static List<ItemStack> items(List<ItemStack> alternatives) {
        Objects.requireNonNull(alternatives, "alternatives");
        if (alternatives.isEmpty()) throw new IllegalArgumentException("alternatives must not be empty");
        return alternatives.stream().map(Snapshots::item).toList();
    }
    static List<FluidStack> fluids(List<FluidStack> alternatives) {
        Objects.requireNonNull(alternatives, "alternatives");
        if (alternatives.isEmpty()) throw new IllegalArgumentException("alternatives must not be empty");
        return alternatives.stream().map(stack -> {
            Objects.requireNonNull(stack, "stack");
            if (stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
            return stack.copy();
        }).toList();
    }
}
