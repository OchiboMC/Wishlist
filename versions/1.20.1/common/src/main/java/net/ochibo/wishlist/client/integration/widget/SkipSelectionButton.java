package net.ochibo.wishlist.client.integration.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.ochibo.wishlist.client.WishlistClient;

public final class SkipSelectionButton {

    private SkipSelectionButton() {}

    public static void draw(GuiGraphics graphics, int x, int y, boolean hovered, boolean enabled){
        int yoffset = 0;
        if(hovered)yoffset = 12;
        if(!enabled)yoffset = 24;
        graphics.blit(WishlistClient.WIDGETS, x, y, 12, 12, 0, 64 + yoffset, 12, 12, 256, 256);
    }

    public static void drawIcon(GuiGraphics graphics, int x, int y){
        graphics.blit(WishlistClient.WIDGETS, x, y, 12, 12, 16, 64, 12, 12, 256, 256);
    }
}
