package net.ochibo.wishlist.client.integration.jei;

import com.mojang.blaze3d.platform.InputConstants;
import net.ochibo.wishlist.client.integration.RecipeInteractionService;
import net.ochibo.wishlist.client.integration.widget.AddToWishlistButton;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.Optional;

/** JEI-native Wishlist recipe button for one recipe layout. */
final class WishlistJeiRecipeButtonController implements IIconButtonController {
    private static final IDrawable STAR_ICON = new StarIcon();

    private final Optional<ResolvedRecipe> resolvedRecipe;
    private final WishlistJeiButtonRegistry buttonRegistry;

    WishlistJeiRecipeButtonController(Optional<ResolvedRecipe> resolvedRecipe, WishlistJeiButtonRegistry buttonRegistry) {
        this.resolvedRecipe = resolvedRecipe;
        this.buttonRegistry = buttonRegistry;
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        int mouseButton = mouseButton(input);
        if (mouseButton < 0) return false;
        return handle(input.isSimulate(), Minecraft.getInstance().screen, mouseButton);
    }

    /** Handles right click captured from Forge at the exact JEI-provided button hitbox. */
    boolean handleExternalMousePress(Screen sourceScreen, int mouseButton) {
        return handle(false, sourceScreen, mouseButton);
    }

    private boolean handle(boolean simulate, Screen sourceScreen, int mouseButton) {
        RecipeInteractionService.State interaction = RecipeInteractionService.state(resolvedRecipe);
        if (!interaction.enabled() || interaction.recipe().isEmpty()) return false;
        if (!RecipeInteractionService.canHandleClick(mouseButton)) return false;

        // JEI first asks on mouse-down whether this controller wants the input.
        // The actual action must execute only on the non-simulated follow-up.
        if (simulate) return true;
        if (sourceScreen == null) return false;
        return RecipeInteractionService.handleClick(
                sourceScreen,
                interaction.recipe().orElseThrow(),
                mouseButton);
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(RecipeInteractionService.tooltip(RecipeInteractionService.state(resolvedRecipe)));
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(STAR_ICON);
        updateState(state);
    }

    @Override
    public void updateState(IButtonState state) {
        RecipeInteractionService.State interaction = RecipeInteractionService.state(resolvedRecipe);
        state.setVisible(true);
        state.setActive(interaction.enabled());
        state.setForcePressed(false);
    }

    @Override
    public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTicks) {
        buttonRegistry.record(this, Minecraft.getInstance().screen, buttonArea);
    }

    private static int mouseButton(IJeiUserInput input) {
        InputConstants.Key key = input.getKey();
        if (key.getType() != InputConstants.Type.MOUSE) return -1;
        return key.getValue();
    }

    /** Small icon so JEI can use its own standard side-button frame and hitbox. */
    private static final class StarIcon implements IDrawable {
        private static final int WIDTH = 9;
        private static final int HEIGHT = 9;

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            AddToWishlistButton.drawIcon(guiGraphics,0,0);
        }
    }
}
