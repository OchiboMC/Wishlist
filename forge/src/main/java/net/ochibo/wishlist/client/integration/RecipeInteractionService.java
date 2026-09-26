package net.ochibo.wishlist.client.integration;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.ui.DetailedAddScreen;
import net.ochibo.wishlist.client.ui.WishlistSelectScreen;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Shared recipe-button behavior for Vanilla, JEI, and optional recipe viewers.
 *
 * <p>Viewer integrations own only placement/input adaptation. Conversion,
 * selection validation, tooltips, and Wishlist actions live here so that JEI's
 * native button integration and Forge overlays behave identically.</p>
 */
public final class RecipeInteractionService {
    public record State(Optional<ResolvedRecipe> recipe, boolean enabled, String tooltipKey) {}

    private RecipeInteractionService() {}

    public static State state(Object rawRecipe) {
        return state(ExternalRecipeConverter.convert(rawRecipe));
    }

    public static State state(Optional<ResolvedRecipe> converted) {
        if (converted.isEmpty()) {
            return new State(Optional.empty(), false, "screen.wishlist.unreadable_recipe");
        }

        ResolvedRecipe recipe = converted.orElseThrow();
        if (!recipe.supported()) {
            return new State(converted, false, "screen.wishlist.unsupported");
        }

        Optional<ClientWorkspaceController.SelectionRequest> pending =
                ClientWorkspaceController.get().pendingSelection();
        if (pending.isPresent() &&
                pending.orElseThrow().node().ingredient().candidates().stream()
                        .noneMatch(candidate -> ResourceIdentity.matches(candidate, recipe.outputItemId()))) {
            return new State(converted, false, "screen.wishlist.wrong_selection_output");
        }

        return new State(
                converted,
                true,
                pending.isPresent() ? "screen.wishlist.select_this_recipe" : "screen.wishlist.overlay_help");
    }

    public static Component tooltip(State state) {
        Component text = Component.translatable(state.tooltipKey());
        return state.enabled() ? text : text.copy().withStyle(ChatFormatting.GRAY);
    }

    /** Returns whether Wishlist owns this mouse button for the current interaction state. */
    public static boolean canHandleClick(int mouseButton) {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        if (controller.pendingSelection().isPresent()) {
            return mouseButton == 0;
        }

        return OverlayActionResolver.resolve(
                mouseButton,
                Screen.hasShiftDown(),
                controller.workspace().wishlists().size()) != OverlayClickAction.NONE;
    }

    public static boolean canSkipAutomaticSelection() {
        return ClientWorkspaceController.get().pendingSelection()
                .map(ClientWorkspaceController.SelectionRequest::automatic).orElse(false);
    }

    public static boolean skipAutomaticSelection() {
        if (!canSkipAutomaticSelection()) return false;
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        Screen returnScreen = RecipeViewerRouter.selectionReturnScreen();
        Optional<ClientWorkspaceController.SelectionRequest> next = controller.skipPendingSelection();
        if (next.isPresent()) {
            if (!RecipeViewerRouter.openForSelection(next.orElseThrow(), returnScreen)) {
                controller.cancelPendingSelection();
                RecipeViewerRouter.clearSelectionReturnScreen();
                Minecraft.getInstance().setScreen(returnScreen);
            }
        } else {
            RecipeViewerRouter.clearSelectionReturnScreen();
            Minecraft.getInstance().setScreen(returnScreen);
        }
        return true;
    }

    /** Executes a Wishlist recipe-button action. */
    public static boolean handleClick(Screen sourceScreen, ResolvedRecipe recipe, int mouseButton) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorkspaceController controller = ClientWorkspaceController.get();

        if (controller.pendingSelection().isPresent()) {
            if (mouseButton != 0) return false;
            Screen returnScreen = RecipeViewerRouter.selectionReturnScreen();
            Optional<ClientWorkspaceController.SelectionRequest> next = controller.acceptPendingSelection(recipe);
            if (next.isPresent()) {
                if (!RecipeViewerRouter.openForSelection(next.orElseThrow(), returnScreen)) {
                    controller.cancelPendingSelection();
                    RecipeViewerRouter.clearSelectionReturnScreen();
                    mc.setScreen(returnScreen);
                }
            } else {
                RecipeViewerRouter.clearSelectionReturnScreen();
                mc.setScreen(returnScreen);
            }
            return true;
        }

        OverlayClickAction action = OverlayActionResolver.resolve(
                mouseButton,
                Screen.hasShiftDown(),
                controller.workspace().wishlists().size());
        return switch (action) {
            case SELECT_WISHLIST -> {
                mc.setScreen(new WishlistSelectScreen(sourceScreen, recipe, 1));
                yield true;
            }
            case QUICK_ADD -> {
                controller.quickAdd(recipe, 1);
                yield true;
            }
            case DETAILS -> {
                mc.setScreen(new DetailedAddScreen(sourceScreen, recipe));
                yield true;
            }
            case NONE -> false;
        };
    }
}
