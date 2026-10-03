package net.ochibo.wishlist.client.ui;

public final class CandidateCycle {
    public static final long TOTAL_CYCLE_MS = 8_000L;

    private CandidateCycle() {}

    public static int indexAt(long nowMs, int candidateCount) {
        if (candidateCount <= 1) return 0;
        long interval = Math.max(1L, TOTAL_CYCLE_MS / candidateCount);
        return (int) ((Math.floorDiv(nowMs, interval)) % candidateCount);
    }
}
