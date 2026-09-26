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

import java.util.UUID;

public final class DetailedAddScreen extends Screen {
    private final Screen parent;
    private final ResolvedRecipe recipe;
    private EditBox count;
    private UUID selectedWishlist;

    public DetailedAddScreen(Screen parent, ResolvedRecipe recipe) {
        super(Component.translatable("screen.wishlist.details_title"));
        this.parent = parent;
        this.recipe = recipe;
    }

    @Override
    protected void init() {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        if (selectedWishlist == null) selectedWishlist = controller.workspace().active().map(Wishlist::id).orElse(null);
        int cx = width / 2;
        count = new EditBox(font, cx - 54, 48, 108, 20, Component.translatable("screen.wishlist.edit"));
        count.setValue("1");
        count.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        addRenderableWidget(count);
        int y = 82;
        for (Wishlist wishlist : controller.workspace().wishlists()) {
            if (y > height - 74) break;
            Button button = Button.builder(label(wishlist), b -> {
                selectedWishlist = wishlist.id();
                rebuildWidgets();
            }).bounds(cx - 120, y, 240, 20).build();
            addRenderableWidget(button);
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.add"), b -> add())
                .bounds(cx - 100, height - 48, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.cancel"), b -> onClose())
                .bounds(cx + 4, height - 48, 96, 20).build());
    }

    private Component label(Wishlist wishlist) {
        return Component.translatable(wishlist.id().equals(selectedWishlist)
                ? "screen.wishlist.list_selected" : "screen.wishlist.list_unselected", Component.literal(wishlist.name()));
    }

    private void add() {
        if (selectedWishlist == null) return;
        long value;
        try { value = Long.parseLong(count.getValue()); }
        catch (NumberFormatException e) { return; }
        if (value <= 0) return;
        ClientWorkspaceController.get().addTo(selectedWishlist, recipe, value);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, UiText.itemName(recipe.outputItemId()), width / 2, 20, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.wishlist.desired_count"), width / 2 - 114, 54, 0xBFBFBF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
