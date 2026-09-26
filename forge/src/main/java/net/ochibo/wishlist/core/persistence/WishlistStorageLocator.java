package net.ochibo.wishlist.core.persistence;

import java.nio.file.Path;
import java.util.Objects;

public final class WishlistStorageLocator {
    private WishlistStorageLocator() {}

    public static Path listDirectory(Path configDirectory, ClientWorldKey key) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        Objects.requireNonNull(key, "key");
        String kind = key.kind() == ClientWorldKey.Kind.LOCAL ? "local" : "server";
        return configDirectory.resolve("wishlist").resolve("world").resolve(kind).resolve(key.directoryName()).resolve("lists");
    }
}
