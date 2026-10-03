package net.ochibo.wishlist.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** Keeps the JEI-only placement while avoiding visible screen widgets elsewhere. */
public final class WishlistButtonPlacement {
    public record Rect(int x, int y, int width, int height) {
        boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    private WishlistButtonPlacement() {}

    public static Rect forScreen(Screen screen, boolean jei, boolean emi) {
        List<Rect> occupied = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible)
                occupied.add(new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        }
        return place(screen.width, screen.height, jei, emi, occupied);
    }

    public static Rect place(int screenWidth, int screenHeight, boolean jei, boolean emi, List<Rect> occupied) {
        if (jei && !emi) return new Rect(50, screenHeight - 26, 20, 20);
        int startX = emi ? 46 : 2;
        int y = screenHeight - 22;
        for (int x = startX; x + 20 <= screenWidth; x += 22) {
            Rect choice = new Rect(x, y, 20, 20);
            if (occupied.stream().noneMatch(choice::overlaps)) return choice;
        }
        return new Rect(startX, y, 20, 20);
    }
}
