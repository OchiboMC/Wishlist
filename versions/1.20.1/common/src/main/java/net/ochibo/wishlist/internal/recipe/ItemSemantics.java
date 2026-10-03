package net.ochibo.wishlist.internal.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.TreeSet;

/** Shared material identity for native recipes and recipe viewers.
 * Unknown mod data is retained; only presentation metadata is discarded.
 */
public final class ItemSemantics {
    private ItemSemantics() {}

    public static CompoundTag recipeTag(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag().copy();
        tag.remove("HideFlags");
        if (tag.contains("display", Tag.TAG_COMPOUND)) {
            CompoundTag display = tag.getCompound("display");
            display.remove("Name");
            display.remove("Lore");
            if (display.isEmpty()) tag.remove("display");
        }
        return tag.isEmpty() ? null : tag;
    }

    public static String tagKey(ItemStack stack) {
        CompoundTag tag = recipeTag(stack);
        return tag == null ? null : canonical(tag);
    }

    /** Compound order is irrelevant; list order can carry recipe semantics. */
    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var parts = new java.util.ArrayList<String>();
            for (String key : new TreeSet<>(compound.getAllKeys())) {
                String encodedKey = key.matches("[A-Za-z0-9._+-]+") ? key : net.minecraft.nbt.StringTag.quoteAndEscape(key);
                parts.add(encodedKey + ":" + canonical(compound.get(key)));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (tag instanceof ListTag list) {
            var parts = new java.util.ArrayList<String>();
            for (Tag entry : list) parts.add(canonical(entry));
            return "[" + String.join(",", parts) + "]";
        }
        return tag.toString();
    }

    public static boolean matches(ItemStack requested, ItemStack actual) {
        if (requested.isEmpty() || actual.isEmpty() || requested.getItem() != actual.getItem()) return false;
        CompoundTag wanted = recipeTag(requested);
        return wanted == null || wanted.equals(recipeTag(actual));
    }
}
