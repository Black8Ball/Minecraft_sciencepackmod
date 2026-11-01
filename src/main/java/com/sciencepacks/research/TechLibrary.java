package com.sciencepacks.research;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 科技库：服务端从数据包加载，客户端从网络同步。
 *
 * <p>数据包（data/&lt;namespace&gt;/sciencepacks/technologies/*.json）是「基础科技」；
 * 另外还支持一份「运行时覆盖」列表（见 {@code com.sciencepacks.api.SciencePacksApi}），
 * 方便 KubeJS 之类的脚本在服务器启动后追加/替换/删除科技。</p>
 */
public final class TechLibrary {

    private static volatile Map<ResourceLocation, Technology> technologies = Collections.emptyMap();
    private static volatile List<Technology> ordered = Collections.emptyList();
    private static volatile Map<ResourceLocation, ResourceLocation> recipeOwner = Collections.emptyMap();
    /** 按「产物物品 id」建立的归属表（用于 KubeJS / 模组配方的兜底匹配）。 */
    private static volatile Map<ResourceLocation, ResourceLocation> itemOwner = Collections.emptyMap();
    /** 白名单配方：这些配方永远不锁（对应 unlocks/whitelist 里的 recipe:xxx）。 */
    private static volatile Set<ResourceLocation> whitelistRecipes = Collections.emptySet();
    /** 白名单物品：这些物品的配方永远不锁。 */
    private static volatile Set<ResourceLocation> whitelistItems = Collections.emptySet();
    private static volatile Map<String, ResourceLocation> structureOwner = Collections.emptyMap();
    /** 数据包加载出来的基础科技。 */
    private static volatile List<Technology> base = Collections.emptyList();
    /** 脚本/API 追加或覆盖的科技（同 id 覆盖基础科技）。 */
    private static volatile Map<ResourceLocation, Technology> custom = Collections.emptyMap();
    /** 脚本/API 隐藏掉的科技 id（在合并后统一剔除）。 */
    private static volatile Set<ResourceLocation> hidden = Collections.emptySet();
    /** 配置文件（全局 + 单世界）里的科技，覆盖数据包、小于脚本 API。 */
    private static volatile Map<ResourceLocation, Technology> config = Collections.emptyMap();
    /** 配置文件里 remove 掉的科技 id。 */
    private static volatile Set<ResourceLocation> configHidden = Collections.emptySet();
    /** 因模组条件（requiredMods）不满足而未加载的科技 id，含因前置缺失而连带隐藏的科技。 */
    private static volatile Set<ResourceLocation> modHidden = Collections.emptySet();

    private TechLibrary() {
    }

    /** 数据包加载：替换基础科技，但保留脚本覆盖。 */
    public static void load(Collection<Technology> techs) {
        base = List.copyOf(techs);
        rebuild();
    }

    /**
     * 设置运行时覆盖科技（同 id 会覆盖数据包里的定义）。
     *
     * <p>传空集合等价于「撤销所有脚本改动」。</p>
     */
    public static void setCustom(Collection<Technology> techs) {
        Map<ResourceLocation, Technology> map = new LinkedHashMap<>();
        for (Technology tech : techs) {
            map.put(tech.id(), tech);
        }
        custom = map;
        rebuild();
    }

    /** 当前脚本覆盖的科技。 */
    public static Collection<Technology> customTechs() {
        return custom.values();
    }

    /** 设置被脚本隐藏的科技（数据包里存在但界面上不显示、也不参与锁定）。 */
    public static void setHidden(Collection<ResourceLocation> ids) {
        hidden = ids.isEmpty() ? Collections.emptySet() : Set.copyOf(ids);
        rebuild();
    }

    /**
     * 设置配置文件里的研究树覆盖（全局 + 单世界）。
     *
     * @param techs   覆盖/新增的科技，同 id 覆盖数据包
     * @param removed 需要隐藏的科技 id（对应 JSON 里的 remove 列表）
     */
    public static void setConfig(Collection<Technology> techs, Collection<ResourceLocation> removed) {
        Map<ResourceLocation, Technology> map = new LinkedHashMap<>();
        for (Technology tech : techs) {
            map.put(tech.id(), tech);
        }
        config = map;
        configHidden = removed.isEmpty() ? Collections.emptySet() : Set.copyOf(removed);
        rebuild();
    }

