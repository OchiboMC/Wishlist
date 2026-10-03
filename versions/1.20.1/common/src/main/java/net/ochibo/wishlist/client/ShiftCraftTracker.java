package net.ochibo.wishlist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Counts all results of a shift-click, including crafts performed only on the server. */
public final class ShiftCraftTracker {
    private static final List<Pending> pending = new ArrayList<>();

    private ShiftCraftTracker() {}

    public static void mousePressed(Screen screen, Slot slot, int button) {
        if ((button != 0 && button != 1) || !Screen.hasShiftDown()
                || !(screen instanceof AbstractContainerScreen<?> container)) return;
        if (!(slot instanceof ResultSlot) || !slot.hasItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack output = slot.getItem().copy();
        if (find(output) != null) return;
        pending.add(new Pending(output, container.getMenu(), inventoryCount(mc, output)));
    }

    public static void crafted(ItemStack output, long count) {
        Pending session = find(output);
        if (session != null) {
            session.count.crafted(count);
            session.callbackCount += Math.max(0, count);
        } else {
            ClientWorkspaceController.get().recordCraft(output, count);
        }
    }

    public static void tick() {
        if (pending.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            pending.clear();
            return;
        }
        for (Iterator<Pending> iterator = pending.iterator(); iterator.hasNext();) {
            Pending session = iterator.next();
            long inventory = inventoryCount(mc, session.output);
            int stateId = session.menu.getStateId();
            if (mc.player.containerMenu != session.menu
                    && (inventory > session.startingInventoryCount || session.callbackCount > 0)) {
                stateId = session.startingStateId + 1;
            }
            var count = session.count.poll(inventory, stateId);
            if (count.isPresent()) {
                if (count.getAsLong() > 0) ClientWorkspaceController.get().recordCraft(session.output, count.getAsLong());
                iterator.remove();
            }
        }
    }

    public static void clear() {
        pending.clear();
    }

    private static Pending find(ItemStack output) {
        for (Pending session : pending) {
            if (ItemStack.isSameItemSameTags(session.output, output)) return session;
        }
        return null;
    }

    private static long inventoryCount(Minecraft mc, ItemStack output) {
        long count = 0;
        if (mc.player == null) return count;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(stack, output)) count += stack.getCount();
        }
        return count;
    }

    private static final class Pending {
        private final ItemStack output;
        private final AbstractContainerMenu menu;
        private final ShiftCraftCount count;
        private final int startingStateId;
        private final long startingInventoryCount;
        private long callbackCount;

        private Pending(ItemStack output, AbstractContainerMenu menu, long startingInventoryCount) {
            this.output = output;
            this.menu = menu;
            this.startingStateId = menu.getStateId();
            this.startingInventoryCount = startingInventoryCount;
            this.count = new ShiftCraftCount(startingInventoryCount, startingStateId);
        }

    }
}
