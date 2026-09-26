package net.ochibo.wishlist;

import net.minecraft.resources.ResourceLocation;
import net.ochibo.wishlist.client.WishlistClient;
import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WishlistMod.MOD_ID)
public final class WishlistMod {
    public static final String MOD_ID = "wishlist";

    public WishlistMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, WishlistClientConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WishlistClient.register(context.getModEventBus());
        }
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static ResourceLocation id(String location){ return ResourceLocation.fromNamespaceAndPath(MOD_ID,location); }
}
