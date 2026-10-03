package net.ochibo.wishlist.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.HudMaterialPanel;
import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.ochibo.wishlist.core.layout.HudPlacement;

/** Drag the exact HUD preview to choose its screen-relative position. */
public final class HudPositionScreen extends Screen {
    private final Screen parent;
    private int xBasis;
    private int yBasis;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging;
    private HudMaterialPanel.Layout panel;

    public HudPositionScreen(Screen parent) {
        super(Component.translatable("screen.wishlist.config.position"));
        this.parent = parent;
        xBasis = WishlistClientConfig.hudPositionX();
        yBasis = WishlistClientConfig.hudPositionY();
    }

    @Override
    protected void init() {
        ClientWorkspaceController.get().ensureLoaded();
        panel = HudMaterialPanel.layout(true);
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.done"), button -> onClose())
                .bounds(width / 2 - 60, height - 28, 120, 20).build());
    }

    private HudPlacement.Bounds panelBounds() {
        return HudMaterialPanel.bounds(panel, width, height, xBasis, yBasis);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.wishlist.config.position_hint"),
                width / 2, 26, 0xC0C0C0);
        HudPlacement.Bounds bounds = panelBounds();
        HudMaterialPanel.render(graphics, panel, bounds);
        int color = dragging || bounds.contains(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF999999;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.top() + 1, color);
        graphics.fill(bounds.left(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        graphics.fill(bounds.left(), bounds.top(), bounds.left() + 1, bounds.bottom(), color);
        graphics.fill(bounds.right() - 1, bounds.top(), bounds.right(), bounds.bottom(), color);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && panelBounds().contains(mouseX, mouseY)) {
            HudPlacement.Bounds bounds = panelBounds();
            dragOffsetX = (int) mouseX - bounds.left();
            dragOffsetY = (int) mouseY - bounds.top();
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging) {
            HudPlacement.Bounds bounds = panelBounds();
            xBasis = HudPlacement.horizontalBasis(width, bounds.width(), (int) mouseX - dragOffsetX);
            yBasis = HudPlacement.verticalBasis(height, bounds.height(), (int) mouseY - dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            WishlistClientConfig.setHudPosition(xBasis, yBasis);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        WishlistClientConfig.setHudPosition(xBasis, yBasis);
        Minecraft.getInstance().setScreen(parent);
    }
}
