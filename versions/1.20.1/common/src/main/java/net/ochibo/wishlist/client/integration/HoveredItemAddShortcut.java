package net.ochibo.wishlist.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.ui.DetailedAddScreen;
import net.ochibo.wishlist.core.utils.StackResolver;

/** Shared shortcut action; loaders supply their own screen key and slot events. */
public final class HoveredItemAddShortcut {
    private HoveredItemAddShortcut() {}

    public static boolean handle(Screen screen, ItemStack hovered, boolean shiftDown) {
        Minecraft mc = Minecraft.getInstance();
        if (screen == null || hovered == null || hovered.isEmpty() || mc.player == null || mc.level == null
                || screen.getFocused() instanceof EditBox) return false;

        ClientWorkspaceController controller = ClientWorkspaceController.get();
        if (controller.pendingSelection().isPresent()) return false;
        String itemId = StackResolver.id(hovered);
        if (shiftDown) controller.quickAddItem(itemId, 1);
        else mc.setScreen(new DetailedAddScreen(screen, itemId));
        return true;
    }
}
