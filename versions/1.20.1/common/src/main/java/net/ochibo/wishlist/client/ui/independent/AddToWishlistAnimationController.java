package net.ochibo.wishlist.client.ui.independent;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.client.WishlistContainerButton;
import net.ochibo.wishlist.client.ui.UiText;

import java.util.ArrayList;
import java.util.List;

/** Item icons flying into the visible Wishlist button after successful additions. */
public final class AddToWishlistAnimationController {
    private static final int MAX_FLIGHTS = 32;
    private record AnimationStack(ItemStack stack, ItemFlightMotion motion) {}
    private static final List<AnimationStack> animationStacks = new ArrayList<>();
    private static final AddToWishlistAnimationIUI UI = new AddToWishlistAnimationIUI();

    private AddToWishlistAnimationController() {}

    /** Called only after an item/recipe has actually been added to a Wishlist. */
    public static void onItemAdded(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        var window = mc.getWindow();
        if (window.getScreenWidth() <= 0 || window.getScreenHeight() <= 0) return;
        int x = (int) (mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int y = (int) (mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        addStack(UiText.itemStack(itemId), x, y);
    }

    /** Queue a copied icon at the supplied center point in GUI coordinates, on the client thread. */
    public static void addStack(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        ItemStack icon = stack.copy();
        icon.setCount(1);
        if (animationStacks.size() >= MAX_FLIGHTS) animationStacks.remove(0);
        animationStacks.add(new AnimationStack(icon, new ItemFlightMotion(x, y,
                window.getGuiScaledWidth(), window.getGuiScaledHeight(), Util.getMillis())));
        IndependentUIManager.getInstance().register(UI);
        UI.show();
    }

    public static void clear() {
        animationStacks.clear();
        UI.hide();
    }

    public static final class AddToWishlistAnimationIUI extends IndependentUI {
        private AddToWishlistAnimationIUI() { hide(); }

        @Override
        protected void render(GuiGraphics graphics, Screen currentScreen, int mouseX, int mouseY, float partialTick) {
            long now = Util.getMillis();
            var position = WishlistContainerButton.getPosition(currentScreen);
            var window = Minecraft.getInstance().getWindow();
            for (AnimationStack flight : List.copyOf(animationStacks)) {
                if (flight.motion.expired(now)) {
                    animationStacks.remove(flight);
                    continue;
                }
                if (position == null) continue;
                var frame = flight.motion.sample(now, window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                        position.x() + position.width() / 2.0, position.y() + position.height() / 2.0);
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(frame.x(), frame.y(), 100);
                    graphics.pose().scale((float) frame.scale(), (float) frame.scale(), (float) frame.scale());
                    graphics.renderItem(flight.stack, -8, -8);
                } finally {
                    graphics.pose().popPose();
                }
            }
            if (animationStacks.isEmpty()) hide();
        }
    }
}
