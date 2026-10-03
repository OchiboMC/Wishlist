package net.ochibo.wishlist.core.persistence;

import java.io.IOException;

public final class UnsupportedWishlistVersionException extends IOException {
    private static final long serialVersionUID = 1L;
    private final int version;
    public UnsupportedWishlistVersionException(int version) {
        super("Unsupported WLST version: " + version);
        this.version = version;
    }
    public int version() { return version; }
}
