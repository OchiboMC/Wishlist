package net.ochibo.wishlist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.ochibo.wishlist.client.ui.WishlistScreen;

/** Opens Wishlist from the bottom left of inventory and container screens. */
public final class WishlistContainerButton {
    private static final int SIZE = 20;

    private static int x() {
        if(ModList.get().isLoaded("emi")) return 46;
        else if(ModList.get().isLoaded("jei")) return 50;
        return 2;
    }

    private static int y(Screen screen) {
        if(ModList.get().isLoaded("emi")) return screen.height - 22;
        if(ModList.get().isLoaded("jei")) return screen.height - 26;
        return screen.height - 22;
    }

    private static boolean inside(Screen screen, double mouseX, double mouseY) {
        return mouseX >= x() && mouseX < x() + SIZE && mouseY >= y(screen) && mouseY < y(screen) + SIZE;
    }

    @SubscribeEvent
    public void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        boolean hovered = inside(screen, event.getMouseX(), event.getMouseY());
        event.getGuiGraphics().blit(WishlistClient.WIDGETS, x(), y(screen), SIZE, SIZE,
                0, 112 + (hovered ? 20 : 0), SIZE, SIZE, 256, 256);
        if (hovered) event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font,
                Component.translatable("screen.wishlist.open"), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || event.getButton() != 0 || !inside(screen, event.getMouseX(), event.getMouseY())) return;
        event.setCanceled(true);
        Minecraft.getInstance().setScreen(new WishlistScreen(screen));
    }
}
