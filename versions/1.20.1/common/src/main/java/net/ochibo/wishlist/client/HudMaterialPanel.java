package net.ochibo.wishlist.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.ochibo.wishlist.client.ui.UiText;
import net.ochibo.wishlist.core.layout.HudPlacement;
import net.ochibo.wishlist.core.material.MaterialSummaryRow;
import net.ochibo.wishlist.core.material.MaterialCalculation;
import net.ochibo.wishlist.core.material.MaterialVisibility;

/** Shared HUD layout used by the overlay and its drag preview. */
public final class HudMaterialPanel {
    private HudMaterialPanel() {}

    private record LayoutKey(MaterialCalculation calculation, Set<String> completed, boolean missingOnly,
                             int limit, boolean preview, Object font, String language) {}
    private static LayoutKey cachedKey;
    private static Layout cachedLayout;

    public static void invalidate() { cachedKey = null; cachedLayout = null; }

    public record Row(ItemStack stack, boolean alternatives, boolean tagged, Component text, int color, int y) {}
    public record Layout(int width, int height, Component title, List<Row> rows,
                         Component omission, int omissionY) {}

    public static Layout layout(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        var controller = ClientWorkspaceController.get();
        MaterialCalculation calculation = controller.materialCalculation();
        Set<String> completed = controller.completedMaterials();
        boolean missingOnly = WishlistClientConfig.missingOnly();
        int limit = HudPlacement.rowLimit(mc.getWindow().getGuiScaledHeight(), mc.font.lineHeight,
                WishlistClientConfig.hudScale(), WishlistClientConfig.hudMaxItems());
        LayoutKey layoutKey = new LayoutKey(calculation, completed, missingOnly, limit, preview, mc.font, mc.options.languageCode);
        if (layoutKey.equals(cachedKey)) return cachedLayout;
        List<MaterialSummaryRow> rows = MaterialVisibility.hudRows(calculation.rows(), completed, missingOnly);
        Component title = Component.translatable("hud.wishlist.title");
        int width = mc.font.width(title);
        int bottom = mc.font.lineHeight;
        int y = 20;
        List<Row> displayed = new ArrayList<>();
        int normalColor = HudOverlayStyle.textColor(0x00FFFFFF);
        int doneColor = HudOverlayStyle.textColor(0x00BBBBBB);
        for (MaterialSummaryRow row : rows) {
            if (displayed.size() >= limit) break;
            if (row.candidates().isEmpty()) continue;
            String key = row.candidates().get(0);
            Component text = Component.translatable("screen.wishlist.material_count",
                    row.allocatedOwned(), row.required());
            String unit = UiText.unit(key);
            if (!unit.isBlank()) text = text.copy().append(" " + unit);
            displayed.add(new Row(UiText.itemStack(key), row.candidates().size() > 1,
                    net.ochibo.wishlist.core.model.ResourceIdentity.parse(key).tag() != null,
                    text, row.satisfied() ? doneColor : normalColor, y));
            width = Math.max(width, 20 + mc.font.width(text));
            bottom = Math.max(bottom, y + Math.max(14, mc.font.lineHeight));
            y += 18;
        }
        if (preview && displayed.isEmpty() && limit > 0) {
            Component text = Component.translatable("screen.wishlist.material_count", 0, 64);
            displayed.add(new Row(new ItemStack(Items.COBBLESTONE), false, false, text, normalColor, y));
            width = Math.max(width, 20 + mc.font.width(text));
            bottom = Math.max(bottom, y + Math.max(14, mc.font.lineHeight));
            y += 18;
        }
        Component omission = rows.size() > limit
                ? Component.translatable("hud.wishlist.omission", rows.size() - limit) : null;
        if (omission != null) {
            width = Math.max(width, mc.font.width(omission));
            bottom = Math.max(bottom, y + mc.font.lineHeight);
        }
        cachedKey = layoutKey;
        cachedLayout = new Layout(width, bottom, title, List.copyOf(displayed), omission, y);
        return cachedLayout;
    }

    public static HudPlacement.Bounds bounds(Layout layout, int screenWidth, int screenHeight,
                                              int xBasis, int yBasis) {
        return HudPlacement.bounds(screenWidth, screenHeight, layout.width(), layout.height(),
                WishlistClientConfig.hudScale(), xBasis, yBasis);
    }

    public static void render(GuiGraphics graphics, Layout layout, HudPlacement.Bounds bounds) {
        Minecraft mc = Minecraft.getInstance();
        float scale = WishlistClientConfig.hudScale();
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.contentX(), bounds.contentY(), 0);
        graphics.pose().scale(scale, scale, 1);
        int background = HudOverlayStyle.backgroundColor(WishlistClientConfig.hudBackgroundOpacityPercent());
        if (background != 0) {
            graphics.fill(-HudPlacement.PADDING, -HudPlacement.PADDING,
                    layout.width() + HudPlacement.PADDING,
                    layout.height() + HudPlacement.PADDING, background);
        }
        graphics.drawString(mc.font, layout.title(), 0, 0,
                HudOverlayStyle.textColor(0x00FFFFFF), true);
        for (Row row : layout.rows()) {
            if (!row.stack().isEmpty()) {
                graphics.renderItem(row.stack(), 0, row.y() - 3);
                if (row.alternatives() || row.tagged()) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, 0, 200);
                    if (row.alternatives()) graphics.blit(WishlistClient.WIDGETS,
                            row.tagged() ? 9 : 13, row.y() + 10,
                            4, 4, 0, 0, 4, 4, 256, 256);
                    if (row.tagged()) graphics.blit(WishlistClient.WIDGETS, 13, row.y() + 10,
                            4, 4, 4, 0, 4, 4, 256, 256);
                    graphics.pose().popPose();
                }
            }
            graphics.drawString(mc.font, row.text(), 20, row.y(), row.color(), true);
        }
        if (layout.omission() != null) {
            graphics.drawString(mc.font, layout.omission(), 0, layout.omissionY(),
                    HudOverlayStyle.OMISSION_COLOR, true);
        }
        graphics.pose().popPose();
    }
}
