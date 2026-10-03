package net.ochibo.wishlist.core.layout;

/** Screen-relative HUD placement that keeps a scaled panel within the viewport. */
public final class HudPlacement {
    public static final int MAX_BASIS = 10000;
    public static final int MARGIN = 4;
    public static final int PADDING = 4;

    public record Bounds(int left, int top, int width, int height, int contentX, int contentY) {
        public int right() { return left + width; }
        public int bottom() { return top + height; }
        public boolean contains(double x, double y) {
            return x >= left && x < right() && y >= top && y < bottom();
        }
    }

    private HudPlacement() {}

    /** Reserve title, omission text and padding; offscreen icons must never be rendered in all mode. */
    public static int rowLimit(int screenHeight, int lineHeight, float scale, int requested) {
        int contentHeight = (int) Math.floor(Math.max(0, screenHeight - 2 * MARGIN) / scale) - 2 * PADDING;
        int capacity = Math.max(0, (contentHeight - 20 - lineHeight) / 18);
        return Math.min(Math.max(0, requested), capacity);
    }

    public static int clampBasis(int value) {
        return Math.max(0, Math.min(MAX_BASIS, value));
    }

    public static Bounds bounds(int screenWidth, int screenHeight, int contentWidth, int contentHeight,
                                float scale, int xBasis, int yBasis) {
        int panelWidth = (int) Math.ceil(Math.max(0, contentWidth + 2 * PADDING) * scale);
        int panelHeight = (int) Math.ceil(Math.max(0, contentHeight + 2 * PADDING) * scale);
        int left = coordinate(screenWidth, panelWidth, xBasis);
        int top = coordinate(screenHeight, panelHeight, yBasis);
        int inset = Math.round(PADDING * scale);
        return new Bounds(left, top, panelWidth, panelHeight, left + inset, top + inset);
    }

    public static int horizontalBasis(int screenWidth, int panelWidth, int left) {
        return basis(screenWidth, panelWidth, left);
    }

    public static int verticalBasis(int screenHeight, int panelHeight, int top) {
        return basis(screenHeight, panelHeight, top);
    }

    private static int coordinate(int screen, int panel, int basis) {
        if (panel >= screen) return 0;
        int travel = Math.max(0, screen - 2 * MARGIN - panel);
        return Math.min(screen - panel, MARGIN + (int) Math.round(travel * clampBasis(basis) / (double) MAX_BASIS));
    }

    private static int basis(int screen, int panel, int coordinate) {
        int travel = screen - 2 * MARGIN - panel;
        if (travel <= 0) return 0;
        return clampBasis((int) Math.round((coordinate - MARGIN) * (double) MAX_BASIS / travel));
    }
}
