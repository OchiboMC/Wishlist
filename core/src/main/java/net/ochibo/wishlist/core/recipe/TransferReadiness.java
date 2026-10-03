package net.ochibo.wishlist.core.recipe;

/** Whether a recipe can be placed by an available recipe viewer. */
public enum TransferReadiness {
    UNSUPPORTED,
    MISSING,
    READY;

    public static TransferReadiness combine(TransferReadiness first, TransferReadiness second) {
        if (first == READY || second == READY) return READY;
        if (first == MISSING || second == MISSING) return MISSING;
        return UNSUPPORTED;
    }
}
