package net.ochibo.wishlist.client.integration.emi;

final class EmiWishlistButtonPosition {
    record Position(int x, int y) {}

    private EmiWishlistButtonPosition() {}

    static Position place(int width, int height, int rightButtons) {
        int rows = Math.max(1, (height + 10) / 14);
        int space = Math.min(8, height + 8 - (Math.min(rows, rightButtons) * 14 - 2));
        int bottom = height + 4 - 12 - space / 2;
        return new Position(width + 5 + (rightButtons / rows) * 14,
                bottom - (rightButtons % rows) * 14);
    }
}
