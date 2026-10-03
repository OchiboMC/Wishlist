package net.ochibo.wishlist.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.ochibo.wishlist.client.ui.independent.IndependentUIRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class IndependentUiRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void wishlist$renderIndependentUi(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        IndependentUIRenderer.render(partialTick);
    }
}
