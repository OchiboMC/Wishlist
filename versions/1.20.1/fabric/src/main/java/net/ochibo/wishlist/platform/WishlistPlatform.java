package net.ochibo.wishlist.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Path;

public final class WishlistPlatform {
    private WishlistPlatform() {}

    public static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean hasCraftingRemainingItem(ItemStack stack) {
        return stack.getItem().hasCraftingRemainingItem();
    }

    public static ItemStack craftingRemainder(ItemStack stack) {
        var item = stack.getItem().getCraftingRemainingItem();
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
