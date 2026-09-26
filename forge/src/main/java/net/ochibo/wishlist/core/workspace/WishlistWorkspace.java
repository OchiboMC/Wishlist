package net.ochibo.wishlist.core.workspace;

import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WishlistWorkspace {
    private final List<Wishlist> wishlists = new ArrayList<>();
    private final LinkedHashSet<UUID> combinedSelection = new LinkedHashSet<>();
    private UUID activeId;

    public WishlistWorkspace(Collection<Wishlist> initial) {
        Objects.requireNonNull(initial, "initial");
        wishlists.addAll(initial);
        if (!wishlists.isEmpty()) activeId = wishlists.get(0).id();
    }

    public List<Wishlist> wishlists() { return List.copyOf(wishlists); }

    public Wishlist create(String name) {
        Wishlist wishlist = Wishlist.create(name);
        wishlists.add(wishlist);
        activeId = wishlist.id();
        return wishlist;
    }

    public Optional<Wishlist> find(UUID id) {
        return wishlists.stream().filter(w -> w.id().equals(id)).findFirst();
    }

    public Optional<Wishlist> active() { return activeId == null ? Optional.empty() : find(activeId); }

    public void setActive(UUID id) {
        if (find(id).isEmpty()) throw new IllegalArgumentException("unknown wishlist: " + id);
        activeId = id;
    }

    public boolean remove(UUID id) {
        boolean removed = wishlists.removeIf(w -> w.id().equals(id));
        combinedSelection.remove(id);
        if (removed && Objects.equals(activeId, id)) {
            activeId = wishlists.isEmpty() ? null : wishlists.get(Math.max(0, wishlists.size() - 1)).id();
        }
        return removed;
    }

    public WishlistEntry addTo(UUID wishlistId, ResolvedRecipe recipe, long desiredCount) {
        Wishlist wishlist = find(wishlistId).orElseThrow(() -> new IllegalArgumentException("unknown wishlist: " + wishlistId));
        return wishlist.addEntry(WishlistEntry.fromRecipe(recipe, desiredCount));
    }


    public void setRequestedCount(UUID wishlistId, UUID entryId, long value) {
        Wishlist wishlist = find(wishlistId).orElseThrow(() -> new IllegalArgumentException("unknown wishlist: " + wishlistId));
        WishlistEntry entry = wishlist.entries().stream()
                .filter(e -> e.id().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown entry: " + entryId));
        entry.setRequestedCount(value);
        wishlist.touch();
    }

    public boolean removeEntry(UUID wishlistId, UUID entryId) {
        Wishlist wishlist = find(wishlistId).orElseThrow(() -> new IllegalArgumentException("unknown wishlist: " + wishlistId));
        return wishlist.removeEntry(entryId);
    }

    public void setCombinedSelection(Collection<UUID> ids) {
        combinedSelection.clear();
        Set<UUID> known = wishlists.stream().map(Wishlist::id).collect(java.util.stream.Collectors.toSet());
        for (UUID id : ids) if (known.contains(id)) combinedSelection.add(id);
    }

    public List<UUID> combinedSelection() { return List.copyOf(combinedSelection); }

    public List<Wishlist> selectedWishlists() {
        if (combinedSelection.isEmpty()) return active().map(List::of).orElseGet(List::of);
        return combinedSelection.stream().map(this::find).flatMap(Optional::stream).toList();
    }
}
