package net.ochibo.wishlist.client.config;

import net.ochibo.wishlist.client.HudOverlayStyle;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class WishlistClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MISSING_ONLY;
    public static final ForgeConfigSpec.ConfigValue<String> HUD_MAX_ITEMS;
    public static final ForgeConfigSpec.IntValue HUD_SCALE_PERCENT;
    public static final ForgeConfigSpec.IntValue HUD_BACKGROUND_OPACITY_PERCENT;
    public static final ForgeConfigSpec.BooleanValue RECIPE_EXPANSION_DEBUG;

    private static final List<String> HUD_LIMIT_VALUES = List.of(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "all");

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("hud");
        MISSING_ONLY = builder
                .comment("Show only missing materials in the Wishlist screen and HUD.")
                .translation("config.wishlist.missing_only")
                .define("missingOnly", false);
        HUD_ENABLED = builder
                .comment("Whether the Wishlist material HUD is enabled.")
                .translation("config.wishlist.hud.enabled")
                .define("enabled", false);
        HUD_MAX_ITEMS = builder
                .comment("Maximum number of material rows shown in the HUD. Use 'all' to show every row.")
                .translation("config.wishlist.hud.max_items")
                .defineInList("maxItems", "5", HUD_LIMIT_VALUES);
        HUD_SCALE_PERCENT = builder
                .comment("Wishlist HUD size in percent.")
                .translation("config.wishlist.hud.scale")
                .defineInRange(
                        "scalePercent",
                        HudOverlayStyle.DEFAULT_SCALE_PERCENT,
                        HudOverlayStyle.MIN_SCALE_PERCENT,
                        HudOverlayStyle.MAX_SCALE_PERCENT);
        HUD_BACKGROUND_OPACITY_PERCENT = builder
                .comment("Opacity of the black Wishlist HUD background, from 0 to 100 percent.")
                .translation("config.wishlist.hud.background_opacity")
                .defineInRange("backgroundOpacityPercent",
                        HudOverlayStyle.DEFAULT_BACKGROUND_OPACITY_PERCENT, 0, 100);
        builder.pop();
        builder.push("recipeExpansion");
        RECIPE_EXPANSION_DEBUG = builder
                .comment("Enable detailed Wishlist recipe-expansion diagnostics in the game log.")
                .define("debug", false);
        builder.pop();
        SPEC = builder.build();
    }

    private WishlistClientConfig() {}

    public static boolean missingOnly() {
        return SPEC.isLoaded() && MISSING_ONLY.get();
    }

    public static void setMissingOnly(boolean enabled) {
        if (!SPEC.isLoaded()) return;
        MISSING_ONLY.set(enabled);
        MISSING_ONLY.save();
    }

    public static boolean hudEnabled() {
        return SPEC.isLoaded() && HUD_ENABLED.get();
    }

    public static void setHudEnabled(boolean enabled) {
        if (!SPEC.isLoaded()) return;
        HUD_ENABLED.set(enabled);
        HUD_ENABLED.save();
    }

    public static String hudMaxItemsValue() {
        return SPEC.isLoaded() ? HUD_MAX_ITEMS.get() : "5";
    }

    public static void setHudMaxItemsValue(String value) {
        if (!SPEC.isLoaded() || !HUD_LIMIT_VALUES.contains(value)) return;
        HUD_MAX_ITEMS.set(value);
        HUD_MAX_ITEMS.save();
    }

    public static int hudMaxItems() {
        if (!SPEC.isLoaded()) return 5;
        String value = HUD_MAX_ITEMS.get();
        if ("all".equalsIgnoreCase(value)) return Integer.MAX_VALUE;
        try {
            return Math.max(1, Math.min(10, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 5;
        }
    }

    public static int hudScalePercent() {
        return SPEC.isLoaded()
                ? HudOverlayStyle.clampScalePercent(HUD_SCALE_PERCENT.get())
                : HudOverlayStyle.DEFAULT_SCALE_PERCENT;
    }

    public static void setHudScalePercent(int percent) {
        if (!SPEC.isLoaded()) return;
        HUD_SCALE_PERCENT.set(HudOverlayStyle.clampScalePercent(percent));
        HUD_SCALE_PERCENT.save();
    }

    public static float hudScale() {
        return HudOverlayStyle.scaleFromPercent(hudScalePercent());
    }

    public static int hudBackgroundOpacityPercent() {
        return SPEC.isLoaded() ? HUD_BACKGROUND_OPACITY_PERCENT.get()
                : HudOverlayStyle.DEFAULT_BACKGROUND_OPACITY_PERCENT;
    }

    public static void setHudBackgroundOpacityPercent(int percent) {
        if (!SPEC.isLoaded()) return;
        HUD_BACKGROUND_OPACITY_PERCENT.set(Math.max(0, Math.min(100, percent)));
        HUD_BACKGROUND_OPACITY_PERCENT.save();
    }

    public static boolean recipeExpansionDebug() {
        return SPEC.isLoaded() && RECIPE_EXPANSION_DEBUG.get();
    }
}
