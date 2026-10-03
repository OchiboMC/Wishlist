package net.ochibo.wishlist.client.integration.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.ochibo.wishlist.client.integration.emi.widget.SkipButtonWidget;
import net.ochibo.wishlist.client.integration.emi.widget.WishlistButtonWidget;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Draws Wishlist controls for EMI recipes that did not receive the decorator. */
public final class EmiRecipeOverlayFallback {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field CURRENT_PAGE = findCurrentPage();

    private record Buttons(WishlistButtonWidget wishlist, SkipButtonWidget skip) {}

    private EmiRecipeOverlayFallback() {}

    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!(screen instanceof RecipeScreen recipeScreen)) return;
        for (Buttons buttons : buttons(recipeScreen)) {
            buttons.wishlist().render(graphics, mouseX, mouseY, partialTick);
            buttons.skip().render(graphics, mouseX, mouseY, partialTick);
            if (buttons.wishlist().getBounds().contains(mouseX, mouseY)) {
                EmiRenderHelper.drawTooltip(recipeScreen, EmiDrawContext.wrap(graphics),
                        buttons.wishlist().getTooltip(mouseX, mouseY), mouseX, mouseY);
            } else if (buttons.skip().getBounds().contains(mouseX, mouseY)) {
                EmiRenderHelper.drawTooltip(recipeScreen, EmiDrawContext.wrap(graphics),
                        buttons.skip().getTooltip(mouseX, mouseY), mouseX, mouseY);
            }
        }
    }

    /** Returns true when Wishlist consumes the click. */
    public static boolean mousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (!(screen instanceof RecipeScreen recipeScreen)) return false;
        for (Buttons buttons : buttons(recipeScreen)) {
            int x = (int) mouseX;
            int y = (int) mouseY;
            if (buttons.skip().getBounds().contains(x, y)
                    && buttons.skip().mouseClicked(x, y, button)) return true;
            if (buttons.wishlist().getBounds().contains(x, y)
                    && buttons.wishlist().mouseClicked(x, y, button)) return true;
        }
        return false;
    }

    private static Field findCurrentPage() {
        try {
            Field field = RecipeScreen.class.getDeclaredField("currentPage");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.warn("Could not access EMI's current recipe page for Wishlist buttons", error);
            return null;
        }
    }

    private static List<Buttons> buttons(RecipeScreen screen) {
        if (CURRENT_PAGE == null) return List.of();
        List<Buttons> buttons = new ArrayList<>();
        try {
            if (!(CURRENT_PAGE.get(screen) instanceof List<?> groups)) return List.of();
            for (Object value : groups) {
                if (!(value instanceof WidgetGroup group) || group.recipe == null
                        || group.widgets.stream().anyMatch(WishlistButtonWidget.class::isInstance)) continue;
                var recipe = group.recipe;
                int rightButtons = EmiNativeButtonCount.count(
                        EmiConfig.recipeFillButton, EmiRecipeFiller.isSupported(recipe),
                        recipe.supportsRecipeTree(), EmiConfig.recipeTreeButton, EmiConfig.recipeDefaultButton);
                var position = EmiWishlistButtonPosition.place(
                        recipe.getDisplayWidth(), recipe.getDisplayHeight(), rightButtons);
                int x = group.x() + position.x();
                int y = group.y() + position.y();
                buttons.add(new Buttons(new WishlistButtonWidget(x, y, recipe),
                        new SkipButtonWidget(x, y - 14)));
            }
        } catch (IllegalAccessException | RuntimeException error) {
            LOGGER.warn("Could not draw Wishlist buttons on EMI's recipe page", error);
            return List.of();
        }
        return buttons;
    }
}
