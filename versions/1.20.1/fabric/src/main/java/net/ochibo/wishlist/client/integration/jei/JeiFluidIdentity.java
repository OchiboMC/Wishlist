package net.ochibo.wishlist.client.integration.jei;

import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.ochibo.wishlist.core.model.ResourceIdentity;

/** Preserves JEI Fabric fluid identity, amount, and NBT in Wishlist's shared format. */
final class JeiFluidIdentity {
    private JeiFluidIdentity() {}

    static String id(IJeiFluidIngredient fluid) {
        return id(fluid.getFluid(), fluid.getTag().orElse(null));
    }

    static String id(net.minecraft.world.level.material.Fluid fluid, CompoundTag tag) {
        return ResourceIdentity.fluid(BuiltInRegistries.FLUID.getKey(fluid).toString(),
                tag == null || tag.isEmpty() ? null : tag.toString());
    }
}
