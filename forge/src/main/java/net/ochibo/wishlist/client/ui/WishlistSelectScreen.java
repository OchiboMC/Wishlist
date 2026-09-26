package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.Wishlist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WishlistSelectScreen extends Screen {
    private final Screen parent;
    private final ResolvedRecipe recipe;
    private final long count;

    public WishlistSelectScreen(Screen parent, ResolvedRecipe recipe, long count) {
        super(Component.translatable("screen.wishlist.select"));
        this.parent = parent;
        this.recipe = recipe;
        this.count = Math.max(1, count);
    }

    @Override
    protected void init() {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        int buttonWidth = Math.min(260, width - 40);
        int x = (width - buttonWidth) / 2;
        int y = 44;
        for (Wishlist wishlist : controller.workspace().wishlists()) {
            if (y + 20 > height - 40) break;
            addRenderableWidget(Button.builder(Component.literal(wishlist.name()), b -> {
                controller.addTo(wishlist.id(), recipe, count);
                Minecraft.getInstance().setScreen(parent);
            }).bounds(x, y, buttonWidth, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.new_list"), b ->
                Minecraft.getInstance().setScreen(new CreateWishlistScreen(parent, recipe, count)))
                .bounds(x, Math.min(y + 4, height - 48), buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, UiText.itemName(recipe.outputItemId()), width / 2, 30, 0xBFBFBF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
