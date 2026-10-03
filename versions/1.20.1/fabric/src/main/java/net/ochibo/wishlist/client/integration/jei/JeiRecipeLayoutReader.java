package net.ochibo.wishlist.client.integration.jei;

import net.ochibo.wishlist.core.model.RecipeKey;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.ochibo.wishlist.client.integration.MaterialAdapterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Converts a JEI public recipe layout into Wishlist's dependency-light layout snapshot. */
final class JeiRecipeLayoutReader {
    private JeiRecipeLayoutReader() {}

    static <T> Optional<JeiRecipeSnapshot> read(
            IRecipeLayoutDrawable<T> layout,
            IIngredientManager ingredientManager,
            IFocusGroup focusGroup) {
        Objects.requireNonNull(layout, "layout");
        IRecipeCategory<T> category = layout.getRecipeCategory();
        T recipe = layout.getRecipe();
        ResourceLocation registryName = category.getRegistryName(recipe);
        if (registryName == null) return Optional.empty();

        List<JeiRecipeSlot> slots = new ArrayList<>();
        for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews()) {
            int allIngredientCount = 0;
            int blankIngredientCount = 0;
            for (var ingredient : slot.getAllIngredientsList()) {
                if (ingredient == null) blankIngredientCount++;
                else allIngredientCount++;
            }

            List<JeiStackCandidate> candidates = new ArrayList<>();
            for (var typed : slot.getAllIngredientsList()) {
                if (typed == null) continue;
                ItemStack item = typed.getCastIngredient(VanillaTypes.ITEM_STACK);
                if (item != null && !item.isEmpty()) {
                    candidates.add(new JeiStackCandidate(StackResolver.id(item), item.getCount(), item.hasTag()));
                    continue;
                }
                IJeiFluidIngredient fluid = typed.getCastIngredient(FabricTypes.FLUID_STACK);
                if (fluid != null && fluid.getAmount() > 0) {
                    candidates.add(new JeiStackCandidate(JeiFluidIdentity.id(fluid), fluid.getAmount(), fluid.getTag().isPresent()));
                    continue;
                }
                MaterialAdapterRegistry.read(typed).ifPresent(material -> candidates.add(
                        new JeiStackCandidate(material.key(), material.amount(),
                                net.ochibo.wishlist.core.model.ResourceIdentity.parse(material.key()).tag() != null)));
            }

            JeiIngredientRole role = switch (slot.getRole()) {
                case INPUT -> JeiIngredientRole.INPUT;
                case OUTPUT -> JeiIngredientRole.OUTPUT;
                case CATALYST -> JeiIngredientRole.CATALYST;
                case RENDER_ONLY -> JeiIngredientRole.RENDER_ONLY;
            };
            String displayKey = slot.getSlotName()
                    .orElseGet(() -> candidates.isEmpty() ? role.name().toLowerCase() : candidates.get(0).itemId());

            slots.add(new JeiRecipeSlot(
                    role,
                    displayKey,
                    candidates,
                    allIngredientCount,
                    candidates.size(),
                    blankIngredientCount));
        }

        boolean unsafeInvisibleIngredients;
        try {
            JeiIngredientCaptureBuilder.CaptureResult capture = JeiIngredientCaptureBuilder.capture(
                    category, recipe, focusGroup, ingredientManager);
            unsafeInvisibleIngredients = capture.hasUnmodeledInvisibleIngredients(slots);
        } catch (RuntimeException | LinkageError ignored) {
            // If the category cannot be replayed safely, do not guess that its visible slots are complete.
            unsafeInvisibleIngredients = true;
        }
        return Optional.of(new JeiRecipeSnapshot(
                new RecipeKey(registryName.toString()), slots, unsafeInvisibleIngredients));
    }
}
