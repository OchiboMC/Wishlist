package net.ochibo.wishlist.client.integration.jei;

import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static net.ochibo.wishlist.core.utils.StackResolver.stack;

/**
 * Generic JEI-backed recipe resolver.
 *
 * <p>It intentionally knows nothing about mod-specific recipe classes. Every
 * recipe is normalized from the public JEI recipe layout API.</p>
 */
final class JeiGenericRecipeResolver implements RecipeResolver {
    private record CacheKey(RecipeKey recipeKey, String targetItemId) {}
    private record RecipeRef<T>(IRecipeCategory<T> category, T recipe) {}

    private final IRecipeManager recipeManager;
    private final IJeiHelpers helpers;
    private final IIngredientManager ingredientManager;
    private final IFocusGroup emptyFocusGroup;
    private final IRecipeTransferManager transferManager;
    private final Map<RecipeKey, RecipeRef<?>> recipeIndex = new LinkedHashMap<>();
    private final Map<CacheKey, ResolvedRecipe> resolvedCache = new LinkedHashMap<>();
    private boolean fullIndexBuilt;

    JeiGenericRecipeResolver(IJeiRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        this.recipeManager = runtime.getRecipeManager();
        this.helpers = runtime.getJeiHelpers();
        this.ingredientManager = runtime.getIngredientManager();
        this.emptyFocusGroup = helpers.getFocusFactory().getEmptyFocusGroup();
        this.transferManager = runtime.getRecipeTransferManager();
    }

    synchronized boolean canTransfer(RecipeKey key, AbstractContainerScreen<?> screen) {
        RecipeRef<?> ref = transferRef(key);
        return ref != null && hasHandler(ref, screen.getMenu());
    }

    synchronized boolean hasIngredients(RecipeKey key, AbstractContainerScreen<?> screen) {
        RecipeRef<?> ref = transferRef(key);
        return ref != null && canTransferCaptured(ref, screen.getMenu());
    }

    synchronized boolean transfer(RecipeKey key, AbstractContainerScreen<?> screen) {
        RecipeRef<?> ref = transferRef(key);
        return ref != null && transferCaptured(ref, screen.getMenu());
    }

    private RecipeRef<?> transferRef(RecipeKey key) {
        RecipeRef<?> ref = recipeIndex.get(key);
        if (ref == null && !fullIndexBuilt) {
            buildFullIndex();
            ref = recipeIndex.get(key);
        }
        return ref;
    }

    private <T> boolean hasHandler(RecipeRef<T> ref, AbstractContainerMenu menu) {
        return transferManager.getRecipeTransferHandler(menu, ref.category()).isPresent();
    }

    private <T> boolean canTransferCaptured(RecipeRef<T> ref, AbstractContainerMenu menu) {
        var handler = transferManager.getRecipeTransferHandler(menu, ref.category());
        if (handler.isEmpty() || Minecraft.getInstance().player == null) return false;
        var layout = recipeManager.createRecipeLayoutDrawable(ref.category(), ref.recipe(), emptyFocusGroup);
        if (layout.isEmpty()) return false;
        var error = handler.orElseThrow().transferRecipe(menu, ref.recipe(),
                layout.orElseThrow().getRecipeSlotsView(), Minecraft.getInstance().player, false, false);
        return error == null || error.getType().allowsTransfer;
    }

    private <T> boolean transferCaptured(RecipeRef<T> ref, AbstractContainerMenu menu) {
        var handler = transferManager.getRecipeTransferHandler(menu, ref.category());
        if (handler.isEmpty() || Minecraft.getInstance().player == null) return false;
        var layout = recipeManager.createRecipeLayoutDrawable(ref.category(), ref.recipe(), emptyFocusGroup);
        if (layout.isEmpty()) return false;
        var error = handler.orElseThrow().transferRecipe(menu, ref.recipe(),
                layout.orElseThrow().getRecipeSlotsView(), Minecraft.getInstance().player, false, true);
        return error == null || error.getType().allowsTransfer;
    }

    @Override
    public synchronized Optional<ResolvedRecipe> findByKey(RecipeKey key) {
        return findByKey(key, "");
    }

    @Override
    public synchronized Optional<ResolvedRecipe> findByKey(RecipeKey key, String targetItemId) {
        String target = normalizeTarget(targetItemId);
        CacheKey cacheKey = new CacheKey(key, target);
        ResolvedRecipe cached = resolvedCache.get(cacheKey);
        if (cached != null) return Optional.of(cached);

        RecipeRef<?> ref = recipeIndex.get(key);
        if (ref == null && !target.isBlank()) {
            findForOutput(target);
            ResolvedRecipe focused = resolvedCache.get(cacheKey);
            if (focused != null) return Optional.of(focused);
            ref = recipeIndex.get(key);
        }
        if (ref == null && !fullIndexBuilt) {
            buildFullIndex();
            ref = recipeIndex.get(key);
        }
        if (ref == null) return Optional.empty();
        return resolveRef(ref, target);
    }

