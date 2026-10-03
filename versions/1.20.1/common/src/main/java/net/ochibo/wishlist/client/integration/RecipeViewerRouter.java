package net.ochibo.wishlist.client.integration;

import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.WishlistToasts;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.recipe.TransferReadiness;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;

public final class RecipeViewerRouter {
    /**
     * Typed boundary implemented only by the optional JEI plugin.
     *
     * <p>The main mod never links against JEI classes. JEI registers this bridge
     * once its runtime is available, so optional integration needs neither JEI
     * implementation reflection nor reflection back into Wishlist itself.</p>
     */
    public interface JeiBridge {
        boolean showRecipes(ItemStack stack);
        ItemStack hoveredItem(Screen screen);
        boolean isRecipesGuiOpen();
        boolean isRecipesGui(Screen screen);
        RecipeResolver recipeResolver();
        void beginRecipeButtonFrame(Screen screen);
        boolean handleRecipeButtonMousePress(Screen screen, double mouseX, double mouseY, int mouseButton);
        boolean canTransfer(RecipeKey key, AbstractContainerScreen<?> screen);
        boolean hasIngredients(RecipeKey key, AbstractContainerScreen<?> screen);
        boolean transfer(RecipeKey key, AbstractContainerScreen<?> screen);
    }


    public interface EmiBridge {
        boolean showRecipes(ItemStack stack);
        ItemStack hoveredItem(Screen screen);
        boolean isRecipesGuiOpen();
        boolean isRecipesGui(Screen screen);
        RecipeResolver recipeResolver();
        void beginRecipeButtonFrame(Screen screen);
        boolean handleRecipeButtonMousePress(Screen screen, double mouseX, double mouseY, int mouseButton);
        boolean canTransfer(RecipeKey key, AbstractContainerScreen<?> screen);
        boolean hasIngredients(RecipeKey key, AbstractContainerScreen<?> screen);
        boolean transfer(RecipeKey key, AbstractContainerScreen<?> screen);
    }

    private static Screen selectionReturnScreen;
    private static boolean selectionTransitionInProgress;
    private static Screen iconReturnScreen;
    private static boolean iconTransitionInProgress;
    private static ViewerKind lastUsed;
    private static volatile JeiBridge jeiBridge;
    private static volatile EmiBridge emiBridge;

    private RecipeViewerRouter() {}

    public static void registerJeiBridge(JeiBridge bridge) {
        jeiBridge = bridge;
    }

    public static void unregisterJeiBridge(JeiBridge bridge) {
        if (jeiBridge == bridge) jeiBridge = null;
    }

    public static void registerEmiBridge(EmiBridge bridge) {
        emiBridge = bridge;
    }

    public static void unregisterEmiBridge(EmiBridge bridge) {
        if (emiBridge == bridge) emiBridge = null;
    }

    public static boolean available() {
        return WishlistMod.isLoaded("jei") || WishlistMod.isLoaded("emi");
    }

