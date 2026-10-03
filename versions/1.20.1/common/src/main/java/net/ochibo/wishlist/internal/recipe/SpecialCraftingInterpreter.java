package net.ochibo.wishlist.internal.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.ochibo.wishlist.api.recipe.*;
import net.ochibo.wishlist.spi.recipe.*;

import java.util.*;

/** Dynamic vanilla crafting adapters propose inputs, then delegate output and
 * remainder semantics to the recipe. Unknown mod subclasses are never guessed.
 */
public final class SpecialCraftingInterpreter {
    private static final Map<Integer, List<DyeItem>> DYE_INPUTS = new HashMap<>();
    public boolean accepts(Recipe<?> recipe, ItemStack target) {
        Class<?> type = recipe.getClass();
        if (type == TippedArrowRecipe.class) return target.is(Items.TIPPED_ARROW);
        if (type == FireworkRocketRecipe.class) return target.is(Items.FIREWORK_ROCKET);
        if (type == FireworkStarRecipe.class || type == FireworkStarFadeRecipe.class) return target.is(Items.FIREWORK_STAR);
        if (type == ShulkerBoxColoring.class) return Block.byItem(target.getItem()) instanceof ShulkerBoxBlock;
        if (type == ArmorDyeRecipe.class) return target.getItem() instanceof DyeableLeatherItem && target.getTagElement("display") != null;
        if (type == ShieldDecorationRecipe.class) return target.is(Items.SHIELD) && target.getTagElement("BlockEntityTag") != null;
        if (type == SuspiciousStewRecipe.class) return target.is(Items.SUSPICIOUS_STEW);
        if (type == BannerDuplicateRecipe.class) return target.getItem() instanceof BannerItem && target.getTagElement("BlockEntityTag") != null;
        if (type == BookCloningRecipe.class) return target.is(Items.WRITTEN_BOOK) && target.hasTag() && target.getTag().getInt("generation") > 0;
        if (type == MapCloningRecipe.class) return target.is(Items.FILLED_MAP) && target.hasTag();
        if (type == MapExtendingRecipe.class) return target.is(Items.FILLED_MAP) && target.hasTag()
                && target.getTag().getInt("map_scale_direction") == 1;
        if (type == RepairItemRecipe.class) return target.isDamageableItem() && target.hasTag() && target.getDamageValue() > 0;
        return false;
    }

