package net.ochibo.wishlist.client.integration.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.recipe.RecipeResolver;


@EmiEntrypoint
public class WishlistEmiPlugin implements RecipeViewerRouter.EmiBridge, EmiPlugin {

    private volatile EmiGenericRecipeResolver recipeResolver;
    @Override
    public boolean showRecipes(ItemStack stack) {
        if (recipeResolver == null || stack.isEmpty() || EmiApi.getRecipeManager() == null
                || EmiApi.getRecipeManager().getRecipesByOutput(EmiStack.of(stack)).isEmpty()) return false;
        EmiApi.displayRecipes(EmiStack.of(stack));
        return true;
    }

    @Override
    public boolean isRecipesGuiOpen() {
        return isEmiScreen(Minecraft.getInstance().screen);
    }

    @Override
    public boolean isRecipesGui(Screen screen) {
        return isEmiScreen(screen);
    }

    @Override
    public RecipeResolver recipeResolver() {
        return recipeResolver;
    }

    @Override
    public void beginRecipeButtonFrame(Screen screen) {

    }

    @Override
    public boolean handleRecipeButtonMousePress(Screen screen, double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    @Override
    public void register(EmiRegistry registry) {
        recipeResolver = new EmiGenericRecipeResolver();
        registry.addRecipeDecorator(new EmiWishlistButtonDecorator());
        RecipeViewerRouter.registerEmiBridge(this);
    }

    private static boolean isEmiScreen(Screen screen) {
        return screen != null && screen.getClass().getName().equals("dev.emi.emi.screen.RecipeScreen");
    }
}
