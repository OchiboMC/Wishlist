package net.ochibo.wishlist.client;

public final class HudOverlayStyle {
    public static final int MIN_SCALE_PERCENT = 1;
    public static final int MAX_SCALE_PERCENT = 200;
    public static final int DEFAULT_SCALE_PERCENT = 100;
    public static final int DEFAULT_BACKGROUND_OPACITY_PERCENT = 50;
    public static final int TEXT_ALPHA = 0xFF;
    public static final int OMISSION_COLOR = 0xFFBBBBBB;

    private HudOverlayStyle() {}

    public static int clampScalePercent(int percent) {
        return Math.max(MIN_SCALE_PERCENT, Math.min(MAX_SCALE_PERCENT, percent));
    }

    public static float scaleFromPercent(int percent) {
        return clampScalePercent(percent) / 100.0F;
    }

    public static int textColor(int rgb) {
        return (TEXT_ALPHA << 24) | (rgb & 0x00FFFFFF);
    }

    public static int backgroundColor(int opacityPercent) {
        int percent = Math.max(0, Math.min(100, opacityPercent));
        return (int) Math.round(percent * 255.0 / 100.0) << 24;
    }
}
