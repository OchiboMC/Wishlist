package net.ochibo.wishlist.client.integration;

public final class SelectionClosePolicy {
    private SelectionClosePolicy() {}

    public static boolean shouldCancel(boolean recipeViewerScreen, boolean pendingSelection, boolean transitionInProgress) {
        return recipeViewerScreen && pendingSelection && !transitionInProgress;
    }
}
