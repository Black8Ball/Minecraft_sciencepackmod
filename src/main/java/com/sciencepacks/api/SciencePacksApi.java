package com.sciencepacks.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sciencepacks.SciencePacks;
import com.sciencepacks.research.LockBridge;
import com.sciencepacks.research.PlayerResearch;
import com.sciencepacks.research.Research;
import com.sciencepacks.research.ResearchSavedData;
import com.sciencepacks.research.TechLibrary;
import com.sciencepacks.research.Technology;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 给 KubeJS / CraftTweaker / 其他模组用的公开 API。
 *
 * <p>不依赖任何脚本模组的类，因此本模组单独安装时也能正常工作；
 * 脚本里用 {@code Java.loadClass('com.sciencepacks.api.SciencePacksApi')} 即可调用。</p>
 *
 * <p>改完科技后会自动把新的科技树同步给所有在线玩家；被改动的科技
 * 在当前存档里保留玩家已有的解锁状态。</p>
 */
public final class SciencePacksApi {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 脚本追加/覆盖的科技。 */
    private static final Map<ResourceLocation, Technology> CUSTOM = new LinkedHashMap<>();
    /** 脚本隐藏的科技。 */
    private static final Set<ResourceLocation> HIDDEN = new LinkedHashSet<>();

    private SciencePacksApi() {
    }

    /* ---------------- 科技树 ---------------- */

    /**
     * 追加或替换一项科技（同 id 覆盖数据包里的定义）。
     *
     * <p>json 可以是单个科技对象（需要 "id" 字段，或写成 "namespace:path"），
     * 也可以是科技对象数组。</p>
     *
     * @return 实际生效的科技 id 列表（逗号分隔）；解析失败返回空串
     */
    public static String addTech(String json) {
        int added = 0;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject() && addOne(element.getAsJsonObject())) {
                        added++;
                    }
                }
            } else if (root.isJsonObject() && addOne(root.getAsJsonObject())) {
                added = 1;
            }
        } catch (Exception exception) {
            SciencePacks.LOGGER.error("SciencePacksApi.addTech: 无法解析科技 JSON", exception);
            return "";
        }
        if (added > 0) {
            refresh();
        }
        return added == 0 ? "" : idsOf(CUSTOM.keySet());
    }

    /** 追加/替换一项科技，id 由参数给出（JSON 里可以不写 id）。 */
    public static String addTech(String id, String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            TechId parsed = new TechId(id);
            if (parsed.id == null) {
                return "";
            }
            CUSTOM.put(parsed.id, Technology.fromJson(parsed.id, object));
            HIDDEN.remove(parsed.id);
            refresh();
            return parsed.id.toString();
        } catch (Exception exception) {
            SciencePacks.LOGGER.error("SciencePacksApi.addTech({}, ...): 无法解析科技 JSON", id, exception);
            return "";
        }
    }

    /** 删除（隐藏）一项科技，数据包里的原定义也会被屏蔽，直到 reset() 或 /reload。 */
    public static boolean removeTech(String id) {
        TechId parsed = new TechId(id);
        if (parsed.id == null) {
            return false;
        }
        CUSTOM.remove(parsed.id);
        HIDDEN.add(parsed.id);
        refresh();
        return true;
    }

    /** 撤销所有脚本改动（回到数据包原样）。 */
    public static void reset() {
        CUSTOM.clear();
        HIDDEN.clear();
        refresh();
    }

    /** 当前所有科技的 id（逗号分隔）。 */
    public static String techIds() {
        return idsOf(TechLibrary.ordered().stream().map(Technology::id).toList());
    }

    /** 当前科技树的 JSON（与 /sciencepacks techs 输出一致，方便脚本检查）。 */
    public static String techJson() {
        return Research.techJson();
    }

    /* ---------------- 解锁状态 ---------------- */

    /** 直接给某个玩家（按名字）解锁一项研究。 */
    public static boolean unlock(String playerName, String techId) {
        ServerPlayer player = findPlayer(playerName);
        Technology tech = tech(techId);
        if (player == null || tech == null) {
            return false;
        }
        Research.unlockGroup(player.getServer(), Research.of(player).groupKey(), tech);
        return true;
    }

    /** 收回某个玩家（按名字）对一项研究的解锁。 */
    public static boolean lock(String playerName, String techId) {
        ServerPlayer player = findPlayer(playerName);
        Technology tech = tech(techId);
        if (player == null || tech == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        PlayerResearch research = Research.of(player);
        if (!research.lock(tech.id()) || server == null) {
            return false;
        }
        ResearchSavedData.get(server).setDirty();
        for (ServerPlayer member : Research.members(server, research.groupKey())) {
            Research.refreshRecipeBook(member);
            Research.syncState(member, research);
        }
        return true;
    }

    /** 某个玩家是否已经解锁某项研究。 */
    public static boolean isUnlocked(String playerName, String techId) {
        ServerPlayer player = findPlayer(playerName);
        Technology tech = tech(techId);
        return player != null && tech != null && Research.of(player).isUnlocked(tech.id());
    }

    /** 结构（多方块）由哪项研究解锁；没有科技声明时返回空串。 */
    public static String structureOwner(String structure) {
        ResourceLocation owner = TechLibrary.ownerOfStructure(structure);
        return owner == null ? "" : owner.toString();
    }

    /* ---------------- 内部 ---------------- */

    private static boolean addOne(JsonObject object) {
        if (!object.has("id")) {
            SciencePacks.LOGGER.warn("SciencePacksApi.addTech: 科技 JSON 缺少 id，已跳过");
            return false;
        }
        TechId parsed = new TechId(object.get("id").getAsString());
        if (parsed.id == null) {
            return false;
        }
        CUSTOM.put(parsed.id, Technology.fromJson(parsed.id, object));
        HIDDEN.remove(parsed.id);
        return true;
    }

    private static Technology tech(String techId) {
        TechId parsed = new TechId(techId);
        return parsed.id == null ? null : TechLibrary.get(parsed.id);
    }

    private static ServerPlayer findPlayer(String name) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayerByName(name);
    }

    private static void refresh() {
        TechLibrary.setCustom(new ArrayList<>(CUSTOM.values()));
        TechLibrary.setHidden(new ArrayList<>(HIDDEN));
        LockBridge.clearFurnaceCache();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Research.sync(player);
            }
        }
    }

    private static String idsOf(Collection<ResourceLocation> ids) {
        List<String> result = new ArrayList<>();
        for (ResourceLocation id : ids) {
            result.add(id.toString());
        }
        return String.join(",", result);
    }

    /** 把 "foo" / "sciencepacks:foo" 统一成 ResourceLocation。 */
    private static final class TechId {
        private final ResourceLocation id;

        private TechId(String raw) {
            ResourceLocation parsed = raw == null ? null : ResourceLocation.tryParse(raw.trim());
            if (parsed != null && !raw.contains(":")) {
                parsed = ResourceLocation.fromNamespaceAndPath(SciencePacks.MOD_ID, raw.trim());
            }
            this.id = parsed;
        }
    }
}
