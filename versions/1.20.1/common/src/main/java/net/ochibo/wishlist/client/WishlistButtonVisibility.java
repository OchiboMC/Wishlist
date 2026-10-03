package net.ochibo.wishlist.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;

/** Shared display and input policy for the button that opens Wishlist. */
public final class WishlistButtonVisibility {
    private WishlistButtonVisibility() {}

    public static boolean forScreen(Screen screen) {
        return shouldShow(screen instanceof AbstractContainerScreen<?>,
                RecipeViewerRouter.isRecipeViewerScreen(screen),
                ClientWorkspaceController.get().pendingSelection().isPresent());
    }

    public static boolean shouldShow(boolean container, boolean recipeViewer, boolean selectingRecipe) {
        return (container || recipeViewer) && !(recipeViewer && selectingRecipe);
    }
}
