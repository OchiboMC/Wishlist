package net.ochibo.wishlist.integration.create;

import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.ochibo.wishlist.integration.forge.RegisterRecipeExpansionHandlersEvent;
import net.minecraft.resources.ResourceLocation;

/** Built-in Create integration implemented exclusively through the public Wishlist SPI. */
public final class CreateRecipeHandlers {
    private CreateRecipeHandlers() {}

    public static void register(RegisterRecipeExpansionHandlersEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("wishlist", "create/sequenced_assembly"),
                SequencedAssemblyRecipe.class, new SequencedAssemblyHandler());
        event.register(ResourceLocation.fromNamespaceAndPath("wishlist", "create/mechanical_crafting"),
                MechanicalCraftingRecipe.class, new MechanicalCraftingHandler());
    }
}
