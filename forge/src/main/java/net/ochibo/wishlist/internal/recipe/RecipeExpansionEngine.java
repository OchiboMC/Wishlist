package net.ochibo.wishlist.internal.recipe;

import com.mojang.logging.LogUtils;
import net.ochibo.wishlist.api.recipe.ExpansionProblem;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Client-thread semantic dispatch. Invalidate after recipe, world, tag, or viewer changes. */
public final class RecipeExpansionEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HandlerTable handlers;
    private final Map<Class<?>, List<List<HandlerTable.Entry<?>>>> dispatch = new HashMap<>();
    private final Map<CacheKey, RecipeExpansionDecision> results = new HashMap<>();
    private long generation;

    public RecipeExpansionEngine(HandlerTable handlers) { this.handlers = Objects.requireNonNull(handlers, "handlers"); }

    public RecipeExpansionDecision expand(RecipeExpansionRequest<?> request) {
        Objects.requireNonNull(request, "request");
        CacheKey key = key(request);
        RecipeExpansionDecision cached = results.get(key);
        if (cached != null) return cached;
        RecipeExpansionDecision decision = dispatch(request);
        // Fallback is caller-specific and may change independently of recipe identity.
        if (decision.kind() != RecipeExpansionDecision.Kind.DECLINE) results.put(key, decision);
        return decision;
    }

    public void invalidate() { results.clear(); dispatch.clear(); generation++; }
    public long generation() { return generation; }

    private RecipeExpansionDecision dispatch(RecipeExpansionRequest<?> request) {
        for (List<HandlerTable.Entry<?>> tier : dispatch.computeIfAbsent(request.recipe().getClass(), this::tiers)) {
            RecipeExpansionDecision selected = RecipeExpansionDecision.decline();
            List<ResourceLocation> accepted = new ArrayList<>();
            for (HandlerTable.Entry<?> entry : tier) {
                RecipeExpansionDecision decision;
                try {
                    decision = Objects.requireNonNull(entry.expand(request), "Handler returned null");
                    if (decision.kind() == RecipeExpansionDecision.Kind.RESOLVED
                            && !matchesRequestedTarget(request.targetOutput(), decision.plan().orElseThrow().targetOutput())) {
                        throw new IllegalArgumentException("Handler resolved a different target item or NBT");
                    }
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.error("Recipe expansion handler {} failed for recipe {}", entry.id(), request.recipeId(), failure);
                    decision = blocked("provider_error", "Handler " + entry.id() + " failed for recipe " + request.recipeId());
                }
                if (decision.kind() != RecipeExpansionDecision.Kind.DECLINE) {
                    accepted.add(entry.id());
                    selected = decision;
                }
            }
            if (accepted.size() > 1) {
                LOGGER.warn("Recipe expansion handler conflict for recipe {}: {}", request.recipeId(), accepted);
                return blocked("handler_conflict", "Conflicting handlers for " + request.recipeId() + ": " + accepted);
            }
            if (!accepted.isEmpty()) return selected;
        }
        return RecipeExpansionDecision.decline();
    }

    private List<List<HandlerTable.Entry<?>>> tiers(Class<?> concreteClass) {
        Map<Class<?>, Integer> distances = new HashMap<>();
        ArrayDeque<Class<?>> pending = new ArrayDeque<>();
        distances.put(concreteClass, 0);
        pending.add(concreteClass);
        while (!pending.isEmpty()) {
            Class<?> current = pending.remove();
            int distance = distances.get(current) + 1;
            Class<?> parent = current.getSuperclass();
            if (parent != null && distances.putIfAbsent(parent, distance) == null) pending.add(parent);
            for (Class<?> contract : current.getInterfaces()) {
                if (distances.putIfAbsent(contract, distance) == null) pending.add(contract);
            }
        }
        Map<Integer, List<HandlerTable.Entry<?>>> tiers = new TreeMap<>();
        for (HandlerTable.Entry<?> entry : handlers.entries()) {
            Integer distance = distances.get(entry.recipeClass());
            if (distance != null) tiers.computeIfAbsent(distance, ignored -> new ArrayList<>()).add(entry);
        }
        return tiers.values().stream().map(List::copyOf).toList();
    }

    private CacheKey key(RecipeExpansionRequest<?> request) {
        ItemStack target = request.targetOutput();
        CompoundTag tag = target.getTag();
        return new CacheKey(request.recipeId(), BuiltInRegistries.ITEM.getKey(target.getItem()),
                tag == null ? null : tag.copy(), target.getCount(), generation);
    }


    private static boolean matchesRequestedTarget(ItemStack requested, ItemStack actual) {
        if (requested.isEmpty() || actual.isEmpty() || requested.getItem() != actual.getItem()) return false;
        return !requested.hasTag() || ItemStack.isSameItemSameTags(requested, actual);
    }

    private static RecipeExpansionDecision blocked(String code, String message) {
        return RecipeExpansionDecision.blocked(ExpansionProblem.of(ResourceLocation.fromNamespaceAndPath("wishlist", code), message));
    }

    private record CacheKey(ResourceLocation recipe, ResourceLocation target, CompoundTag tag, int count, long generation) { }
}
