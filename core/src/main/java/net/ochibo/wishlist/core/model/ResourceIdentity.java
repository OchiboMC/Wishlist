package net.ochibo.wishlist.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Stable, viewer-independent identity for a material candidate. */
public record ResourceIdentity(String kind, String registryId, String tag) {
    public ResourceIdentity {
        kind = Objects.requireNonNull(kind, "kind");
        registryId = Objects.requireNonNull(registryId, "registryId");
        if (kind.isBlank() || kind.indexOf('|') >= 0 || registryId.isBlank() || registryId.indexOf('|') >= 0)
            throw new IllegalArgumentException("invalid resource identity");
        if (tag != null && tag.isBlank()) tag = null;
    }

    /** An untagged item keeps the v1/v2 ID so existing saves remain valid. */
    public static String item(String registryId, String tag) {
        return tag == null || tag.isBlank() ? registryId : new ResourceIdentity("item", registryId, tag).encode();
    }

    public static String fluid(String registryId, String tag) {
        return new ResourceIdentity("fluid", registryId, tag).encode();
    }

    /** Extension point for additional material kinds registered by other mods. */
    public static String of(String kind, String registryId, String tag) {
        return new ResourceIdentity(kind, registryId, tag).encode();
    }

    public String encode() {
        String prefix = kind + '|' + registryId;
        return tag == null ? prefix : prefix + '|' + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tag.getBytes(StandardCharsets.UTF_8));
    }

    public static ResourceIdentity parse(String key) {
        Objects.requireNonNull(key, "key");
        String[] parts = key.split("\\|", -1);
        if (parts.length == 1) return new ResourceIdentity("item", key, null);
        if (parts.length == 2) return new ResourceIdentity(parts[0], parts[1], null);
        if (parts.length == 3) return new ResourceIdentity(parts[0], parts[1],
                new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8));
        throw new IllegalArgumentException("invalid resource key: " + key);
    }

    /** Untagged requirements accept any tag; tagged requirements require exact identity. */
    public static boolean matches(String requirement, String available) {
        ResourceIdentity wanted = parse(requirement);
        ResourceIdentity owned = parse(available);
        return wanted.kind.equals(owned.kind) && wanted.registryId.equals(owned.registryId)
                && (wanted.tag == null || wanted.tag.equals(owned.tag));
    }
}
