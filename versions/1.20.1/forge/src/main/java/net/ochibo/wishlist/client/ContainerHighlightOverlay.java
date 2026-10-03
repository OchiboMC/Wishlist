package net.ochibo.wishlist.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.ochibo.wishlist.core.material.ContainerHighlightPlan;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Highlights all required Wishlist item materials in external container slots. */
public final class ContainerHighlightOverlay {
    private static final int ENOUGH_COLOR = 0x904444CC;
    private static final int SHORT_COLOR = 0x90CC4444;
    private static final long PULSE_MILLIS = 1200;

    private final KeyMapping key;
    private boolean enabled;
    private boolean keyHeld;

    public ContainerHighlightOverlay(KeyMapping key) {
        this.key = key;
    }

    @SubscribeEvent
    public void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!isExternalContainer(event.getScreen()) || !matches(event.getKeyCode(), event.getScanCode())) return;
        if (!keyHeld) enabled = !enabled;
        keyHeld = true;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (!matches(event.getKeyCode(), event.getScanCode())) return;
        keyHeld = false;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void screenOpening(ScreenEvent.Opening event) {
        enabled = false;
        keyHeld = false;
    }

    @SubscribeEvent
    public void render(ContainerScreenEvent.Render.Foreground event) {
        if (!enabled || !isExternalContainer(event.getContainerScreen())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        List<Slot> containerSlots = screen.getMenu().slots.stream()
                .filter(slot -> slot.isActive() && slot.container != mc.player.getInventory()).toList();
        if (containerSlots.isEmpty()) return;

        List<ContainerHighlightPlan.Stack> stacks = new ArrayList<>();
        for (Slot slot : containerSlots) {
            var item = slot.getItem();
            if (!item.isEmpty()) stacks.add(new ContainerHighlightPlan.Stack(StackResolver.id(item), item.getCount()));
        }
        var targets = ContainerHighlightPlan.targetsForRows(
                ClientWorkspaceController.get().materialCalculation().rows());
        var plan = ContainerHighlightPlan.evaluate(targets, stacks, inventoryStacks(mc.player.getInventory()));
        int enough = WishlistClientConfig.highlightAnimated()
                ? pulse(ENOUGH_COLOR, System.currentTimeMillis()) : ENOUGH_COLOR;
        int shortfall = WishlistClientConfig.highlightAnimated()
                ? pulse(SHORT_COLOR, System.currentTimeMillis()) : SHORT_COLOR;
        for (Slot slot : containerSlots) {
            var item = slot.getItem();
            if (item.isEmpty()) continue;
            ContainerHighlightPlan.Status status = plan.statusFor(StackResolver.id(item));
            if (status == ContainerHighlightPlan.Status.NONE) continue;
            int x = slot.x;
            int y = slot.y;
            event.getGuiGraphics().pose().pushPose();
            // Slot items and count labels reach z=250 and z=300; tooltips use z=400.
            event.getGuiGraphics().pose().translate(0, 0, 350);
            event.getGuiGraphics().fill(x - 1, y - 1, x + 17, y + 17,
                    status == ContainerHighlightPlan.Status.ENOUGH ? enough : shortfall);
            event.getGuiGraphics().drawString(mc.font,
                    status == ContainerHighlightPlan.Status.ENOUGH ? "✓" : "!",
                    x + 1, y + 1, 0xFFFFFFFF, true);
            event.getGuiGraphics().pose().popPose();
        }
    }

    @SubscribeEvent
    public void tooltip(RenderTooltipEvent.GatherComponents event) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || !isExternalContainer(mc.screen)
                || event.getItemStack().isEmpty()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || !hovered.isActive() || hovered.container == mc.player.getInventory()
                || hovered.getItem().isEmpty()
                || !net.minecraft.world.item.ItemStack.isSameItemSameTags(hovered.getItem(), event.getItemStack())) return;

        List<Slot> containerSlots = screen.getMenu().slots.stream()
                .filter(slot -> slot.isActive() && slot.container != mc.player.getInventory()).toList();
        List<ContainerHighlightPlan.Stack> containerStacks = new ArrayList<>();
        for (Slot slot : containerSlots) {
            if (!slot.getItem().isEmpty()) containerStacks.add(new ContainerHighlightPlan.Stack(
                    StackResolver.id(slot.getItem()), slot.getItem().getCount()));
        }
        var targets = ContainerHighlightPlan.targetsForRows(
                ClientWorkspaceController.get().materialCalculation().rows());
        List<ContainerHighlightPlan.Stack> inventoryStacks = inventoryStacks(mc.player.getInventory());
        var plan = ContainerHighlightPlan.evaluate(targets, containerStacks, inventoryStacks);
        ContainerHighlightPlan.Target target = plan.targetFor(StackResolver.id(hovered.getItem()));
        if (target == null) return;

        long inChest = ContainerHighlightPlan.countFor(target, containerStacks);
        long inInventory = ContainerHighlightPlan.countFor(target, inventoryStacks);
        Component count = Component.translatable("tooltip.wishlist.container_count",
                inChest+inInventory , target.required()).withStyle(
                inChest + inInventory >= target.required() ? ChatFormatting.WHITE : ChatFormatting.RED);
        event.getTooltipElements().add(Either.left(count));
    }

    private static List<ContainerHighlightPlan.Stack> inventoryStacks(Inventory inventory) {
        List<ContainerHighlightPlan.Stack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var item = inventory.getItem(i);
            if (!item.isEmpty()) stacks.add(new ContainerHighlightPlan.Stack(
                    StackResolver.id(item), item.getCount()));
        }
        return stacks;
    }

    private boolean matches(int keyCode, int scanCode) {
        return key.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    private static boolean isExternalContainer(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen);
    }

    static int pulse(int argb, long millis) {
        double wave = (1.0 + Math.sin(millis * (2.0 * Math.PI / PULSE_MILLIS))) * 0.5;
        int baseAlpha = (argb >>> 24) & 0xFF;
        int alpha = (int) Math.round(baseAlpha * (0.2 + 0.8 * wave));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
