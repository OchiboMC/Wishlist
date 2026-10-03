package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class DeleteWishlistScreen extends Screen {
    private final Screen parent;
    private final UUID wishlistId;
    private final String wishlistName;

    public DeleteWishlistScreen(Screen parent, UUID wishlistId, String wishlistName) {
        super(Component.translatable("screen.wishlist.delete_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
        this.wishlistName = wishlistName;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.delete_confirm_button"), b -> delete())
                .bounds(cx - 100, height / 2 + 12, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.cancel"), b -> onClose())
                .bounds(cx + 4, height / 2 + 12, 96, 20).build());
    }

    private void delete() {
        ClientWorkspaceController.get().deleteWishlist(wishlistId);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishlist.delete_confirm", Component.literal(wishlistName)), width / 2, height / 2 - 16, 0xFFAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
