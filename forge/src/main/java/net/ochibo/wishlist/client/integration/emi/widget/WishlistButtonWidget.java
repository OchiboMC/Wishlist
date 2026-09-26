package net.ochibo.wishlist.client.integration.emi.widget;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.integration.RecipeInteractionService;
import net.ochibo.wishlist.client.integration.emi.EmiGenericRecipeResolver;
import net.ochibo.wishlist.client.integration.widget.AddToWishlistButton;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.utils.StackResolver;

import java.util.List;
import java.util.Optional;

public class WishlistButtonWidget extends Widget {
    protected final EmiRecipe recipe;
    protected final int x, y;
    private final EmiGenericRecipeResolver resolver = new EmiGenericRecipeResolver();

    public WishlistButtonWidget(int x, int y, EmiRecipe recipe) {
        this.x = x;
        this.y = y;
        this.recipe = recipe;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        RecipeInteractionService.State state = state();
        Screen screen = Minecraft.getInstance().screen;
        if (!state.enabled() || state.recipe().isEmpty() || screen == null) return true;
        if (!RecipeInteractionService.canHandleClick(button)) return false;
        if (!RecipeInteractionService.handleClick(screen, state.recipe().orElseThrow(), button)) return false;
        playButtonSound();
        return true;
    }

    public void playButtonSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, 12, 12);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        AddToWishlistButton.draw(graphics, x, y, getBounds().contains(mouseX, mouseY), state().enabled());
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        return List.of(ClientTooltipComponent.create(RecipeInteractionService.tooltip(state()).getVisualOrderText()));
    }

    private RecipeInteractionService.State state() {
        String target = ClientWorkspaceController.get().pendingSelection()
                .map(ClientWorkspaceController.SelectionRequest::itemId).orElse("");
        try {
            Optional<ResolvedRecipe> resolved = resolver.resolveRecipe(recipe, target);
            if (resolved.filter(ResolvedRecipe::supported).isEmpty() && recipe.getId() != null) {
                String nativeTarget = target;
                if (nativeTarget.isBlank() && !recipe.getOutputs().isEmpty()) {
                    ItemStack displayed = recipe.getOutputs().get(0).getItemStack();
                    if (!displayed.isEmpty()) {
                        ItemStack untagged = displayed.copy();
                        untagged.setTag(null);
                        nativeTarget = StackResolver.id(untagged);
                    }
                }
                Optional<ResolvedRecipe> nativeResult = ClientWorkspaceController.get().resolver()
                        .findByKey(new RecipeKey(recipe.getId().toString()), nativeTarget);
                if (nativeResult.filter(ResolvedRecipe::supported).isPresent()) resolved = nativeResult;
            }
            return RecipeInteractionService.state(resolved);
        } catch (RuntimeException | LinkageError ignored) {
            return RecipeInteractionService.state(Optional.empty());
        }
    }
}
