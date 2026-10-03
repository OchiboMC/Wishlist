package net.ochibo.wishlist.client.integration;

public final class OverlayGeometry {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private OverlayGeometry() {}

    public static Rect topRight(int x, int y, int width, int height, int buttonSize, int inset) {
        int safeSize = Math.max(1, buttonSize);
        int safeInset = Math.max(0, inset);
        return new Rect(x + Math.max(0, width - safeSize - safeInset), y + safeInset, safeSize, safeSize);
    }
}
