package net.ochibo.wishlist.core.ingredients;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IngredientsResolver {
    public Set<String> resolve(List<IngredientsDefinition> definitions, Map<String, Set<String>> tags) {
        List<IngredientsDefinition> ordered = new ArrayList<>(definitions);
        ordered.sort(Comparator
                .comparingInt((IngredientsDefinition d) -> d.namespace().equals("wishlist") ? 0 : 1)
                .thenComparing(d -> d.namespace().equals("wishlist") ? "" : d.namespace())
                .thenComparingInt(IngredientsDefinition::resourceOrder));

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (IngredientsDefinition definition : ordered) {
            if (definition.replace()) result.clear();
            for (String value : definition.values()) result.addAll(expand(value, tags, new LinkedHashSet<>()));
            for (String value : definition.remove()) result.removeAll(expand(value, tags, new LinkedHashSet<>()));
        }
        return Set.copyOf(result);
    }

    private Set<String> expand(String value, Map<String, Set<String>> tags, Set<String> visiting) {
        if (value == null || value.isBlank()) return Set.of();
        if (!value.startsWith("#")) return Set.of(value);
        String tag = value.substring(1);
        if (!visiting.add(tag)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String member : tags.getOrDefault(tag, Set.of())) {
            result.addAll(expand(member, tags, visiting));
        }
        visiting.remove(tag);
        return result;
    }
}
