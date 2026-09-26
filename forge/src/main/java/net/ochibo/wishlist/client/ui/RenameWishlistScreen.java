package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.model.Wishlist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class RenameWishlistScreen extends Screen {
    private final Screen parent;
    private final UUID wishlistId;
    private EditBox name;

    public RenameWishlistScreen(Screen parent, UUID wishlistId) {
        super(Component.translatable("screen.wishlist.rename_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
    }

    @Override
    protected void init() {
        Wishlist wishlist = ClientWorkspaceController.get().workspace().find(wishlistId).orElse(null);
        if (wishlist == null) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        int cx = width / 2;
        name = new EditBox(font, cx - 100, height / 2 - 28, 200, 20, Component.translatable("screen.wishlist.name"));
        name.setMaxLength(80);
        name.setValue(wishlist.name());
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.save"), b -> save())
                .bounds(cx - 100, height / 2 + 2, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.cancel"), b -> onClose())
                .bounds(cx + 4, height / 2 + 2, 96, 20).build());
        setInitialFocus(name);
    }

    private void save() {
        String value = name.getValue().trim();
        if (value.isEmpty()) return;
        ClientWorkspaceController.get().renameWishlist(wishlistId, value);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
