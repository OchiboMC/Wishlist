package net.ochibo.wishlist.client.integration.jei;

import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** Wishlist's supported JEI integration. No JEI implementation classes are accessed. */
@JeiPlugin
public final class WishlistJeiPlugin implements IModPlugin, RecipeViewerRouter.JeiBridge {
    private final WishlistJeiButtonRegistry buttonRegistry = new WishlistJeiButtonRegistry();
    private volatile IJeiRuntime runtime;
    private volatile JeiGenericRecipeResolver recipeResolver;

    @Override
    public ResourceLocation getPluginUid() {
        return Objects.requireNonNull(ResourceLocation.tryBuild(WishlistMod.MOD_ID, "jei"));
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(new IRecipeButtonControllerFactory() {
            @Override
            public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
                return new WishlistJeiRecipeButtonController(resolveRecipe(recipeLayoutDrawable), buttonRegistry);
            }
        });
        registration.addRecipeButtonFactory(new IRecipeButtonControllerFactory() {
            @Override
            public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
                return new WishlistJeiSkipButtonController();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        recipeResolver = new JeiGenericRecipeResolver(jeiRuntime);
        RecipeViewerRouter.registerJeiBridge(this);
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeViewerRouter.unregisterJeiBridge(this);
        buttonRegistry.clear();
        recipeResolver = null;
        runtime = null;
    }

    @Override
    public boolean isRecipesGuiOpen() {
        IJeiRuntime rt = runtime;
        return rt != null && rt.getRecipesGui().getParentScreen().isPresent();
    }

    @Override
    public boolean isRecipesGui(Screen screen) {
        IJeiRuntime rt = runtime;
        return screen != null && rt != null && screen == rt.getRecipesGui();
    }

    @Override
    public RecipeResolver recipeResolver() {
        return recipeResolver;
    }

    @Override
    public boolean canTransfer(RecipeKey key, AbstractContainerScreen<?> screen) {
        JeiGenericRecipeResolver resolver = recipeResolver;
        return resolver != null && resolver.canTransfer(key, screen);
    }

    @Override
    public boolean hasIngredients(RecipeKey key, AbstractContainerScreen<?> screen) {
        JeiGenericRecipeResolver resolver = recipeResolver;
        return resolver != null && resolver.hasIngredients(key, screen);
    }

    @Override
    public boolean transfer(RecipeKey key, AbstractContainerScreen<?> screen) {
        JeiGenericRecipeResolver resolver = recipeResolver;
        return resolver != null && resolver.transfer(key, screen);
    }

    @Override
    public boolean showRecipes(ItemStack stack) {
        IJeiRuntime rt = runtime;
        if (rt == null || stack.isEmpty()) return false;
        var focus = rt.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK,
                stack);
        rt.getRecipesGui().show(focus);
        return true;
    }

    @Override
    public ItemStack hoveredItem(Screen screen) {
        IJeiRuntime rt = runtime;
        if (rt == null) return ItemStack.EMPTY;
        if (isRecipesGui(screen)) {
            Optional<ItemStack> recipeItem = rt.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
            if (recipeItem.isPresent()) return recipeItem.orElseThrow();
        }
        ItemStack listItem = rt.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (listItem != null && !listItem.isEmpty()) return listItem;
        ItemStack bookmarkItem = rt.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        return bookmarkItem == null ? ItemStack.EMPTY : bookmarkItem;
    }

    @Override
    public void beginRecipeButtonFrame(Screen screen) {
        buttonRegistry.beginFrame(screen);
    }

    @Override
    public boolean handleRecipeButtonMousePress(
            Screen screen,
            double mouseX,
            double mouseY,
            int mouseButton) {
        return buttonRegistry.handleMousePress(screen, mouseX, mouseY, mouseButton);
    }

    private <T> Optional<ResolvedRecipe> resolveRecipe(IRecipeLayoutDrawable<T> layout) {
        JeiGenericRecipeResolver resolver = recipeResolver;
        if (resolver == null) return Optional.empty();
        String target = ClientWorkspaceController.get().pendingSelection()
                .map(ClientWorkspaceController.SelectionRequest::itemId)
                .orElse("");
        return resolver.resolveLayout(layout, target);
    }
}
