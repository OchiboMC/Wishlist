package net.ochibo.wishlist.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.WishlistClient;
import net.ochibo.wishlist.core.model.RecipeKey;

/** Twelve-pixel recipe transfer button from Wishlist's widget atlas. */
public final class TransferRecipeButton extends Button {
    private final RecipeKey recipeKey;

    public TransferRecipeButton(int x, int y, boolean ready, RecipeKey recipeKey, OnPress onPress) {
        super(x, y, 12, 12, Component.empty(), onPress, DEFAULT_NARRATION);
        this.recipeKey = recipeKey;
        active = ready;
        setTooltip(Tooltip.create(ready
                ? Component.translatable("screen.wishlist.transfer_recipe")
                : Component.translatable("screen.wishlist.transfer_missing").withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!active && isHovered()) setTooltip(Tooltip.create(Screen.hasShiftDown()
                ? TransferMissingTooltip.describe(recipeKey)
                : Component.translatable("screen.wishlist.transfer_missing_shift").withStyle(ChatFormatting.GRAY)));
        int v = 64 + (!active ? 24 : isHovered() ? 12 : 0);
        graphics.blit(WishlistClient.WIDGETS, getX(), getY(), 12, 12,
                32, v, 12, 12, 256, 256);
    }
}
