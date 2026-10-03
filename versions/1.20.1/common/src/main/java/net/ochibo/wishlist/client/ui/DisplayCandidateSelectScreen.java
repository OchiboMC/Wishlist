package net.ochibo.wishlist.client.ui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Chooses the candidate displayed by a Wishlist icon without changing its recipe. */
public final class DisplayCandidateSelectScreen extends Screen {
    private final Screen parent;
    private final List<String> candidates;
    private final Consumer<String> select;
    private final String initiallySelected;
    private String query = "";
    private int scroll;
    private int maxScroll;

    public DisplayCandidateSelectScreen(Screen parent, List<String> candidates,
                                        String initiallySelected, Consumer<String> select) {
        super(Component.translatable("screen.wishlist.choose_display_candidate"));
        this.parent = parent;
        this.candidates = List.copyOf(candidates);
        this.initiallySelected = initiallySelected;
        this.select = select;
    }

    private List<String> filtered() {
        String needle = query.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(key ->
                UiText.shortName(key).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    @Override protected void init() {
        EditBox search = new EditBox(font, width / 2 - 110, 34, 220, 20,
                Component.translatable("screen.wishlist.search_candidates"));
        search.setHint(Component.translatable("screen.wishlist.search_candidates"));
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll = 0; });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.back"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        List<String> visible = filtered();
        int rows = Math.max(1, (height - 94) / 22);
        maxScroll = Math.max(0, visible.size() - rows);
        scroll = Math.min(scroll, maxScroll);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = scroll; i < Math.min(visible.size(), scroll + rows); i++) {
            String key = visible.get(i);
            int x = width / 2 - 110;
            int y = 60 + (i - scroll) * 22;
            boolean over = mouseX >= x && mouseX < x + 220 && mouseY >= y && mouseY < y + 20;
            graphics.fill(x, y, x + 220, y + 20, key.equals(initiallySelected)
                    ? 0x886688AA : over ? 0xAA555555 : 0x88333333);
            ItemStack stack = UiText.itemStack(key);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, y + 2);
                if (over) hovered = stack;
            }
            graphics.drawString(font, font.plainSubstrByWidth(UiText.itemName(key).getString(), 190),
                    x + 23, y + 6, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= width / 2.0 - 110 && mouseX < width / 2.0 + 110
                && mouseY >= 60 && mouseY < height - 34) {
            int row = ((int) mouseY - 60) / 22;
            int rows = Math.max(1, (height - 94) / 22);
            List<String> visible = filtered();
            int index = scroll + row;
            if (row >= 0 && row < rows && mouseY < 60 + row * 22 + 20
                    && index >= 0 && index < visible.size()) {
                select.accept(visible.get(index));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
