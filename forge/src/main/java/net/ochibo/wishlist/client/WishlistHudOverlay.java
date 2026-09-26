package net.ochibo.wishlist.client;

import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.ochibo.wishlist.client.ui.UiText;
import net.ochibo.wishlist.core.material.MaterialSummaryRow;
import net.ochibo.wishlist.core.material.MaterialVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

public final class WishlistHudOverlay {
    private WishlistHudOverlay() {}

    private static final int BACKGROUND_PADDING = 4;
    private static final int SCREEN_MARGIN = 8;
    private record HudRow(ItemStack stack, boolean alternatives, Component text, int color, int y) {}

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("wishlist_materials", WishlistHudOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!WishlistClientConfig.hudEnabled() || mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;

        ClientWorkspaceController controller = ClientWorkspaceController.get();
        List<MaterialSummaryRow> rows = MaterialVisibility.hudRows(controller.materialCalculation().rows(),
                controller.completedMaterials(), WishlistClientConfig.missingOnly());
        if (rows.isEmpty()) return;

        int normalColor = HudOverlayStyle.textColor(0x00FFFFFF);
        int doneColor = HudOverlayStyle.textColor(0x00BBBBBB);
        float scale = WishlistClientConfig.hudScale();
        Component title = Component.translatable("hud.wishlist.title");
        int contentWidth = mc.font.width(title);
        int contentBottom = mc.font.lineHeight;
        int y = 20;
        int limit = WishlistClientConfig.hudMaxItems();
        List<HudRow> displayed = new ArrayList<>();
        for (MaterialSummaryRow row : rows) {
            if (displayed.size() >= limit) break;
            List<String> candidates = row.candidates();
            if (candidates.isEmpty()) continue;

            ItemStack stack = UiText.itemStack(candidates.get(0));
            Component rowText = Component.translatable("screen.wishlist.material_count",
                    row.allocatedOwned(), row.required());
            String unit = UiText.unit(candidates.get(0));
            if (!unit.isBlank()) rowText = rowText.copy().append(" " + unit);
            displayed.add(new HudRow(stack, candidates.size() > 1, rowText,
                    row.satisfied() ? doneColor : normalColor, y));
            contentWidth = Math.max(contentWidth, 20 + mc.font.width(rowText));
            contentBottom = Math.max(contentBottom, y + Math.max(14, mc.font.lineHeight));
            y += 18;
        }

        Component omission = rows.size() > limit
                ? Component.translatable("hud.wishlist.omission", rows.size() - limit) : null;
        if (omission != null) {
            contentWidth = Math.max(contentWidth, mc.font.width(omission));
            contentBottom = Math.max(contentBottom, y + mc.font.lineHeight);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(SCREEN_MARGIN, SCREEN_MARGIN, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        int backgroundColor = HudOverlayStyle.backgroundColor(
                WishlistClientConfig.hudBackgroundOpacityPercent());
        if (backgroundColor != 0) {
            graphics.fill(-BACKGROUND_PADDING, -BACKGROUND_PADDING,
                    contentWidth + BACKGROUND_PADDING, contentBottom + BACKGROUND_PADDING,
                    backgroundColor);
        }
        graphics.drawString(mc.font, title, 0, 0, normalColor, true);

        for (HudRow row : displayed) {
            if (!row.stack().isEmpty()) {
                graphics.renderItem(row.stack(), 0, row.y() - 3);
                if (row.alternatives()) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, 0, 200);
                    graphics.blit(WishlistClient.WIDGETS, 13, row.y() + 10, 4, 4, 0, 0, 4, 4, 256, 256);
                    graphics.pose().popPose();
                }
            }
            graphics.drawString(mc.font, row.text(), 20, row.y(), row.color(), true);
        }

        if (omission != null) {
            graphics.drawString(
                    mc.font,
                    omission,
                    0,
                    y,
                    HudOverlayStyle.OMISSION_COLOR,
                    true);
        }

        graphics.pose().popPose();
    }
}
