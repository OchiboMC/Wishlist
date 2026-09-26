package net.ochibo.wishlist.client.integration.emi.widget;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.ochibo.wishlist.client.integration.RecipeInteractionService;
import net.ochibo.wishlist.client.integration.widget.AddToWishlistButton;
import net.ochibo.wishlist.client.integration.widget.SkipSelectionButton;

import java.util.List;

public final class SkipButtonWidget extends Widget {
    private final int x;
    private final int y;

    public SkipButtonWidget(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public Bounds getBounds() {
        return RecipeInteractionService.canSkipAutomaticSelection()
                ? new Bounds(x, y, 12, 12) : Bounds.EMPTY;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !RecipeInteractionService.skipAutomaticSelection()) return false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!RecipeInteractionService.canSkipAutomaticSelection()) return;
        SkipSelectionButton.draw(graphics, x, y, getBounds().contains(mouseX, mouseY), true);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        return RecipeInteractionService.canSkipAutomaticSelection()
                ? List.of(ClientTooltipComponent.create(Component.translatable("screen.wishlist.skip_tooltip")
                        .getVisualOrderText())) : List.of();
    }
}
