package net.ochibo.wishlist.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.ui.UiText;

/** A compact progress caption while expand-all is waiting in JEI or EMI. */
public final class ExpandProgressOverlay {
    private ExpandProgressOverlay() {}

    public static void render(Screen screen, GuiGraphics graphics) {
        if (!RecipeViewerRouter.isRecipeViewerScreen(screen)) return;
        ClientWorkspaceController.get().autoProgress().ifPresent(progress -> {
            Component name = progress.itemId() == null ? Component.literal("?")
                    : UiText.itemName(progress.itemId());
            Component caption = Component.translatable("screen.wishlist.expand_progress",
                    progress.handled(), progress.waiting(), name);
            int textWidth = Minecraft.getInstance().font.width(caption);
            int x = Math.max(4, (screen.width - textWidth) / 2);
            graphics.fill(x - 4, 2, x + textWidth + 4, 16, 0xCC101010);
            graphics.drawString(Minecraft.getInstance().font, caption, x, 5, 0xFFFFFFFF, false);
        });
    }
}
