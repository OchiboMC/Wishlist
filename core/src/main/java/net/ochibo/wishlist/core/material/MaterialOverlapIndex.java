package net.ochibo.wishlist.core.material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.ochibo.wishlist.core.model.ResourceIdentity;

/** Detects overlap in linear candidate work, including wildcard tags, without pairing all groups. */
final class MaterialOverlapIndex {
    private record Base(String kind, String id) {
        static Base of(ResourceIdentity identity) { return new Base(identity.kind(), identity.registryId()); }
    }
    private static final class Membership {
        int first = -1;
        boolean multiple;
        void add(int group) { if (first == -1) first = group; else if (first != group) multiple = true; }
        boolean hasOther(int group) { return first != -1 && (first != group || multiple); }
    }
    private static final class Bucket {
        final Membership all = new Membership();
        final Membership wildcard = new Membership();
        final Map<String, Membership> tags = new HashMap<>();
    }
    private final Map<Base, Bucket> buckets = new HashMap<>();
    private final Map<String, ResourceIdentity> identities = new HashMap<>();

    void add(int group, List<String> candidates) {
        for (String candidate : candidates) {
            ResourceIdentity identity = identities.computeIfAbsent(candidate, ResourceIdentity::parse);
            Bucket bucket = buckets.computeIfAbsent(Base.of(identity), ignored -> new Bucket());
            bucket.all.add(group);
            if (identity.tag() == null) bucket.wildcard.add(group);
            else bucket.tags.computeIfAbsent(identity.tag(), ignored -> new Membership()).add(group);
        }
    }

    boolean overlaps(int group, List<String> candidates) {
        for (String candidate : candidates) {
            ResourceIdentity identity = identities.get(candidate);
            Bucket bucket = buckets.get(Base.of(identity));
            if (identity.tag() == null ? bucket.all.hasOther(group)
                    : bucket.wildcard.hasOther(group) || bucket.tags.get(identity.tag()).hasOther(group)) return true;
        }
        return false;
    }
}