    /** 当前配置文件里的覆盖项（给命令/调试用）。 */
    public static Collection<Technology> configTechs() {
        return config.values();
    }

    private static void rebuild() {
        Map<ResourceLocation, Technology> map = new LinkedHashMap<>();
        for (Technology tech : base) {
            if (!hidden.contains(tech.id()) && !configHidden.contains(tech.id())) {
                map.put(tech.id(), tech);
            }
        }
        for (Map.Entry<ResourceLocation, Technology> entry : config.entrySet()) {
            if (!hidden.contains(entry.getKey()) && !configHidden.contains(entry.getKey())) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<ResourceLocation, Technology> entry : custom.entrySet()) {
            if (!hidden.contains(entry.getKey()) && !configHidden.contains(entry.getKey())) {
                map.put(entry.getKey(), entry.getValue());
            }
        }

        // 模组条件：缺依赖的联动科技不加载；因前置被隐藏而断链的科技一并隐藏（反复到稳定）
        Set<ResourceLocation> unavailable = new LinkedHashSet<>();
        map.entrySet().removeIf(entry -> {
            if (entry.getValue().isAvailable()) {
                return false;
            }
            unavailable.add(entry.getKey());
            return true;
        });
        pruneBrokenDependencies(map, unavailable);

        // 研究互斥：excludes 里列出的研究只要还在树里，本条就隐藏（用来做「装了某模组就换一套研究树」）
        boolean excluded = true;
        while (excluded) {
            excluded = false;
            Iterator<Map.Entry<ResourceLocation, Technology>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Technology tech = iterator.next().getValue();
                for (String excludedId : tech.excludes()) {
                    ResourceLocation target = ResourceLocation.tryParse(excludedId);
                    if (target != null && map.containsKey(target) && !target.equals(tech.id())) {
                        unavailable.add(tech.id());
                        iterator.remove();
                        excluded = true;
                        break;
                    }
                }
            }
            if (excluded) {
                pruneBrokenDependencies(map, unavailable);
            }
        }
        modHidden = Collections.unmodifiableSet(unavailable);

