package net.ochibo.wishlist.client.config;

import net.ochibo.wishlist.client.HudOverlayStyle;
import net.ochibo.wishlist.core.layout.HudPlacement;
import net.ochibo.wishlist.platform.WishlistPlatform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the same client TOML file and keys as the Forge edition. */
public final class WishlistClientConfig {
    private static final Set<String> LIMITS = Set.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "all");
    private static boolean loaded;
    private static boolean missingOnly;
    private static boolean hudEnabled;
    private static String maxItems = "5";
    private static int scalePercent = HudOverlayStyle.DEFAULT_SCALE_PERCENT;
    private static int backgroundOpacityPercent = HudOverlayStyle.DEFAULT_BACKGROUND_OPACITY_PERCENT;
    private static int positionX;
    private static int positionY;
    private static boolean recipeExpansionDebug;
    private static boolean highlightAnimated = true;

    private WishlistClientConfig() {}

    private static Path file() { return WishlistPlatform.configDirectory().resolve("wishlist-client.toml"); }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file())) return;
        try {
            String section = "";
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    section = trimmed.substring(1, trimmed.length() - 1);
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals < 0) continue;
                String key = trimmed.substring(0, equals).strip();
                String value = trimmed.substring(equals + 1).split("#", 2)[0].strip().replace("\"", "");
                if (section.equals("hud")) switch (key) {
                    case "missingOnly" -> missingOnly = Boolean.parseBoolean(value);
                    case "enabled" -> hudEnabled = Boolean.parseBoolean(value);
                    case "maxItems" -> { if (LIMITS.contains(value)) maxItems = value; }
                    case "scalePercent" -> scalePercent = HudOverlayStyle.clampScalePercent(parseInt(value, scalePercent));
                    case "backgroundOpacityPercent" -> backgroundOpacityPercent = clampOpacity(parseInt(value, backgroundOpacityPercent));
                    case "positionX" -> positionX = HudPlacement.clampBasis(parseInt(value, positionX));
                    case "positionY" -> positionY = HudPlacement.clampBasis(parseInt(value, positionY));
                    default -> {}
                }
                if (section.equals("recipeExpansion") && key.equals("debug")) recipeExpansionDebug = Boolean.parseBoolean(value);
                if (section.equals("highlight") && key.equals("animated")) highlightAnimated = Boolean.parseBoolean(value);
            }
        } catch (IOException ignored) {
            // Defaults keep the UI usable if a copied file is unreadable.
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int clampOpacity(int value) { return Math.max(0, Math.min(100, value)); }

    private static synchronized void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            List<String> lines = Files.exists(path) ? new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8)) : new ArrayList<>();
            put(lines, "hud", "missingOnly", Boolean.toString(missingOnly));
            put(lines, "hud", "enabled", Boolean.toString(hudEnabled));
            put(lines, "hud", "maxItems", "\"" + maxItems + "\"");
            put(lines, "hud", "scalePercent", Integer.toString(scalePercent));
            put(lines, "hud", "backgroundOpacityPercent", Integer.toString(backgroundOpacityPercent));
            put(lines, "hud", "positionX", Integer.toString(positionX));
            put(lines, "hud", "positionY", Integer.toString(positionY));
            put(lines, "recipeExpansion", "debug", Boolean.toString(recipeExpansionDebug));
            put(lines, "highlight", "animated", Boolean.toString(highlightAnimated));
            Path temp = Files.createTempFile(path.getParent(), "wishlist-client-", ".toml.tmp");
            try {
                Files.write(temp, lines, StandardCharsets.UTF_8);
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save Wishlist client config", error);
        }
    }

    private static void put(List<String> lines, String section, String key, String value) {
        int sectionStart = -1;
        int sectionEnd = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.equals("[" + section + "]")) { sectionStart = i; continue; }
            if (sectionStart >= 0 && line.startsWith("[") && line.endsWith("]")) { sectionEnd = i; break; }
        }
        if (sectionStart < 0) {
            lines.add("");
            lines.add("[" + section + "]");
            lines.add(key + " = " + value);
            return;
        }
        for (int i = sectionStart + 1; i < sectionEnd; i++) {
            if (lines.get(i).strip().startsWith(key + " =")) { lines.set(i, key + " = " + value); return; }
        }
        lines.add(sectionEnd, key + " = " + value);
    }

    public static boolean missingOnly() { load(); return missingOnly; }
    public static void setMissingOnly(boolean value) { load(); missingOnly = value; save(); }
    public static boolean hudEnabled() { load(); return hudEnabled; }
    public static void setHudEnabled(boolean value) { load(); hudEnabled = value; save(); }
    public static String hudMaxItemsValue() { load(); return maxItems; }
    public static void setHudMaxItemsValue(String value) { load(); if (LIMITS.contains(value)) { maxItems = value; save(); } }
    public static int hudMaxItems() { load(); return maxItems.equals("all") ? Integer.MAX_VALUE : parseInt(maxItems, 5); }
    public static int hudScalePercent() { load(); return scalePercent; }
    public static void setHudScalePercent(int value) { load(); scalePercent = HudOverlayStyle.clampScalePercent(value); save(); }
    public static float hudScale() { return HudOverlayStyle.scaleFromPercent(hudScalePercent()); }
    public static int hudBackgroundOpacityPercent() { load(); return backgroundOpacityPercent; }
    public static void setHudBackgroundOpacityPercent(int value) { load(); backgroundOpacityPercent = clampOpacity(value); save(); }
    public static boolean recipeExpansionDebug() { load(); return recipeExpansionDebug; }
    public static boolean highlightAnimated() { load(); return highlightAnimated; }
    public static void setHighlightAnimated(boolean value) { load(); highlightAnimated = value; save(); }
    public static int hudPositionX() { load(); return positionX; }
    public static int hudPositionY() { load(); return positionY; }
    public static void setHudPosition(int x, int y) {
        load();
        positionX = HudPlacement.clampBasis(x);
        positionY = HudPlacement.clampBasis(y);
        save();
    }
}
