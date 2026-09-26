package net.ochibo.wishlist.core.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceState(UUID activeId, List<UUID> combinedSelection, List<String> completedMaterials) {
    public WorkspaceState(UUID activeId, List<UUID> combinedSelection) {
        this(activeId, combinedSelection, List.of());
    }

    public WorkspaceState {
        Objects.requireNonNull(activeId, "activeId");
        Objects.requireNonNull(combinedSelection, "combinedSelection");
        combinedSelection = List.copyOf(combinedSelection);
        completedMaterials = List.copyOf(Objects.requireNonNull(completedMaterials, "completedMaterials"));
    }
}
