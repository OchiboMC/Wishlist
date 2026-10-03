package net.ochibo.wishlist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.ui.WishlistScreen;
import net.ochibo.wishlist.client.ui.UiClickSound;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.jetbrains.annotations.Nullable;

/** Opens Wishlist from inventory, container, and ordinary recipe viewer screens. */
public final class WishlistContainerButton {
    private static final int SIZE = 20;

    private static int textureU() {
        return WishlistMod.isLoaded("jei") && !WishlistMod.isLoaded("emi") ? 32 : 0;
    }

    /** Current button bounds in GUI coordinates, or null when it is not displayed. */
    @Nullable
    public static WishlistButtonPlacement.Rect getPosition() {
        return getPosition(Minecraft.getInstance().screen);
    }

    /** The same visibility and placement used for rendering and mouse input. */
    @Nullable
    public static WishlistButtonPlacement.Rect getPosition(@Nullable Screen screen) {
        if (screen == null || !WishlistButtonVisibility.forScreen(screen)) return null;
        return WishlistButtonPlacement.forScreen(screen,
                WishlistMod.isLoaded("jei"), WishlistMod.isLoaded("emi"));
    }

    private static boolean inside(WishlistButtonPlacement.Rect pos, double mouseX, double mouseY) {
        return mouseX >= pos.x() && mouseX < pos.x() + SIZE
                && mouseY >= pos.y() && mouseY < pos.y() + SIZE;
    }

    public static void render(Screen current, GuiGraphics graphics, int mouseX, int mouseY) {
        Screen screen = current;
        var pos = getPosition(screen);
        if (pos == null) return;
        boolean hovered = inside(pos, mouseX, mouseY);
        graphics.blit(WishlistClient.WIDGETS, pos.x(), pos.y(), SIZE, SIZE,
                textureU(), 112 + (hovered ? 20 : 0), SIZE, SIZE, 256, 256);
        if (hovered) graphics.renderComponentTooltip(Minecraft.getInstance().font,
                screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                        || RecipeViewerRouter.isRecipeViewerScreen(screen)
                        ? List.of(Component.translatable("screen.wishlist.open"),
                                Component.translatable("screen.wishlist.add_hovered_hint", WishlistClient.addHoveredKeyName())
                                        .withStyle(ChatFormatting.GRAY))
                        : List.of(Component.translatable("screen.wishlist.open"),
                                Component.translatable("screen.wishlist.highlight_hint", WishlistClient.highlightKeyName())
                                        .withStyle(ChatFormatting.GRAY)),
                mouseX, mouseY);
    }

    public static boolean mousePressed(Screen current, double mouseX, double mouseY, int button) {
        Screen screen = current;
        if (button != 0) return false;
        var pos = getPosition(screen);
        if (pos == null || !inside(pos, mouseX, mouseY)) return false;
        UiClickSound.play();
        Minecraft.getInstance().setScreen(new WishlistScreen(screen));
        return true;
    }
}
