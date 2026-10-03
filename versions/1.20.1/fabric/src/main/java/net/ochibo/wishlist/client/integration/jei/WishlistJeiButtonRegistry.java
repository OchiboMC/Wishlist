package net.ochibo.wishlist.client.integration.jei;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.IdentityHashMap;
import java.util.Map;

final class WishlistJeiButtonRegistry {
    private record Hitbox(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }

    private final Map<WishlistJeiRecipeButtonController, Hitbox> visibleButtons = new IdentityHashMap<>();
    private Screen currentScreen;

    void beginFrame(Screen screen) {
        currentScreen = screen;
        visibleButtons.clear();
    }

    void record(WishlistJeiRecipeButtonController controller, Screen screen, Rect2i area) {
        if (screen == null || area.getWidth() <= 0 || area.getHeight() <= 0) return;

        if (screen != currentScreen) {
            beginFrame(screen);
        }
        visibleButtons.put(controller, new Hitbox(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
    }

    boolean handleMousePress(Screen screen, double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 1 || screen == null || screen != currentScreen) return false;

        for (Map.Entry<WishlistJeiRecipeButtonController, Hitbox> entry : visibleButtons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return entry.getKey().handleExternalMousePress(screen, mouseButton);
            }
        }
        return false;
    }

    void clear() {
        currentScreen = null;
        visibleButtons.clear();
    }
}
