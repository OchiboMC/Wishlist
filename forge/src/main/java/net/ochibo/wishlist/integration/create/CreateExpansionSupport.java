package net.ochibo.wishlist.integration.create;

import net.ochibo.wishlist.api.recipe.AuxiliaryItemRequirement;
import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.api.recipe.ExpansionProblem;
import net.ochibo.wishlist.api.recipe.FluidRequirement;
import net.ochibo.wishlist.api.recipe.ItemRequirement;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class CreateExpansionSupport {
    private CreateExpansionSupport() {}

    static RecipeExpansionDecision blocked(String code, String message) {
        return RecipeExpansionDecision.blocked(ExpansionProblem.of(
                ResourceLocation.fromNamespaceAndPath("wishlist", code), message));
    }

    static List<ItemStack> candidates(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return List.of();
        // Forge custom Ingredient subclasses can encode predicates that are not
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

    static boolean addFluid(ExpansionPlan.Builder plan, List<FluidStack> alternatives, long amount) {
        List<FluidStack> usable = alternatives.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(FluidStack::copy)
                .toList();
        if (usable.isEmpty()) return false;
        plan.addFluid(FluidRequirement.of(usable, amount));
        return true;
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
