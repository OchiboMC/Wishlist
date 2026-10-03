package net.ochibo.wishlist.client.ui;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.ClientWorkspaceController;

/** Confirmation before deleting a root target and its progress. */
public final class DeleteEntryScreen extends Screen {
    private final Screen parent;
    private final UUID wishlistId;
    private final UUID entryId;
    private final Component itemName;

    public DeleteEntryScreen(Screen parent, UUID wishlistId, UUID entryId, Component itemName) {
        super(Component.translatable("screen.wishlist.delete_entry_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
        this.entryId = entryId;
        this.itemName = itemName;
    }

    @Override protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.delete_confirm_button"), b -> {
            ClientWorkspaceController.get().removeEntry(wishlistId, entryId);
            Minecraft.getInstance().setScreen(parent);
        }).bounds(cx - 100, height / 2 + 12, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.cancel"), b -> onClose())
                .bounds(cx + 4, height / 2 + 12, 96, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishlist.delete_entry_confirm", itemName),
                width / 2, height / 2 - 16, 0xFFAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