    @Override
    public synchronized List<ResolvedRecipe> findForOutput(String itemId) {
        ItemStack outputStack = stack(itemId);
        if (outputStack.isEmpty()) return List.of();

        IFocus<ItemStack> focus = helpers.getFocusFactory().createFocus(
                mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK,
                outputStack);

        Map<RecipeKey, ResolvedRecipe> found = new LinkedHashMap<>();
        recipeManager.createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .get()
                .forEach(category -> collectForOutput(category, focus, itemId, found));
        return List.copyOf(found.values());
    }

    private <T> void collectForOutput(
            IRecipeCategory<T> category,
            IFocus<?> focus,
            String targetItemId,
            Map<RecipeKey, ResolvedRecipe> found) {
        recipeManager.createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .get()
                .forEach(recipe -> {
                    indexRecipe(category, recipe);
                    recipeManager.createRecipeLayoutDrawable(category, recipe, emptyFocusGroup)
                            .flatMap(layout -> resolveLayout(layout, targetItemId))
                            .ifPresent(resolved -> {
                                ResolvedRecipe existing = found.get(resolved.key());
                                if (existing == null || (!existing.supported() && resolved.supported())) {
                                    found.put(resolved.key(), resolved);
                                }
                            });
                });
    }

    synchronized <T> Optional<ResolvedRecipe> resolveLayout(IRecipeLayoutDrawable<T> layout, String targetItemId) {
        try {
            Optional<JeiRecipeSnapshot> snapshot = JeiRecipeLayoutReader.read(
                    layout, ingredientManager, emptyFocusGroup);
            if (snapshot.isEmpty()) return Optional.empty();
            String target = normalizeTarget(targetItemId);
            JeiRecipeSnapshot value = snapshot.orElseThrow();
            CacheKey cacheKey = new CacheKey(value.key(), target);
            ResolvedRecipe resolved = resolveSnapshot(value, target);
            resolvedCache.put(cacheKey, resolved);
            return Optional.of(resolved);
        } catch (RuntimeException | LinkageError ignored) {
            // One broken third-party JEI category must not break the Wishlist resolver.
            return Optional.empty();
        }
    }

    private void buildFullIndex() {
        recipeManager.createRecipeCategoryLookup()
                .get()
                .forEach(this::indexCategory);
        fullIndexBuilt = true;
    }

    private <T> void indexCategory(IRecipeCategory<T> category) {
        recipeManager.createRecipeLookup(category.getRecipeType())
                .get()
                .forEach(recipe -> indexRecipe(category, recipe));
    }

    private <T> void indexRecipe(IRecipeCategory<T> category, T recipe) {
        ResourceLocation registryName = category.getRegistryName(recipe);
        if (registryName == null) return;
        recipeIndex.putIfAbsent(new RecipeKey(registryName.toString()), new RecipeRef<>(category, recipe));
    }

    private Optional<ResolvedRecipe> resolveRef(RecipeRef<?> ref, String targetItemId) {
        return resolveCaptured(ref, targetItemId);
    }

    private <T> Optional<ResolvedRecipe> resolveCaptured(RecipeRef<T> ref, String targetItemId) {
        ResourceLocation registryName = ref.category().getRegistryName(ref.recipe());
        if (registryName == null) return Optional.empty();
        RecipeKey key = new RecipeKey(registryName.toString());
        CacheKey cacheKey = new CacheKey(key, targetItemId);
        ResolvedRecipe cached = resolvedCache.get(cacheKey);
        if (cached != null) return Optional.of(cached);

        Optional<ResolvedRecipe> resolved = recipeManager
                .createRecipeLayoutDrawable(ref.category(), ref.recipe(), emptyFocusGroup)
                .flatMap(layout -> resolveLayout(layout, targetItemId));
        resolved.ifPresent(value -> resolvedCache.put(cacheKey, value));
        return resolved;
    }

    private ResolvedRecipe resolveSnapshot(JeiRecipeSnapshot snapshot, String targetItemId) {
        var nativeResult = net.ochibo.wishlist.client.ClientWorkspaceController.get()
                .resolveNativeRecipe(snapshot.key(), targetItemId);
        if (nativeResult.filter(value -> !value.fallbackAllowed()).isPresent()) return nativeResult.orElseThrow();
        return JeiGenericRecipeInterpreter.resolve(snapshot, targetItemId);
    }

    private static String normalizeTarget(String targetItemId) {
        return targetItemId == null ? "" : targetItemId;
    }
}
