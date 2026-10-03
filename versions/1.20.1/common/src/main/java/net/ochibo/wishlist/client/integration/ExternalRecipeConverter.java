package net.ochibo.wishlist.client.integration;

import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.ResolvedRecipe;
import net.ochibo.wishlist.core.recipe.DeterministicRecipePolicy;
import net.ochibo.wishlist.core.recipe.RecipeIngredientAggregator;
import net.ochibo.wishlist.core.utils.StackResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Converts viewer-specific recipe objects only when deterministic item semantics can be proven. */
public final class ExternalRecipeConverter {
    private ExternalRecipeConverter() {}

    public static Optional<ResolvedRecipe> convert(Object recipeObject) {
        if (recipeObject == null) return Optional.empty();
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        if (recipeObject instanceof Recipe<?> recipe) return Optional.of(controller.resolveMinecraftRecipe(recipe));

        Optional<String> id = recipeId(recipeObject);
        Optional<ResolvedRecipe> fromManager = Optional.empty();
        if (id.isPresent()) {
            fromManager = controller.resolver().findByKey(new RecipeKey(id.get()));
            if (fromManager.isPresent()) return fromManager;
        }

        Optional<ResolvedRecipe> converted = convertEmiLike(recipeObject, id.orElse(null));
        if (converted.filter(ResolvedRecipe::supported).isPresent()) return converted;
        return fromManager.isPresent() ? fromManager : converted;
    }

    private static Optional<ResolvedRecipe> convertEmiLike(Object recipeObject, String id) {
        if (id == null) return Optional.empty();
        try {
            List<?> catalysts = list(call(recipeObject, "getCatalysts"));
            if (!catalysts.isEmpty()) return Optional.of(ResolvedRecipe.unsupported(new RecipeKey(id), outputId(recipeObject), 1, "catalysts are not modeled"));

            List<?> outputs = list(call(recipeObject, "getOutputs"));
            if (outputs.size() != 1) return Optional.of(ResolvedRecipe.unsupported(new RecipeKey(id), outputId(recipeObject), 1, "multiple outputs are not modeled"));
            StackInfo out = ingredientInfo(outputs.get(0));
            if (!out.deterministic) {
                return Optional.of(ResolvedRecipe.unsupported(new RecipeKey(id), outputId(recipeObject), 1, "probabilistic output is not modeled"));
            }
            if (out.candidates.size() != 1 || out.amount <= 0) return Optional.empty();

            List<IngredientChoice> inputs = new ArrayList<>();
            for (Object input : list(call(recipeObject, "getInputs"))) {
                StackInfo info = ingredientInfo(input);
                if (!info.deterministic) {
                    return Optional.of(ResolvedRecipe.unsupported(new RecipeKey(id), out.candidates.get(0), out.amount, "probabilistic input is not modeled"));
                }
                if (info.candidates.isEmpty() || info.amount <= 0) return Optional.empty();
                inputs.add(new IngredientChoice(info.candidates.get(0), info.candidates, info.amount));
            }
            return Optional.of(ResolvedRecipe.supported(new RecipeKey(id), out.candidates.get(0), out.amount,
                    RecipeIngredientAggregator.aggregate(inputs)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private record StackInfo(List<String> candidates, long amount, boolean deterministic) {}

    private static StackInfo ingredientInfo(Object ingredient) throws ReflectiveOperationException {
        long amount = number(callIfPresent(ingredient, "getAmount")).map(Number::longValue).orElse(1L);
        List<?> stacks = list(call(ingredient, "getEmiStacks"));
        List<String> candidates = new ArrayList<>();
        List<Double> chances = new ArrayList<>();
        long detectedAmount = amount;
        boolean allItems = true;
        for (Object stack : stacks) {
            double chance = number(callIfPresent(stack, "getChance")).map(Number::doubleValue).orElse(1.0D);
            chances.add(chance);
            Object itemStackObject = callIfPresent(stack, "getItemStack");
            if (!(itemStackObject instanceof ItemStack itemStack) || itemStack.isEmpty()) {
                allItems = false;
                continue;
            }
            candidates.add(StackResolver.id(itemStack));
            Optional<Number> stackAmount = number(callIfPresent(stack, "getAmount"));
            if (stacks.size() == 1 && stackAmount.isPresent()) detectedAmount = stackAmount.get().longValue();
        }
        boolean deterministic = DeterministicRecipePolicy.allChancesCertain(chances);
        return new StackInfo(allItems ? candidates.stream().distinct().sorted().toList() : List.of(),
                Math.max(1, detectedAmount), deterministic);
    }

    private static String outputId(Object recipeObject) {
        try {
            List<?> outputs = list(call(recipeObject, "getOutputs"));
            if (outputs.isEmpty()) return "minecraft:air";
            StackInfo info = ingredientInfo(outputs.get(0));
            return info.candidates.isEmpty() ? "minecraft:air" : info.candidates.get(0);
        } catch (ReflectiveOperationException e) {
            return "minecraft:air";
        }
    }

    private static Optional<String> recipeId(Object recipeObject) {
        try {
            Object id = callIfPresent(recipeObject, "getId");
            if (id == null) return Optional.empty();
            String value = id.toString();
            return ResourceLocation.tryParse(value) == null ? Optional.empty() : Optional.of(value);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static Object callIfPresent(Object target, String method) throws ReflectiveOperationException {
        try { return call(target, method); }
        catch (NoSuchMethodException e) { return null; }
    }

    private static List<?> list(Object value) {
        if (value instanceof List<?> l) return l;
        if (value instanceof Collection<?> c) return List.copyOf(c);
        return List.of();
    }

    private static Optional<Number> number(Object value) {
        return value instanceof Number n ? Optional.of(n) : Optional.empty();
    }
}
