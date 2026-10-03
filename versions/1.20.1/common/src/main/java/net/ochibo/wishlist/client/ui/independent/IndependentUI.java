package net.ochibo.wishlist.client.ui.independent;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Extend this class for a UI whose lifetime is independent of Minecraft Screens.
 * Register the instance with {@link IndependentUIManager#getInstance()} on the client thread.
 * Showing/hiding does not change registration or the current Screen.
 */
public abstract class IndependentUI {
    private boolean visible = true;

    public final void show() { visible = true; }
    public final void hide() { visible = false; }
    public final boolean isVisible() { return visible; }

    /**
     * Called after the normal HUD, Screen, tooltips and toasts, in window GUI coordinates.
     * currentScreen can be null. Mouse coordinates use the same GUI scale.
     * Balance pose/scissor operations within this method. Input routing is not supplied by this base.
     */
    protected abstract void render(GuiGraphics graphics, Screen currentScreen,
                                   int mouseX, int mouseY, float partialTick);
}
