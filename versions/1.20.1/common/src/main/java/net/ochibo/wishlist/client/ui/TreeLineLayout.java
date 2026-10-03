package net.ochibo.wishlist.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Clip and merge identical tree stems so offscreen siblings add no per-frame drawing cost. */
final class TreeLineLayout {
    record Line(int x, int top, int bottom, int color) {}
    private TreeLineLayout() {}

    static List<Line> visible(List<Line> lines, int top, int bottom) {
        List<Line> clipped = new ArrayList<>();
        for (Line line : lines) {
            int a = Math.max(top, Math.min(line.top(), line.bottom()));
            int b = Math.min(bottom, Math.max(line.top(), line.bottom()));
            if (a < b) clipped.add(new Line(line.x(), a, b, line.color()));
        }
        clipped.sort(Comparator.comparingInt(Line::x).thenComparingInt(Line::color).thenComparingInt(Line::top));
        List<Line> merged = new ArrayList<>();
        for (Line line : clipped) {
            if (!merged.isEmpty()) {
                Line previous = merged.get(merged.size() - 1);
                if (line.x() == previous.x() && line.color() == previous.color() && line.top() <= previous.bottom()) {
                    merged.set(merged.size() - 1, new Line(previous.x(), previous.top(),
                            Math.max(previous.bottom(), line.bottom()), previous.color()));
                    continue;
                }
            }
            merged.add(line);
        }
        return List.copyOf(merged);
    }
}
