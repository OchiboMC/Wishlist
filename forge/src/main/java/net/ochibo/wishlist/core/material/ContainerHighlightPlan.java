package net.ochibo.wishlist.core.material;

import net.ochibo.wishlist.core.model.ResourceIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares selected Wishlist targets with the open container and player inventory. */
public final class ContainerHighlightPlan {
    public enum Status { NONE, ENOUGH, SHORT }

    public record Target(List<String> candidates, long required) {
        public Target {
            if (candidates == null || candidates.isEmpty() || required <= 0)
                throw new IllegalArgumentException("target needs candidates and a positive count");
            candidates = candidates.stream().distinct().sorted().toList();
            candidates.forEach(ResourceIdentity::parse);
        }
    }

    public record Stack(String key, long count) {
        public Stack {
            ResourceIdentity.parse(key);
            if (count < 0) throw new IllegalArgumentException("stack count must not be negative");
        }
    }

    private record Result(Target target, long found) {}

    private final List<Result> results;

    private ContainerHighlightPlan(List<Result> results) {
        this.results = List.copyOf(results);
    }

    public static List<Target> targetsForRows(List<MaterialSummaryRow> rows) {
        List<Target> targets = new ArrayList<>();
        for (MaterialSummaryRow row : rows) {
            List<String> itemCandidates = row.candidates().stream()
                    .filter(key -> ResourceIdentity.parse(key).kind().equals("item")).toList();
            if (!itemCandidates.isEmpty() && row.required() > 0)
                targets.add(new Target(itemCandidates, row.required()));
        }
        return List.copyOf(targets);
    }

    public static ContainerHighlightPlan evaluate(List<Target> targets, List<Stack> stacks) {
        return evaluate(targets, stacks, List.of());
    }

    public static ContainerHighlightPlan evaluate(List<Target> targets,
                                                  List<Stack> containerStacks, List<Stack> inventoryStacks) {
        Map<List<String>, Long> grouped = new LinkedHashMap<>();
        for (Target target : targets) {
            grouped.merge(target.candidates(), target.required(), Math::addExact);
        }
        List<Result> results = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Target target = new Target(entry.getKey(), entry.getValue());
            results.add(new Result(target, Math.addExact(
                    countFor(target, containerStacks), countFor(target, inventoryStacks))));
        }
        return new ContainerHighlightPlan(results);
    }

    public Status statusFor(String itemKey) {
        Status status = Status.NONE;
        for (Result result : results) {
            if (!matches(result.target(), itemKey)) continue;
            if (result.found() < result.target().required()) return Status.SHORT;
            status = Status.ENOUGH;
        }
        return status;
    }

    public Target targetFor(String itemKey) {
        Target fallback = null;
        for (Result result : results) {
            if (!matches(result.target(), itemKey)) continue;
            if (result.found() < result.target().required()) return result.target();
            fallback = result.target();
        }
        return fallback;
    }

    public static long countFor(Target target, List<Stack> stacks) {
        long found = 0;
        for (Stack stack : stacks) {
            if (matches(target, stack.key())) found = Math.addExact(found, stack.count());
        }
        return found;
    }

    private static boolean matches(Target target, String key) {
        return target.candidates().stream().anyMatch(candidate -> ResourceIdentity.matches(candidate, key));
    }
}
