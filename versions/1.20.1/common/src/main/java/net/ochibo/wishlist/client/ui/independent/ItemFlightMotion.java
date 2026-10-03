package net.ochibo.wishlist.client.ui.independent;

/** Elapsed-time motion, independent of Minecraft tick rate and frame count. */
final class ItemFlightMotion {
    static final long DURATION_MS = 550;
    private static final long TARGET_WAIT_MS = 1000;
    private final double sourceX, sourceY;
    private final int sourceWidth, sourceHeight;
    private final long createdAt;
    private boolean started;
    private long startedAt;

    ItemFlightMotion(double sourceX, double sourceY, int sourceWidth, int sourceHeight, long now) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceWidth = Math.max(1, sourceWidth);
        this.sourceHeight = Math.max(1, sourceHeight);
        createdAt = now;
    }

    record Frame(double x, double y, double scale) {}

    boolean expired(long now) {
        return started ? now - startedAt >= DURATION_MS : now - createdAt >= TARGET_WAIT_MS;
    }

    Frame sample(long now, int width, int height, double targetX, double targetY) {
        if (!started) { started = true; startedAt = now; }
        double t = Math.max(0, Math.min(1, (double) (now - startedAt) / DURATION_MS));
        double progress = t * t;
        double x = sourceX * width / sourceWidth, y = sourceY * height / sourceHeight;
        double arc = Math.min(36, Math.hypot(targetX - x, targetY - y) * .15);
        double shrink = Math.max(0, (t - .6) / .4);
        return new Frame(x + (targetX - x) * progress,
                y + (targetY - y) * progress - arc * 4 * t * (1 - t),
                1 - .95 * shrink * shrink);
    }
}
