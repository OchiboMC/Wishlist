package net.ochibo.wishlist.client;

import java.util.OptionalLong;

/** Reconciles client craft callbacks with the inventory acknowledged by the server. */
public final class ShiftCraftCount {
    public static final int TIMEOUT_TICKS = 80;
    private final long startingInventoryCount;
    private final int startingStateId;
    private long callbackCount;
    private long lastInventoryCount;
    private int stableTicks;
    private int ticks;

    public ShiftCraftCount(long startingInventoryCount, int startingStateId) {
        this.startingInventoryCount = startingInventoryCount;
        this.startingStateId = startingStateId;
        this.lastInventoryCount = startingInventoryCount;
    }

    public void crafted(long count) {
        callbackCount += Math.max(0, count);
    }

    public OptionalLong poll(long inventoryCount, int stateId) {
        ticks++;
        if (stateId != startingStateId) {
            stableTicks = inventoryCount == lastInventoryCount ? stableTicks + 1 : 1;
            if (stableTicks >= 3) return OptionalLong.of(result(inventoryCount));
        }
        lastInventoryCount = inventoryCount;
        return ticks >= TIMEOUT_TICKS ? OptionalLong.of(result(inventoryCount)) : OptionalLong.empty();
    }

    private long result(long inventoryCount) {
        return Math.max(callbackCount, Math.max(0, inventoryCount - startingInventoryCount));
    }
}
