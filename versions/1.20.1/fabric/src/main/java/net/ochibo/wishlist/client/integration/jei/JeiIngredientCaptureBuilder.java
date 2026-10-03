package net.ochibo.wishlist.client.integration.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.TilingDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.ochibo.wishlist.client.integration.MaterialAdapterRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Captures the ingredients a JEI category registers through the public
 * {@link IRecipeLayoutBuilder} contract, including lookup-only (invisible)
 * ingredients that are deliberately absent from {@code IRecipeSlotsView}.
 *
 * <p>JEI marks these builder interfaces as non-extendable because they may gain
 * methods between JEI releases. Wishlist supports JEI 15.56.0.205 and later and isolates the
 * compatibility surface in this one class so a future API change fails at
 * compile time instead of silently corrupting material counts.</p>
 */
final class JeiIngredientCaptureBuilder implements IRecipeLayoutBuilder {
    record CaptureResult(List<CapturedSlot> visibleSlots, List<CapturedSlot> invisibleSlots) {
        CaptureResult {
            visibleSlots = List.copyOf(visibleSlots);
            invisibleSlots = List.copyOf(invisibleSlots);
        }

        boolean hasUnmodeledInvisibleIngredients(List<JeiRecipeSlot> renderedSlots) {
            for (CapturedSlot hidden : invisibleSlots) {
                if (hidden.role() == JeiIngredientRole.RENDER_ONLY || hidden.isEmpty()) continue;
                boolean duplicateOfRenderedSlot = renderedSlots.stream()
                        .anyMatch(rendered -> hidden.equivalentTo(rendered));
                if (!duplicateOfRenderedSlot) return true;
            }
            return false;
        }
    }

    record CapturedSlot(
            JeiIngredientRole role,
            List<JeiStackCandidate> candidates,
            int allIngredientCount,
            int itemIngredientCount,
            int blankIngredientCount) {
        CapturedSlot {
            candidates = candidates.stream()
                    .sorted(Comparator.comparing(JeiStackCandidate::itemId)
                            .thenComparingLong(JeiStackCandidate::amount)
                            .thenComparing(JeiStackCandidate::hasNbt))
                    .toList();
        }

        boolean isEmpty() {
            return allIngredientCount == 0 && blankIngredientCount == 0;
        }

        boolean equivalentTo(JeiRecipeSlot rendered) {
            if (rendered.role() != role
                    || rendered.allIngredientCount() != allIngredientCount
                    || rendered.itemIngredientCount() != itemIngredientCount
                    || rendered.blankIngredientCount() != blankIngredientCount) {
                return false;
            }
            List<JeiStackCandidate> renderedCandidates = rendered.candidates().stream()
                    .sorted(Comparator.comparing(JeiStackCandidate::itemId)
                            .thenComparingLong(JeiStackCandidate::amount)
                            .thenComparing(JeiStackCandidate::hasNbt))
                    .toList();
            return candidates.equals(renderedCandidates);
        }
    }

    private final IIngredientManager ingredientManager;
    private final List<CapturingSlotBuilder> visibleSlots = new ArrayList<>();
    private final List<CapturingSlotBuilder> invisibleSlots = new ArrayList<>();

    private JeiIngredientCaptureBuilder(IIngredientManager ingredientManager) {
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
    }

