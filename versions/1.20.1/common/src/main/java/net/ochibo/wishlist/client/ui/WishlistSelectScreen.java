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
    private int scroll;
    private int maxScroll;
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
        int visible = Math.max(1, (height - 116) / 24);
        var lists = controller.workspace().wishlists();
        maxScroll = Math.max(0, lists.size() - visible);
        scroll = Math.min(scroll, maxScroll);
        for (int index = scroll; index < Math.min(lists.size(), scroll + visible); index++) {
            Wishlist wishlist = lists.get(index);
            int y = 44 + (index - scroll) * 24;
            addRenderableWidget(Button.builder(Component.literal(wishlist.name()), b -> {
                controller.addTo(wishlist.id(), recipe, count);
                Minecraft.getInstance().setScreen(parent);
            }).bounds(x, y, buttonWidth, 20).build());
        }
        int footerWidth = (buttonWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.new_list"), b ->
                Minecraft.getInstance().setScreen(new CreateWishlistScreen(parent, recipe, count)))
                .bounds(x, height - 48, footerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.back"), b -> onClose())
                .bounds(x + footerWidth + 4, height - 48, buttonWidth - footerWidth - 4, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, UiText.itemName(recipe.outputItemId()), width / 2, 30, 0xBFBFBF);
        if (maxScroll > 0) graphics.drawString(font, (scroll + 1) + "-" + Math.min(
                ClientWorkspaceController.get().workspace().wishlists().size(),
                scroll + Math.max(1, (height - 116) / 24)), width - 52, height - 24, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
