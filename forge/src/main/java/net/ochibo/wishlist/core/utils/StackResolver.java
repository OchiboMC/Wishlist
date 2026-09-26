package net.ochibo.wishlist.core.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.ochibo.wishlist.core.model.ResourceIdentity;

public final class StackResolver {

    private StackResolver() {}

    public static ItemStack stack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceIdentity identity = ResourceIdentity.parse(itemId);
        if (!identity.kind().equals("item")) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(identity.registryId());
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (identity.tag() != null) {
            try { stack.setTag(TagParser.parseTag(identity.tag())); }
            catch (Exception ignored) { return ItemStack.EMPTY; }
        }
        return stack;
    }

    public static String targetOrAir(String itemId) {
        return itemId == null || itemId.isBlank() ? "minecraft:air" : itemId;
    }

    public static String id(ItemStack stack){
        return ResourceIdentity.item(idOf(ForgeRegistries.ITEMS.getKey(stack.getItem())),
                stack.hasTag() ? stack.getTag().toString() : null);
    }

    public static String fluidId(FluidStack stack) {
        return ResourceIdentity.fluid(idOf(ForgeRegistries.FLUIDS.getKey(stack.getFluid())),
                stack.hasTag() ? stack.getTag().toString() : null);
    }

    public static FluidStack fluidStack(String key) {
        if (key == null || key.isBlank()) return FluidStack.EMPTY;
        ResourceIdentity identity = ResourceIdentity.parse(key);
        if (!identity.kind().equals("fluid")) return FluidStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(identity.registryId());
        if (id == null || !ForgeRegistries.FLUIDS.containsKey(id)) return FluidStack.EMPTY;
        var fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null) return FluidStack.EMPTY;
        FluidStack stack = new FluidStack(fluid, 1000);
        if (identity.tag() != null) {
            try { stack.setTag(TagParser.parseTag(identity.tag())); }
            catch (Exception ignored) { return FluidStack.EMPTY; }
        }
        return stack;
    }

    public static String idOf(ResourceLocation location){
        if (location == null)return "minecraft:air";
        return targetOrAir(location.toString());
    }
}
