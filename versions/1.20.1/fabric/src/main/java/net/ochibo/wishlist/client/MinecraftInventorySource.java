package net.ochibo.wishlist.client;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.ochibo.wishlist.client.integration.MaterialAdapterRegistry;
import net.ochibo.wishlist.core.inventory.InventorySnapshot;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.utils.StackResolver;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MinecraftInventorySource {
    public InventorySnapshot snapshot(LocalPlayer player) {
        Map<String, Long> counts = new LinkedHashMap<>();
        var inventory = player.getInventory();
        count(inventory.items, counts);
        count(inventory.armor, counts);
        count(inventory.offhand, counts);
        MaterialAdapterRegistry.addInventory(player, counts);
        return InventorySnapshot.of(counts);
    }

    private static void count(Iterable<ItemStack> stacks, Map<String, Long> counts) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            counts.merge(StackResolver.id(stack), (long) stack.getCount(), Math::addExact);
            var storage = FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
            if (storage == null) continue;
            for (var view : storage.nonEmptyViews()) {
                var variant = view.getResource();
                String key = ResourceIdentity.fluid(
                        StackResolver.idOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(variant.getFluid())),
                        variant.hasNbt() ? variant.getNbt().toString() : null);
                long milliBuckets = view.getAmount() / (FluidConstants.BUCKET / 1000L);
                if (milliBuckets > 0) counts.merge(key,
                        Math.multiplyExact(milliBuckets, stack.getCount()), Math::addExact);
            }
        }
    }
}
