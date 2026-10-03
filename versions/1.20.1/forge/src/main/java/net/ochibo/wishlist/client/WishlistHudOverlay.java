package net.ochibo.wishlist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.ochibo.wishlist.client.config.WishlistClientConfig;

public final class WishlistHudOverlay {
    private WishlistHudOverlay() {}

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("wishlist_materials", WishlistHudOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!WishlistClientConfig.hudEnabled() || mc.options.hideGui
                || mc.player == null || mc.level == null || mc.screen != null) return;
        HudMaterialPanel.Layout layout = HudMaterialPanel.layout(false);
        if (layout.rows().isEmpty() && layout.omission() == null) return;
        HudMaterialPanel.render(graphics, layout, HudMaterialPanel.bounds(layout,
                screenWidth, screenHeight, WishlistClientConfig.hudPositionX(),
                WishlistClientConfig.hudPositionY()));
    }
}
