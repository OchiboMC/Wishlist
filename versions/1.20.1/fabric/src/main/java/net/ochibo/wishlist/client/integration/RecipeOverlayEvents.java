package net.ochibo.wishlist.client.integration;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.ui.DetailedAddScreen;
import net.ochibo.wishlist.client.ui.WishlistSelectScreen;
import net.ochibo.wishlist.client.mixin.ContainerScreenAccessor;
import net.ochibo.wishlist.client.mixin.RecipeBookComponentAccessor;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.item.crafting.Recipe;
import net.ochibo.wishlist.client.integration.widget.AddToWishlistButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Draws Wishlist's Vanilla recipe button. JEI and EMI use their native widget APIs. */
public final class RecipeOverlayEvents {

    private record OverlayTarget(Optional<ResolvedRecipe> recipe, OverlayGeometry.Rect button) {}

    public static void renderPre(Screen screen) {
        // JEI owns drawing and left-click input for its native button. Delimit the
        // public button hitboxes captured by drawExtras so right-click can be kept
        // compatible without inspecting JEI implementation objects.
        RecipeViewerRouter.beginJeiButtonFrame(screen);
        RecipeViewerRouter.beginEmiButtonFrame(screen);
    }

    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        ExpandProgressOverlay.render(screen, graphics);
        List<OverlayTarget> targets = targets(screen);
        if (targets.isEmpty()) return;
        for (OverlayTarget target : targets) {
            RecipeInteractionService.State state = RecipeInteractionService.state(target.recipe());
            boolean hovered = target.button().contains(mouseX, mouseY);
            AddToWishlistButton.draw(graphics, target.button().x(),target.button().y(),hovered,state.enabled());
            if (hovered) {
                graphics.renderTooltip(Minecraft.getInstance().font, RecipeInteractionService.tooltip(state), mouseX, mouseY);
            }
        }
    }

    public static boolean mousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (RecipeViewerRouter.handleJeiRecipeButtonMousePress(
                screen, mouseX, mouseY, button)) {
            return true;
        }

        List<OverlayTarget> targets = targets(screen);
        for (OverlayTarget target : targets) {
            if (!target.button().contains(mouseX, mouseY)) continue;

            RecipeInteractionService.State state = RecipeInteractionService.state(target.recipe());
            if (!state.enabled() || state.recipe().isEmpty()) {
                return true; // The disabled button still owns its small visual area.
            }

            if (RecipeInteractionService.handleClick(screen, state.recipe().orElseThrow(), button)) {
                return true;
            }
            return false;
        }
        return false;
    }

    public static Screen opening(Screen current, Screen next) {
        Screen selected = cancelSelection(current);
        if (selected != null) return selected;
        Screen returnScreen = RecipeViewerRouter.iconReturnScreen();
        if (returnScreen == null || !isRecipeViewerScreen(current)
                || RecipeViewerRouter.iconTransitionInProgress()
                || isRecipeViewerScreen(next)
                || next instanceof DetailedAddScreen || next instanceof WishlistSelectScreen) return next;
        RecipeViewerRouter.clearIconReturnScreen();
        return next != returnScreen ? returnScreen : next;
    }

    private static Screen cancelSelection(Screen current) {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        boolean cancel = SelectionClosePolicy.shouldCancel(
                current != null && isRecipeViewerScreen(current),
                controller.pendingSelection().isPresent(),
                RecipeViewerRouter.selectionTransitionInProgress());
        if (!cancel) return null;

        Screen returnScreen = RecipeViewerRouter.selectionReturnScreen();
        controller.cancelPendingSelection();
        RecipeViewerRouter.clearSelectionReturnScreen();
        if (returnScreen != null && returnScreen != current) {
            return returnScreen;
        }
        return null;
    }

    private static List<OverlayTarget> targets(Screen screen) {
        List<OverlayTarget> result = new ArrayList<>();
        vanillaTarget(screen).ifPresent(result::add);
        return List.copyOf(result);
    }


    private static boolean isRecipeViewerScreen(Screen screen) {
        return RecipeViewerRouter.isRecipeViewerScreen(screen);
    }


    private static Optional<OverlayTarget> vanillaTarget(Screen screen) {
        if (!(screen instanceof RecipeUpdateListener listener)) return Optional.empty();
        if (!(screen instanceof AbstractContainerScreen<?> container)) return Optional.empty();

        GhostRecipe ghost = ((RecipeBookComponentAccessor) listener.getRecipeBookComponent()).wishlist$getGhostRecipe();
        Recipe<?> recipe = ghost.getRecipe();
        if (ghost.size() <= 0 || recipe == null) return Optional.empty();

        ContainerScreenAccessor geometry = (ContainerScreenAccessor) container;
        int x = geometry.wishlist$getLeftPos();
        int y = geometry.wishlist$getTopPos();
        int width = geometry.wishlist$getImageWidth();
        OverlayGeometry.Rect button = OverlayGeometry.topRight(x, y, width,
                geometry.wishlist$getImageHeight(), RecipeOverlayStyle.BUTTON_SIZE, 4);
        return Optional.of(new OverlayTarget(ExternalRecipeConverter.convert(recipe), button));
    }
}
