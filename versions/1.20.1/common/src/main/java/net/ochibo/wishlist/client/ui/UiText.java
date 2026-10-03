package net.ochibo.wishlist.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.ochibo.wishlist.client.integration.MaterialAdapterRegistry;

public final class UiText {
    private UiText() {}

    public static ItemStack itemStack(String itemId) {
        ResourceIdentity identity = ResourceIdentity.parse(itemId);
        if (identity.kind().equals("fluid")) {
            var fluid = StackResolver.fluidStack(itemId);
            if (fluid.isEmpty()) return ItemStack.EMPTY;
            var bucket = fluid.getFluid().getBucket();
            return new ItemStack(bucket == Items.AIR ? Items.BUCKET : bucket);
        }
        return identity.kind().equals("item") ? StackResolver.stack(itemId) : MaterialAdapterRegistry.icon(identity);
    }

    public static Component itemName(String itemId) {
        ResourceIdentity identity = ResourceIdentity.parse(itemId);
        if (identity.kind().equals("fluid")) {
            var fluid = StackResolver.fluidStack(itemId);
            return fluid.isEmpty() ? Component.literal(identity.registryId()) : fluid.getDisplayName();
        }
        if (!identity.kind().equals("item")) return MaterialAdapterRegistry.name(identity);
        ItemStack stack = itemStack(itemId);
        return stack.isEmpty() ? Component.literal(ResourceIdentity.parse(itemId).registryId()) : stack.getHoverName();
    }

    public static String shortName(String itemId) {
        return itemName(itemId).getString();
    }

    public static String unit(String key) {
        ResourceIdentity identity = ResourceIdentity.parse(key);
        return identity.kind().equals("fluid") ? "mB" : MaterialAdapterRegistry.unit(identity);
    }
}