    public RecipeExpansionDecision expand(RecipeExpansionRequest<?> request) {
        if (!accepts(request.recipe(), request.targetOutput())) return RecipeExpansionDecision.decline();
        CraftingRecipe recipe = (CraftingRecipe) request.recipe();
        ItemStack target = request.targetOutput();
        List<ItemStack> inputs = new ArrayList<>();
        if (recipe.getClass() == TippedArrowRecipe.class) {
            for (int i = 0; i < 9; i++) inputs.add(new ItemStack(Items.ARROW));
            ItemStack potion = new ItemStack(Items.LINGERING_POTION);
            potion.setTag(ItemSemantics.recipeTag(target));
            inputs.set(4, potion);
        } else if (recipe.getClass() == FireworkRocketRecipe.class) {
            inputs.add(new ItemStack(Items.PAPER));
            CompoundTag fireworks = target.getTagElement("Fireworks");
            int flight = fireworks == null ? 1 : fireworks.getByte("Flight");
            if (flight < 1 || flight > 3) return RecipeExpansionDecision.decline();
            for (int i = 0; i < flight; i++) inputs.add(new ItemStack(Items.GUNPOWDER));
            if (fireworks != null) for (Tag explosion : fireworks.getList("Explosions", Tag.TAG_COMPOUND)) {
                ItemStack star = new ItemStack(Items.FIREWORK_STAR);
                star.getOrCreateTag().put("Explosion", explosion.copy());
                inputs.add(star);
            }
        } else if (recipe.getClass() == FireworkStarFadeRecipe.class) {
            CompoundTag explosion = target.getTagElement("Explosion");
            if (explosion == null || !explosion.contains("FadeColors")) return RecipeExpansionDecision.decline();
            ItemStack base = target.copyWithCount(1);
            base.getTagElement("Explosion").remove("FadeColors");
            inputs.add(base);
            if (!addColors(inputs, explosion.getIntArray("FadeColors"))) return RecipeExpansionDecision.decline();
        } else if (recipe.getClass() == FireworkStarRecipe.class) {
            CompoundTag explosion = target.getTagElement("Explosion");
            if (explosion != null && explosion.contains("FadeColors")) return RecipeExpansionDecision.decline();
            inputs.add(new ItemStack(Items.GUNPOWDER));
            if (explosion == null) inputs.add(new ItemStack(Items.WHITE_DYE));
            else {
                if (!addColors(inputs, explosion.getIntArray("Colors"))) return RecipeExpansionDecision.decline();
                if (explosion.getBoolean("Trail")) inputs.add(new ItemStack(Items.DIAMOND));
                if (explosion.getBoolean("Flicker")) inputs.add(new ItemStack(Items.GLOWSTONE_DUST));
                // Find a shape ingredient by querying vanilla assembly, rather than
                // duplicating the shape-to-item table.
                if (explosion.getByte("Type") != 0) {
                    for (var item : BuiltInRegistries.ITEM) {
                        var proposed = new ArrayList<>(inputs);
                        proposed.add(new ItemStack(item));
                        var result = verified(request, recipe, proposed);
                        if (result.kind() == RecipeExpansionDecision.Kind.RESOLVED) return result;
                    }
                    return RecipeExpansionDecision.decline();
                }
            }
        } else if (recipe.getClass() == ShulkerBoxColoring.class) {
            ItemStack base = new ItemStack(Items.SHULKER_BOX);
            base.setTag(ItemSemantics.recipeTag(target));
            for (DyeColor color : DyeColor.values()) {
                var result = verified(request, recipe, List.of(base, new ItemStack(DyeItem.byColor(color))));
                if (result.kind() == RecipeExpansionDecision.Kind.RESOLVED) return result;
            }
            return RecipeExpansionDecision.decline();
        } else if (recipe.getClass() == ArmorDyeRecipe.class) {
            ItemStack base = target.copyWithCount(1);
            ((DyeableLeatherItem) base.getItem()).clearColor(base);
            int color = ((DyeableLeatherItem) target.getItem()).getColor(target);
            List<DyeItem> dyes = DYE_INPUTS.computeIfAbsent(color, wanted -> findDyes(base, wanted));
            if (dyes.isEmpty()) return RecipeExpansionDecision.decline();
            inputs.add(base);
            dyes.forEach(dye -> inputs.add(new ItemStack(dye)));
        } else if (recipe.getClass() == ShieldDecorationRecipe.class) {
            CompoundTag bannerData = target.getTagElement("BlockEntityTag");
            ItemStack base = target.copyWithCount(1);
            base.removeTagKey("BlockEntityTag");
            for (var item : BuiltInRegistries.ITEM) if (item instanceof BannerItem banner && banner.getColor().getId() == bannerData.getInt("Base")) {
                ItemStack pattern = new ItemStack(item);
                CompoundTag data = bannerData.copy();
                data.remove("Base");
                pattern.getOrCreateTag().put("BlockEntityTag", data);
                inputs.add(base);
                inputs.add(pattern);
                break;
            }
        } else if (recipe.getClass() == SuspiciousStewRecipe.class) {
            for (var item : BuiltInRegistries.ITEM) {
                var result = verified(request, recipe, List.of(new ItemStack(Items.BOWL), new ItemStack(Items.RED_MUSHROOM), new ItemStack(Items.BROWN_MUSHROOM), new ItemStack(item)));
                if (result.kind() == RecipeExpansionDecision.Kind.RESOLVED) return result;
            }
            return RecipeExpansionDecision.decline();
        } else if (recipe.getClass() == BannerDuplicateRecipe.class) {
            inputs.add(target.copyWithCount(1));
            inputs.add(new ItemStack(target.getItem()));
        } else if (recipe.getClass() == BookCloningRecipe.class) {
            ItemStack source = target.copyWithCount(1);
            int generation = source.getTag().getInt("generation") - 1;
            if (generation < 0 || generation > 1) return RecipeExpansionDecision.decline();
            source.getTag().putInt("generation", generation);
            inputs.add(source);
            inputs.add(new ItemStack(Items.WRITABLE_BOOK));
        } else if (recipe.getClass() == MapCloningRecipe.class) {
            inputs.add(target.copyWithCount(1));
            inputs.add(new ItemStack(Items.MAP));
        } else if (recipe.getClass() == MapExtendingRecipe.class) {
            for (int i = 0; i < 9; i++) inputs.add(new ItemStack(Items.PAPER));
            ItemStack source = target.copyWithCount(1);
            source.removeTagKey("map_scale_direction");
            inputs.set(4, source);
        } else if (recipe.getClass() == RepairItemRecipe.class) {
            int max = target.getMaxDamage();
            int totalDamage = max + max * 5 / 100 + target.getDamageValue();
            ItemStack first = target.copyWithCount(1);
            ItemStack second = target.copyWithCount(1);
            first.setDamageValue(Math.min(max - 1, totalDamage / 2));
            second.setDamageValue(totalDamage - first.getDamageValue());
            inputs.add(first);
            inputs.add(second);
        }
        return verified(request, recipe, inputs);
    }

