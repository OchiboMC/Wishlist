package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.tree.TerminalReason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class IngredientCandidateSelectScreen extends Screen {
    private static final int CELL = 24;
    private static final int ICON = 16;
    private static final int TOP = 44;
    private static final int BOTTOM_MARGIN = 36;

    private final Screen parent;
    private final UUID wishlistId;
    private final UUID nodeId;
    private final List<String> candidates;
    private int columns;
    private int rowsVisible;
    private int scrollRows;
    private int maxScrollRows;

    public IngredientCandidateSelectScreen(Screen parent, UUID wishlistId, UUID nodeId, List<String> candidates) {
        super(Component.translatable("screen.wishlist.candidate_select_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
        this.nodeId = nodeId;
        this.candidates = List.copyOf(candidates);
    }

    @Override
    protected void init() {
        columns = Math.max(1, (width - 24) / CELL);
        rowsVisible = Math.max(1, (height - TOP - BOTTOM_MARGIN) / CELL);
        int totalRows = (candidates.size() + columns - 1) / columns;
        maxScrollRows = Math.max(0, totalRows - rowsVisible);
        scrollRows = Math.min(scrollRows, maxScrollRows);

        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.back"), button -> onClose())
                .bounds(8, height - 28, 72, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishlist.candidate_select_hint"), width / 2, 26, 0xAAAAAA);

        int firstIndex = scrollRows * columns;
        int visibleCount = rowsVisible * columns;
        int end = Math.min(candidates.size(), firstIndex + visibleCount);
        ItemStack hovered = ItemStack.EMPTY;

        for (int index = firstIndex; index < end; index++) {
            int local = index - firstIndex;
            int column = local % columns;
            int row = local / columns;
            int x = 12 + column * CELL;
            int y = TOP + row * CELL;
            boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            graphics.fill(x, y, x + CELL - 2, y + CELL - 2, over ? 0xAA666666 : 0xAA333333);
            ItemStack stack = UiText.itemStack(candidates.get(index));
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + (CELL - ICON) / 2 - 1, y + (CELL - ICON) / 2 - 1);
                if (over) hovered = stack;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= TOP && mouseY < height - BOTTOM_MARGIN) {
            int column = ((int) mouseX - 12) / CELL;
            int row = ((int) mouseY - TOP) / CELL;
            if (mouseX >= 12 && column >= 0 && column < columns && row >= 0 && row < rowsVisible) {
                int index = (scrollRows + row) * columns + column;
                if (index >= 0 && index < candidates.size()) {
                    choose(candidates.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(String candidate) {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        TerminalReason result = controller.expandCandidate(wishlistId, nodeId, candidate);
        if (result == TerminalReason.MULTIPLE_RECIPES) {
            if (RecipeViewerRouter.available()) {
                ClientWorkspaceController.SelectionRequest request =
                        controller.beginRecipeSelection(wishlistId, nodeId, candidate);
                if (RecipeViewerRouter.openForSelection(request, parent)) return;
                controller.cancelPendingSelection();
                RecipeViewerRouter.clearSelectionReturnScreen();
            }
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        scrollRows = Math.max(0, Math.min(maxScrollRows, scrollRows - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
