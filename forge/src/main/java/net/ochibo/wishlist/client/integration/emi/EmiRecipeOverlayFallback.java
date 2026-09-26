package net.ochibo.wishlist.client.integration.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.ochibo.wishlist.client.integration.emi.widget.SkipButtonWidget;
import net.ochibo.wishlist.client.integration.emi.widget.WishlistButtonWidget;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Draws Wishlist controls for EMI recipes that did not receive the decorator. */
public final class EmiRecipeOverlayFallback {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Field currentPage;

    private record Buttons(WishlistButtonWidget wishlist, SkipButtonWidget skip) {}

    public EmiRecipeOverlayFallback() {
        Field found = null;
        try {
            found = RecipeScreen.class.getDeclaredField("currentPage");
            found.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.warn("Could not access EMI's current recipe page for Wishlist buttons", error);
        }
        currentPage = found;
    }

    @SubscribeEvent
    public void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof RecipeScreen screen)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        for (Buttons buttons : buttons(screen)) {
            buttons.wishlist().render(graphics, mouseX, mouseY, event.getPartialTick());
            buttons.skip().render(graphics, mouseX, mouseY, event.getPartialTick());
            if (buttons.wishlist().getBounds().contains(mouseX, mouseY)) {
                EmiRenderHelper.drawTooltip(screen, EmiDrawContext.wrap(graphics),
                        buttons.wishlist().getTooltip(mouseX, mouseY), mouseX, mouseY);
            } else if (buttons.skip().getBounds().contains(mouseX, mouseY)) {
                EmiRenderHelper.drawTooltip(screen, EmiDrawContext.wrap(graphics),
                        buttons.skip().getTooltip(mouseX, mouseY), mouseX, mouseY);
            }
        }
    }

    @SubscribeEvent
    public void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof RecipeScreen screen)) return;
        for (Buttons buttons : buttons(screen)) {
            int mouseX = (int) event.getMouseX();
            int mouseY = (int) event.getMouseY();
            if (buttons.skip().getBounds().contains(mouseX, mouseY)
                    && buttons.skip().mouseClicked(mouseX, mouseY, event.getButton())) {
                event.setCanceled(true);
                return;
            }
            if (buttons.wishlist().getBounds().contains(mouseX, mouseY)
                    && buttons.wishlist().mouseClicked(mouseX, mouseY, event.getButton())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private List<Buttons> buttons(RecipeScreen screen) {
        if (currentPage == null) return List.of();
        List<Buttons> buttons = new ArrayList<>();
        try {
            if (!(currentPage.get(screen) instanceof List<?> groups)) return List.of();
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
