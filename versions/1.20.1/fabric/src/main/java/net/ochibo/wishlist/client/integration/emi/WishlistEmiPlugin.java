package net.ochibo.wishlist.client.integration.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.model.RecipeKey;


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
    public ItemStack hoveredItem(Screen screen) {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return ItemStack.EMPTY;
        for (var candidate : hovered.getStack().getEmiStacks()) {
            ItemStack item = candidate.getItemStack();
            if (!item.isEmpty()) return item;
        }
        return ItemStack.EMPTY;
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
    public boolean canTransfer(RecipeKey key, AbstractContainerScreen<?> screen) {
        EmiRecipe recipe = emiRecipe(key);
        if (recipe == null) return false;
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, screen);
        return handler != null && handler.supportsRecipe(recipe);
    }

    @Override
    public boolean hasIngredients(RecipeKey key, AbstractContainerScreen<?> screen) {
        EmiRecipe recipe = emiRecipe(key);
        return recipe != null && hasIngredients(recipe, screen);
    }

    private static <T extends AbstractContainerMenu> boolean hasIngredients(
            EmiRecipe recipe, AbstractContainerScreen<T> screen) {
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, screen);
        if (handler == null || !handler.supportsRecipe(recipe)) return false;
        var context = new EmiCraftContext<>(screen, handler.getInventory(screen),
                EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1);
        return handler.canCraft(recipe, context);
    }

    @Override
    public boolean transfer(RecipeKey key, AbstractContainerScreen<?> screen) {
        EmiRecipe recipe = emiRecipe(key);
        return recipe != null && EmiRecipeFiller.performFill(recipe, screen,
                EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1);
    }

    private static EmiRecipe emiRecipe(RecipeKey key) {
        if (EmiApi.getRecipeManager() == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(key.id());
        return id == null ? null : EmiApi.getRecipeManager().getRecipe(id);
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
