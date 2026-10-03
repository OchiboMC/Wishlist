package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.tree.TerminalReason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

public final class IngredientCandidateSelectScreen extends Screen {
    private static final int CELL = 24;
    private static final int ICON = 16;
    private static final int TOP = 62;
    private static final int BOTTOM_MARGIN = 36;

    private final Screen parent;
    private final UUID wishlistId;
    private final UUID nodeId;
    private final List<String> candidates;
    private int columns;
    private int rowsVisible;
    private int scrollRows;
    private int maxScrollRows;
    private String query = "";
    private boolean showNames;
    private String highlightedCandidate;
    private EditBox searchBox;

    private List<String> filtered() {
        String needle = query.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(key ->
                UiText.shortName(key).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    public IngredientCandidateSelectScreen(Screen parent, UUID wishlistId, UUID nodeId, List<String> candidates) {
        super(Component.translatable("screen.wishlist.candidate_select_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
        this.nodeId = nodeId;
        this.candidates = List.copyOf(candidates);
    }

    @Override
    protected void init() {
        columns = showNames ? 1 : Math.max(1, (width - 24) / CELL);
        rowsVisible = Math.max(1, (height - TOP - BOTTOM_MARGIN) / CELL);
        int totalRows = (filtered().size() + columns - 1) / columns;
        maxScrollRows = Math.max(0, totalRows - rowsVisible);
        scrollRows = Math.min(scrollRows, maxScrollRows);

        searchBox = new EditBox(font, width / 2 - 110, 39, 220, 18,
                Component.translatable("screen.wishlist.search_candidates"));
        searchBox.setHint(Component.translatable("screen.wishlist.search_candidates"));
        searchBox.setValue(query);
        searchBox.setResponder(value -> { query = value; scrollRows = 0; });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.back"), button -> onClose())
                .bounds(8, height - 28, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(showNames
                        ? "screen.wishlist.show_icons" : "screen.wishlist.show_names"), button -> {
                    showNames = !showNames;
                    scrollRows = 0;
                    clearWidgets();
                    init();
                }).bounds(width - 118, height - 28, 110, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishlist.candidate_select_hint"), width / 2, 26, 0xAAAAAA);

        List<String> shown = filtered();
        if (!shown.contains(highlightedCandidate)) highlightedCandidate = shown.isEmpty() ? null : shown.get(0);
        maxScrollRows = Math.max(0, (shown.size() + columns - 1) / columns - rowsVisible);
        scrollRows = Math.min(scrollRows, maxScrollRows);
        int firstIndex = scrollRows * columns;
        int visibleCount = rowsVisible * columns;
        int end = Math.min(shown.size(), firstIndex + visibleCount);
        ItemStack hovered = ItemStack.EMPTY;

        for (int index = firstIndex; index < end; index++) {
            int local = index - firstIndex;
            int column = local % columns;
            int row = local / columns;
            int x = showNames ? 12 : 12 + column * CELL;
            int y = TOP + row * CELL;
            int cellWidth = showNames ? width - 24 : CELL;
            boolean over = mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + CELL;
            boolean highlighted = shown.get(index).equals(highlightedCandidate);
            graphics.fill(x, y, x + cellWidth - 2, y + CELL - 2,
                    highlighted ? 0xAA5577AA : over ? 0xAA666666 : 0xAA333333);
            ItemStack stack = UiText.itemStack(shown.get(index));
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, showNames ? x + 3 : x + (CELL - ICON) / 2 - 1,
                        y + (CELL - ICON) / 2 - 1);
                if (over) hovered = stack;
            }
            if (showNames) graphics.drawString(font,
                    font.plainSubstrByWidth(UiText.itemName(shown.get(index)).getString(),
                            Math.max(1, cellWidth - 32)), x + 24, y + 7, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= TOP && mouseY < height - BOTTOM_MARGIN) {
            int column = showNames ? 0 : ((int) mouseX - 12) / CELL;
            int row = ((int) mouseY - TOP) / CELL;
            if (mouseX >= 12 && mouseX < width - 12 && column >= 0 && column < columns
                    && row >= 0 && row < rowsVisible) {
                int index = (scrollRows + row) * columns + column;
                List<String> shown = filtered();
                if (index >= 0 && index < shown.size()) {
                    highlightedCandidate = shown.get(index);
                    choose(shown.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (highlightedCandidate != null) {
                choose(highlightedCandidate);
                return true;
            }
        }
        if ((searchBox == null || !searchBox.isFocused())
                && (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_LEFT)) {
            List<String> shown = filtered();
            if (!shown.isEmpty()) {
                int current = Math.max(0, shown.indexOf(highlightedCandidate));
                int step = keyCode == GLFW.GLFW_KEY_DOWN ? columns
                        : keyCode == GLFW.GLFW_KEY_UP ? -columns
                        : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
                int next = Math.max(0, Math.min(shown.size() - 1, current + step));
                highlightedCandidate = shown.get(next);
                scrollRows = Math.max(0, Math.min(maxScrollRows, next / columns - rowsVisible + 1));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
