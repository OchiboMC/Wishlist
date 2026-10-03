package net.ochibo.wishlist.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.ochibo.wishlist.client.WishlistClient;
import net.ochibo.wishlist.client.integration.RecipeOverlayEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Screen wishlist$onScreenOpening(Screen next) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.screen != next) WishlistClient.highlight().screenOpening();
        return RecipeOverlayEvents.opening(mc.screen, next);
    }
}
