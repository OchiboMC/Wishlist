package net.ochibo.wishlist.client.integration;

import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
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
        boolean isRecipesGuiOpen();
        boolean isRecipesGui(Screen screen);
        RecipeResolver recipeResolver();
        void beginRecipeButtonFrame(Screen screen);
        boolean handleRecipeButtonMousePress(Screen screen, double mouseX, double mouseY, int mouseButton);
    }


    public interface EmiBridge {
        boolean showRecipes(ItemStack stack);
        boolean isRecipesGuiOpen();
        boolean isRecipesGui(Screen screen);
        RecipeResolver recipeResolver();
        void beginRecipeButtonFrame(Screen screen);
        boolean handleRecipeButtonMousePress(Screen screen, double mouseX, double mouseY, int mouseButton);
    }

    private static Screen selectionReturnScreen;
    private static boolean selectionTransitionInProgress;
    private static Screen iconReturnScreen;
    private static boolean iconTransitionInProgress;
    private static ViewerPreference preference;
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

    public static ViewerPreference preference() {
        if (preference == null) preference = preferenceStore().load();
        return preference;
    }

    public static ViewerPreference cyclePreference() {
        ViewerPreference current = preference();
        preference = switch (current) {
            case AUTO -> ViewerPreference.JEI;
            case JEI -> ViewerPreference.EMI;
            case EMI -> ViewerPreference.AUTO;
        };
        try {
            preferenceStore().save(preference);
        } catch (IOException ignored) {
            // The in-memory preference still remains usable for this session.
        }
        return preference;
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
        List<ViewerKind> order = preference().order(jei, emi, lastUsed);
        for (ViewerKind kind : order) {
            boolean opened = kind == ViewerKind.JEI ? openJei(stack) : openEmi(stack);
            if (opened) {
                lastUsed = kind;
                return true;
            }
        }
        return false;
    }

    private static ViewerPreferenceStore preferenceStore() {
        return new ViewerPreferenceStore(FMLPaths.CONFIGDIR.get().resolve("wishlist").resolve("client.properties"));
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
