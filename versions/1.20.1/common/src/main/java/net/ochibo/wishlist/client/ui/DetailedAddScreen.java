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
    private final String itemId;
    private EditBox count;
    private EditBox search;
    private String countText = "1";
    private String searchText = "";
    private int scroll;
    private int maxScroll;
    private UUID selectedWishlist;

    public DetailedAddScreen(Screen parent, ResolvedRecipe recipe) {
        super(Component.translatable("screen.wishlist.details_title"));
        this.parent = parent;
        this.recipe = recipe;
        this.itemId = recipe.outputItemId();
    }

    public DetailedAddScreen(Screen parent, String itemId) {
        super(Component.translatable("screen.wishlist.details_title"));
        this.parent = parent;
        this.recipe = null;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        if (selectedWishlist == null) selectedWishlist = controller.workspace().active().map(Wishlist::id).orElse(null);
        int cx = width / 2;
        count = new EditBox(font, cx - 54, 48, 108, 20, Component.translatable("screen.wishlist.edit"));
        count.setValue(countText);
        count.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        count.setResponder(value -> countText = value);
        addRenderableWidget(count);
        search = new EditBox(font, cx - 120, 76, 240, 20,
                Component.translatable("screen.wishlist.search_lists"));
        search.setHint(Component.translatable("screen.wishlist.search_lists"));
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            scroll = 0;
            rebuildWidgets();
            setFocused(search);
            search.setCursorPosition(value.length());
        });
        addRenderableWidget(search);
        var lists = controller.workspace().wishlists().stream()
                .filter(wishlist -> wishlist.name().toLowerCase(java.util.Locale.ROOT)
                        .contains(searchText.toLowerCase(java.util.Locale.ROOT))).toList();
        int visible = Math.max(1, (height - 174) / 22);
        maxScroll = Math.max(0, lists.size() - visible);
        scroll = Math.min(scroll, maxScroll);
        for (int index = scroll; index < Math.min(lists.size(), scroll + visible); index++) {
            Wishlist wishlist = lists.get(index);
            int y = 102 + (index - scroll) * 22;
            Button button = Button.builder(label(wishlist), b -> {
                selectedWishlist = wishlist.id();
                rebuildWidgets();
            }).bounds(cx - 120, y, 240, 20).build();
            addRenderableWidget(button);
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
        if (recipe == null) ClientWorkspaceController.get().addItemTo(selectedWishlist, itemId, value);
        else ClientWorkspaceController.get().addTo(selectedWishlist, recipe, value);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, UiText.itemName(itemId), width / 2, 20, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.wishlist.desired_count"), width / 2 - 114, 54, 0xBFBFBF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
