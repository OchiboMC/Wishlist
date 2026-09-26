package net.ochibo.wishlist.core.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public record ClientWorldKey(Kind kind, String stableId) {
    public enum Kind { LOCAL, SERVER }

    public ClientWorldKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stableId, "stableId");
        if (stableId.isBlank()) throw new IllegalArgumentException("stableId must not be blank");
    }

    public static ClientWorldKey local(String levelFolderIdentity) { return new ClientWorldKey(Kind.LOCAL, levelFolderIdentity); }
    public static ClientWorldKey server(String serverAddress) { return new ClientWorldKey(Kind.SERVER, serverAddress.toLowerCase(Locale.ROOT)); }

    public String directoryName() {
        String safe = stableId.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.length() > 48) safe = safe.substring(0, 48);
        if (safe.isBlank()) safe = "world";
        return safe + "-" + shortHash(stableId);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
