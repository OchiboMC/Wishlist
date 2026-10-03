package net.ochibo.wishlist.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.material.InventoryAllocator;
import net.ochibo.wishlist.core.model.RecipeKey;

/** Describes one batch of missing recipe ingredients while Shift is held. */
public final class TransferMissingTooltip {
    private TransferMissingTooltip() {}

    public static Component describe(RecipeKey key) {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        var recipe = controller.resolver().findByKey(key).orElse(null);
        if (recipe == null || !recipe.supported())
            return Component.translatable("screen.wishlist.transfer_missing");
        var ingredients = recipe.ingredients();
        List<InventoryAllocator.Request> requests = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++)
            requests.add(new InventoryAllocator.Request(i, ingredients.get(i), ingredients.get(i).count()));
        var allocated = new InventoryAllocator().allocate(requests,
                controller.inventorySnapshot().counts()).allocatedByRequest();
        Component tooltip = Component.translatable("screen.wishlist.transfer_missing");
        int lines = 0;
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i);
            long missing = ingredient.count() - allocated.getOrDefault(i, 0L);
            if (missing <= 0) continue;
            tooltip = tooltip.copy().append("\n").append(Component.translatable(
                    "screen.wishlist.transfer_missing_line", UiText.itemName(ingredient.displayKey()), missing));
            if (++lines >= 8) break;
        }
        return tooltip;
    }
}
