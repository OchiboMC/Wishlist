package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class EditEntryScreen extends Screen {
    private final Screen parent;
    private final UUID wishlistId;
    private final UUID entryId;
    private EditBox count;
    private WishlistEntry entry;

    public EditEntryScreen(Screen parent, UUID wishlistId, UUID entryId) {
        super(Component.translatable("screen.wishlist.edit_entry_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
        this.entryId = entryId;
    }

    @Override
    protected void init() {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        Wishlist wishlist = controller.workspace().find(wishlistId).orElse(null);
        entry = wishlist == null ? null : wishlist.entries().stream()
                .filter(e -> e.id().equals(entryId)).findFirst().orElse(null);
        if (entry == null) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }

        int cx = width / 2;
        count = new EditBox(font, cx - 50, height / 2 - 24, 100, 20, Component.translatable("screen.wishlist.edit"));
        count.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        count.setMaxLength(18);
        count.setValue(Long.toString(entry.requestedCount()));
        addRenderableWidget(count);
        setInitialFocus(count);

        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.save"), b -> save())
                .bounds(cx - 104, height / 2 + 8, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.delete"), b -> delete())
                .bounds(cx - 33, height / 2 + 8, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.back"), b -> onClose())
                .bounds(cx + 38, height / 2 + 8, 66, 20).build());
    }

    private void save() {
        long value;
        try {
            value = Long.parseLong(count.getValue());
        } catch (NumberFormatException e) {
            return;
        }
        if (value <= 0) return;
        ClientWorkspaceController.get().setRequestedCount(wishlistId, entryId, value);
        Minecraft.getInstance().setScreen(parent);
    }

    private void delete() {
        ClientWorkspaceController.get().removeEntry(wishlistId, entryId);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 58, 0xFFFFFF);
        if (entry != null) {
            graphics.drawCenteredString(font, UiText.itemName(entry.outputItemId()), width / 2, height / 2 - 42, 0xBFBFBF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
