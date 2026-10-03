package net.ochibo.wishlist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.ochibo.wishlist.client.config.WishlistClientConfig;

public final class WishlistHudOverlay {
    private WishlistHudOverlay() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!WishlistClientConfig.hudEnabled() || mc.options.hideGui
                || mc.player == null || mc.level == null || mc.screen != null) return;
        HudMaterialPanel.Layout layout = HudMaterialPanel.layout(false);
        if (layout.rows().isEmpty() && layout.omission() == null) return;
        HudMaterialPanel.render(graphics, layout, HudMaterialPanel.bounds(layout,
                graphics.guiWidth(), graphics.guiHeight(), WishlistClientConfig.hudPositionX(),
                WishlistClientConfig.hudPositionY()));
    }
}
