package net.ochibo.wishlist.client.ui;

/** The user-selected width ratios for paired Wishlist buttons. */
public final class WishlistButtonWidths {
    private static final int GAP = 4;

    private WishlistButtonWidths() {}

    /** Collapse : Change Recipe = 2 : 3. */
    public static Pair collapseAndChange(int totalWidth) {
        return split(totalWidth, 2, 5);
    }

    /** Missing Only : HUD = 2 : 1. */
    public static Pair missingAndHud(int totalWidth) {
        return split(totalWidth, 2, 3);
    }

    private static Pair split(int totalWidth, int firstPart, int totalParts) {
        int contentWidth = Math.max(0, totalWidth - GAP);
        int first = (contentWidth * firstPart + totalParts / 2) / totalParts;
        return new Pair(first, contentWidth - first);
    }

    public record Pair(int first, int second) {}
}
