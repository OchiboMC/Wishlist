package net.ochibo.wishlist.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("hoveredSlot") Slot wishlist$getHoveredSlot();
    @Accessor("leftPos") int wishlist$getLeftPos();
    @Accessor("topPos") int wishlist$getTopPos();
    @Accessor("imageWidth") int wishlist$getImageWidth();
    @Accessor("imageHeight") int wishlist$getImageHeight();
}