    public static ItemStack hoveredViewerItem(Screen screen) {
        try {
            if (emiBridge != null && emiBridge.isRecipesGui(screen)) {
                ItemStack hovered = emiBridge.hoveredItem(screen);
                if (!hovered.isEmpty()) return hovered;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Optional viewers can be unavailable during reloads.
        }
        try {
            if (jeiBridge != null) {
                ItemStack hovered = jeiBridge.hoveredItem(screen);
                if (!hovered.isEmpty()) return hovered;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // The other viewer can still supply a hovered item.
        }
        try {
            if (emiBridge != null) return emiBridge.hoveredItem(screen);
        } catch (RuntimeException | LinkageError ignored) {
            // Fall back to the vanilla container slot.
        }
        return ItemStack.EMPTY;
    }

    public static boolean canTransfer(RecipeKey key, Screen parent) {
        return transferReadiness(key, parent) != TransferReadiness.UNSUPPORTED;
    }

    public static TransferReadiness transferReadiness(RecipeKey key, Screen parent) {
        if (!(parent instanceof AbstractContainerScreen<?> container) || key == null)
            return TransferReadiness.UNSUPPORTED;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != container.getMenu())
            return TransferReadiness.UNSUPPORTED;
        return TransferReadiness.combine(readinessEmi(key, container), readinessJei(key, container));
    }

    public static boolean transfer(RecipeKey key, Screen parent) {
        if (!(parent instanceof AbstractContainerScreen<?> container)
                || transferReadiness(key, parent) != TransferReadiness.READY) {
            WishlistToasts.transferFailed();
            return false;
        }
        Minecraft.getInstance().setScreen(parent);
        for (ViewerKind kind : ViewerKind.availableOrder(jeiBridge != null, emiBridge != null, lastUsed)) {
            try {
                if (kind == ViewerKind.EMI && readinessEmi(key, container) == TransferReadiness.READY
                        && emiBridge.transfer(key, container)) return true;
                if (kind == ViewerKind.JEI && readinessJei(key, container) == TransferReadiness.READY
                        && jeiBridge.transfer(key, container)) return true;
            } catch (RuntimeException | LinkageError ignored) {
                // An optional viewer or its transfer handler may be incompatible.
            }
        }
        WishlistToasts.transferFailed();
        return false;
    }

    private static TransferReadiness readinessEmi(RecipeKey key, AbstractContainerScreen<?> screen) {
        try {
            if (emiBridge == null || !emiBridge.canTransfer(key, screen)) return TransferReadiness.UNSUPPORTED;
            return emiBridge.hasIngredients(key, screen) ? TransferReadiness.READY : TransferReadiness.MISSING;
        } catch (RuntimeException | LinkageError ignored) {
            return TransferReadiness.UNSUPPORTED;
        }
    }

    private static TransferReadiness readinessJei(RecipeKey key, AbstractContainerScreen<?> screen) {
        try {
            if (jeiBridge == null || !jeiBridge.canTransfer(key, screen)) return TransferReadiness.UNSUPPORTED;
            return jeiBridge.hasIngredients(key, screen) ? TransferReadiness.READY : TransferReadiness.MISSING;
        } catch (RuntimeException | LinkageError ignored) {
            return TransferReadiness.UNSUPPORTED;
        }
    }

    public static boolean openForSelection(ClientWorkspaceController.SelectionRequest request, Screen returnScreen) {
        selectionReturnScreen = returnScreen;
        selectionTransitionInProgress = true;
        try {
            boolean opened = openRecipesFor(request.itemId());
            if (!opened) selectionReturnScreen = null;
            return opened;
        } finally {
            selectionTransitionInProgress = false;
        }
    }

    public static Screen selectionReturnScreen() {
        return selectionReturnScreen;
    }

    public static boolean selectionTransitionInProgress() {
        return selectionTransitionInProgress;
    }

    public static void clearSelectionReturnScreen() {
        selectionReturnScreen = null;
    }

    public static boolean openRecipesFromWishlist(String itemId, Screen returnScreen) {
        iconReturnScreen = returnScreen;
        iconTransitionInProgress = true;
        try {
            boolean opened = openRecipesFor(itemId);
            if (!opened) iconReturnScreen = null;
            return opened;
        } finally {
            iconTransitionInProgress = false;
        }
    }

    public static Screen iconReturnScreen() {
        return iconReturnScreen;
    }

    public static boolean iconTransitionInProgress() {
        return iconTransitionInProgress;
    }

    public static void clearIconReturnScreen() {
        iconReturnScreen = null;
    }

    public static boolean isRecipeViewerScreen(Screen screen) {
        if (screen == null) return false;
        JeiBridge jei = jeiBridge;
        EmiBridge emi = emiBridge;
        return (jei != null && jei.isRecipesGui(screen))
                || (emi != null && emi.isRecipesGui(screen));
    }

    public static boolean openRecipesFor(String itemId) {
        ItemStack stack = stack(itemId);
        if (stack.isEmpty()) return false;

        boolean jei = WishlistMod.isLoaded("jei");
        boolean emi = WishlistMod.isLoaded("emi");
        List<ViewerKind> order = ViewerKind.availableOrder(jei, emi, lastUsed);
        for (ViewerKind kind : order) {
            boolean opened = kind == ViewerKind.JEI ? openJei(stack) : openEmi(stack);
            if (opened) {
                lastUsed = kind;
                return true;
            }
        }
        return false;
    }

    public static boolean isJeiRecipesGuiOpen() {
        JeiBridge bridge = jeiBridge;
        return bridge != null && bridge.isRecipesGuiOpen();
    }

    public static Optional<RecipeResolver> jeiRecipeResolver() {
        JeiBridge bridge = jeiBridge;
        return bridge == null ? Optional.empty() : Optional.ofNullable(bridge.recipeResolver());
    }

    /** Starts a new visible-JEI-button frame before the current screen is rendered. */
    public static void beginJeiButtonFrame(Screen screen) {
        JeiBridge bridge = jeiBridge;
        if (bridge != null) bridge.beginRecipeButtonFrame(screen);
    }

    public static boolean isEmiRecipesGuiOpen() {
        EmiBridge bridge = emiBridge;
        return bridge != null && bridge.isRecipesGuiOpen();
    }

    public static Optional<RecipeResolver> emiRecipeResolver() {
        EmiBridge bridge = emiBridge;
        return bridge == null ? Optional.empty() : Optional.ofNullable(bridge.recipeResolver());
    }

    /** Starts a new visible-JEI-button frame before the current screen is rendered. */
    public static void beginEmiButtonFrame(Screen screen) {
        EmiBridge bridge = emiBridge;
        if (bridge != null) bridge.beginRecipeButtonFrame(screen);
    }

    /**
     * Gives JEI's Wishlist button a chance to handle mouse buttons that JEI's
     * standard icon-button widget does not dispatch (notably right click).
     */
    public static boolean handleJeiRecipeButtonMousePress(
            Screen screen,
            double mouseX,
            double mouseY,
            int mouseButton) {
        JeiBridge bridge = jeiBridge;
        return bridge != null && bridge.handleRecipeButtonMousePress(screen, mouseX, mouseY, mouseButton);
    }

    private static boolean openJei(ItemStack stack) {
        JeiBridge bridge = jeiBridge;
        return bridge != null && bridge.showRecipes(stack);
    }

    private static boolean openEmi(ItemStack stack) {
        EmiBridge bridge = emiBridge;
        return bridge != null && bridge.showRecipes(stack);
    }

    private static ItemStack stack(String itemId) {
        return StackResolver.stack(itemId);
    }
}
