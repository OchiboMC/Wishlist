package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.Wishlist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CreateWishlistScreen extends Screen {
    private final Screen parent;
    private final ResolvedRecipe recipeToAdd;
    private final long recipeCount;
    private EditBox name;

    public CreateWishlistScreen(Screen parent) {
        this(parent, null, 0);
    }

    public CreateWishlistScreen(Screen parent, ResolvedRecipe recipeToAdd, long recipeCount) {
        super(Component.translatable("screen.wishlist.create_title"));
        this.parent = parent;
        this.recipeToAdd = recipeToAdd;
        this.recipeCount = recipeCount;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        name = new EditBox(font, cx - 100, height / 2 - 28, 200, 20, Component.translatable("screen.wishlist.name"));
        name.setMaxLength(80);
        name.setValue(Component.translatable("screen.wishlist.default_name").getString());
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.create"), b -> create())
                .bounds(cx - 100, height / 2 + 2, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.cancel"), b -> onClose())
                .bounds(cx + 4, height / 2 + 2, 96, 20).build());
        setInitialFocus(name);
    }

    private void create() {
        String value = name.getValue().trim();
        if (value.isEmpty()) return;
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        Wishlist created = controller.createWishlist(value);
        if (recipeToAdd != null && recipeCount > 0) {
            controller.addTo(created.id(), recipeToAdd, recipeCount);
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
