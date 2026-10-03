package net.ochibo.wishlist.client.integration.emi;

/** Mirrors EMI's visible right-side button conditions for its supported version. */
final class EmiNativeButtonCount {
    private EmiNativeButtonCount() {}

    static int count(boolean fillEnabled, boolean fillSupported, boolean supportsTree,
                     boolean treeEnabled, boolean defaultEnabled) {
        int count = fillEnabled && fillSupported ? 1 : 0;
        if (supportsTree && treeEnabled) count++;
        if (supportsTree && defaultEnabled) count++;
        return count;
    }
}
