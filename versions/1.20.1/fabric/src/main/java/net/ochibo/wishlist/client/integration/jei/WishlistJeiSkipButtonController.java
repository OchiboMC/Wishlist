package net.ochibo.wishlist.client.integration.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.integration.RecipeInteractionService;
import net.ochibo.wishlist.client.integration.widget.AddToWishlistButton;
import net.ochibo.wishlist.client.integration.widget.SkipSelectionButton;

/** JEI places this after Wishlist's star in its normal recipe side-button grid. */
final class WishlistJeiSkipButtonController implements IIconButtonController {
    private static final IDrawable ICON = new SkipIcon();

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE
                || input.getKey().getValue() != 0
                || !RecipeInteractionService.canSkipAutomaticSelection()) return false;
        return input.isSimulate() || RecipeInteractionService.skipAutomaticSelection();
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("screen.wishlist.skip_tooltip"));
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(ICON);
        updateState(state);
    }

    @Override
    public void updateState(IButtonState state) {
        boolean visible = RecipeInteractionService.canSkipAutomaticSelection();
        state.setVisible(visible);
        state.setActive(visible);
        state.setForcePressed(false);
    }

    @Override
    public void drawExtras(GuiGraphics graphics, Rect2i area, int mouseX, int mouseY, float partialTicks) {}

    private static final class SkipIcon implements IDrawable {
        @Override public int getWidth() { return 9; }
        @Override public int getHeight() { return 9; }
        @Override public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
            SkipSelectionButton.drawIcon(graphics,0,0);
        }
    }
}
