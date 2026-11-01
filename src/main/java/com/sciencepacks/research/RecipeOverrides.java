package com.sciencepacks.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.sciencepacks.SciencePacks;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.*;

/**
 * 联动配方覆盖：{@code data/<namespace>/sciencepacks/recipe_overrides/*.json}。
 *
 * <p>当声明条件的模组存在（或不存在）时，移除本模组/其他模组的某些配方，并替换成新配方。
 * 典型用法：装了 Create 时，科技包配方改用「安山合金」而不是铁锭。</p>
 *
 * <pre>{@code
 * {
 *   "mods": ["create"],
 *   "remove": ["sciencepacks:pack_green"],
 *   "recipes": [
 *     {
 *       "id": "sciencepacks:pack_green",
 *       "recipe": { "type": "minecraft:crafting_shapeless", "category": "misc",
 *                   "ingredients": [...], "result": { "id": "sciencepacks:pack_green", "count": 2 } }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>应用时机：服务端启动完成（{@code ServerStartedEvent}）时读取并一次性替换配方表；
 * 改动覆盖文件后需要重启服务器（{@code /reload} 会重新读取定义，但配方表在下次启动时生效）。</p>
 */
public final class RecipeOverrides extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "sciencepacks/recipe_overrides";
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile List<Entry> overrides = List.of();
    private static volatile long version;
    private static volatile long appliedVersion = -1;

    /** 一条替换：把 id 位置的配方换成 recipe。 */
    public record Replacement(ResourceLocation id, JsonObject recipe) {
    }

    /** 一个覆盖文件：条件模组 + 移除列表 + 替换配方。 */
    public record Entry(List<String> mods, List<ResourceLocation> remove, List<Replacement> replacements) {
    }

    public RecipeOverrides() {
        super(GSON, DIRECTORY);
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new RecipeOverrides());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            try {
                loaded.add(parse(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (Exception exception) {
                SciencePacks.LOGGER.error("Failed to parse recipe override {}", entry.getKey(), exception);
            }
        }
        overrides = List.copyOf(loaded);
        version++;
        if (!loaded.isEmpty()) {
            SciencePacks.LOGGER.info("Loaded {} recipe override file(s)", loaded.size());
        }
    }

    private static Entry parse(ResourceLocation fileId, JsonObject json) {
        List<String> mods = new ArrayList<>();
        for (String value : readStrings(json, "mods")) {
            if (!mods.contains(value)) {
                mods.add(value);
            }
        }
        List<ResourceLocation> remove = new ArrayList<>();
        if (json.has("remove")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "remove")) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id != null) {
                    remove.add(id);
                }
            }
        }
        List<Replacement> replacements = new ArrayList<>();
        if (json.has("recipes")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "recipes")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(entry, "id", ""));
                JsonObject recipe = entry.has("recipe") && entry.get("recipe").isJsonObject()
                        ? entry.getAsJsonObject("recipe") : null;
                if (id == null || recipe == null) {
                    SciencePacks.LOGGER.warn("Recipe override {} has an entry without id/recipe, skipped", fileId);
                    continue;
                }
                replacements.add(new Replacement(id, recipe));
            }
        }
        return new Entry(List.copyOf(mods), List.copyOf(remove), List.copyOf(replacements));
    }

    private static List<String> readStrings(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        if (!json.has(key)) {
            return result;
        }
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    /**
     * 应用配方覆盖（幂等：每次数据包重载后只应用一次）。
     *
     * @param server 当前服务器；为 null 或没有覆盖文件时直接返回
     */
    public static void apply(MinecraftServer server) {
        if (server == null || overrides.isEmpty() || appliedVersion == version) {
            return;
        }
        appliedVersion = version;

        RecipeManager manager = server.getRecipeManager();
        List<RecipeHolder<?>> recipes = new ArrayList<>(manager.getRecipes());
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        int removed = 0;
        int added = 0;

        for (Entry entry : overrides) {
            if (!ModConditions.allMatch(entry.mods())) {
                continue;
            }
            Set<ResourceLocation> targets = new HashSet<>(entry.remove());
            if (!targets.isEmpty()) {
                int before = recipes.size();
                recipes.removeIf(holder -> targets.contains(holder.id()));
                removed += before - recipes.size();
            }
            for (Replacement replacement : entry.replacements()) {
                DataResult<Recipe<?>> result = Recipe.CODEC.parse(ops, replacement.recipe());
                Recipe<?> recipe = result.result().orElse(null);
                if (recipe == null) {
                    SciencePacks.LOGGER.error("Invalid recipe in override {}: {}", replacement.id(),
                            result.error().map(DataResult.Error::message).orElse("unknown error"));
                    continue;
                }
                recipes.removeIf(holder -> holder.id().equals(replacement.id()));
                recipes.add(new RecipeHolder<>(replacement.id(), recipe));
                added++;
            }
        }

        if (removed == 0 && added == 0) {
            return;
        }
        manager.replaceRecipes(recipes);
        SciencePacks.LOGGER.info("Applied recipe overrides: removed {}, added {}", removed, added);
    }
}
