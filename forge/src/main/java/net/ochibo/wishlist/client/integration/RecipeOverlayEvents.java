package net.ochibo.wishlist.client.integration;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.ui.DetailedAddScreen;
import net.ochibo.wishlist.client.ui.WishlistSelectScreen;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.ochibo.wishlist.client.integration.widget.AddToWishlistButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Draws Wishlist's Vanilla recipe button. JEI and EMI use their native widget APIs. */
public final class RecipeOverlayEvents {

    private record OverlayTarget(Optional<ResolvedRecipe> recipe, OverlayGeometry.Rect button) {}

    @SubscribeEvent
    public void renderPre(ScreenEvent.Render.Pre event) {
        // JEI owns drawing and left-click input for its native button. Delimit the
        // public button hitboxes captured by drawExtras so right-click can be kept
        // compatible without inspecting JEI implementation objects.
        RecipeViewerRouter.beginJeiButtonFrame(event.getScreen());
    }

    @SubscribeEvent
    public void render(ScreenEvent.Render.Post event) {
        List<OverlayTarget> targets = targets(event.getScreen());
        if (targets.isEmpty()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        for (OverlayTarget target : targets) {
            RecipeInteractionService.State state = RecipeInteractionService.state(target.recipe());
            boolean hovered = target.button().contains(mouseX, mouseY);
            AddToWishlistButton.draw(graphics, target.button().x(),target.button().y(),hovered,state.enabled());
            if (hovered) {
                graphics.renderTooltip(Minecraft.getInstance().font, RecipeInteractionService.tooltip(state), mouseX, mouseY);
            }
        }
    }

    @SubscribeEvent
    public void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (RecipeViewerRouter.handleJeiRecipeButtonMousePress(
                event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }

        List<OverlayTarget> targets = targets(event.getScreen());
        for (OverlayTarget target : targets) {
            if (!target.button().contains(event.getMouseX(), event.getMouseY())) continue;

            RecipeInteractionService.State state = RecipeInteractionService.state(target.recipe());
            if (!state.enabled() || state.recipe().isEmpty()) {
                event.setCanceled(true); // The disabled button still owns its small visual area.
                return;
            }

            if (RecipeInteractionService.handleClick(event.getScreen(), state.recipe().orElseThrow(), event.getButton())) {
                event.setCanceled(true);
            }
            return;
        }
    }

    @SubscribeEvent
    public void opening(ScreenEvent.Opening event) {
        if (cancelSelection(event)) return;
        Screen returnScreen = RecipeViewerRouter.iconReturnScreen();
        Screen current = event.getCurrentScreen();
        Screen next = event.getNewScreen();
        if (returnScreen == null || !isRecipeViewerScreen(current)
                || RecipeViewerRouter.iconTransitionInProgress()
                || isRecipeViewerScreen(next)
                || next instanceof DetailedAddScreen || next instanceof WishlistSelectScreen) return;
        RecipeViewerRouter.clearIconReturnScreen();
        if (next != returnScreen) event.setNewScreen(returnScreen);
    }

    private boolean cancelSelection(ScreenEvent.Opening event){
        Screen current = event.getCurrentScreen();
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        boolean cancel = SelectionClosePolicy.shouldCancel(
                current != null && isRecipeViewerScreen(current),
                controller.pendingSelection().isPresent(),
                RecipeViewerRouter.selectionTransitionInProgress());
        if (!cancel) return false;

        Screen returnScreen = RecipeViewerRouter.selectionReturnScreen();
        controller.cancelPendingSelection();
        RecipeViewerRouter.clearSelectionReturnScreen();
        if (returnScreen != null && returnScreen != current) {
            event.setNewScreen(returnScreen);
            return true;
        }
        return false;
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

        GhostRecipe ghost = listener.getRecipeBookComponent().ghostRecipe;
        Recipe<?> recipe = ghost.getRecipe();
        if (ghost.size() <= 0 || recipe == null) return Optional.empty();

        int x = container.getGuiLeft();
        int y = container.getGuiTop();
        int width = container.getXSize();
        OverlayGeometry.Rect button = OverlayGeometry.topRight(x, y, width, container.getYSize(), RecipeOverlayStyle.BUTTON_SIZE, 4);
        return Optional.of(new OverlayTarget(ExternalRecipeConverter.convert(recipe), button));
    }
}
