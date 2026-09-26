package net.ochibo.wishlist.client.integration;

public final class OverlayActionResolver {
    private OverlayActionResolver() {}

    public static OverlayClickAction resolve(int mouseButton, boolean shiftDown) {
        return resolve(mouseButton, shiftDown, Integer.MAX_VALUE);
    }

    public static OverlayClickAction resolve(int mouseButton, boolean shiftDown, int wishlistCount) {
        if (mouseButton == 0) {
            if (shiftDown || wishlistCount == 1) return OverlayClickAction.QUICK_ADD;
            return OverlayClickAction.SELECT_WISHLIST;
        }
        if (mouseButton == 1) return OverlayClickAction.DETAILS;
        return OverlayClickAction.NONE;
    }
}
