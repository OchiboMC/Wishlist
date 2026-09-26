package net.ochibo.wishlist.client;

import net.ochibo.wishlist.client.ingredients.MinecraftIngredientsLoader;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.material.MaterialCalculation;
import net.ochibo.wishlist.core.material.MaterialSummaryRow;
import net.ochibo.wishlist.core.material.MaterialCalculator;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.persistence.ClientWorldKey;
import net.ochibo.wishlist.core.persistence.WishlistRepository;
import net.ochibo.wishlist.core.persistence.WishlistStorageLocator;
import net.ochibo.wishlist.core.persistence.WorkspaceState;
import net.ochibo.wishlist.core.persistence.WorkspaceStateRepository;
import net.ochibo.wishlist.core.recipe.CompositeRecipeResolver;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.tree.ExpandAllSession;
import net.ochibo.wishlist.core.tree.RecipeNode;
import net.ochibo.wishlist.core.tree.RecipeTreeService;
import net.ochibo.wishlist.core.tree.TerminalReason;
import net.ochibo.wishlist.core.workspace.WishlistWorkspace;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.loading.FMLPaths;

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
                                   SelectionMode mode, String targetItemId) {
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
    private final Set<String> completedMaterials = new LinkedHashSet<>();

    private record AutoContext(UUID wishlistId, ExpandAllSession session) {}

    private ClientWorkspaceController() {}

    public synchronized void ensureLoaded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientWorldKey key = ClientContextResolver.current(mc);
        if (key.equals(loadedKey) && resolver != null) return;

        loadedKey = key;
        var listDirectory = WishlistStorageLocator.listDirectory(FMLPaths.CONFIGDIR.get(), key);
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
            stateRepository.load().ifPresent(state -> {
                if (workspace.find(state.activeId()).isPresent()) workspace.setActive(state.activeId());
                workspace.setCombinedSelection(state.combinedSelection());
                completedMaterials.addAll(state.completedMaterials());
            });
        } catch (IOException e) {
            errors.add(new WishlistRepository.LoadError(listDirectory.getParent().resolve("workspace.wstate"), e.toString()));
        }
        loadErrors = List.copyOf(errors);
        saveWorkspaceState();
        rebuildResolvers(mc);
        ResourceManager raw = mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().getResourceManager();
        ingredients = ingredientsLoader.load(raw);
        pendingSelection = null;
        autoQueue.clear();
    }

    public synchronized void invalidate() {
        loadedKey = null;
        resolver = null;
        nativeResolver = null;
        repository = null;
        stateRepository = null;
        pendingSelection = null;
        autoQueue.clear();
        completedMaterials.clear();
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
        nativeResolver = new MinecraftRecipeResolver(mc.level.getRecipeManager(), mc.level.registryAccess());
        resolver = new CompositeRecipeResolver(
                new CompositeRecipeResolver(nativeResolver, RecipeViewerRouter::jeiRecipeResolver),
                RecipeViewerRouter::emiRecipeResolver);
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

    public WishlistEntry addTo(UUID wishlistId, ResolvedRecipe recipe, long count) {
        ensureLoaded();
        if (!recipe.supported()) throw new IllegalArgumentException("Unsupported recipe: " + recipe.unsupportedReason());
        WishlistEntry entry = workspace.addTo(wishlistId, recipe, count);
        save(workspace.find(wishlistId).orElseThrow());
        return entry;
    }

    public WishlistEntry quickAdd(ResolvedRecipe recipe, long count) {
        ensureLoaded();
        Wishlist target = workspace.active().orElseGet(() -> createWishlist(defaultWishlistName()));
        return addTo(target.id(), recipe, count);
    }

    public void setRequestedCount(UUID wishlistId, UUID entryId, long count) {
        ensureLoaded();
        workspace.setRequestedCount(wishlistId, entryId, count);
        save(workspace.find(wishlistId).orElseThrow());
    }

    public void removeEntry(UUID wishlistId, UUID entryId) {
        ensureLoaded();
        if (workspace.removeEntry(wishlistId, entryId)) {
            save(workspace.find(wishlistId).orElseThrow());
        }
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
        return new MaterialCalculator(resolver).calculate(workspace.selectedWishlists(), inventory);
    }

    public boolean isMaterialCompleted(MaterialSummaryRow row) {
        ensureLoaded();
        return completedMaterials.contains(row.identityKey());
    }

    public Set<String> completedMaterials() {
        ensureLoaded();
        return Set.copyOf(completedMaterials);
    }

    public void toggleMaterialCompleted(MaterialSummaryRow row) {
        ensureLoaded();
        String key = row.identityKey();
        if (!completedMaterials.remove(key)) completedMaterials.add(key);
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
            tree.selectAndExpand(request.node, recipe, request.ancestors);
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
        context.session.expandAvailable();
        touchAndSave(context.wishlistId);
        pendingSelection = null;
        return advanceAutoSelection();
    }

    public Optional<SelectionRequest> startExpandAll() {
        ensureLoaded();
        autoQueue.clear();
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
            throw new IllegalStateException("Failed to save Wishlist workspace state", e);
        }
    }

    private void save(Wishlist wishlist) {
        if (repository == null) return;
        try { repository.save(wishlist); }
        catch (IOException e) { throw new IllegalStateException("Failed to save wishlist " + wishlist.id(), e); }
    }
}
