package net.ochibo.wishlist.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.ochibo.wishlist.client.WishlistClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void wishlist$beginRender(GuiGraphics graphics, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        WishlistClient.highlight().beginRender((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void wishlist$renderSlotOverlay(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        WishlistClient.highlight().drawSlotOverlay((AbstractContainerScreen<?>) (Object) this, graphics, slot);
    }
}
