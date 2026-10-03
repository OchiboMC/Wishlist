package net.ochibo.wishlist.api.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Complete immutable semantics of one recipe execution or attempt.
 * Runtime only: discard on recipe, world or viewer invalidation. Do not persist.
 * Requirements are not scaled for requested quantities or expected success.
 */
public final class ExpansionPlan {
    private final ItemStack targetOutput;
    private final long targetAmount;
    private final List<ItemRequirement> consumedItems;
    private final List<FluidRequirement> consumedFluids;
    private final List<AuxiliaryItemRequirement> auxiliaryItems;
    private final List<OutputOption> outputs;
    private final OptionalDouble successProbability;

    private ExpansionPlan(Builder builder) {
        targetOutput = builder.targetOutput.copy();
        targetAmount = builder.targetAmount;
        consumedItems = List.copyOf(builder.consumedItems);
        consumedFluids = List.copyOf(builder.consumedFluids);
        auxiliaryItems = List.copyOf(builder.auxiliaryItems);
        outputs = List.copyOf(builder.outputs);
        successProbability = builder.successProbability;
    }

    public static Builder builder(ItemStack targetOutput, long targetAmount) {
        return new Builder(targetOutput, targetAmount);
    }

    public ItemStack targetOutput() { return targetOutput.copy(); }
    public long targetAmount() { return targetAmount; }
    public List<ItemRequirement> consumedItems() { return consumedItems; }
    public List<FluidRequirement> consumedFluids() { return consumedFluids; }
    public List<AuxiliaryItemRequirement> auxiliaryItems() { return auxiliaryItems; }
    public List<OutputOption> outputs() { return outputs; }
    public OptionalDouble successProbability() { return successProbability; }

    /** Mutable construction helper; built snapshots are independent of later edits. */
    public static final class Builder {
        private final ItemStack targetOutput;
        private final long targetAmount;
        private final List<ItemRequirement> consumedItems = new ArrayList<>();
        private final List<FluidRequirement> consumedFluids = new ArrayList<>();
        private final List<AuxiliaryItemRequirement> auxiliaryItems = new ArrayList<>();
        private final List<OutputOption> outputs = new ArrayList<>();
        private OptionalDouble successProbability = OptionalDouble.empty();

        private Builder(ItemStack targetOutput, long targetAmount) {
            this.targetOutput = Snapshots.item(targetOutput);
            this.targetAmount = Snapshots.positive(targetAmount);
        }

        /**
         * Adds a consumed item requirement. Equivalent alternative sets are merged
         * irrespective of candidate order or snapshot stack counts. NBT remains part
         * of candidate identity, so semantically different ingredients never merge.
         */
        public Builder addItem(ItemRequirement requirement) {
            Objects.requireNonNull(requirement, "requirement");
            ItemRequirement normalized = normalize(requirement);
            for (int i = 0; i < consumedItems.size(); i++) {
                ItemRequirement existing = consumedItems.get(i);
                if (sameItems(existing.alternatives(), normalized.alternatives())) {
                    consumedItems.set(i, ItemRequirement.of(existing.alternatives(),
                            Math.addExact(existing.amount(), normalized.amount())));
                    return this;
                }
            }
            consumedItems.add(normalized);
            return this;
        }

        /** Same merging contract as {@link #addItem(ItemRequirement)} for fluids. */
        public Builder addFluid(FluidRequirement requirement) {
            Objects.requireNonNull(requirement, "requirement");
            FluidRequirement normalized = normalize(requirement);
            for (int i = 0; i < consumedFluids.size(); i++) {
                FluidRequirement existing = consumedFluids.get(i);
                if (sameFluids(existing.alternatives(), normalized.alternatives())) {
                    consumedFluids.set(i, FluidRequirement.of(existing.alternatives(),
                            Math.addExact(existing.amount(), normalized.amount())));
                    return this;
                }
            }
            consumedFluids.add(normalized);
            return this;
        }

        public Builder addAuxiliary(AuxiliaryItemRequirement requirement) {
            auxiliaryItems.add(Objects.requireNonNull(requirement, "requirement"));
            return this;
        }

        public Builder addOutput(OutputOption output) {
            outputs.add(Objects.requireNonNull(output, "output"));
            return this;
        }

        public Builder successProbability(double probability) {
            successProbability = OptionalDouble.of(Snapshots.probability(probability));
            return this;
        }

        public ExpansionPlan build() { return new ExpansionPlan(this); }

        private static ItemRequirement normalize(ItemRequirement requirement) {
            List<ItemStack> unique = new ArrayList<>();
            for (ItemStack candidate : requirement.alternatives()) {
                if (unique.stream().noneMatch(existing -> ItemStack.isSameItemSameTags(existing, candidate))) {
                    ItemStack copy = candidate.copy();
                    copy.setCount(1);
                    unique.add(copy);
                }
            }
            return ItemRequirement.of(unique, requirement.amount());
        }

        private static FluidRequirement normalize(FluidRequirement requirement) {
            List<FluidStack> unique = new ArrayList<>();
            for (FluidStack candidate : requirement.alternatives()) {
                if (unique.stream().noneMatch(existing -> sameFluid(existing, candidate))) {
                    FluidStack copy = candidate.copy();
                    copy.setAmount(1);
                    unique.add(copy);
                }
            }
            return FluidRequirement.of(unique, requirement.amount());
        }

        private static boolean sameItems(List<ItemStack> left, List<ItemStack> right) {
            if (left.size() != right.size()) return false;
            return left.stream().allMatch(candidate ->
                    right.stream().anyMatch(other -> ItemStack.isSameItemSameTags(candidate, other)));
        }

        private static boolean sameFluids(List<FluidStack> left, List<FluidStack> right) {
            if (left.size() != right.size()) return false;
            return left.stream().allMatch(candidate ->
                    right.stream().anyMatch(other -> sameFluid(candidate, other)));
        }

        private static boolean sameFluid(FluidStack left, FluidStack right) {
            return left.getFluid() == right.getFluid() && Objects.equals(left.getTag(), right.getTag());
        }
    }
}
