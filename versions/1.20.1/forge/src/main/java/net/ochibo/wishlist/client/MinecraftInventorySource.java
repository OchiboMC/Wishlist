package net.ochibo.wishlist.client;

import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.ochibo.wishlist.client.integration.MaterialAdapterRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MinecraftInventorySource {
    public InventorySnapshot snapshot(LocalPlayer player) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        count(inv.items, counts);
        count(inv.armor, counts);
        count(inv.offhand, counts);
        MaterialAdapterRegistry.addInventory(player, counts);
        return InventorySnapshot.of(counts);
    }

    private static void count(Iterable<ItemStack> stacks, Map<String, Long> counts) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            counts.merge(StackResolver.id(stack), (long) stack.getCount(), Math::addExact);
            stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).ifPresent(handler -> {
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    var fluid = handler.getFluidInTank(tank);
                    if (!fluid.isEmpty()) counts.merge(StackResolver.fluidId(fluid),
                            Math.multiplyExact((long) fluid.getAmount(), stack.getCount()), Math::addExact);
                }
            });
        }
    }
}
