package net.ochibo.wishlist.client;

import net.ochibo.wishlist.client.ingredients.MinecraftIngredientsLoader;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.client.ui.independent.AddToWishlistAnimationController;
import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.material.MaterialCalculation;
import net.ochibo.wishlist.core.material.MaterialCalculationCache;
import net.ochibo.wishlist.core.material.MaterialSummaryRow;
import net.ochibo.wishlist.core.material.MaterialCalculator;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.persistence.ClientWorldKey;
import net.ochibo.wishlist.core.persistence.WishlistRepository;
import net.ochibo.wishlist.core.persistence.WishlistStorageLocator;
import net.ochibo.wishlist.core.persistence.WorkspaceState;
import net.ochibo.wishlist.core.persistence.WorkspaceStateRepository;
import net.ochibo.wishlist.core.recipe.CompositeRecipeResolver;
import net.ochibo.wishlist.core.recipe.CachingRecipeResolver;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.tree.ExpandAllSession;
import net.ochibo.wishlist.core.tree.RecipeNode;
import net.ochibo.wishlist.core.tree.RecipeTreeService;
import net.ochibo.wishlist.core.tree.TerminalReason;
import net.ochibo.wishlist.core.workspace.WishlistWorkspace;
import net.ochibo.wishlist.core.workspace.CraftProgressRecorder;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.platform.WishlistPlatform;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClientWorkspaceController {
    public enum SelectionMode { EXPAND, CHANGE }

    public record SelectionRequest(UUID wishlistId, RecipeNode node, List<RecipeKey> ancestors, boolean automatic,
                                   SelectionMode mode, String targetItemId, WishlistEntry rootEntry) {
        public SelectionRequest(UUID wishlistId, RecipeNode node, List<RecipeKey> ancestors, boolean automatic,
                                SelectionMode mode, String targetItemId) {
            this(wishlistId, node, ancestors, automatic, mode, targetItemId, null);
        }
        public String itemId() {
            if (targetItemId != null && !targetItemId.isBlank()) return targetItemId;
            return node.ingredient().candidates().isEmpty() ? node.ingredient().displayKey() : node.ingredient().candidates().get(0);
        }
    }

    private static final ClientWorkspaceController INSTANCE = new ClientWorkspaceController();
    public static ClientWorkspaceController get() { return INSTANCE; }

    private final RecipeTreeService tree = new RecipeTreeService();
    private final MinecraftInventorySource inventorySource = new MinecraftInventorySource();
    private final MinecraftIngredientsLoader ingredientsLoader = new MinecraftIngredientsLoader();
    private ClientWorldKey loadedKey;
    private WishlistRepository repository;
    private WorkspaceStateRepository stateRepository;
    private WishlistWorkspace workspace = new WishlistWorkspace(List.of());
    private MinecraftRecipeResolver nativeResolver;
    private RecipeResolver resolver;
    private Set<String> ingredients = Set.of();
    private List<WishlistRepository.LoadError> loadErrors = List.of();
    private SelectionRequest pendingSelection;
    private final Deque<AutoContext> autoQueue = new ArrayDeque<>();
    private int autoSelectionsHandled;
    private final Set<String> completedMaterials = new LinkedHashSet<>();
    private Set<String> completedMaterialsSnapshot;
    private final MaterialCalculationCache calculationCache = new MaterialCalculationCache();
    private record ResolverStamp(Object nativeResolver, Object jeiResolver, Object emiResolver) {}

    private record AutoContext(UUID wishlistId, ExpandAllSession session) {}

    private ClientWorkspaceController() {}

    public synchronized void ensureLoaded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientWorldKey key = ClientContextResolver.current(mc);
        if (key.equals(loadedKey) && resolver != null) return;

        loadedKey = key;
        var listDirectory = WishlistStorageLocator.listDirectory(WishlistPlatform.configDirectory(), key);
        repository = new WishlistRepository(listDirectory);
        stateRepository = new WorkspaceStateRepository(listDirectory.getParent().resolve("workspace.wstate"));
        List<WishlistRepository.LoadError> errors = new ArrayList<>();
        try {
            WishlistRepository.LoadBatch batch = repository.loadAllTolerant();
            errors.addAll(batch.errors());
            workspace = new WishlistWorkspace(batch.wishlists());
        } catch (IOException e) {
            errors.add(new WishlistRepository.LoadError(repository.directory(), e.toString()));
            workspace = new WishlistWorkspace(List.of());
        }
        if (workspace.wishlists().isEmpty()) {
            Wishlist first = workspace.create(defaultWishlistName());
            save(first);
        }
        try {
            completedMaterials.clear();
            completedMaterialsSnapshot = null;
            stateRepository.load().ifPresent(state -> {
                if (workspace.find(state.activeId()).isPresent()) workspace.setActive(state.activeId());
                workspace.setCombinedSelection(state.combinedSelection());
                completedMaterials.addAll(state.completedMaterials());
            });
        } catch (IOException e) {
            errors.add(new WishlistRepository.LoadError(listDirectory.getParent().resolve("workspace.wstate"), e.toString()));
        }
        loadErrors = List.copyOf(errors);
        for (WishlistRepository.LoadError error : loadErrors) {
            if (error.file().toString().endsWith(".wlst")) WishlistToasts.loadFailed(error.wishlistName());
        }
        saveWorkspaceState();
        rebuildResolvers(mc);
        ResourceManager raw = mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().getResourceManager();
        ingredients = ingredientsLoader.load(raw);
        pendingSelection = null;
        autoQueue.clear();
    }

    public synchronized void invalidate() {
        calculationCache.invalidate();
        HudMaterialPanel.invalidate();
        AddToWishlistAnimationController.clear();
        loadedKey = null;
        resolver = null;
        nativeResolver = null;
        repository = null;
        stateRepository = null;
        pendingSelection = null;
        autoQueue.clear();
        completedMaterials.clear();
        completedMaterialsSnapshot = null;
    }

    public WishlistWorkspace workspace() { ensureLoaded(); return workspace; }
    public RecipeResolver resolver() { ensureLoaded(); return resolver; }
    public Set<String> ingredients() { ensureLoaded(); return ingredients; }
    public List<WishlistRepository.LoadError> loadErrors() { ensureLoaded(); return loadErrors; }

    private static String defaultWishlistName() {
        return Component.translatable("screen.wishlist.default_name").getString();
    }

    public Wishlist createWishlist(String name) {
        ensureLoaded();
        Wishlist wishlist = workspace.create(name);
        workspace.setCombinedSelection(List.of(wishlist.id()));
        save(wishlist);
        saveWorkspaceState();
        return wishlist;
    }

    public void renameWishlist(UUID id, String name) {
        ensureLoaded();
        Wishlist wishlist = workspace.find(id).orElseThrow(() -> new IllegalArgumentException("unknown wishlist: " + id));
        wishlist.rename(name);
        save(wishlist);
    }

    public void deleteWishlist(UUID id) {
        ensureLoaded();
        if (workspace.find(id).isEmpty()) return;
        try {
            if (repository != null) repository.delete(id);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete wishlist " + id, e);
        }
        workspace.remove(id);
        if (workspace.wishlists().isEmpty()) {
            Wishlist replacement = workspace.create(defaultWishlistName());
            save(replacement);
        }
        saveWorkspaceState();
    }

    public synchronized void refreshResources() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (loadedKey == null) {
            ensureLoaded();
            return;
        }
        rebuildResolvers(mc);
        ResourceManager raw = mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().getResourceManager();
        ingredients = ingredientsLoader.load(raw);
        pendingSelection = null;
        autoQueue.clear();
    }

    private void rebuildResolvers(Minecraft mc) {
        if (mc.level == null) return;
        calculationCache.invalidate();
        HudMaterialPanel.invalidate();
        nativeResolver = new MinecraftRecipeResolver(mc.level.getRecipeManager(), mc.level.registryAccess());
        RecipeResolver combined = new CompositeRecipeResolver(
                new CompositeRecipeResolver(nativeResolver, RecipeViewerRouter::jeiRecipeResolver),
                RecipeViewerRouter::emiRecipeResolver);
        resolver = new CachingRecipeResolver(combined, () -> new ResolverStamp(nativeResolver,
                RecipeViewerRouter.jeiRecipeResolver().orElse(null), RecipeViewerRouter.emiRecipeResolver().orElse(null)));
    }

    public void setActive(UUID id) {
        ensureLoaded();
        workspace.setActive(id);
        saveWorkspaceState();
    }

    public void setCombinedSelection(List<UUID> ids) {
        ensureLoaded();
        workspace.setCombinedSelection(ids);
        saveWorkspaceState();
    }

    public ResolvedRecipe resolveMinecraftRecipe(Recipe<?> recipe) {
        ensureLoaded();
        return nativeResolver.convert(recipe);
    }

    /** Native interpretation only; never re-enters a viewer fallback. */
    public Optional<ResolvedRecipe> resolveNativeRecipe(RecipeKey key, String target) {
        ensureLoaded();
        return target == null || target.isBlank() ? nativeResolver.findByKey(key)
                : nativeResolver.findByKey(key, target);
    }

    public WishlistEntry addTo(UUID wishlistId, ResolvedRecipe recipe, long count) {
        ensureLoaded();
        if (!recipe.supported()) throw new IllegalArgumentException("Unsupported recipe: " + recipe.unsupportedReason());
        WishlistEntry entry = workspace.addTo(wishlistId, recipe, count);
        save(workspace.find(wishlistId).orElseThrow());
        AddToWishlistAnimationController.onItemAdded(entry.outputItemId());
        return entry;
    }

    public WishlistEntry quickAdd(ResolvedRecipe recipe, long count) {
        ensureLoaded();
        Wishlist target = workspace.active().orElseGet(() -> createWishlist(defaultWishlistName()));
        return addTo(target.id(), recipe, count);
    }

    public WishlistEntry addItemTo(UUID wishlistId, String itemId, long count) {
        ensureLoaded();
        WishlistEntry entry = workspace.addItemTo(wishlistId, itemId, count);
        save(workspace.find(wishlistId).orElseThrow());
        AddToWishlistAnimationController.onItemAdded(entry.outputItemId());
        return entry;
    }

    public WishlistEntry quickAddItem(String itemId, long count) {
        ensureLoaded();
        Wishlist target = workspace.active().orElseGet(() -> createWishlist(defaultWishlistName()));
        return addItemTo(target.id(), itemId, count);
    }

    public List<ResolvedRecipe> rootRecipes(String itemId) {
        ensureLoaded();
        Map<RecipeKey, ResolvedRecipe> found = new java.util.LinkedHashMap<>();
        for (ResolvedRecipe recipe : resolver.findForOutput(itemId)) {
            if (recipe.supported() && ResourceIdentity.matches(itemId, recipe.outputItemId())) {
                found.putIfAbsent(recipe.key(), recipe);
            }
        }
        return List.copyOf(found.values());
    }

    public void selectRootRecipe(UUID wishlistId, UUID entryId, ResolvedRecipe recipe) {
        ensureLoaded();
        workspace.selectRootRecipe(wishlistId, entryId, recipe);
        save(workspace.find(wishlistId).orElseThrow());
    }

    public void setRequestedCount(UUID wishlistId, UUID entryId, long count) {
        ensureLoaded();
        workspace.setRequestedCount(wishlistId, entryId, count);
        save(workspace.find(wishlistId).orElseThrow());
    }

    public void setEntryProgress(UUID wishlistId, UUID entryId, long requested, long crafted) {
        ensureLoaded();
        Wishlist wishlist = workspace.find(wishlistId).orElseThrow();
        WishlistEntry entry = wishlist.entries().stream().filter(e -> e.id().equals(entryId)).findFirst().orElseThrow();
        entry.setRequestedCount(requested);
        if (!entry.completed()) entry.setCraftedCount(crafted);
        wishlist.touch();
        save(wishlist);
    }

    public void setNodeCraftedCount(UUID wishlistId, UUID nodeId, long crafted) {
        ensureLoaded();
        RecipeNode node = locateNode(wishlistId, nodeId).orElseThrow().node;
        var quantity = materialCalculation().nodeQuantities().get(nodeId);
        long required = quantity == null ? 0 : quantity.required();
        node.setCraftedCount(crafted, required);
        touchAndSave(wishlistId);
    }

    public synchronized void recordCraft(ItemStack result, long craftedAmount) {
        if (result.isEmpty() || craftedAmount <= 0) return;
        ensureLoaded();
        if (resolver == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InventorySnapshot inventory = inventorySource.snapshot(mc.player);
        String outputId = StackResolver.id(result);
        for (Wishlist wishlist : workspace.wishlists()) {
            Map<UUID, net.ochibo.wishlist.core.material.NodeQuantity> quantities =
                    new MaterialCalculator(resolver).calculate(List.of(wishlist), inventory).nodeQuantities();
            if (CraftProgressRecorder.record(wishlist, quantities, outputId, craftedAmount)) save(wishlist);
        }
    }

    public void removeEntry(UUID wishlistId, UUID entryId) {
        ensureLoaded();
        if (workspace.removeEntry(wishlistId, entryId)) {
            save(workspace.find(wishlistId).orElseThrow());
        }
    }

    public void markEntriesCompleted(Set<UUID> ids) {
        ensureLoaded();
        for (Wishlist wishlist : workspace.wishlists()) {
            if (wishlist.markEntriesCompleted(ids)) save(wishlist);
        }
    }

    public void removeEntries(Set<UUID> ids) {
        ensureLoaded();
        for (Wishlist wishlist : workspace.wishlists()) {
            if (wishlist.removeEntries(ids)) save(wishlist);
        }
    }

    public void toggleEntryCompleted(UUID wishlistId, UUID entryId) {
        ensureLoaded();
        workspace.toggleEntryCompleted(wishlistId, entryId);
        save(workspace.find(wishlistId).orElseThrow());
    }

    public MaterialCalculation materialCalculation() {
        ensureLoaded();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return new MaterialCalculation(List.of(), Map.of());
        return materialCalculation(inventorySource.snapshot(mc.player));
    }

    public InventorySnapshot inventorySnapshot() {
        ensureLoaded();
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? InventorySnapshot.empty() : inventorySource.snapshot(mc.player);
    }

    public MaterialCalculation materialCalculation(InventorySnapshot inventory) {
        ensureLoaded();
        if (Minecraft.getInstance().player == null) return new MaterialCalculation(List.of(), Map.of());
        List<Wishlist> selected = workspace.selectedWishlists();
        ResolverStamp sources = new ResolverStamp(resolver, RecipeViewerRouter.jeiRecipeResolver().orElse(null),
                RecipeViewerRouter.emiRecipeResolver().orElse(null));
        return calculationCache.get(selected, inventory, sources, () -> calculateMaterials(selected, inventory));
    }

    private MaterialCalculation calculateMaterials(List<Wishlist> selected, InventorySnapshot inventory) {
        MaterialCalculation calculation = new MaterialCalculator(resolver).calculate(selected, inventory);
        for (Wishlist wishlist : selected) {
            boolean changed = false;
            for (WishlistEntry entry : wishlist.entries()) {
                for (RecipeNode node : entry.children()) changed |= clampCraftedToCalculated(node, calculation);
            }
            if (changed) {
                wishlist.touch();
                save(wishlist);
            }
        }
        return calculation;
    }

    private boolean clampCraftedToCalculated(RecipeNode node, MaterialCalculation calculation) {
        var quantity = calculation.nodeQuantities().get(node.id());
        long required = quantity == null ? 0 : quantity.required();
        boolean changed = node.craftedCount() > required;
        if (changed) node.setCraftedCount(required, required);
        for (RecipeNode child : node.children()) changed |= clampCraftedToCalculated(child, calculation);
        return changed;
    }

    public boolean isMaterialCompleted(MaterialSummaryRow row) {
        ensureLoaded();
        return completedMaterials.contains(row.identityKey());
    }

    public Set<String> completedMaterials() {
        ensureLoaded();
        if (completedMaterialsSnapshot == null) completedMaterialsSnapshot = Set.copyOf(completedMaterials);
        return completedMaterialsSnapshot;
    }

    public void toggleMaterialCompleted(MaterialSummaryRow row) {
        ensureLoaded();
        String key = row.identityKey();
        if (!completedMaterials.remove(key)) completedMaterials.add(key);
        completedMaterialsSnapshot = null;
        saveWorkspaceState();
    }

    public TerminalReason expand(UUID wishlistId, UUID nodeId) {
        ensureLoaded();
        NodeLocation location = locateNode(wishlistId, nodeId).orElseThrow();
        TerminalReason result = tree.expandOne(location.node, resolver, location.ancestors);
        touchAndSave(wishlistId);
        return result;
    }

    public TerminalReason expandCandidate(UUID wishlistId, UUID nodeId, String candidateItemId) {
        ensureLoaded();
        NodeLocation location = locateNode(wishlistId, nodeId).orElseThrow();
        TerminalReason result = tree.expandOneForCandidate(location.node, resolver, location.ancestors, candidateItemId);
        touchAndSave(wishlistId);
        return result;
    }

    public void collapse(UUID wishlistId, UUID nodeId) {
        ensureLoaded();
        RecipeNode node = locateNode(wishlistId, nodeId).orElseThrow().node;
        tree.collapse(node);
        touchAndSave(wishlistId);
    }

    public SelectionRequest beginRecipeSelection(UUID wishlistId, UUID nodeId) {
        ensureLoaded();
        NodeLocation location = locateNode(wishlistId, nodeId).orElseThrow();
        String target = location.node.selectedCandidateItemId();
        if (target == null && !location.node.ingredient().candidates().isEmpty()) {
            target = location.node.ingredient().candidates().get(0);
        }
        pendingSelection = new SelectionRequest(wishlistId, location.node, location.ancestors, false, SelectionMode.EXPAND, target);
        return pendingSelection;
    }

    public SelectionRequest beginRootRecipeSelection(UUID wishlistId, UUID entryId) {
        ensureLoaded();
        Wishlist wishlist = workspace.find(wishlistId).orElseThrow();
        WishlistEntry entry = wishlist.entries().stream().filter(candidate -> candidate.id().equals(entryId))
                .findFirst().orElseThrow();
        if (!entry.itemOnly()) throw new IllegalStateException("root already has a recipe");
        RecipeNode node = new RecipeNode(IngredientChoice.exact(entry.outputItemId(), 1));
        pendingSelection = new SelectionRequest(wishlistId, node, List.of(), false,
                SelectionMode.EXPAND, entry.outputItemId(), entry);
        return pendingSelection;
    }

    public SelectionRequest beginRecipeSelection(UUID wishlistId, UUID nodeId, String targetItemId) {
        ensureLoaded();
        NodeLocation location = locateNode(wishlistId, nodeId).orElseThrow();
        pendingSelection = new SelectionRequest(wishlistId, location.node, location.ancestors, false, SelectionMode.EXPAND, targetItemId);
        return pendingSelection;
    }

    public SelectionRequest beginRecipeChange(UUID wishlistId, UUID nodeId) {
        ensureLoaded();
        NodeLocation location = locateNode(wishlistId, nodeId).orElseThrow();
        String target = location.node.selectedCandidateItemId();
        if (target == null && location.node.ingredient().candidates().size() == 1) {
            target = location.node.ingredient().candidates().get(0);
        }
        if (target == null && location.node.selectedRecipe() != null) {
            target = resolver.findByKey(location.node.selectedRecipe()).map(ResolvedRecipe::outputItemId).orElse(null);
        }
        if (target == null && !location.node.ingredient().candidates().isEmpty()) {
            target = location.node.ingredient().candidates().get(0);
        }
        pendingSelection = new SelectionRequest(wishlistId, location.node, location.ancestors, false, SelectionMode.CHANGE, target);
        return pendingSelection;
    }

    public Optional<SelectionRequest> pendingSelection() { return Optional.ofNullable(pendingSelection); }

    public Optional<SelectionRequest> acceptPendingSelection(ResolvedRecipe recipe) {
        ensureLoaded();
        SelectionRequest request = pendingSelection;
        if (request == null) return Optional.empty();
        if (request.automatic) {
            AutoContext context = autoQueue.peekFirst();
            if (context == null) { pendingSelection = null; return Optional.empty(); }
            context.session.selectRecipe(request.node, recipe);
            autoSelectionsHandled++;
            context.session.expandAvailable();
            touchAndSave(context.wishlistId);
            pendingSelection = null;
            return advanceAutoSelection();
        }
        if (request.mode == SelectionMode.CHANGE) {
            if (request.node.ingredient().candidates().stream()
                    .noneMatch(candidate -> net.ochibo.wishlist.core.model.ResourceIdentity.matches(candidate, recipe.outputItemId()))) {
                throw new IllegalArgumentException("recipe output does not satisfy this ingredient node");
            }
            request.node.selectCandidate(recipe.outputItemId());
            tree.changeRecipe(request.node, recipe.key());
        } else {
            TerminalReason result = tree.selectAndExpand(request.node, recipe, request.ancestors, resolver);
            if (request.rootEntry != null && result == TerminalReason.NONE) {
                request.rootEntry.selectRootRecipe(recipe);
            }
        }
        touchAndSave(request.wishlistId);
        pendingSelection = null;
        return Optional.empty();
    }

    public void cancelPendingSelection() {
        pendingSelection = null;
        if (!autoQueue.isEmpty()) {
            for (AutoContext context : autoQueue) context.session.cancel();
            autoQueue.clear();
        }
    }

    public Optional<SelectionRequest> skipPendingSelection() {
        SelectionRequest request = pendingSelection;
        if (request == null || !request.automatic()) return Optional.empty();
        AutoContext context = autoQueue.peekFirst();
        if (context == null) { pendingSelection = null; return Optional.empty(); }
        context.session.skipSelection(request.node());
        autoSelectionsHandled++;
        context.session.expandAvailable();
        touchAndSave(context.wishlistId);
        pendingSelection = null;
        return advanceAutoSelection();
    }

    public Optional<SelectionRequest> startExpandAll() {
        ensureLoaded();
        autoQueue.clear();
        autoSelectionsHandled = 0;
        for (Wishlist wishlist : workspace.selectedWishlists()) {
            ExpandAllSession session = new ExpandAllSession(wishlist, resolver, ingredients);
            session.expandAvailable();
            touchAndSave(wishlist.id());
            autoQueue.addLast(new AutoContext(wishlist.id(), session));
        }
        return advanceAutoSelection();
    }

    public void collapseAll() {
        ensureLoaded();
        for (Wishlist wishlist : workspace.selectedWishlists()) {
            ExpandAllSession session = new ExpandAllSession(wishlist, resolver, ingredients);
            session.collapseAll();
            touchAndSave(wishlist.id());
        }
        autoQueue.clear();
        pendingSelection = null;
    }

    private Optional<SelectionRequest> advanceAutoSelection() {
        while (!autoQueue.isEmpty()) {
            AutoContext context = autoQueue.peekFirst();
            List<ExpandAllSession.PendingSelection> pending = context.session.pendingSelections();
            if (!pending.isEmpty()) {
                ExpandAllSession.PendingSelection p = pending.get(0);
                String target = p.node().ingredient().candidates().isEmpty() ? null : p.node().ingredient().candidates().get(0);
                pendingSelection = new SelectionRequest(context.wishlistId, p.node(), p.ancestors(), true, SelectionMode.EXPAND, target);
                return Optional.of(pendingSelection);
            }
            autoQueue.removeFirst();
        }
        pendingSelection = null;
        return Optional.empty();
    }

    public Optional<AutoProgress> autoProgress() {
        if (pendingSelection == null || !pendingSelection.automatic()) return Optional.empty();
        int waiting = autoQueue.stream().mapToInt(context -> context.session.pendingSelections().size()).sum();
        return Optional.of(new AutoProgress(pendingSelection.itemId(), autoSelectionsHandled, waiting));
    }

    public record AutoProgress(String itemId, int handled, int waiting) {}

    public Optional<RecipeNode> findNode(UUID wishlistId, UUID nodeId) {
        return locateNode(wishlistId, nodeId).map(NodeLocation::node);
    }

    private record NodeLocation(RecipeNode node, List<RecipeKey> ancestors) {}

    private Optional<NodeLocation> locateNode(UUID wishlistId, UUID nodeId) {
        Wishlist wishlist = workspace.find(wishlistId).orElse(null);
        if (wishlist == null) return Optional.empty();
        for (WishlistEntry entry : wishlist.entries()) {
            List<RecipeKey> rootAncestors = List.of(entry.rootRecipe());
            for (RecipeNode node : entry.children()) {
                Optional<NodeLocation> found = locateRecursive(node, nodeId, rootAncestors);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private Optional<NodeLocation> locateRecursive(RecipeNode node, UUID id, List<RecipeKey> ancestors) {
        if (node.id().equals(id)) return Optional.of(new NodeLocation(node, ancestors));
        List<RecipeKey> next = tree.childAncestors(ancestors, node);
        for (RecipeNode child : node.children()) {
            Optional<NodeLocation> found = locateRecursive(child, id, next);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private void touchAndSave(UUID wishlistId) {
        Wishlist wishlist = workspace.find(wishlistId).orElseThrow();
        wishlist.touch();
        save(wishlist);
    }

    private void saveWorkspaceState() {
        if (stateRepository == null) return;
        Optional<Wishlist> active = workspace.active();
        if (active.isEmpty()) return;
        try {
            stateRepository.save(new WorkspaceState(active.orElseThrow().id(), workspace.combinedSelection(),
                    List.copyOf(completedMaterials)));
        } catch (IOException e) {
            WishlistToasts.saveFailed();
        }
    }

    private void save(Wishlist wishlist) {
        if (repository == null) return;
        try { repository.save(wishlist); }
        catch (IOException e) { WishlistToasts.saveFailed(); }
    }
}
