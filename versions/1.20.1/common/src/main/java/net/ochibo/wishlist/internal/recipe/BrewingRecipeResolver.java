package net.ochibo.wishlist.internal.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.model.ResourceIdentity;
import net.ochibo.wishlist.core.recipe.RecipeResolver;
import net.ochibo.wishlist.core.utils.StackResolver;

import java.util.*;

/** Derives an acyclic shortest brewing route from Minecraft's actual mix function.
 * No potion-effect or modifier table is duplicated here. Water is the terminal.
 * Uses the same three-bottle batch as EMI: three inputs and one reagent.
 */
public final class BrewingRecipeResolver implements RecipeResolver {
    private Map<RecipeKey, ResolvedRecipe> recipes;

    private void initialize() {
        if (recipes != null) return;
        var found = new LinkedHashMap<RecipeKey, ResolvedRecipe>();
        List<ItemStack> reagents = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (PotionBrewing.isIngredient(stack)) reagents.add(stack);
        }
        reagents.sort(Comparator.comparing(StackResolver::id));
        var pending = new ArrayDeque<ItemStack>();
        var visited = new HashSet<String>();
        ItemStack water = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        visited.add(StackResolver.id(water));
        pending.add(water);
        while (!pending.isEmpty()) {
            ItemStack input = pending.remove();
            for (ItemStack reagent : reagents) {
                if (!PotionBrewing.hasMix(input, reagent)) continue;
                ItemStack output = PotionBrewing.mix(reagent, input);
                String outputId = StackResolver.id(output);
                if (!visited.add(outputId)) continue;
                String container = BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
                String potion = BuiltInRegistries.POTION.getKey(PotionUtils.getPotion(output)).toString().replace(':', '/');
                RecipeKey key = new RecipeKey("wishlist:brewing/" + container + "/" + potion);
                found.put(key, ResolvedRecipe.supported(key, outputId, 3,
                        List.of(choice(input, 3), choice(reagent, 1))));
                pending.add(output);
            }
        }
        recipes = Collections.unmodifiableMap(found);
    }

    private static IngredientChoice choice(ItemStack stack, long amount) {
        String id = StackResolver.id(stack);
        return new IngredientChoice(id, List.of(id), amount);
    }

    @Override public Optional<ResolvedRecipe> findByKey(RecipeKey key) {
        initialize();
        return Optional.ofNullable(recipes.get(key));
    }

    @Override public Optional<ResolvedRecipe> findByKey(RecipeKey key, String targetItemId) {
        return findByKey(key).filter(recipe -> targetItemId == null || targetItemId.isBlank()
                || ResourceIdentity.matches(targetItemId, recipe.outputItemId()));
    }

    @Override public List<ResolvedRecipe> findForOutput(String itemId) {
        initialize();
        return recipes.values().stream().filter(recipe -> ResourceIdentity.matches(itemId, recipe.outputItemId())).toList();
    }

    @Override public boolean isAuthoritativeForOutput(String itemId) {
        ItemStack target = StackResolver.stack(itemId);
        if (target.isEmpty()) return false;
        // Custom effects are deliberately outside the vanilla mix graph.
        if (target.hasTag() && target.getTag().contains("CustomPotionEffects")) return false;
        String water = StackResolver.id(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        return itemId.equals(water) || !findForOutput(itemId).isEmpty();
    }
}