    static <T> CaptureResult capture(
            IRecipeCategory<T> category,
            T recipe,
            IFocusGroup focuses,
            IIngredientManager ingredientManager) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(focuses, "focuses");
        JeiIngredientCaptureBuilder builder = new JeiIngredientCaptureBuilder(ingredientManager);
        category.setRecipe(builder, recipe, focuses);
        return new CaptureResult(
                builder.visibleSlots.stream().map(CapturingSlotBuilder::snapshot).toList(),
                builder.invisibleSlots.stream().map(CapturingSlotBuilder::snapshot).toList());
    }

    @Override
    public IRecipeSlotBuilder addSlot(mezz.jei.api.recipe.RecipeIngredientRole role) {
        CapturingSlotBuilder slot = new CapturingSlotBuilder(ingredientManager, mapRole(role));
        visibleSlots.add(slot);
        return slot;
    }

    @SuppressWarnings("removal")
    @Override
    public IRecipeSlotBuilder addSlotToWidget(
            mezz.jei.api.recipe.RecipeIngredientRole role,
            mezz.jei.api.gui.widgets.ISlottedWidgetFactory<?> widgetFactory) {
        return addSlot(role);
    }

    @Override
    public IIngredientAcceptor<?> addInvisibleIngredients(mezz.jei.api.recipe.RecipeIngredientRole role) {
        CapturingSlotBuilder slot = new CapturingSlotBuilder(ingredientManager, mapRole(role));
        invisibleSlots.add(slot);
        return slot;
    }

    @Override
    public void moveRecipeTransferButton(int posX, int posY) {
        // Drawing-only metadata is intentionally ignored.
    }

    @Override
    public void setShapeless() {
        // Drawing-only metadata is intentionally ignored.
    }

    @Override
    public void setShapeless(int posX, int posY) {
        // Drawing-only metadata is intentionally ignored.
    }

    @Override
    public void createFocusLink(IIngredientAcceptor<?>... slots) {
        // Focus links affect rendering/filtering, not the un-focused ingredient set.
    }

    private static JeiIngredientRole mapRole(mezz.jei.api.recipe.RecipeIngredientRole role) {
        return switch (role) {
            case INPUT -> JeiIngredientRole.INPUT;
            case OUTPUT -> JeiIngredientRole.OUTPUT;
            case CATALYST -> JeiIngredientRole.CATALYST;
            case RENDER_ONLY -> JeiIngredientRole.RENDER_ONLY;
        };
    }

    private static final class CapturingSlotBuilder implements IRecipeSlotBuilder {
        private final IIngredientManager ingredientManager;
        private final JeiIngredientRole role;
        private final List<JeiStackCandidate> candidates = new ArrayList<>();
        private int allIngredientCount;
        private int itemIngredientCount;
        private int blankIngredientCount;

        private CapturingSlotBuilder(IIngredientManager ingredientManager, JeiIngredientRole role) {
            this.ingredientManager = ingredientManager;
            this.role = role;
        }

        CapturedSlot snapshot() {
            return new CapturedSlot(role, candidates, allIngredientCount, itemIngredientCount, blankIngredientCount);
        }

        @Override
        public <I> IRecipeSlotBuilder addIngredients(IIngredientType<I> ingredientType, List<@Nullable I> ingredients) {
            Objects.requireNonNull(ingredientType, "ingredientType");
            Objects.requireNonNull(ingredients, "ingredients");
            for (I ingredient : ingredients) {
                if (ingredient == null) {
                    blankIngredientCount++;
                } else {
                    ingredientManager.createTypedIngredient(ingredientType, ingredient)
                            .ifPresent(this::captureTyped);
                }
            }
            return this;
        }

        @Override
        public <I> IRecipeSlotBuilder addIngredient(IIngredientType<I> ingredientType, I ingredient) {
            Objects.requireNonNull(ingredientType, "ingredientType");
            Objects.requireNonNull(ingredient, "ingredient");
            ingredientManager.createTypedIngredient(ingredientType, ingredient)
                    .ifPresent(this::captureTyped);
            return this;
        }

        @Override
        public IRecipeSlotBuilder addIngredientsUnsafe(List<?> ingredients) {
            Objects.requireNonNull(ingredients, "ingredients");
            for (Object ingredient : ingredients) {
                if (ingredient == null) {
                    blankIngredientCount++;
                } else {
                    ingredientManager.createTypedIngredient(ingredient)
                            .ifPresent(this::captureTyped);
                }
            }
            return this;
        }

        @Override
        public IRecipeSlotBuilder addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
            Objects.requireNonNull(ingredients, "ingredients");
            ingredients.forEach(this::captureTyped);
            return this;
        }

        @Override
        public IRecipeSlotBuilder addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
            Objects.requireNonNull(ingredients, "ingredients");
            for (Optional<ITypedIngredient<?>> ingredient : ingredients) {
                if (ingredient.isPresent()) captureTyped(ingredient.orElseThrow());
                else blankIngredientCount++;
            }
            return this;
        }

        private void captureTyped(ITypedIngredient<?> typedIngredient) {
            allIngredientCount++;
            ItemStack itemStack = typedIngredient.getCastIngredient(VanillaTypes.ITEM_STACK);
            if (itemStack != null && !itemStack.isEmpty()) {
                itemIngredientCount++;
                candidates.add(new JeiStackCandidate(StackResolver.id(itemStack),
                        Math.max(1, itemStack.getCount()), itemStack.hasTag()));
                return;
            }
            IJeiFluidIngredient fluid = typedIngredient.getCastIngredient(FabricTypes.FLUID_STACK);
            if (fluid != null && fluid.getAmount() > 0) {
                itemIngredientCount++;
                candidates.add(new JeiStackCandidate(JeiFluidIdentity.id(fluid), fluid.getAmount(), fluid.getTag().isPresent()));
                return;
            }
            MaterialAdapterRegistry.read(typedIngredient).ifPresent(material -> {
                itemIngredientCount++;
                candidates.add(new JeiStackCandidate(material.key(), material.amount(),
                        net.ochibo.wishlist.core.model.ResourceIdentity.parse(material.key()).tag() != null));
            });
        }

        @Override
        public IRecipeSlotBuilder addFluidStack(Fluid fluid) {
            return addFluidStack(fluid, 1000, null);
        }

        @Override
        public IRecipeSlotBuilder addFluidStack(Fluid fluid, long amount) {
            return addFluidStack(fluid, amount, null);
        }

        @Override
        public IRecipeSlotBuilder addFluidStack(Fluid fluid, long amount, CompoundTag tag) {
            allIngredientCount++;
            if (fluid == null || amount <= 0) return this;
            itemIngredientCount++;
            candidates.add(new JeiStackCandidate(JeiFluidIdentity.id(fluid, tag), amount, tag != null && !tag.isEmpty()));
            return this;
        }

        @Override
        public IRecipeSlotBuilder setStandardSlotBackground() { return this; }

        @Override
        public IRecipeSlotBuilder setOutputSlotBackground() { return this; }

        @Override
        public IRecipeSlotBuilder setBackground(IDrawable background, int xOffset, int yOffset) { return this; }

        @Override
        public IRecipeSlotBuilder setOverlay(IDrawable overlay, int xOffset, int yOffset) { return this; }

        @Override
        public IRecipeSlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height) { return this; }

        @Override
        public IRecipeSlotBuilder setFluidRenderer(
                long capacity,
                boolean showCapacity,
                int width,
                int height,
                TilingDirection tilingDirection) {
            return this;
        }

        @Override
        public <T> IRecipeSlotBuilder setCustomRenderer(
                IIngredientType<T> ingredientType,
                IIngredientRenderer<T> ingredientRenderer) {
            return this;
        }

        @SuppressWarnings("removal")
        @Override
        public IRecipeSlotBuilder addTooltipCallback(
                mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback tooltipCallback) {
            return this;
        }

        @Override
        public IRecipeSlotBuilder addRichTooltipCallback(IRecipeSlotRichTooltipCallback tooltipCallback) { return this; }

        @Override
        public IRecipeSlotBuilder setSlotName(String slotName) { return this; }

        @Override
        public IRecipeSlotBuilder setPosition(int xPos, int yPos) { return this; }

        @Override
        public IRecipeSlotBuilder setPosition(
                int areaX,
                int areaY,
                int areaWidth,
                int areaHeight,
                HorizontalAlignment horizontalAlignment,
                VerticalAlignment verticalAlignment) {
            return this;
        }

        @Override
        public int getWidth() { return 16; }

        @Override
        public int getHeight() { return 16; }
    }
}
