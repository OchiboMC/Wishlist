package net.ochibo.wishlist;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.ochibo.wishlist.client.WishlistClient;

public final class WishlistMod implements ClientModInitializer {
    public static final String MOD_ID = "wishlist";

    @Override
    public void onInitializeClient() {
        WishlistClient.register();
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static ResourceLocation id(String location) {
        return new ResourceLocation(MOD_ID, location);
    }
}
