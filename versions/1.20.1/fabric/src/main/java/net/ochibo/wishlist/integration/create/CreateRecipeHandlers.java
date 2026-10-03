package net.ochibo.wishlist.integration.create;

import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.ochibo.wishlist.internal.recipe.HandlerTable;
import net.minecraft.resources.ResourceLocation;

/** Registers the built-in Fabric Create handlers with Wishlist's expansion table. */
public final class CreateRecipeHandlers {
    private CreateRecipeHandlers() {}

    public static void register(HandlerTable.Builder handlers) {
        handlers.register(new ResourceLocation("wishlist", "create/sequenced_assembly"),
                SequencedAssemblyRecipe.class, new SequencedAssemblyHandler());
        handlers.register(new ResourceLocation("wishlist", "create/mechanical_crafting"),
                MechanicalCraftingRecipe.class, new MechanicalCraftingHandler());
    }
}