    private static List<DyeItem> findDyes(ItemStack base, int wanted) {
        // At most eight dye slots fit beside the armor in a crafting table.
        // Search multisets in stable, shortest-first order using vanilla mixing.
        for (int count = 1; count <= 8; count++) {
            List<DyeItem> found = findDyes(base, wanted, count, 0, new ArrayList<>());
            if (found != null) return found;
        }
        return List.of();
    }

    private static List<DyeItem> findDyes(ItemStack base, int wanted, int remaining, int start, List<DyeItem> selected) {
        if (remaining == 0) {
            ItemStack output = DyeableLeatherItem.dyeArmor(base, selected);
            return ((DyeableLeatherItem) output.getItem()).getColor(output) == wanted ? List.copyOf(selected) : null;
        }
        DyeColor[] palette = DyeColor.values();
        for (int i = start; i < palette.length; i++) {
            selected.add(DyeItem.byColor(palette[i]));
            List<DyeItem> found = findDyes(base, wanted, remaining - 1, i, selected);
            selected.remove(selected.size() - 1);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean addColors(List<ItemStack> inputs, int[] colors) {
        if (colors.length == 0) return false;
        for (int color : colors) {
            DyeColor found = Arrays.stream(DyeColor.values()).filter(dye -> dye.getFireworkColor() == color).findFirst().orElse(null);
            if (found == null) return false;
            inputs.add(new ItemStack(DyeItem.byColor(found)));
        }
        return true;
    }

    private static RecipeExpansionDecision verified(RecipeExpansionRequest<?> request, CraftingRecipe recipe, List<ItemStack> inputs) {
        if (inputs.isEmpty() || inputs.size() > 9) return RecipeExpansionDecision.decline();
        CraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return false; }
        }, 3, 3);
        for (int i = 0; i < inputs.size(); i++) grid.setItem(i, inputs.get(i).copyWithCount(1));
        // Map eligibility depends on saved map scale and exploration status.
        // Validate against the current world rather than inferring it from NBT.
        net.minecraft.world.level.Level level = null;
        if (recipe.getClass() == MapExtendingRecipe.class) {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client == null || client.level == null) return RecipeExpansionDecision.decline();
            level = client.level;
        }
        if (!recipe.matches(grid, level)) return RecipeExpansionDecision.decline();
        ItemStack output = recipe.assemble(grid, request.registryAccess());
        if (!ItemSemantics.matches(request.targetOutput(), output)) return RecipeExpansionDecision.decline();
        var plan = ExpansionPlan.builder(output, output.getCount()).addOutput(OutputOption.of(output, output.getCount(), 1));
        var remainders = recipe.getRemainingItems(grid);
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack input = inputs.get(i);
            ItemStack returned = remainders.get(i);
            if (ItemStack.isSameItemSameTags(input, returned)) {
                plan.addAuxiliary(AuxiliaryItemRequirement.of(List.of(input), 1));
            } else {
                plan.addItem(ItemRequirement.of(List.of(input), 1));
                if (!returned.isEmpty()) plan.addOutput(OutputOption.of(returned, returned.getCount(), 1));
            }
        }
        return RecipeExpansionDecision.resolved(plan.build());
    }
}
