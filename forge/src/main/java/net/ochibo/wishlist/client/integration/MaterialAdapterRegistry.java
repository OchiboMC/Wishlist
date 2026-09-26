package net.ochibo.wishlist.client.integration;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client extension API for material kinds beyond built-in Item and Fluid support. */
public final class MaterialAdapterRegistry {
    public record MaterialStack(String key, long amount) {
        public MaterialStack {
            ResourceIdentity.parse(key);
            if (amount <= 0) throw new IllegalArgumentException("material amount must be positive");
        }
    }

    public interface Adapter {
        /** A unique kind used with {@link ResourceIdentity#of(String, String, String)}. */
        String kind();

        /** Receives a JEI ITypedIngredient or an EMI EmiStack. Unknown objects return empty. */
        Optional<MaterialStack> readViewerIngredient(Object ingredient);

        /** Add owned quantities in the same unit returned by readViewerIngredient. */
        default void addInventory(LocalPlayer player, Map<String, Long> counts) {}

        /** A representative icon for Wishlist's material rows. */
        default ItemStack icon(ResourceIdentity identity) { return ItemStack.EMPTY; }

        default Component name(ResourceIdentity identity) { return Component.literal(identity.registryId()); }

        /** Unit suffix for quantities, such as FE. */
        default String unit() { return ""; }
    }

    private static final CopyOnWriteArrayList<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private MaterialAdapterRegistry() {}

    public static void register(Adapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        String kind = adapter.kind();
        new ResourceIdentity(kind, "wishlist:validation", null);
        if (kind.equals("item") || kind.equals("fluid") || ADAPTERS.stream().anyMatch(a -> a.kind().equals(kind)))
            throw new IllegalArgumentException("material kind already registered: " + kind);
        ADAPTERS.add(adapter);
    }

    public static Optional<MaterialStack> read(Object ingredient) {
        if (ingredient == null) return Optional.empty();
        for (Adapter adapter : ADAPTERS) {
            Optional<MaterialStack> value = adapter.readViewerIngredient(ingredient);
            if (value.isPresent()) {
                if (!ResourceIdentity.parse(value.orElseThrow().key()).kind().equals(adapter.kind()))
                    throw new IllegalArgumentException("adapter returned a different material kind");
                return value;
            }
        }
        return Optional.empty();
    }

    public static void addInventory(LocalPlayer player, Map<String, Long> counts) {
        for (Adapter adapter : ADAPTERS) adapter.addInventory(player, counts);
    }

    public static ItemStack icon(ResourceIdentity identity) {
        for (Adapter adapter : ADAPTERS) if (adapter.kind().equals(identity.kind())) return adapter.icon(identity);
        return ItemStack.EMPTY;
    }

    public static Component name(ResourceIdentity identity) {
        for (Adapter adapter : ADAPTERS) if (adapter.kind().equals(identity.kind())) return adapter.name(identity);
        return Component.literal(identity.registryId());
    }

    public static String unit(ResourceIdentity identity) {
        for (Adapter adapter : ADAPTERS) if (adapter.kind().equals(identity.kind())) return adapter.unit();
        return "";
    }
}
