package net.ochibo.wishlist.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.client.ShiftCraftTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void wishlist$recordCraft(ItemStack result, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (removeCount > 0 && player == mc.player && player.level().isClientSide()) {
            ShiftCraftTracker.crafted(result, removeCount);
        }
    }
}
