package net.ochibo.wishlist.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Wishlist {
    private final UUID id;
    private String name;
    private final long createdAtEpochMillis;
    private long updatedAtEpochMillis;
    private long revision;
    private final List<WishlistEntry> entries = new ArrayList<>();

    private Wishlist(UUID id, String name, long createdAt, long updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        setNameInternal(name);
        this.createdAtEpochMillis = createdAt;
        this.updatedAtEpochMillis = updatedAt;
    }

    public static Wishlist create(String name) {
        long now = Instant.now().toEpochMilli();
        return new Wishlist(UUID.randomUUID(), name, now, now);
    }

    public static Wishlist restore(UUID id, String name, long createdAt, long updatedAt, List<WishlistEntry> entries) {
        Wishlist wishlist = new Wishlist(id, name, createdAt, updatedAt);
        wishlist.entries.addAll(entries);
        return wishlist;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public long createdAtEpochMillis() { return createdAtEpochMillis; }
    public long updatedAtEpochMillis() { return updatedAtEpochMillis; }
    /** In-memory change counter; timestamps cannot distinguish edits within one millisecond. */
    public long revision() { return revision; }
    public List<WishlistEntry> entries() { return Collections.unmodifiableList(entries); }

    public WishlistEntry addEntry(WishlistEntry incoming) {
        for (WishlistEntry existing : entries) {
            if (existing.outputItemId().equals(incoming.outputItemId()) && existing.rootRecipe().equals(incoming.rootRecipe())) {
                existing.addRequestedCount(incoming.requestedCount());
                touch();
                return existing;
            }
        }
        entries.add(incoming);
        touch();
        return incoming;
    }

    public void rename(String newName) { setNameInternal(newName); touch(); }
    public boolean removeEntry(UUID entryId) { boolean r = entries.removeIf(e -> e.id().equals(entryId)); if (r) touch(); return r; }

    public boolean markEntriesCompleted(Set<UUID> ids) {
        boolean changed = false;
        for (WishlistEntry entry : entries) {
            if (ids.contains(entry.id()) && !entry.completed()) {
                entry.setCompleted(true);
                changed = true;
            }
        }
        if (changed) touch();
        return changed;
    }

    public boolean removeEntries(Set<UUID> ids) {
        boolean changed = entries.removeIf(entry -> ids.contains(entry.id()));
        if (changed) touch();
        return changed;
    }
    public void touch() { revision++; updatedAtEpochMillis = Instant.now().toEpochMilli(); }

    private void setNameInternal(String value) {
        Objects.requireNonNull(value, "name");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("name must not be blank");
        this.name = trimmed;
    }
}
