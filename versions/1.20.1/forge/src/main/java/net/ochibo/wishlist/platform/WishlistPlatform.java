package net.ochibo.wishlist.platform;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Path;

public final class WishlistPlatform {
    private WishlistPlatform() {}

    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean hasCraftingRemainingItem(ItemStack stack) {
        return stack.hasCraftingRemainingItem();
    }

    public static ItemStack craftingRemainder(ItemStack stack) {
        return stack.getCraftingRemainingItem();
    }
}
