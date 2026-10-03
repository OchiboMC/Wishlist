package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Selects registered roots without searching or pruning their descendants. */
public final class WishlistTreeSearch {
    private WishlistTreeSearch() {}

    public record Root(Wishlist wishlist, WishlistEntry entry) {}

    public static List<Root> roots(List<Wishlist> wishlists, String query, Function<String, String> itemName) {
        return new Index().roots(wishlists, query, itemName, null);
    }

    /** Screen-owned index; resource/language changes invalidate labels without changing tree objects. */
    public static final class Index {
        private record Version(Wishlist wishlist, long revision) {}
        private record Item(TreeSearchRanking.PreparedText name, String id, List<TreeSearchRanking.PreparedText> ids) {}
        private record Match(Root root, TreeSearchRanking.Score score, boolean idOnly, String id, int index) {}
        private final Map<String, Item> items = new HashMap<>();
        private List<Version> versions;
        private List<Root> roots = List.of();
        private Object textGeneration;
        private String lastQuery;
        private List<Root> lastResult;

        public void invalidate() {
            items.clear(); versions = null; lastQuery = null; lastResult = null;
        }

        public List<Root> roots(List<Wishlist> wishlists, String query, Function<String, String> itemName, Object generation) {
            if (!Objects.equals(textGeneration, generation)) {
                items.clear(); lastQuery = null; textGeneration = generation;
            }
            List<Version> current = wishlists.stream().map(wishlist -> new Version(wishlist, wishlist.revision())).toList();
            if (!current.equals(versions)) {
                roots = wishlists.stream().flatMap(wishlist -> wishlist.entries().stream().map(entry -> new Root(wishlist, entry))).toList();
                versions = current;
                lastQuery = null;
                var retained = new HashSet<String>();
                for (Root root : roots) retained.add(root.entry().outputItemId());
                items.keySet().retainAll(retained);
            }
            String normalized = TreeSearchRanking.normalize(query);
            if (normalized.equals(lastQuery)) return lastResult;
            if (normalized.isEmpty()) {
                lastQuery = normalized; lastResult = roots; return roots;
            }
            TreeSearchRanking.PreparedText wanted = new TreeSearchRanking.PreparedText(normalized);
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++) {
                Root root = roots.get(i);
                Item item = items.computeIfAbsent(root.entry().outputItemId(), key -> prepare(key, itemName));
                TreeSearchRanking.Score score = TreeSearchRanking.match(item.name(), wanted);
                boolean idOnly = score == null;
                if (idOnly) {
                    for (var id : item.ids()) {
                        var candidate = TreeSearchRanking.match(id, wanted);
                        if (candidate != null && (score == null || TreeSearchRanking.compare(candidate, score) < 0)) score = candidate;
                    }
                }
                if (score != null) matches.add(new Match(root, score, idOnly, item.id(), i));
            }
            matches.sort(Comparator.comparing(Match::idOnly)
                    .thenComparing(Match::score, TreeSearchRanking::compare)
                    .thenComparing(match -> match.idOnly() ? match.id() : "")
                    .thenComparingInt(Match::index));
            lastQuery = normalized;
            lastResult = matches.stream().map(Match::root).toList();
            return lastResult;
        }

        private static Item prepare(String key, Function<String, String> itemName) {
            String id = ResourceIdentity.parse(key).registryId();
            int colon = id.indexOf(':');
            List<TreeSearchRanking.PreparedText> ids = colon < 0 ? List.of(TreeSearchRanking.prepare(id))
                    : List.of(TreeSearchRanking.prepare(id), TreeSearchRanking.prepare(id.substring(0, colon)),
                            TreeSearchRanking.prepare(id.substring(colon + 1)));
            return new Item(TreeSearchRanking.prepare(itemName.apply(key)), id, ids);
        }
    }
}
