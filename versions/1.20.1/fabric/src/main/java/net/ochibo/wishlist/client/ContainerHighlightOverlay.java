package net.ochibo.wishlist.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.ochibo.wishlist.core.material.ContainerHighlightPlan;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.ochibo.wishlist.client.mixin.ContainerScreenAccessor;
import net.ochibo.wishlist.client.config.WishlistClientConfig;

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
    private AbstractContainerScreen<?> activeScreen;
    private ContainerHighlightPlan activePlan;
    private int enoughPulse;
    private int shortPulse;

    public ContainerHighlightOverlay(KeyMapping key) {
        this.key = key;
    }

    public void register() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> addTooltip(stack, lines));
    }

    public boolean keyPressed(Screen screen, int keyCode, int scanCode) {
        if (!isExternalContainer(screen) || !matches(keyCode, scanCode)) return false;
        if (!keyHeld) enabled = !enabled;
        keyHeld = true;
        return true;
    }

    public boolean keyReleased(int keyCode, int scanCode) {
        if (!matches(keyCode, scanCode)) return false;
        keyHeld = false;
        return true;
    }

    public void screenOpening() {
        enabled = false;
        keyHeld = false;
        activeScreen = null;
        activePlan = null;
    }

    public void beginRender(AbstractContainerScreen<?> screen) {
        activeScreen = null;
        activePlan = null;
        if (!enabled || !isExternalContainer(screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
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
        activeScreen = screen;
        activePlan = ContainerHighlightPlan.evaluate(targets, stacks, inventoryStacks(mc.player.getInventory()));
        enoughPulse = WishlistClientConfig.highlightAnimated()
                ? pulse(ENOUGH_COLOR, System.currentTimeMillis()) : ENOUGH_COLOR;
        shortPulse = WishlistClientConfig.highlightAnimated()
                ? pulse(SHORT_COLOR, System.currentTimeMillis()) : SHORT_COLOR;
    }

    public void drawSlotOverlay(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot) {
        if (screen != activeScreen || activePlan == null || !slot.isActive() || slot.getItem().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || slot.container == mc.player.getInventory()) return;
        ContainerHighlightPlan.Status status = activePlan.statusFor(StackResolver.id(slot.getItem()));
        if (status == ContainerHighlightPlan.Status.NONE) return;
        // renderSlot has drawn the item and its count; the screen tooltip renders afterward.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);
        graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17,
                status == ContainerHighlightPlan.Status.ENOUGH ? enoughPulse : shortPulse);
        graphics.drawString(mc.font, status == ContainerHighlightPlan.Status.ENOUGH ? "✓" : "!",
                slot.x + 1, slot.y + 1, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private void addTooltip(net.minecraft.world.item.ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || !isExternalContainer(mc.screen)
                || stack.isEmpty()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        Slot hovered = ((ContainerScreenAccessor) screen).wishlist$getHoveredSlot();
        if (hovered == null || !hovered.isActive() || hovered.container == mc.player.getInventory()
                || hovered.getItem().isEmpty()
                || !net.minecraft.world.item.ItemStack.isSameItemSameTags(hovered.getItem(), stack)) return;

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
        lines.add(count);
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
        return key.matches(keyCode, scanCode);
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