        Map<ResourceLocation, ResourceLocation> recipes = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> items = new LinkedHashMap<>();
        Map<String, ResourceLocation> structures = new LinkedHashMap<>();
        Set<ResourceLocation> allowRecipes = new LinkedHashSet<>();
        Set<ResourceLocation> allowItems = new LinkedHashSet<>();
        for (Technology tech : map.values()) {
            for (ResourceLocation entry : tech.unlocks()) {
                String raw = entry.toString();
                if (raw.startsWith("recipe:")) {
                    // 只锁这一条配方，同产物的其它配方不受影响
                    ResourceLocation recipeId = ResourceLocation.tryParse(raw.substring("recipe:".length()));
                    if (recipeId != null) {
                        recipes.putIfAbsent(recipeId, tech.id());
                    }
                } else if (raw.startsWith("item:")) {
                    ResourceLocation itemId = ResourceLocation.tryParse(raw.substring("item:".length()));
                    if (itemId != null) {
                        items.putIfAbsent(itemId, tech.id());
                    }
                } else {
                    // 兼容旧写法：同时按配方 id 与产物物品 id 登记
                    recipes.putIfAbsent(entry, tech.id());
                    items.putIfAbsent(entry, tech.id());
                }
            }
            for (String entry : tech.whitelist()) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("recipe:")) {
                    ResourceLocation id = ResourceLocation.tryParse(trimmed.substring("recipe:".length()));
                    if (id != null) {
                        allowRecipes.add(id);
                    }
                } else if (trimmed.startsWith("item:")) {
                    ResourceLocation id = ResourceLocation.tryParse(trimmed.substring("item:".length()));
                    if (id != null) {
                        allowItems.add(id);
                    }
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(trimmed);
                    if (id != null) {
                        // 不带前缀：配方 id 与物品 id 都放行
                        allowRecipes.add(id);
                        allowItems.add(id);
                    }
                }
            }
            for (String structure : tech.structures()) {
                // 条目可能带 |图标|名称 后缀，归属表只存核心 id
                structures.putIfAbsent(Structures.rawKey(structure), tech.id());
            }
        }
        List<Technology> sorted = new ArrayList<>(map.values());
        sorted.sort(Comparator.comparing(Technology::id));
        technologies = map;
        ordered = List.copyOf(sorted);
        recipeOwner = recipes;
        itemOwner = items;
        whitelistRecipes = allowRecipes;
        whitelistItems = allowItems;
        structureOwner = structures;
    }

    public static void clear() {
        custom = Collections.emptyMap();
        hidden = Collections.emptySet();
        config = Collections.emptyMap();
        configHidden = Collections.emptySet();
        load(Collections.emptyList());
    }

    /** 圈定某科技是否因模组条件不满足而没有加载。 */
    public static boolean isModHidden(ResourceLocation id) {
        return modHidden.contains(id);
    }

    /** 因模组条件不满足而未加载的科技 id（只读）。 */
    public static Set<ResourceLocation> modHidden() {
        return modHidden;
    }

    /** 反复剔除「前置已不可用」的科技，直到不再变化。 */
    private static void pruneBrokenDependencies(Map<ResourceLocation, Technology> map,
                                               Set<ResourceLocation> unavailable) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Iterator<Map.Entry<ResourceLocation, Technology>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Technology tech = iterator.next().getValue();
                for (ResourceLocation prerequisite : tech.effectivePrerequisites()) {
                    if (unavailable.contains(prerequisite)) {
                        unavailable.add(tech.id());
                        iterator.remove();
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    public static Technology get(ResourceLocation id) {
        return technologies.get(id);
    }

    public static Collection<Technology> all() {
        return ordered;
    }

    public static List<Technology> ordered() {
        return ordered;
    }

    public static int size() {
        return ordered.size();
    }

    /** 配方归属的科技；没有被任何科技锁定的配方返回 null。 */
    public static ResourceLocation ownerOfRecipe(ResourceLocation recipeId) {
        return recipeOwner.get(recipeId);
    }

    /** 产物物品归属的科技；没有科技锁定该物品时返回 null。 */
    public static ResourceLocation ownerOfItem(ResourceLocation itemId) {
        return itemOwner.get(itemId);
    }

    /** 该配方是否被白名单放行（解锁前也能用）。 */
    public static boolean whitelistedRecipe(ResourceLocation recipeId) {
        return whitelistRecipes.contains(recipeId);
    }

    /** 该物品的所有配方是否被白名单放行。 */
    public static boolean whitelistedItem(ResourceLocation itemId) {
        return whitelistItems.contains(itemId);
    }

    /** 多方块结构归属的科技；没有科技声明该结构时返回 null。 */
    public static ResourceLocation ownerOfStructure(String structure) {
        return structureOwner.get(structure);
    }

    /** 该科技的下游科技（用于树状图连线）。 */
    public static List<Technology> childrenOf(ResourceLocation id) {
        List<Technology> result = new ArrayList<>();
        for (Technology tech : ordered) {
            if (tech.effectivePrerequisites().contains(id)) {
                result.add(tech);
            }
        }
        return result;
    }

    /** 循环依赖检测：返回存在环的科技 id 列表（空表示正常）。 */
    public static List<ResourceLocation> findCycles() {
        List<ResourceLocation> cycles = new ArrayList<>();
        Set<ResourceLocation> visiting = new HashSet<>();
        Set<ResourceLocation> done = new HashSet<>();
        for (Technology tech : ordered) {
            if (hasCycle(tech.id(), visiting, done)) {
                cycles.add(tech.id());
            }
        }
        return cycles;
    }

    private static boolean hasCycle(ResourceLocation id, Set<ResourceLocation> visiting, Set<ResourceLocation> done) {
        if (done.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        Technology tech = technologies.get(id);
        boolean cycle = false;
        if (tech != null) {
            for (ResourceLocation prereq : tech.effectivePrerequisites()) {
                if (hasCycle(prereq, visiting, done)) {
                    cycle = true;
                    break;
                }
            }
        }
        visiting.remove(id);
        done.add(id);
        return cycle;
    }
}
