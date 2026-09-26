package net.ochibo.wishlist.client.ingredients;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ochibo.wishlist.core.ingredients.IngredientsDefinition;
import net.ochibo.wishlist.core.ingredients.IngredientsResolver;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MinecraftIngredientsLoader {
    private static final String PATH = "tags/items/wishlist/ingredients.json";
    private final IngredientsResolver resolver = new IngredientsResolver();

    public Set<String> load(ResourceManager rawDataResourcesOrNull) {
        List<IngredientsDefinition> definitions = new ArrayList<>();
        definitions.add(new IngredientsDefinition("wishlist", Integer.MIN_VALUE, false,
                List.of("#minecraft:planks", "minecraft:stick", "minecraft:cobblestone", "#forge:ingots"), List.of()));

        if (rawDataResourcesOrNull != null) {
            Map<ResourceLocation, List<Resource>> stacks = rawDataResourcesOrNull.listResourceStacks(
                    "tags/items/wishlist",
                    id -> id.getPath().equals(PATH));
            for (var entry : stacks.entrySet()) {
                int order = 0;
                for (Resource resource : entry.getValue()) {
                    try (BufferedReader reader = resource.openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        definitions.add(new IngredientsDefinition(
                                entry.getKey().getNamespace(), order++,
                                json.has("replace") && json.get("replace").getAsBoolean(),
                                readEntries(json.getAsJsonArray("values")),
                                readEntries(json.getAsJsonArray("remove"))));
                    } catch (IOException | RuntimeException ignored) {
                        // A malformed optional extension must not prevent Wishlist from loading.
                    }
                }
            }
        } else {
            // Remote servers do not send the original JSON resource-stack order to clients.
            // We can still recognize the already-resolved pseudo-tags, but intentionally do not
            // invent cross-namespace replace ordering that is unavailable on the client.
            BuiltInRegistries.ITEM.getTagNames()
                    .filter(tag -> tag.location().getPath().equals("wishlist/ingredients"))
                    .forEach(tag -> definitions.add(new IngredientsDefinition(
                            tag.location().getNamespace(), 0, false,
                            List.of("#" + tag.location()), List.of())));
        }
        return resolver.resolve(definitions, buildTagMap());
    }

    private static List<String> readEntries(JsonArray array) {
        if (array == null) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) result.add(element.getAsString());
            else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                result.add(element.getAsJsonObject().get("id").getAsString());
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Set<String>> buildTagMap() {
        Map<String, Set<String>> tags = new LinkedHashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (Holder<Item> holder : pair.getSecond()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
                if (id != null) values.add(id.toString());
            }
            tags.put(pair.getFirst().location().toString(), Set.copyOf(values));
        });
        return Map.copyOf(tags);
    }
}
