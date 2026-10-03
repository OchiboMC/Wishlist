package net.ochibo.wishlist.client.ui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Name relevance shared by both loaders; follows the reviewed HTML prototype. */
public final class TreeSearchRanking {
    private static final Pattern SPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Comparator<Score> SCORES = Comparator.comparingInt(Score::kind)
            .thenComparingInt(Score::edits).thenComparingInt(Score::position)
            .thenComparingInt(Score::extra).thenComparing(Score::name);

    private TreeSearchRanking() {}

    public record Score(int kind, int edits, int position, int extra, String name) {}
    private record Ranked<T>(T value, Score score, int index) {}
    private record Word(int[] points, int position, int length) {}

    static final class PreparedText {
        final String value;
        final List<Word> words;
        PreparedText(String value) {
            this.value = value;
            words = WORD_SEPARATOR.splitAsStream(value).filter(word -> !word.isEmpty())
                    .map(word -> new Word(word.codePoints().toArray(), value.indexOf(word), word.length())).toList();
        }
    }

    static PreparedText prepare(String value) { return new PreparedText(normalize(value)); }
    static int compare(Score left, Score right) { return SCORES.compare(left, right); }

    public static String normalize(String value) {
        return SPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).strip()).replaceAll(" ").trim();
    }

    public static Optional<Score> match(String name, String query) {
        return Optional.ofNullable(match(prepare(name), prepare(query)));
    }

    static Score match(PreparedText name, PreparedText query) {
        String value = name.value, wanted = query.value;
        if (wanted.isEmpty()) return null;
        int position = value.indexOf(wanted);
        int extra = Math.abs(value.length() - wanted.length());
        if (value.equals(wanted)) return new Score(0, 0, 0, extra, value);
        if (value.startsWith(wanted)) return new Score(1, 0, 0, extra, value);
        if (position >= 0) {
            int end = position + wanted.length();
            boolean word = (position == 0 || !wordCharacter(value.codePointBefore(position)))
                    && (end == value.length() || !wordCharacter(value.codePointAt(end)));
            return new Score(word ? 2 : 3, 0, position, extra, value);
        }
        if (query.words.isEmpty() || name.words.isEmpty()) return null;
        int edits = 0, first = value.length();
        for (Word token : query.words) {
            int bestEdits = Integer.MAX_VALUE, bestPosition = value.length();
            // The original tolerance uses UTF-16 string length, including supplementary characters.
            int length = token.length;
            int allowance = length < 4 ? 0 : length < 7 ? 1 : 2;
            for (Word candidate : name.words) {
                int change = distance(token.points, candidate.points, allowance), at = candidate.position;
                if (change < bestEdits || change == bestEdits && at < bestPosition) {
                    bestEdits = change;
                    bestPosition = at;
                }
            }
            if (bestEdits > allowance) return null;
            edits += bestEdits;
            first = Math.min(first, bestPosition);
        }
        return new Score(4, edits, first, extra, value);
    }

    /** Call with roots only. Returned values keep their original trees and ownership. */
    public static <T> List<T> rank(List<T> roots, String query, Function<T, String> name) {
        PreparedText wanted = prepare(query);
        if (wanted.value.isEmpty()) return List.copyOf(roots);
        List<Ranked<T>> ranked = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            T value = roots.get(i);
            Score score = match(prepare(name.apply(value)), wanted);
            if (score != null) ranked.add(new Ranked<>(value, score, i));
        }
        ranked.sort(Comparator.comparing((Ranked<T> value) -> value.score, SCORES)
                .thenComparingInt(value -> value.index));
        return ranked.stream().map(Ranked::value).toList();
    }

    private static boolean wordCharacter(int point) {
        int type = Character.getType(point);
        return Character.isLetter(point) || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    /** Optimal string alignment distance, including adjacent letter transpositions. */
    private static int distance(int[] left, int[] right, int limit) {
        if (Math.abs(left.length - right.length) > limit) return limit + 1;
        if (limit == 0) return Arrays.equals(left, right) ? 0 : 1;
        int[] before = new int[right.length + 1], previous = new int[right.length + 1], current = new int[right.length + 1];
        Arrays.fill(before, limit + 1);
        Arrays.fill(previous, limit + 1);
        for (int j = 0; j <= Math.min(limit, right.length); j++) previous[j] = j;
        for (int i = 1; i <= left.length; i++) {
            Arrays.fill(current, limit + 1);
            current[0] = i <= limit ? i : limit + 1;
            int minimum = current[0];
            for (int j = Math.max(1, i - limit); j <= Math.min(right.length, i + limit); j++) {
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (left[i - 1] == right[j - 1] ? 0 : 1));
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1])
                    current[j] = Math.min(current[j], before[j - 2] + 1);
                minimum = Math.min(minimum, current[j]);
            }
            if (minimum > limit) return limit + 1;
            int[] reused = before; before = previous; previous = current; current = reused;
        }
        return Math.min(limit + 1, previous[right.length]);
    }
}
