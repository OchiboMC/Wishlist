package net.ochibo.wishlist.api.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Fabric-side fluid value used by the recipe expansion API. Amounts use millibuckets. */
public final class FluidStack {
    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY, 0);

    private final Fluid fluid;
    private int amount;
    private CompoundTag tag;

    public FluidStack(Fluid fluid, int amount) {
        this.fluid = fluid;
        this.amount = amount;
    }

    public Fluid getFluid() { return fluid; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public boolean isEmpty() { return fluid == Fluids.EMPTY || amount <= 0; }
    public boolean hasTag() { return tag != null && !tag.isEmpty(); }
    public CompoundTag getTag() { return tag == null ? null : tag.copy(); }
    public void setTag(CompoundTag tag) { this.tag = tag == null ? null : tag.copy(); }
    public Component getDisplayName() {
        var bucket = fluid.getBucket();
        return bucket == Items.AIR ? Component.literal(BuiltInRegistries.FLUID.getKey(fluid).toString())
                : new ItemStack(bucket).getHoverName();
    }

    public FluidStack copy() {
        FluidStack copy = new FluidStack(fluid, amount);
        copy.setTag(tag);
        return copy;
    }
}
