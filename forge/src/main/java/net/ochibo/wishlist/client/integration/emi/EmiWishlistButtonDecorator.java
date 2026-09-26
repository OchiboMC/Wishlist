package net.ochibo.wishlist.client.integration.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeDecorator;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.ochibo.wishlist.client.integration.emi.widget.WishlistButtonWidget;
import net.ochibo.wishlist.client.integration.emi.widget.SkipButtonWidget;

public class EmiWishlistButtonDecorator implements EmiRecipeDecorator {
    @Override
    public void decorateRecipe(EmiRecipe recipe, WidgetHolder widgets) {
        // EMI's decorator API does not expose its visible side buttons. These are
        // the same conditions used by RecipeDisplay in the supported EMI version.
        int rightButtons = EmiNativeButtonCount.count(
                EmiConfig.recipeFillButton, EmiRecipeFiller.isSupported(recipe),
                recipe.supportsRecipeTree(), EmiConfig.recipeTreeButton, EmiConfig.recipeDefaultButton);
        var position = EmiWishlistButtonPosition.place(recipe.getDisplayWidth(), recipe.getDisplayHeight(), rightButtons);
        widgets.add(new WishlistButtonWidget(position.x(), position.y(), recipe));
        widgets.add(new SkipButtonWidget(position.x(), position.y() - 14));
    }
}
