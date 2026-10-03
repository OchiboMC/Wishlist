package net.ochibo.wishlist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Brief, non-blocking persistence and recipe-transfer feedback. */
public final class WishlistToasts {
    private WishlistToasts() {}

    public static void saveFailed() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> SystemToast.addOrUpdate(mc.getToasts(),
                SystemToast.SystemToastIds.WORLD_ACCESS_FAILURE,
                Component.translatable("toast.wishlist.save_failed"), null));
    }

    public static void loadFailed(String name) {
        Minecraft mc = Minecraft.getInstance();
        String display = name == null || name.isBlank() ? "Unknown" : name;
        mc.execute(() -> SystemToast.add(mc.getToasts(),
                SystemToast.SystemToastIds.WORLD_ACCESS_FAILURE,
                Component.translatable("toast.wishlist.load_failed", display), null));
    }

    public static void transferFailed() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> SystemToast.addOrUpdate(mc.getToasts(),
                SystemToast.SystemToastIds.PACK_COPY_FAILURE,
                Component.translatable("toast.wishlist.transfer_failed"), null));
    }
}
