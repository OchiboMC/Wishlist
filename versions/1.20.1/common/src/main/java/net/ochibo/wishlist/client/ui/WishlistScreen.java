package net.ochibo.wishlist.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.WishlistClient;
import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.material.MaterialCalculation;
import net.ochibo.wishlist.core.material.MaterialCalculator;
import net.ochibo.wishlist.core.material.MaterialSummaryRow;
import net.ochibo.wishlist.core.material.MaterialVisibility;
import net.ochibo.wishlist.core.material.NodeQuantity;
import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.recipe.TransferReadiness;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.tree.RecipeNode;
import net.ochibo.wishlist.core.tree.TerminalReason;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WishlistScreen extends Screen {
    private static final int RIGHT_WIDTH = 150;
    private static final int TOP_BAR_HEIGHT = 28;
    private static final int CONTENT_TOP = 66;
    private static final int ROW_HEIGHT = 20;
    private static final int COL_WIDTH = 18;
    private static final int ICON_SIZE = 16;
    private static final int CHECKBOX_SIZE = 13;
    private static final int ROOT_CHECKBOX_X = 2;
    private static final int ROOT_ITEM_X = ROOT_CHECKBOX_X + CHECKBOX_SIZE + 2;
    private static final int COMPLETED_TREE_COLOR = 0x777777;
    private static final int LINE_WIDTH = 2;
    private static final int LINE_PADDING = 4;

    private final Screen parent;
    private final ClientWorkspaceController controller = ClientWorkspaceController.get();
    private final List<TreeItemVisual> treeItems = new ArrayList<>();
    private final List<TextLine> textLines = new ArrayList<>();
    private List<TreeItemVisual> visibleTreeItems = List.of();
    private List<TreeLineLayout.Line> visibleTreeLines = List.of();
    private List<TextLine> visibleTextLines = List.of();
    private final LinkedHashSet<UUID> selectedWishlistIds = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> selectedEntryIds = new LinkedHashSet<>();
    private final Map<String, Integer> pausedCandidateIndices = new HashMap<>();
    private final Map<String, String> chosenCandidates = new HashMap<>();
    private final Map<String, Boolean> recipeAvailabilityCache = new HashMap<>();
    private final Map<RecipeKey, TransferReadiness> transferReadinessCache = new HashMap<>();
    private final Map<WishlistEntry, Optional<ResolvedRecipe>> rootRecipeCache = new HashMap<>();
    private final Map<RecipeNode, List<String>> displayCandidateCache = new HashMap<>();
    private ResolverStamp recipeCacheStamp;
    private MaterialCalculation visibleMaterialCalculation;
    private Set<String> visibleMaterialCompleted = Set.of();
    private boolean visibleMaterialMissingOnly;
    private List<MaterialSummaryRow> visibleMaterialRows = List.of();
    private boolean sortMaterialsByShortage;
    private String treeSearchText = "";
    private final WishlistTreeSearch.Index treeSearchIndex = new WishlistTreeSearch.Index();
    private EditBox treeSearch;
    private boolean treeSearchRefreshPending;
    private final List<IconTarget> iconTargets = new ArrayList<>();
    private final List<CheckboxTarget> checkboxTargets = new ArrayList<>();
    private final List<RootCheckboxVisual> rootCheckboxes = new ArrayList<>();
    private List<RootCheckboxVisual> visibleRootCheckboxes = List.of();

    private boolean wishlistMenuOpen;
    private int treeScroll;
    private int treeMaxScroll;
    private int materialScroll;
    private int materialMaxScroll;
    private Scrollbar draggedScrollbar;
    private int scrollbarDragOffset;
    private int wishlistMenuScroll;
    private int wishlistMenuMaxScroll;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private Component hoveredResourceName;
    private Component hoveredCheckboxTooltip;

    private record TreeItemVisual(String key, int x, int y, List<String> candidates, long count, long crafted,
                                  int color, TreeItemVisual root, boolean completed, boolean searchMatch) {}
    private record TextLine(int x, int y, Component text, int color) {}
    private record ResolverStamp(Object nativeResolver, Object jeiResolver, Object emiResolver) {}
    private record SearchTextStamp(ResolverStamp sources, String language) {}
    private record IconTarget(int x, int y, String key, List<String> candidates, String itemId) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
        }
    }
    private record CheckboxTarget(int x, int y, MaterialSummaryRow row) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + CHECKBOX_SIZE
                    && mouseY >= y && mouseY < y + CHECKBOX_SIZE;
        }
    }
    private record RootCheckboxVisual(int x, int y, UUID wishlistId, UUID entryId, boolean checked) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + CHECKBOX_SIZE
                    && mouseY >= y && mouseY < y + CHECKBOX_SIZE;
        }
    }
    private enum Scrollbar { TREE, MATERIALS }

    public WishlistScreen(Screen parent) {
        super(Component.translatable("screen.wishlist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        controller.ensureLoaded();
        treeSearchIndex.invalidate();
        syncSelectionFromWorkspace();
        rebuildContents();
    }

    private void syncSelectionFromWorkspace() {
        selectedWishlistIds.clear();
        for (Wishlist wishlist : controller.workspace().selectedWishlists()) selectedWishlistIds.add(wishlist.id());
        if (selectedWishlistIds.isEmpty()) {
            controller.workspace().active().ifPresent(wishlist -> selectedWishlistIds.add(wishlist.id()));
        }
    }

    private void rebuildContents() {
        rebuildContents(true);
    }

    private void rebuildContents(boolean modelChanged) {
        treeSearchRefreshPending = false;
        boolean searchFocused = treeSearch != null && getFocused() == treeSearch;
        clearWidgets();
        treeItems.clear();
        textLines.clear();
        visibleTreeItems = List.of();
        visibleTreeLines = List.of();
        visibleTextLines = List.of();
        iconTargets.clear();
        checkboxTargets.clear();
        rootCheckboxes.clear();
        visibleRootCheckboxes = List.of();
        ResolverStamp sources = resolverStamp();
        if (modelChanged || !sources.equals(recipeCacheStamp)) {
            recipeAvailabilityCache.clear();
            transferReadinessCache.clear();
            rootRecipeCache.clear();
            displayCandidateCache.clear();
            recipeCacheStamp = sources;
        }
        if (wishlistMenuOpen) {
            buildWishlistMenu();
        } else {
            materialMaxScroll = Math.max(0, visibleMaterials().size() * Math.max(ROW_HEIGHT, CHECKBOX_SIZE + 2)
                    - Math.max(1, viewBottom(Scrollbar.MATERIALS) - CONTENT_TOP));
            materialScroll = Math.min(materialScroll, materialMaxScroll);
            buildMainToolbar();
            buildTreeWidgets();
        }
        if (searchFocused) setFocused(treeSearch != null && children().contains(treeSearch) ? treeSearch : null);
    }

    private MaterialCalculation currentCalculation() {
        return controller.materialCalculation();
    }

    private ResolverStamp resolverStamp() {
        return new ResolverStamp(controller.resolver(),
                RecipeViewerRouter.jeiRecipeResolver().orElse(null),
                RecipeViewerRouter.emiRecipeResolver().orElse(null));
    }

    private List<MaterialSummaryRow> visibleMaterials() {
        MaterialCalculation calculation = currentCalculation();
        Set<String> completed = controller.completedMaterials();
        boolean missingOnly = WishlistClientConfig.missingOnly();
        if (calculation != visibleMaterialCalculation || !completed.equals(visibleMaterialCompleted)
                || missingOnly != visibleMaterialMissingOnly) {
            visibleMaterialRows = MaterialVisibility.screenRows(calculation.rows(), completed, missingOnly);
            if (sortMaterialsByShortage) visibleMaterialRows = visibleMaterialRows.stream()
                    .sorted(Comparator.comparingLong((MaterialSummaryRow row) ->
                            Math.max(0, row.required() - row.allocatedOwned())).reversed()).toList();
            visibleMaterialCalculation = calculation;
            visibleMaterialCompleted = completed;
            visibleMaterialMissingOnly = missingOnly;
        }
        return visibleMaterialRows;
    }

    private void buildMainToolbar() {
        int treeRight = treeRight();
        int hamburgerX = Math.max(4, width - 26);
        Button hamburger = Button.builder(Component.translatable("screen.wishlist.menu_icon"), button -> {
            wishlistMenuOpen = true;
            wishlistMenuScroll = 0;
            rebuildContents();
        }).bounds(hamburgerX, 4, 22, 20).build();
        hamburger.setTooltip(Tooltip.create(Component.translatable("screen.wishlist.menu")));
        addRenderableWidget(hamburger);

        int y = TOP_BAR_HEIGHT + 2;
        int treeToolbarWidth = Math.max(42, (treeRight - 16) / 2);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.expand_all"), button -> {
            var request = controller.startExpandAll();
            rebuildContents();
            if (RecipeViewerRouter.available()) {
                request.ifPresent(this::openSelectionOrCancel);
            } else if (request.isPresent()) {
                controller.cancelPendingSelection();
            }
        }).bounds(6, y, treeToolbarWidth, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.collapse_all"), button -> {
            controller.collapseAll();
            rebuildContents();
        }).bounds(10 + treeToolbarWidth, y, treeToolbarWidth, 18).build());

        int rightX = treeRight + 4;
        WishlistButtonWidths.Pair toolbarWidths = WishlistButtonWidths.missingAndHud(RIGHT_WIDTH - 8);
        addRenderableWidget(Button.builder(missingOnlyLabel(), button -> {
            WishlistClientConfig.setMissingOnly(!WishlistClientConfig.missingOnly());
            rebuildContents(false);
        }).bounds(rightX, y, toolbarWidths.first(), 18).build());

        addRenderableWidget(Button.builder(hudLabel(), button -> {
            WishlistClientConfig.setHudEnabled(!WishlistClientConfig.hudEnabled());
            button.setMessage(hudLabel());
        }).bounds(rightX + toolbarWidths.first() + 4, y, toolbarWidths.second(), 18).build());

        if (selectedEntryIds.isEmpty()) {
            // Keep the same editor across view rebuilds so cursor, selection and focus survive typing.
            if (treeSearch == null) {
                treeSearch = new EditBox(font, 24, height - 23, Math.max(30, treeRight - 36), 16,
                        Component.translatable("screen.wishlist.search_tree"));
                treeSearch.setValue(treeSearchText);
                treeSearch.setResponder(value -> {
                    treeSearchText = value;
                    treeSearchRefreshPending = true;
                    treeScroll = 0;
                });
            }
            treeSearch.setY(height - 23);
            treeSearch.setWidth(Math.max(30, treeRight - 36));
            addRenderableWidget(treeSearch);
        }
        addRenderableWidget(Button.builder(Component.translatable(sortMaterialsByShortage
                        ? "screen.wishlist.sort_shortage" : "screen.wishlist.sort_original"), button -> {
                    sortMaterialsByShortage = !sortMaterialsByShortage;
                    visibleMaterialCalculation = null;
                    materialScroll = 0;
                    rebuildContents(false);
                }).bounds(rightX + 64, 49, RIGHT_WIDTH - 72, 16).build());
        if (!selectedEntryIds.isEmpty()) {
            int actionY = height - 48;
            int bulkWidth = Math.max(18, (treeRight - 20) / 3);
            addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.bulk_complete"), b -> {
                controller.markEntriesCompleted(Set.copyOf(selectedEntryIds));
                selectedEntryIds.clear();
                rebuildContents();
            }).bounds(6, actionY, bulkWidth, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.bulk_delete"), b ->
                    Minecraft.getInstance().setScreen(new BulkDeleteEntriesScreen(this,
                            Set.copyOf(selectedEntryIds), () -> {
                                selectedEntryIds.clear();
                                rebuildContents();
                            }))).bounds(10 + bulkWidth, actionY, bulkWidth, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.bulk_clear"), b -> {
                selectedEntryIds.clear();
                rebuildContents(false);
            }).bounds(14 + bulkWidth * 2, actionY, bulkWidth, 18).build());
        }
    }

    private Component missingOnlyLabel() {
        return Component.translatable(WishlistClientConfig.missingOnly()
                ? "screen.wishlist.missing_only_on"
                : "screen.wishlist.missing_only_off");
    }

    private Component hudLabel() {
        return Component.translatable(WishlistClientConfig.hudEnabled()
                ? "screen.wishlist.hud_on"
                : "screen.wishlist.hud_off");
    }

    private void buildWishlistMenu() {
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.close_icon"), button -> {
            wishlistMenuOpen = false;
            rebuildContents();
        }).bounds(6, 6, 22, 20).build());

        int listTop = 38;
        int listBottom = height - 58;
        int rowWidth = Math.max(180, Math.min(360, width - 24));
        int x = 12;
        int y = listTop - wishlistMenuScroll;
        for (Wishlist wishlist : controller.workspace().wishlists()) {
            if (y + 20 >= listTop && y <= listBottom) {
                boolean selected = selectedWishlistIds.contains(wishlist.id());
                Component label = Component.translatable(
                        selected ? "screen.wishlist.checkbox_checked" : "screen.wishlist.checkbox_unchecked",
                        Component.literal(wishlist.name()));
                Button select = Button.builder(label, button -> toggleWishlist(wishlist.id()))
                        .bounds(x, y, rowWidth - 58, 20).build();
                select.setTooltip(Tooltip.create(label));
                addRenderableWidget(select);

                Button rename = Button.builder(Component.translatable("screen.wishlist.rename_icon"), button ->
                                Minecraft.getInstance().setScreen(new RenameWishlistScreen(this, wishlist.id())))
                        .bounds(x + rowWidth - 54, y, 25, 20).build();
                rename.setTooltip(Tooltip.create(Component.translatable("screen.wishlist.rename")));
                addRenderableWidget(rename);

                Button delete = Button.builder(Component.translatable("screen.wishlist.delete_icon"), button ->
                                Minecraft.getInstance().setScreen(new DeleteWishlistScreen(this, wishlist.id(), wishlist.name())))
                        .bounds(x + rowWidth - 27, y, 25, 20).build();
                delete.setTooltip(Tooltip.create(Component.translatable("screen.wishlist.delete")));
                addRenderableWidget(delete);
            }
            y += 24;
        }

        int visibleHeight = Math.max(1, listBottom - listTop);
        wishlistMenuMaxScroll = Math.max(0, controller.workspace().wishlists().size() * 24 - visibleHeight);
        wishlistMenuScroll = Math.min(wishlistMenuScroll, wishlistMenuMaxScroll);

        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.new_list"), button ->
                        Minecraft.getInstance().setScreen(new CreateWishlistScreen(this)))
                .bounds(12, height - 28, Math.max(120, Math.min(190, rowWidth)), 20).build());
    }

    private void toggleWishlist(UUID id) {
        if (selectedWishlistIds.contains(id)) {
            if (selectedWishlistIds.size() <= 1) return;
            selectedWishlistIds.remove(id);
        } else {
            selectedWishlistIds.add(id);
        }
        controller.setCombinedSelection(List.copyOf(selectedWishlistIds));
        if (selectedWishlistIds.contains(id)) {
            controller.setActive(id);
        } else {
            UUID fallback = selectedWishlistIds.iterator().next();
            controller.setActive(fallback);
        }
        rebuildContents();
    }

    private void buildTreeWidgets() {
        MaterialCalculation calc = currentCalculation();
        int treeRight = treeRight() - 10;
        int y = CONTENT_TOP - treeScroll;
        List<Wishlist> selected = controller.workspace().selectedWishlists();
        boolean showListHeading = selected.size() > 1;
        boolean searching = !TreeSearchRanking.normalize(treeSearchText).isEmpty();
        boolean hasVisibleRecipe = false;
        UUID previousWishlist = null;

        SearchTextStamp textStamp = new SearchTextStamp(resolverStamp(), Minecraft.getInstance().options.languageCode);
        for (WishlistTreeSearch.Root result : treeSearchIndex.roots(selected, treeSearchText, UiText::shortName, textStamp)) {
            Wishlist wishlist = result.wishlist();
            WishlistEntry entry = result.entry();
            ResolvedRecipe root = rootRecipeCache.computeIfAbsent(entry, ignored ->
                    entry.itemOnly() ? Optional.empty()
                            : controller.resolver().findByKey(entry.rootRecipe(), entry.outputItemId())).orElse(null);
            if (root == null && !entry.itemOnly()) continue;
            hasVisibleRecipe = true;
            if (showListHeading && !wishlist.id().equals(previousWishlist)) {
                textLines.add(new TextLine(8, y + 4,
                        Component.translatable("screen.wishlist.tree_wishlist", Component.literal(wishlist.name()))
                                .withStyle(ChatFormatting.AQUA), 0x55FFFF));
                y += 18;
            }
            previousWishlist = wishlist.id();
            String rootKey = "entry:" + entry.id();
            boolean completed = entry.completed();
            if (!entry.itemOnly() && transferAvailable(entry.rootRecipe())) y += 18;
            long marked = Math.min(entry.requestedCount(), entry.craftedCount());
            TreeItemVisual rootNode = new TreeItemVisual(rootKey, ROOT_ITEM_X, y + 1,
                    List.of(entry.outputItemId()), entry.requestedCount() - marked, marked,
                    completed ? COMPLETED_TREE_COLOR : 0xFFFFFF,
                    null, completed, searching);
            treeItems.add(rootNode);
            rootCheckboxes.add(new RootCheckboxVisual(ROOT_CHECKBOX_X,
                    y + 1 + (ICON_SIZE - CHECKBOX_SIZE) / 2, wishlist.id(), entry.id(), completed));
            if (!entry.itemOnly()) addTransferButton(entry.rootRecipe(), ROOT_ITEM_X, y + 1);
            if (entry.itemOnly() && actionTouchesViewport(y) && hasRecipe(entry.outputItemId())) {
                addSmallButton(Component.translatable("screen.wishlist.expand"), treeRight - 96, y, 42, () -> {
                    ClientWorkspaceController.SelectionRequest request =
                            controller.beginRootRecipeSelection(wishlist.id(), entry.id());
                    if (RecipeViewerRouter.available()) {
                        openSelectionOrCancel(request);
                    } else {
                        List<ResolvedRecipe> choices = controller.rootRecipes(entry.outputItemId());
                        if (choices.size() == 1) {
                            controller.acceptPendingSelection(choices.get(0));
                            rebuildContents();
                        } else {
                            controller.cancelPendingSelection();
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) mc.player.displayClientMessage(
                                    Component.translatable("screen.wishlist.install_viewer"), true);
                        }
                    }
                }, null, true);
            }
            addSmallButton(Component.translatable("screen.wishlist.edit"), treeRight - 48, y, 42, () ->
                            Minecraft.getInstance().setScreen(new EditEntryScreen(this, wishlist.id(), entry.id())),
                    Component.translatable("screen.wishlist.edit_entry"), true);
            y += ROW_HEIGHT;

            if (entry.itemOnly()) {
                // An item-only root is an unexpanded leaf in the materials list.
            } else if (!root.supported()) {
                textLines.add(new TextLine(ROOT_ITEM_X + ICON_SIZE + 2, y + 4,
                        dimLabel(Component.translatable("screen.wishlist.unsupported")
                                .withStyle(ChatFormatting.GRAY), completed),
                        COMPLETED_TREE_COLOR));
                y += ROW_HEIGHT;
            } else {
                for (RecipeNode node : entry.children()) {
                    y = buildNode(wishlist.id(), node, 1, y, treeRight, calc, rootNode, completed);
                }
            }
            y += 4;
        }

        if (!hasVisibleRecipe) {
            textLines.add(new TextLine(8, CONTENT_TOP + 4,
                    Component.translatable(searching ? "screen.wishlist.search_no_results" : "screen.wishlist.empty_hint")
                            .withStyle(ChatFormatting.GRAY), 0xAAAAAA));
            if (!searching) textLines.add(new TextLine(8, CONTENT_TOP + 16,
                    Component.translatable("screen.wishlist.add_hovered_hint", WishlistClient.addHoveredKeyName())
                            .withStyle(ChatFormatting.GRAY), 0xAAAAAA));
        }
        treeMaxScroll = Math.max(0, y + treeScroll - viewBottom(Scrollbar.TREE) + 6);
        int clampedScroll = Math.min(treeScroll, treeMaxScroll);
        if (clampedScroll != treeScroll) {
            treeScroll = clampedScroll;
            rebuildContents(false);
            return;
        }
        visibleTextLines = textLines.stream()
                .filter(line -> line.y >= CONTENT_TOP - 2 && line.y < viewBottom(Scrollbar.TREE)).toList();
        visibleTreeItems = treeItems.stream().filter(visual -> visual.y >= CONTENT_TOP - 2
                && visual.y < viewBottom(Scrollbar.TREE) - ICON_SIZE).toList();
        List<TreeLineLayout.Line> stems = new ArrayList<>();
        for (TreeItemVisual visual : treeItems) {
            if (visual.root == null) continue;
            stems.add(new TreeLineLayout.Line(visual.x + ICON_SIZE / 2 - LINE_WIDTH / 2 - COL_WIDTH,
                    visual.root.y + ICON_SIZE + LINE_PADDING, visual.y + ICON_SIZE / 2,
                    visual.completed ? 0xFF777777 : 0xFFFFFFFF));
        }
        visibleTreeLines = TreeLineLayout.visible(stems, CONTENT_TOP, viewBottom(Scrollbar.TREE));
        visibleRootCheckboxes = rootCheckboxes.stream()
                .filter(checkbox -> checkbox.y + CHECKBOX_SIZE > CONTENT_TOP
                        && checkbox.y < viewBottom(Scrollbar.TREE)).toList();
    }

    private int buildNode(UUID wishlistId, RecipeNode node, int depth, int y, int treeRight,
                          MaterialCalculation calc, TreeItemVisual root, boolean completed) {
        int itemX = ROOT_ITEM_X + depth * COL_WIDTH;
        RecipeKey transferKey = node.selectedRecipe();
        if (transferAvailable(transferKey)) y += 18;
        NodeQuantity quantity = calc.nodeQuantities().get(node.id());
        long required = quantity == null ? 0 : quantity.required();
        long marked = Math.min(required, node.craftedCount());
        String visualKey = "node:" + node.id();
        List<String> candidates = displayCandidates(node);
        TreeItemVisual newNode = new TreeItemVisual(visualKey, itemX, y + 1, candidates, required - marked, marked,
                completed ? COMPLETED_TREE_COLOR : 0xE8E8E8, root, completed, false);
        treeItems.add(newNode);
        addTransferButton(transferKey, itemX, y + 1);
        if (node.expanded()) {
            int editX = Math.max(treeRight - 184, itemX + 18);
            addSmallButton(Component.translatable("screen.wishlist.edit"), editX, y, 34, () ->
                    Minecraft.getInstance().setScreen(EditEntryScreen.forNode(this, wishlistId, node.id())),
                    Component.translatable("screen.wishlist.edit_branch"), true);
        }

        if (node.expanded()) {
            int actionY = y;
            int buttonX = treeRight - 142;
            if (buttonX < itemX + 52) {
                actionY += ROW_HEIGHT;
                y += ROW_HEIGHT;
                buttonX = itemX + COL_WIDTH;
            }
            int available = Math.max(48, treeRight - buttonX - 6);
            int gap = 4;
            WishlistButtonWidths.Pair actionWidths = WishlistButtonWidths.collapseAndChange(available);
            int firstWidth = actionWidths.first();
            int secondWidth = actionWidths.second();
            addSmallButton(Component.translatable("screen.wishlist.collapse"), buttonX, actionY, firstWidth, () -> {
                controller.collapse(wishlistId, node.id());
                rebuildContents();
            }, null, true);
            addSmallButton(Component.translatable("screen.wishlist.change_recipe"), buttonX + firstWidth + gap, actionY, secondWidth, () -> {
                ClientWorkspaceController.SelectionRequest request = controller.beginRecipeChange(wishlistId, node.id());
                openSelectionOrCancel(request);
            }, null, RecipeViewerRouter.available());
            y += ROW_HEIGHT;
            for (RecipeNode child : node.children()) {
                y = buildNode(wishlistId, child, depth + 1, y, treeRight, calc, newNode, completed);
            }
            return y;
        }

        TerminalReason terminal = node.terminalReason();
        int buttonX = Math.max(itemX + 52, treeRight - 136);
        if (terminal == TerminalReason.MULTIPLE_RECIPES) {
            boolean viewer = RecipeViewerRouter.available();
            int actionY = y;
            int width = Math.min(130, Math.max(48, treeRight - buttonX - 6));
            if (buttonX < itemX + 52 || width < 88) {
                actionY += ROW_HEIGHT;
                y += ROW_HEIGHT;
                buttonX = itemX + COL_WIDTH;
                width = Math.max(48, treeRight - buttonX - 6);
            }
            addSmallButton(Component.translatable("screen.wishlist.multiple_recipes"), buttonX, actionY, width, () -> {
                String candidate = node.selectedCandidateItemId();
                if (candidate == null && !node.ingredient().candidates().isEmpty()) candidate = node.ingredient().candidates().get(0);
                ClientWorkspaceController.SelectionRequest request = controller.beginRecipeSelection(wishlistId, node.id(), candidate);
                openSelectionOrCancel(request);
            }, viewer ? null : Component.translatable("screen.wishlist.install_viewer"), viewer);
        } else if (terminal == TerminalReason.CYCLE) {
            textLines.add(new TextLine(itemX + 20, y + ROW_HEIGHT + 4,
                    dimLabel(Component.translatable("screen.wishlist.cycle")
                            .withStyle(ChatFormatting.GRAY), completed),
                    COMPLETED_TREE_COLOR));
            y += ROW_HEIGHT;
            List<String> alternatives = node.ingredient().candidates().stream()
                    .filter(candidate -> !candidate.equals(node.selectedCandidateItemId()) && hasRecipe(candidate))
                    .toList();
            if (!alternatives.isEmpty()) {
                int actionY = y + ROW_HEIGHT;
                addSmallButton(Component.translatable("screen.wishlist.expand"), treeRight - 48,
                        actionY, 42, () -> Minecraft.getInstance().setScreen(
                                new IngredientCandidateSelectScreen(this, wishlistId, node.id(), alternatives)),
                        null, true);
                y += ROW_HEIGHT;
            }
        } else if (terminal == TerminalReason.UNSUPPORTED) {
            textLines.add(new TextLine(itemX + 20, y + ROW_HEIGHT + 4,
                    dimLabel(Component.translatable("screen.wishlist.unsupported")
                            .withStyle(ChatFormatting.GRAY), completed),
                    COMPLETED_TREE_COLOR));
            y += ROW_HEIGHT;
        } else {
            // Wide rows have fixed height, so offscreen availability is unnecessary.
            // Narrow rows still need it to retain their existing wrapped-action geometry.
            boolean narrow = treeRight - 48 < itemX + 52;
            List<String> expandable = narrow || actionTouchesViewport(y)
                    ? expandableCandidates(node) : List.of();
            if (!expandable.isEmpty()) {
                int actionY = y;
                int expandX = treeRight - 48;
                if (narrow) {
                    actionY += ROW_HEIGHT;
                    y += ROW_HEIGHT;
                    expandX = itemX + COL_WIDTH;
                }
                int expandWidth = 42;
                addSmallButton(Component.translatable("screen.wishlist.expand"), expandX, actionY, expandWidth, () -> {
                    if (node.selectedCandidateItemId() == null) {
                        List<String> available = expandableCandidates(node);
                        if (available.size() > 1) {
                            Minecraft.getInstance().setScreen(new IngredientCandidateSelectScreen(
                                    this, wishlistId, node.id(), available));
                            return;
                        }
                        if (available.size() == 1) {
                            TerminalReason result = controller.expandCandidate(wishlistId, node.id(), available.get(0));
                            rebuildContents();
                            if (result == TerminalReason.MULTIPLE_RECIPES && RecipeViewerRouter.available()) {
                                ClientWorkspaceController.SelectionRequest request =
                                        controller.beginRecipeSelection(wishlistId, node.id(), available.get(0));
                                openSelectionOrCancel(request);
                            }
                            return;
                        }
                    }
                    TerminalReason result = controller.expand(wishlistId, node.id());
                    rebuildContents();
                    if (result == TerminalReason.MULTIPLE_RECIPES && RecipeViewerRouter.available()) {
                        ClientWorkspaceController.SelectionRequest request =
                                controller.beginRecipeSelection(wishlistId, node.id());
                        openSelectionOrCancel(request);
                    }
                }, null, true);
            }
        }
        return y + ROW_HEIGHT;
    }

    private Component dimLabel(Component label, boolean completed) {
        return completed ? label.copy().withStyle(ChatFormatting.GRAY) : label;
    }

    private boolean transferAvailable(RecipeKey key) {
        return transferReadiness(key) != TransferReadiness.UNSUPPORTED;
    }

    private TransferReadiness transferReadiness(RecipeKey key) {
        return key == null ? TransferReadiness.UNSUPPORTED
                : transferReadinessCache.computeIfAbsent(key,
                        recipe -> RecipeViewerRouter.transferReadiness(recipe, parent));
    }

    private void addTransferButton(RecipeKey key, int iconX, int iconY) {
        if (!transferAvailable(key) || iconX + ICON_SIZE > treeRight() - 11
                || iconY - 13 < CONTENT_TOP || iconY + ICON_SIZE >= viewBottom(Scrollbar.TREE)) return;
        addRenderableWidget(new TransferRecipeButton(iconX + 2, iconY - 13,
                transferReadiness(key) == TransferReadiness.READY,
                key,
                ignored -> RecipeViewerRouter.transfer(key, parent)));
    }

    private List<String> expandableCandidates(RecipeNode node) {
        String selected = node.selectedCandidateItemId();
        if (selected != null) {
            return hasRecipe(selected) ? List.of(selected) : List.of();
        }
        return node.ingredient().candidates().stream().filter(this::hasRecipe).toList();
    }

    private boolean hasRecipe(String itemId) {
        return recipeAvailabilityCache.computeIfAbsent(itemId, controller.resolver()::hasRecipeForOutput);
    }

    private void openSelectionOrCancel(ClientWorkspaceController.SelectionRequest request) {
        if (!RecipeViewerRouter.openForSelection(request, this)) {
            controller.cancelPendingSelection();
            RecipeViewerRouter.clearSelectionReturnScreen();
        }
    }

    private void addSmallButton(Component label, int x, int y, int width, Runnable action, Component tooltip, boolean enabled) {
        if (!actionTouchesViewport(y) || width <= 4) return;
        Button button = Button.builder(label, ignored -> action.run()).bounds(x, y + 1, width, 16).build();
        button.active = enabled;
        if (tooltip != null) button.setTooltip(Tooltip.create(tooltip));
        addRenderableWidget(button);
    }

    private boolean actionTouchesViewport(int y) {
        return y >= CONTENT_TOP - 4 && y <= viewBottom(Scrollbar.TREE) - 16;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (treeSearchRefreshPending) rebuildContents(false);
        renderBackground(graphics);
        hoveredStack = ItemStack.EMPTY;
        hoveredResourceName = null;
        hoveredCheckboxTooltip = null;
        iconTargets.clear();
        checkboxTargets.clear();
        if (wishlistMenuOpen) {
            renderWishlistMenuBackground(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int treeRight = treeRight();
        graphics.fill(0, 0, treeRight, height, 0xBB222222);
        graphics.fill(treeRight, 0, width, height, 0xCC191919);
        graphics.fill(treeRight - 1, 0, treeRight + 1, height, 0xFF4A4A4A);

        boolean configHovered = configLinkHovered(mouseX, mouseY);
        graphics.blit(WishlistClient.WIDGETS,8,4,27,8,48,((configHovered) ? 8 : 0),27,8,256,256);
        graphics.drawString(font, Component.translatable("screen.wishlist.select_root_hint"),
                40, 5, 0x999999, false);
        graphics.drawString(font, selectedWishlistLabel(), 8, 18, 0xE8E8E8, false);
        graphics.drawString(font, Component.translatable("screen.wishlist.recipe_trees"), 8, 54, 0xBFBFBF, false);
        graphics.drawString(font, Component.translatable("screen.wishlist.materials"), treeRight + 6, 54, 0xBFBFBF, false);

        graphics.enableScissor(0, CONTENT_TOP, treeRight - 11, viewBottom(Scrollbar.TREE));
        for (TreeItemVisual visual : visibleTreeItems) {
            if (visual.searchMatch) {
                graphics.fill(0, visual.y - 2, treeRight - 11, visual.y + ROW_HEIGHT - 2, 0x33BCE2E8);
            }
        }
        for (RootCheckboxVisual checkbox : visibleRootCheckboxes) {
            if (selectedEntryIds.contains(checkbox.entryId())) {
                graphics.fill(0, checkbox.y - 2, treeRight - 11,
                        checkbox.y + ROW_HEIGHT - 2, 0x445577BB);
            }
        }
        for (TextLine line : visibleTextLines) {
            graphics.drawString(font, line.text, line.x, line.y, line.color, false);
        }

        long now = System.currentTimeMillis();
        for (TreeLineLayout.Line line : visibleTreeLines) {
            graphics.fill(line.x(), line.top(), line.x() + LINE_WIDTH, line.bottom(), line.color());
        }
        for (TreeItemVisual visual : visibleTreeItems) {
            final int MAX_Y = 66;
            int lineColor = visual.completed ? 0xFF777777 : 0xFFFFFFFF;
            if (visual.y >= CONTENT_TOP - 2 && visual.y < viewBottom(Scrollbar.TREE) - ICON_SIZE) {
                if (visual.root != null) {
                    // 横
                    graphics.fill(visual.x - LINE_PADDING ,Math.max(MAX_Y,visual.y + ICON_SIZE / 2 - LINE_WIDTH / 2),visual.x + ICON_SIZE / 2 - LINE_WIDTH / 2 - COL_WIDTH, Math.max(MAX_Y,visual.y + ICON_SIZE / 2 + LINE_WIDTH / 2), lineColor);
                }
                // 箱
                if (!visual.completed) {
                    graphics.fill(visual.x-1,Math.max(MAX_Y,visual.y-1),visual.x+ICON_SIZE+1,Math.max(MAX_Y,visual.y+ICON_SIZE+1),0x33ffffff);
                }
                renderCyclingItem(graphics, visual.key, visual.x, visual.y, visual.candidates, visual.count, visual.crafted,
                        visual.color, mouseX, mouseY, now, 1.0F);
            }
        }
        for (RootCheckboxVisual checkbox : visibleRootCheckboxes) {
            graphics.blit(WishlistClient.WIDGETS, checkbox.x, checkbox.y, CHECKBOX_SIZE, CHECKBOX_SIZE,
                    32, checkbox.checked ? CHECKBOX_SIZE : 0,
                    CHECKBOX_SIZE, CHECKBOX_SIZE, 256, 256);
            if (checkbox.contains(mouseX, mouseY)) {
                graphics.fill(checkbox.x, checkbox.y,
                        checkbox.x + CHECKBOX_SIZE, checkbox.y + CHECKBOX_SIZE, 0x46FFFFFF);
                hoveredCheckboxTooltip = Component.translatable(checkbox.checked
                        ? "screen.wishlist.unmark_completed" : "screen.wishlist.mark_completed");
            }
        }
        graphics.disableScissor();
        renderMaterials(graphics, treeRight + 6, CONTENT_TOP, mouseX, mouseY, now);
        renderScrollbar(graphics, Scrollbar.TREE, treeRight - 9, mouseX, mouseY);
        renderScrollbar(graphics, Scrollbar.MATERIALS, width - 9, mouseX, mouseY);
        renderTopButton(graphics, Scrollbar.TREE, mouseX, mouseY);
        renderTopButton(graphics, Scrollbar.MATERIALS, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (treeSearch != null && children().contains(treeSearch)
                && treeSearch.getValue().isEmpty() && !treeSearch.isFocused())
            graphics.drawString(font, Component.translatable("screen.wishlist.search_tree"),
                    treeSearch.getX() + 4, treeSearch.getY() + 4, 0x888888, false);
        if (configHovered) graphics.renderTooltip(font, Component.literal("WishlistMod"), mouseX, mouseY);
        if (hoveredCheckboxTooltip != null) graphics.renderTooltip(font, hoveredCheckboxTooltip, mouseX, mouseY);
        else if (hoveredResourceName != null) graphics.renderTooltip(font, hoveredResourceName, mouseX, mouseY);
        else if (!hoveredStack.isEmpty()) {
            if (hoveredStack.hasTag()) graphics.renderComponentTooltip(font,
                    List.of(hoveredStack.getHoverName(),
                            Component.translatable("screen.wishlist.nbt_detail", hoveredStack.getTag().toString())
                                    .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            else graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    private void renderWishlistMenuBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xE6191919);
        graphics.drawCenteredString(font, Component.translatable("screen.wishlist.menu_title"), width / 2, 12, 0xFFFFFF);
    }

    private Component selectedWishlistLabel() {
        List<String> names = controller.workspace().selectedWishlists().stream().map(Wishlist::name).toList();
        String separator = Component.translatable("screen.wishlist.name_separator").getString();
        String namesText = names.isEmpty()
                ? Component.translatable("screen.wishlist.none_selected").getString()
                : String.join(separator, names);
        Component prefix = Component.translatable("screen.wishlist.selected_prefix");
        int maxWidth = Math.max(20, width / 2);
        int available = Math.max(0, maxWidth - font.width(prefix));
        String ellipsis = Component.translatable("screen.wishlist.ellipsis").getString();
        if (font.width(namesText) > available) {
            namesText = font.plainSubstrByWidth(namesText, Math.max(0, available - font.width(ellipsis))) + ellipsis;
        }
        return prefix.copy().append(Component.literal(namesText));
    }

    private boolean configLinkHovered(double mouseX, double mouseY) {
        return mouseX >= 8 && mouseX < 8 + 27
                && mouseY >= 4 && mouseY < 4 + 7;
    }

    private void renderMaterials(GuiGraphics graphics, int x, int startY, int mouseX, int mouseY, long now) {
        List<MaterialSummaryRow> rows = visibleMaterials();
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.wishlist.no_missing_materials"),
                    x, startY + 5, 0xAAAAAA, false);
            return;
        }
        int materialRowHeight = Math.max(ROW_HEIGHT, CHECKBOX_SIZE + 2);
        int firstRow = Math.max(0, materialScroll / materialRowHeight);
        int y = startY - materialScroll + firstRow * materialRowHeight;
        graphics.enableScissor(treeRight() + 2, CONTENT_TOP, width - 11, viewBottom(Scrollbar.MATERIALS));
        for (int rowIndex = firstRow; rowIndex < rows.size(); rowIndex++) {
            if (y >= viewBottom(Scrollbar.MATERIALS)) break;
            MaterialSummaryRow row = rows.get(rowIndex);
            boolean checked = visibleMaterialCompleted.contains(row.identityKey());
            int color = row.satisfied() || checked ? 0x777777 : 0xE8E8E8;
            float alpha = row.satisfied() || checked ? 0.45F : 1.0F;
            int checkboxY = y + (materialRowHeight - CHECKBOX_SIZE) / 2;
            int iconY = y + (materialRowHeight - ICON_SIZE) / 2;
            checkboxTargets.add(new CheckboxTarget(x, checkboxY, row));
            graphics.blit(WishlistClient.WIDGETS, x, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE,
                    32, checked ? CHECKBOX_SIZE : 0,
                    CHECKBOX_SIZE, CHECKBOX_SIZE, 256, 256);
            if (mouseX >= x && mouseX < x + CHECKBOX_SIZE
                    && mouseY >= checkboxY && mouseY < checkboxY + CHECKBOX_SIZE) {
                graphics.fill(x, checkboxY, x + CHECKBOX_SIZE, checkboxY + CHECKBOX_SIZE, 0x46FFFFFF);
                hoveredCheckboxTooltip = Component.translatable(checked
                        ? "screen.wishlist.unmark_completed" : "screen.wishlist.mark_completed");
            }
            String key = "material:" + row.displayKey() + ':' + String.join(";", row.candidates());
            renderCyclingItem(graphics, key, x + CHECKBOX_SIZE + 4, iconY,
                    row.candidates(), -1, 0, color, mouseX, mouseY, now, alpha);
            Component amount = Component.translatable("screen.wishlist.material_count", row.allocatedOwned(), row.required());
            String unit = UiText.unit(row.candidates().get(0));
            if (!unit.isBlank()) amount = amount.copy().append(" " + unit);
            graphics.drawString(font,
                    amount,
                    x + CHECKBOX_SIZE + ICON_SIZE + 8,
                    y + (materialRowHeight - font.lineHeight) / 2, color, false);
            y += materialRowHeight;
        }
        graphics.disableScissor();
    }

    private int scrollbarX(Scrollbar bar) {
        return bar == Scrollbar.TREE ? treeRight() - 9 : width - 9;
    }

    private int viewBottom(Scrollbar bar) {
        int footer = bar == Scrollbar.TREE && !selectedEntryIds.isEmpty() ? 52 : 28;
        return Math.max(CONTENT_TOP + 1, height - footer);
    }

    private int topButtonX(Scrollbar bar) {
        return bar == Scrollbar.TREE ? 6 : treeRight() + 6;
    }

    private int topButtonY() { return height - 21; }

    private int scrollbarMax(Scrollbar bar) {
        return bar == Scrollbar.TREE ? treeMaxScroll : materialMaxScroll;
    }

    private int scrollbarValue(Scrollbar bar) {
        return bar == Scrollbar.TREE ? treeScroll : materialScroll;
    }

    private int thumbHeight(Scrollbar bar) {
        int viewport = Math.max(1, viewBottom(bar) - CONTENT_TOP);
        return Math.min(viewport, Math.max(14, viewport * viewport / (viewport + scrollbarMax(bar))));
    }

    private int thumbY(Scrollbar bar) {
        int travel = Math.max(0, viewBottom(bar) - CONTENT_TOP - thumbHeight(bar));
        int max = scrollbarMax(bar);
        return CONTENT_TOP + (max == 0 ? 0 : (int) ((long) scrollbarValue(bar) * travel / max));
    }

    private void renderScrollbar(GuiGraphics graphics, Scrollbar bar, int x, int mouseX, int mouseY) {
        if (scrollbarMax(bar) <= 0) return;
        int y = thumbY(bar);
        int thumbHeight = thumbHeight(bar);
        graphics.fill(x, CONTENT_TOP, x + 6, viewBottom(bar), 0x88404040);
        boolean hovered = mouseX >= x && mouseX < x + 6 && mouseY >= y && mouseY < y + thumbHeight;
        graphics.fill(x, y, x + 6, y + thumbHeight,
                hovered || draggedScrollbar == bar ? 0xFFE0E0E0 : 0xFF999999);
    }

    private void renderTopButton(GuiGraphics graphics, Scrollbar bar, int mouseX, int mouseY) {
        int x = topButtonX(bar);
        int y = topButtonY();
        boolean active = scrollbarValue(bar) > 0;
        boolean hovered = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        graphics.blit(WishlistClient.WIDGETS, x, y, 12, 12,
                48, 64 + (!active ? 24 : hovered ? 12 : 0), 12, 12, 256, 256);
        if (hovered) hoveredCheckboxTooltip = Component.translatable("screen.wishlist.scroll_to_top");
    }

    private void setScrollbarFromMouse(Scrollbar bar, double mouseY) {
        int travel = Math.max(1, viewBottom(bar) - CONTENT_TOP - thumbHeight(bar));
        int position = (int) Math.max(0, Math.min(travel, mouseY - CONTENT_TOP - scrollbarDragOffset));
        int value = (int) ((long) position * scrollbarMax(bar) / travel);
        if (bar == Scrollbar.TREE) {
            if (treeScroll != value) {
                treeScroll = value;
                rebuildContents(false);
            }
        } else {
            materialScroll = value;
        }
    }

    private void renderCyclingItem(GuiGraphics graphics, String key, int x, int y, List<String> candidates,
                                   long count, long crafted, int color, int mouseX, int mouseY, long now, float alpha) {
        if (candidates == null || candidates.isEmpty()) return;
        boolean hovered = mouseX >= x - 1 && mouseX < x + ICON_SIZE + 1
                && mouseY >= y - 1 && mouseY < y + ICON_SIZE + 1;
        int current = CandidateCycle.indexAt(now, candidates.size());
        String chosen = chosenCandidates.get(key);
        if (chosen != null && candidates.contains(chosen)) current = candidates.indexOf(chosen);
        Integer paused = pausedCandidateIndices.get(key);
        if (chosen == null && hovered) {
            if (paused == null) {
                paused = current;
                pausedCandidateIndices.put(key, paused);
            }
            current = paused;
        } else if (!hovered) {
            pausedCandidateIndices.remove(key);
        }
        current = Math.max(0, Math.min(candidates.size() - 1, current));
        String selected = candidates.get(current);
        if (candidates.size() > 1 || ResourceIdentity.parse(selected).kind().equals("item")
                && RecipeViewerRouter.available()) {
            iconTargets.add(new IconTarget(x - 1, y - 1, key, candidates, selected));
        }
        ItemStack stack = UiText.itemStack(selected);
        if (!stack.isEmpty()) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.renderItem(stack, x, y);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (hovered) {
                if (!ResourceIdentity.parse(candidates.get(current)).kind().equals("item"))
                    hoveredResourceName = UiText.itemName(candidates.get(current));
                else hoveredStack = stack;
            }
        }
        if (hovered && (candidates.size() > 1 || ResourceIdentity.parse(selected).kind().equals("item")
                && RecipeViewerRouter.available())) {
            graphics.fill(x - 1, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1, 0x32FFFFFF);
        }
        if (count >= 0) {
            Component quantity = Component.translatable("screen.wishlist.quantity", count);
            String unit = UiText.unit(candidates.get(current));
            int amountX = x + 20;
            graphics.drawString(font, quantity, amountX, y + 4, color, false);
            int progressX = amountX + font.width(quantity);
            if (crafted > 0) {
                Component progress = Component.literal("(" + crafted + "/" + (count + crafted) + ")");
                graphics.drawString(font, progress, progressX, y + 4, 0x777777, false);
                progressX += font.width(progress);
            }
            if (!unit.isBlank()) graphics.drawString(font, " " + unit, progressX, y + 4, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!wishlistMenuOpen && button == 0) {
            if (mouseY >= topButtonY() && mouseY < topButtonY() + 12) {
                if (mouseX >= topButtonX(Scrollbar.TREE)
                        && mouseX < topButtonX(Scrollbar.TREE) + 12 && treeScroll > 0) {
                    treeScroll = 0;
                    rebuildContents(false);
                    return true;
                }
                if (mouseX >= topButtonX(Scrollbar.MATERIALS)
                        && mouseX < topButtonX(Scrollbar.MATERIALS) + 12 && materialScroll > 0) {
                    materialScroll = 0;
                    return true;
                }
            }
            if (configLinkHovered(mouseX, mouseY)) {
                UiClickSound.play();
                Minecraft.getInstance().setScreen(new WishlistConfigScreen(this));
                return true;
            }
            for (Scrollbar bar : Scrollbar.values()) {
                int x = scrollbarX(bar);
                if (scrollbarMax(bar) > 0 && mouseX >= x && mouseX < x + 6
                        && mouseY >= CONTENT_TOP && mouseY < viewBottom(bar)) {
                    draggedScrollbar = bar;
                    scrollbarDragOffset = (int) (mouseY - thumbY(bar));
                    if (scrollbarDragOffset < 0 || scrollbarDragOffset >= thumbHeight(bar)) {
                        scrollbarDragOffset = thumbHeight(bar) / 2;
                        setScrollbarFromMouse(bar, mouseY);
                    }
                    return true;
                }
            }
            Scrollbar pane = mouseX < treeRight() ? Scrollbar.TREE : Scrollbar.MATERIALS;
            if (mouseY < CONTENT_TOP || mouseY >= viewBottom(pane))
                return super.mouseClicked(mouseX, mouseY, button);
            if (Screen.hasControlDown()) {
                for (RootCheckboxVisual checkbox : visibleRootCheckboxes) {
                    int iconY = checkbox.y - (ICON_SIZE - CHECKBOX_SIZE) / 2;
                    if (mouseX >= ROOT_ITEM_X && mouseX < ROOT_ITEM_X + ICON_SIZE + 2
                            && mouseY >= iconY - 1 && mouseY < iconY + ICON_SIZE + 1) {
                        if (!selectedEntryIds.add(checkbox.entryId())) selectedEntryIds.remove(checkbox.entryId());
                        rebuildContents(false);
                        return true;
                    }
                }
            }
            for (RootCheckboxVisual checkbox : visibleRootCheckboxes) {
                if (checkbox.contains(mouseX, mouseY)) {
                    controller.toggleEntryCompleted(checkbox.wishlistId(), checkbox.entryId());
                    rebuildContents();
                    return true;
                }
            }
            for (CheckboxTarget checkbox : checkboxTargets) {
                if (checkbox.contains(mouseX, mouseY)) {
                    controller.toggleMaterialCompleted(checkbox.row());
                    rebuildContents();
                    return true;
                }
            }
            for (IconTarget icon : iconTargets) {
                if (!icon.contains(mouseX, mouseY)) continue;
                if (icon.candidates().size() > 1) {
                    Minecraft.getInstance().setScreen(new DisplayCandidateSelectScreen(this, icon.candidates(),
                            icon.itemId(), candidate -> {
                                chosenCandidates.put(icon.key(), candidate);
                                pausedCandidateIndices.remove(icon.key());
                            }));
                    return true;
                }
                if (RecipeViewerRouter.openRecipesFromWishlist(icon.itemId(), this)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggedScrollbar != null) {
            setScrollbarFromMouse(draggedScrollbar, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedScrollbar != null) {
            draggedScrollbar = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private List<String> displayCandidates(RecipeNode node) {
        return displayCandidateCache.computeIfAbsent(node, ignored -> resolveDisplayCandidates(node));
    }

    private List<String> resolveDisplayCandidates(RecipeNode node) {
        if (node.selectedCandidateItemId() != null) return List.of(node.selectedCandidateItemId());
        if (node.selectedRecipe() != null) {
            String target = node.selectedCandidateItemId();
            String output = target == null
                    ? controller.resolver().findByKey(node.selectedRecipe()).map(ResolvedRecipe::outputItemId).orElse(null)
                    : controller.resolver().findByKey(node.selectedRecipe(), target).map(ResolvedRecipe::outputItemId).orElse(null);
            if (output != null && node.ingredient().candidates().stream()
                    .anyMatch(candidate -> ResourceIdentity.matches(candidate, output))) return List.of(output);
        }
        return node.ingredient().candidates();
    }

    private int treeRight() {
        return width - RIGHT_WIDTH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int direction = (int) Math.signum(delta);
        if (wishlistMenuOpen) {
            wishlistMenuScroll = Math.max(0, Math.min(wishlistMenuMaxScroll, wishlistMenuScroll - direction * 24));
            rebuildContents(false);
            return true;
        }
        if (mouseX < treeRight() && mouseY >= CONTENT_TOP - 4) {
            treeScroll = Math.max(0, Math.min(treeMaxScroll, treeScroll - direction * 28));
            rebuildContents(false);
            return true;
        }
        if (mouseX >= treeRight() && mouseY >= CONTENT_TOP - 4) {
            materialScroll = Math.max(0, Math.min(materialMaxScroll, materialScroll - direction * ROW_HEIGHT * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (wishlistMenuOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            wishlistMenuOpen = false;
            rebuildContents();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (wishlistMenuOpen) {
            wishlistMenuOpen = false;
            rebuildContents();
            return;
        }
        if (controller.pendingSelection().isPresent()) controller.cancelPendingSelection();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
