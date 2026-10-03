package net.ochibo.wishlist.integration.create;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.ochibo.wishlist.api.recipe.AuxiliaryItemRequirement;
import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.api.recipe.ExpansionProblem;
import net.ochibo.wishlist.api.recipe.FluidRequirement;
import net.ochibo.wishlist.api.recipe.FluidStack;
import net.ochibo.wishlist.api.recipe.ItemRequirement;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CreateExpansionSupport {
    private CreateExpansionSupport() {}

    static RecipeExpansionDecision blocked(String code, String message) {
        return RecipeExpansionDecision.blocked(ExpansionProblem.of(
                new ResourceLocation("wishlist", code), message));
    }

    static List<ItemStack> candidates(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return List.of();
        // Custom Ingredient subclasses can encode predicates that are not
        // equivalent to the displayed ItemStack alternatives. Do not guess.
        if (ingredient.getClass() != Ingredient.class) return List.of();
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    static boolean addConsumed(ExpansionPlan.Builder plan, Ingredient ingredient, long amount) {
        List<ItemStack> alternatives = candidates(ingredient);
        if (alternatives.isEmpty()) return false;
        plan.addItem(ItemRequirement.of(alternatives, amount));
        return true;
    }

    static boolean addAuxiliary(ExpansionPlan.Builder plan, Ingredient ingredient, long amount) {
        List<ItemStack> alternatives = candidates(ingredient);
        if (alternatives.isEmpty()) return false;
        plan.addAuxiliary(AuxiliaryItemRequirement.of(alternatives, amount));
        return true;
    }

    static void addMachines(ExpansionPlan.Builder plan, Set<ItemLike> machines) {
        for (ItemLike machine : machines) {
            if (machine == null) continue;
            ItemStack stack = new ItemStack(machine);
            if (!stack.isEmpty()) plan.addAuxiliary(AuxiliaryItemRequirement.of(List.of(stack), 1));
        }
    }

    static boolean addFluid(ExpansionPlan.Builder plan,
                            List<io.github.fabricators_of_create.porting_lib.fluids.FluidStack> alternatives,
                            long amount) {
        List<FluidStack> usable = new ArrayList<>();
        for (var candidate : alternatives) {
            if (candidate == null || candidate.isEmpty()) continue;
            // The amount on FluidRequirement is authoritative. Snapshots only
            // need to retain the fluid and its NBT identity.
            FluidStack snapshot = new FluidStack(candidate.getFluid(), 1);
            if (candidate.hasTag()) snapshot.setTag(candidate.getTag());
            usable.add(snapshot);
        }
        if (usable.isEmpty()) return false;
        plan.addFluid(FluidRequirement.of(usable, amount));
        return true;
    }

    static long millibucketsRoundedUp(long fabricAmount) {
        // Fabric Transfer uses 81 units per mB in 1.20.1. Round a fractional
        // mB upward so the displayed requirement is sufficient for the recipe.
        long unitsPerMillibucket = FluidConstants.BUCKET / 1000;
        return fabricAmount / unitsPerMillibucket + (fabricAmount % unitsPerMillibucket == 0 ? 0 : 1);
    }

    static boolean sameRequestedItem(ItemStack requested, ItemStack actual) {
        if (requested == null || actual == null || requested.isEmpty() || actual.isEmpty()) return false;
        if (requested.getItem() != actual.getItem()) return false;
        // An item-only Wishlist target intentionally has no NBT. In that case
        // accept an NBT-specific recipe target and let the graph adapter block
        // the lossy projection. If the caller supplied NBT, it must match.
        return !requested.hasTag() || ItemStack.isSameItemSameTags(requested, actual);
    }
}
