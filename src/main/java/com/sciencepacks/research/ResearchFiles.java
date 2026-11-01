package com.sciencepacks.research;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sciencepacks.SciencePacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置文件里的研究树编辑。
 *
 * <p>加载顺序（后者覆盖前者）：</p>
 * <ol>
 *     <li>数据包 {@code data/<ns>/sciencepacks/technologies/*.json}</li>
 *     <li>全局配置 {@code config/sciencepacks-research.json}（对所有存档生效）</li>
 *     <li>单世界配置 {@code <存档>/serverconfig/sciencepacks-research.json}</li>
 *     <li>脚本 API（KubeJS 等，见 {@code SciencePacksApi}）</li>
 * </ol>
 *
 * <p>文件格式：</p>
 * <pre>{@code
 * {
 *   "technologies": [
 *     { "id": "sciencepacks:my_tech", "name": "我的研究", "icon": "minecraft:book",
 *       "description": "说明", "x": 0, "y": 6, "rounds": 5, "researchTime": 40,
 *       "prerequisites": ["sciencepacks:stone_age"],
 *       "cost": [{ "item": "sciencepacks:pack_red", "count": 2 }],
 *       "unlocks": ["minecraft:iron_pickaxe"],
 *       "structures": ["nether_portal"],
 *       "triggers": ["minecraft:iron_ingot*4"] }
 *   ],
 *   "remove": ["sciencepacks:endgame"]
 * }
 * }</pre>
 *
 * <p>{@code id} 与数据包里的 id 相同即为「覆盖」，不同即为「新增」；
 * {@code remove} 里的 id 会从研究树里隐藏。</p>
 */
public final class ResearchFiles {

    private static final String FILE_NAME = "sciencepacks-research.json";
    private static final String SERVERCONFIG = "serverconfig";

    private ResearchFiles() {
    }

    /** 全局配置：所有存档共用。 */
    public static Path globalFile() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** 单世界配置：只在该存档生效。 */
    public static Path worldFile(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.getWorldPath(LevelResource.ROOT).resolve(SERVERCONFIG).resolve(FILE_NAME);
    }

    /** 读取全局 + 单世界配置并应用到科技库（覆盖数据包）。 */
    public static void load(MinecraftServer server) {
        ensureGlobalSample();
        Map<ResourceLocation, Technology> overrides = new LinkedHashMap<>();
        Set<ResourceLocation> remove = new LinkedHashSet<>();
        readFile(globalFile(), overrides, remove);
        Path world = worldFile(server);
        if (world != null) {
            readFile(world, overrides, remove);
        }
        TechLibrary.setConfig(overrides.values(), remove);
        SciencePacks.LOGGER.info("[sciencepacks] 配置文件研究树：覆盖 {} 项，隐藏 {} 项",
                overrides.size(), remove.size());
    }

    /** 首次启动时生成一份空模板（不覆盖任何研究），方便用户知道有这个文件。 */
    public static void ensureGlobalSample() {
        Path file = globalFile();
        String sample = """
                {
                  "_说明": [
                    "研究树配置文件。默认留空 = 全部沿用数据包里的研究，不覆盖任何内容。",
                    "technologies：按 id 覆盖数据包里的研究，字段与 data/sciencepacks/sciencepacks/technologies/*.json 相同。",
                    "remove：填入研究 id，可以把某个研究从游戏里藏起来。"
                  ],
                  "technologies": [],
                  "remove": []
                }
                """;
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                String old = Files.readString(file, StandardCharsets.UTF_8);
                // 旧版本会把一段「示例覆盖」真的写进配置（stone_age 被固定改掉，导致怎么改都不生效）。
                // 识别到这段旧示例就替换成空模板，避免它继续压住数据包里的研究。
                if (!old.contains("示例：把触发器改成挖 32 个圆石即可解锁。")) {
                    return;
                }
                SciencePacks.LOGGER.info("[sciencepacks] 检测到旧版示例覆盖，已替换为空模板：{}", file);
            }
            Files.writeString(file, sample, StandardCharsets.UTF_8);
            SciencePacks.LOGGER.info("[sciencepacks] 已生成研究树配置文件模板：{}", file);
        } catch (Exception e) {
            SciencePacks.LOGGER.warn("[sciencepacks] 生成研究树配置文件失败：{}", e.toString());
        }
    }

    private static void readFile(Path file, Map<ResourceLocation, Technology> overrides,
                                 Set<ResourceLocation> remove) {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("remove")) {
                for (JsonElement element : json.getAsJsonArray("remove")) {
                    ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                    if (id != null) {
                        remove.add(id);
                    }
                }
            }
            if (json.has("technologies")) {
                JsonArray array = json.getAsJsonArray("technologies");
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    if (!object.has("id")) {
                        SciencePacks.LOGGER.warn("[sciencepacks] 研究树配置缺少 id，已跳过：{}", file);
                        continue;
                    }
                    ResourceLocation id = ResourceLocation.tryParse(object.get("id").getAsString());
                    if (id == null) {
                        continue;
                    }
                    try {
                        overrides.put(id, Technology.fromJson(id, object));
                    } catch (Exception e) {
                        SciencePacks.LOGGER.error("[sciencepacks] 解析研究 {} 失败", id, e);
                    }
                }
            }
        } catch (Exception e) {
            SciencePacks.LOGGER.error("[sciencepacks] 读取研究树配置失败：{}", file, e);
        }
    }

    /** 把配置里的科技导出成 JSON 数组（给 /sciencepacks dumpresearch 用）。 */
    public static String export(List<Technology> techs) {
        JsonArray array = new JsonArray();
        for (Technology tech : techs) {
            array.add(tech.toJson());
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(array);
    }

    /** 供命令使用：当前会加载哪些文件（全局在前）。 */
    public static List<Path> activeFiles(MinecraftServer server) {
        List<Path> files = new ArrayList<>();
        files.add(globalFile());
        Path world = worldFile(server);
        if (world != null) {
            files.add(world);
        }
        return files;
    }
}
