package com.sciencepacks.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 一个科技节点（对应 Factorio 的一项 technology）。
 * 由数据包 data/&lt;namespace&gt;/sciencepacks/technologies/*.json 定义。
 */
public record Technology(
        ResourceLocation id,
        String name,
        String description,
        ResourceLocation icon,
        String category,
        List<ResourceLocation> prerequisites,
        List<PackCost> costs,
        List<Trigger> triggers,
        List<ResourceLocation> unlocks,
        int x,
        int y,
        /** 每一轮消耗需要的 tick 数；0 表示用全局 ticksPerPack。 */
        int researchTime,
        /**
         * 这项研究解锁的「多方块结构」（例如 nether_portal / end_portal / wither）。
         *
         * <p>结构类型由模组内置的事件拦截支持，见 {@link Structures}；
         * 未解锁时对应的搭建/点燃/进入动作会被取消。</p>
         */
        List<String> structures,
        /**
         * 需要的研究轮次。
         *
         * <p>研究台是轮次制：每一轮同时消耗 {@code costs} 里列出的全部材料（倍率为各自的 count），
         * 每轮耗时 {@code researchTime}（为 0 时用全局 ticksPerPack），完成 {@code rounds} 轮后解锁。</p>
         */
        int rounds,
        /**
         * 白名单：解锁这项研究之前也允许合成/使用的配方或物品。
         *
         * <p>写法：{@code recipe:<配方id>} 只放行某条配方；{@code item:<物品id>} 放行该物品的所有配方；
         * 不带前缀时先当配方 id、再当物品 id 处理。用于「某个物品在解锁前还有另一种配方可用」。</p>
         */
        List<String> whitelist,
        /**
         * 隐藏研究：为 true 时，只有满足全部前置且 {@code showWhen} 条件全部满足后才在界面上出现。
         */
        boolean hidden,
        /** 显示条件（与 triggers 相同写法），只在 {@code hidden} 为 true 时有意义。 */
        List<Trigger> showWhen,
        /**
         * 无限研究：完成 {@code rounds} 轮后不结束，而是等级 +1、材料按 {@code costMultiplier} 增长，
         * 可以一直研究下去。等级会存进存档，重新排队时保留。
         */
        boolean infinite,
        /** 无限研究的等级上限（0 = 无上限）；达到上限后按普通研究结束。 */
        int maxLevel,
        /** 无限研究每升一级的消耗倍率（默认 2，即每级翻倍）。 */
        double costMultiplier,
        /** 数值型研究效果：挖掘速度、触及距离、方块放置上限等。 */
        List<Effect> effects,
        /**
         * 模组条件：只有这些条件全部满足时这项研究才会被加载（不出现在树里、也不锁配方）。
         *
         * <p>JSON 写法：{@code "requiredMods": ["create"]}（需要 Create）、
         * {@code "requiredMods": ["!create"]}（没有 Create 才启用）；
         * 用于「装了对应模组才启用这套联动研究树」。</p>
         */
        List<String> requiredMods,
        /**
         * 互斥研究：只要这里列出的研究还在树里，本条研究就自动隐藏，用来做「同一功能的两套研究树」。
         *
         * <p>JSON 写法：{@code "excludes": ["sciencepacks:vanilla_tools"]}；
         * 与 {@link #requiredMods()} 搭配可实现「装了模组就换另一套研究」。</p>
         */
        List<String> excludes,
        /**
         * 根研究：true 时忽略 {@code prerequisites}，这项研究自己就是一棵研究树的起点。
         *
         * <p>JSON 写法：{@code "root": true}。适合「一开局就该能研究」或「由触发物品开启」的科技；
         * 前置列表可以留着做文档/布局参考，但不会被当成解锁条件，树里也不会画入线。</p>
         */
        boolean root
) {

    /** 真正的解锁前置：根研究恒为空。 */
    public List<ResourceLocation> effectivePrerequisites() {
        return root ? List.of() : prerequisites;
    }

    /** 根研究判断：编辑器/界面用。 */
    public boolean isRoot() {
        return root;
    }

    /** 一种研究材料的需求量：每一轮消耗 count 个。 */
    public record PackCost(ResourceLocation item, int count) {
    }

    /**
     * 数值型研究效果。
     *
     * <p>JSON 写法：{"type":"mining_speed","amount":0.1}（挖掘速度 +10%）、
     * {"type":"reach","amount":0.5}（触及距离 +0.5 格）、
     * {"type":"place_limit","block":"minecraft:torch","amount":8}（该方块每级多放 8 个）。</p>
     *
     * <p>有限研究在解锁时生效一次；无限研究按等级叠加（amount × 等级）。</p>
     */
    public record Effect(String type, double amount, ResourceLocation block) {

        /** 挖掘速度加成（0.1 = +10%）。 */
        public static final String MINING_SPEED = "mining_speed";
        /** 触及距离加成（单位：格）。 */
        public static final String REACH = "reach";
        /** 方块放置数量上限加成（需要 block 字段）。 */
        public static final String PLACE_LIMIT = "place_limit";

        public static final Set<String> TYPES = Set.of(MINING_SPEED, REACH, PLACE_LIMIT);

        public static Effect parse(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject json = element.getAsJsonObject();
            String type = GsonHelper.getAsString(json, "type", "").toLowerCase();
            if (!TYPES.contains(type)) {
                return null;
            }
            double amount = GsonHelper.getAsDouble(json, "amount", 0);
            ResourceLocation block = null;
            String blockId = GsonHelper.getAsString(json, "block", "");
            if (!blockId.isBlank()) {
                block = ResourceLocation.tryParse(blockId);
            }
            if (PLACE_LIMIT.equals(type) && block == null) {
                return null;
            }
            if (amount == 0) {
                return null;
            }
            return new Effect(type, amount, block);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", type);
            json.addProperty("amount", amount);
            if (block != null) {
                json.addProperty("block", block.toString());
            }
            return json;
        }
    }

    /**
     * 触发条件：收集/挖掘/击败等事件达成后自动解锁科技。
     *
     * <p>JSON 里既可以写字符串（兼容旧写法，等同 type=item），
     * 也可以写对象 {"type":"block_break","id":"minecraft:iron_ore","count":8}。</p>
     */
    public record Trigger(String type, ResourceLocation id, int count) {

        /** 背包里持有物品。 */
        public static final String ITEM = "item";
        /** 挖掘方块。 */
        public static final String BLOCK_BREAK = "block_break";
        /** 击败生物。 */
        public static final String KILL = "kill";
        /** 合成物品。 */
        public static final String CRAFT = "craft";
        /** 烧炼产出物品。 */
        public static final String SMELT = "smelt";
        /** 拾取物品。 */
        public static final String PICKUP = "pickup";
        /** 到达维度（count 固定按 1 处理）。 */
        public static final String DIMENSION = "dimension";

        public static final Set<String> TYPES = Set.of(
                ITEM, BLOCK_BREAK, KILL, CRAFT, SMELT, PICKUP, DIMENSION);

        /** 触发器在存档里的唯一键：类型 + 目标 id。 */
        public String key() {
            return type + "|" + id;
        }

        /** 只有物品触发器能直接画物品图标。 */
        public boolean isItem() {
            return ITEM.equals(type);
        }

        /** 读一个 JSON 元素（字符串或对象）。 */
        public static Trigger parse(JsonElement element) {
            if (element == null) {
                return null;
            }
            if (element.isJsonObject()) {
                JsonObject json = element.getAsJsonObject();
                String type = GsonHelper.getAsString(json, "type", ITEM).toLowerCase();
                if (!TYPES.contains(type)) {
                    return null;
                }
                ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "id", ""));
                if (id == null) {
                    return null;
                }
                int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
                return new Trigger(type, id, DIMENSION.equals(type) ? 1 : count);
            }
            if (!element.isJsonPrimitive()) {
                return null;
            }
            return parse(element.getAsString());
        }

        /** 读字符串写法：type:id*N / type:id x N / id*N / id。 */
        public static Trigger parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String text = raw.trim();
            int count = 1;
            int star = text.lastIndexOf('*');
            if (star > 0) {
                try {
                    count = Integer.parseInt(text.substring(star + 1).trim());
                    text = text.substring(0, star).trim();
                } catch (NumberFormatException ignored) {
                    // 不是数量后缀，保持原样
                }
            } else {
                // 兼容 "minecraft:iron_ore x 8" 写法
                int mark = text.lastIndexOf(" x ");
                if (mark > 0) {
                    try {
                        count = Integer.parseInt(text.substring(mark + 3).trim());
                        text = text.substring(0, mark).trim();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            String type = ITEM;
            int colon = text.indexOf(':');
            if (colon > 0) {
                String prefix = text.substring(0, colon).toLowerCase();
                if (TYPES.contains(prefix) && prefix.indexOf(':') < 0) {
                    type = prefix;
                    text = text.substring(colon + 1);
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(text);
            if (id == null) {
                return null;
            }
            count = Math.max(1, count);
            return new Trigger(type, id, DIMENSION.equals(type) ? 1 : count);
        }

        public JsonElement toJson() {
            if (isItem() && count == 1) {
                return new com.google.gson.JsonPrimitive(id.toString());
            }
            if (isItem()) {
                return new com.google.gson.JsonPrimitive(id + "*" + count);
            }
            JsonObject json = new JsonObject();
            json.addProperty("type", type);
            json.addProperty("id", id.toString());
            json.addProperty("count", count);
            return json;
        }
    }

    /** 总需求材料件数 = 每轮倍率之和 × 轮数。 */
    public int totalPacks() {
        int perRound = 0;
        for (PackCost cost : costs) {
            perRound += Math.max(0, cost.count());
        }
        return perRound * Math.max(1, rounds);
    }

    public boolean hasCost() {
        return !costs.isEmpty();
    }

    /** 模组条件是否满足（缺依赖的联动科技不会被加载）。 */
    public boolean isAvailable() {
        return ModConditions.allMatch(requiredMods);
    }

    /**
     * 无限研究在指定等级下，每轮需要消耗的数量。
     *
     * <p>等级 0 = 第一次研究（原倍率）；之后每级乘以 {@code costMultiplier} 并向上取整。</p>
     */
    public int packsForLevel(PackCost cost, int level) {
        int base = Math.max(1, cost.count());
        if (!infinite || level <= 0) {
            return base;
        }
        double multiplier = Math.max(1.0, costMultiplier);
        return Math.max(base, (int) Math.ceil(base * Math.pow(multiplier, level)));
    }

    /** 无限研究是否已经达到等级上限。 */
    public boolean levelCapped(int level) {
        return infinite && maxLevel > 0 && level >= maxLevel;
    }

    /** 免费科技：没有成本、也没有触发物品，前置满足时自动解锁。 */
    public boolean isFree() {
        return costs.isEmpty() && triggers.isEmpty();
    }

    public boolean isTriggerOnly() {
        return !triggers.isEmpty() && costs.isEmpty();
    }

    public static Technology fromJson(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getPath());
        String description = GsonHelper.getAsString(json, "description", "");
        ResourceLocation icon = ResourceLocation.tryParse(GsonHelper.getAsString(json, "icon", "minecraft:paper"));
        String category = GsonHelper.getAsString(json, "category", "general");
        List<ResourceLocation> prerequisites = readIds(json, "prerequisites");
        List<Trigger> triggers = readTriggers(json, "triggers");
        List<ResourceLocation> unlocks = readIds(json, "unlocks");
        int researchTime = GsonHelper.getAsInt(json, "researchTime", 0);

        List<PackCost> costs = new ArrayList<>();
        if (json.has("cost")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "cost")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject cost = element.getAsJsonObject();
                ResourceLocation item = ResourceLocation.tryParse(GsonHelper.getAsString(cost, "item", ""));
                int count = GsonHelper.getAsInt(cost, "count", 1);
                if (item != null && count > 0) {
                    costs.add(new PackCost(item, count));
                }
            }
        }

        return new Technology(id, name, description,
                icon == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "paper") : icon,
                category, prerequisites, costs, triggers, unlocks,
                GsonHelper.getAsInt(json, "x", 0), GsonHelper.getAsInt(json, "y", 0),
                Math.max(0, researchTime), readStrings(json, "structures"),
                Math.max(1, GsonHelper.getAsInt(json, "rounds", 1)),
                readStrings(json, "whitelist"),
                GsonHelper.getAsBoolean(json, "hidden", false),
                readTriggers(json, "showWhen"),
                GsonHelper.getAsBoolean(json, "infinite", false),
                Math.max(0, GsonHelper.getAsInt(json, "maxLevel", 0)),
                Math.max(1.0, GsonHelper.getAsDouble(json, "costMultiplier", 2.0)),
                readEffects(json),
                readMods(json),
                readStrings(json, "excludes"),
                GsonHelper.getAsBoolean(json, "root", false));
    }

    /** 读模组条件：同时兼容 requiredMods / required_mods 两种写法。 */
    private static List<String> readMods(JsonObject json) {
        List<String> result = new ArrayList<>();
        for (String key : new String[]{"requiredMods", "required_mods"}) {
            for (String value : readStrings(json, key)) {
                if (!result.contains(value)) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private static List<Effect> readEffects(JsonObject json) {
        List<Effect> result = new ArrayList<>();
        if (!json.has("effects")) {
            return result;
        }
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "effects")) {
            Effect effect = Effect.parse(element);
            if (effect != null) {
                result.add(effect);
            }
        }
        return result;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.addProperty("icon", icon.toString());
        json.addProperty("category", category);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("researchTime", researchTime);
        json.addProperty("rounds", Math.max(1, rounds));
        json.add("prerequisites", writeIds(prerequisites));
        JsonArray triggerArray = new JsonArray();
        for (Trigger trigger : triggers) {
            triggerArray.add(trigger.toJson());
        }
        json.add("triggers", triggerArray);
        json.add("unlocks", writeIds(unlocks));
        JsonArray costArray = new JsonArray();
        for (PackCost cost : costs) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", cost.item().toString());
            entry.addProperty("count", cost.count());
            costArray.add(entry);
        }
        json.add("cost", costArray);
        JsonArray structureArray = new JsonArray();
        for (String structure : structures) {
            structureArray.add(structure);
        }
        json.add("structures", structureArray);
        JsonArray whitelistArray = new JsonArray();
        for (String entry : whitelist) {
            whitelistArray.add(entry);
        }
        json.add("whitelist", whitelistArray);
        if (hidden) {
            json.addProperty("hidden", true);
        }
        if (!showWhen.isEmpty()) {
            JsonArray showArray = new JsonArray();
            for (Trigger trigger : showWhen) {
                showArray.add(trigger.toJson());
            }
            json.add("showWhen", showArray);
        }
        if (infinite) {
            json.addProperty("infinite", true);
            if (maxLevel > 0) {
                json.addProperty("maxLevel", maxLevel);
            }
            json.addProperty("costMultiplier", costMultiplier);
        }
        if (!effects.isEmpty()) {
            JsonArray effectArray = new JsonArray();
            for (Effect effect : effects) {
                effectArray.add(effect.toJson());
            }
            json.add("effects", effectArray);
        }
        if (!requiredMods.isEmpty()) {
            JsonArray modArray = new JsonArray();
            for (String mod : requiredMods) {
                modArray.add(mod);
            }
            json.add("requiredMods", modArray);
        }
        if (!excludes.isEmpty()) {
            JsonArray excludeArray = new JsonArray();
            for (String excluded : excludes) {
                excludeArray.add(excluded);
            }
            json.add("excludes", excludeArray);
        }
        if (root) {
            json.addProperty("root", true);
        }
        return json;
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

    private static List<Trigger> readTriggers(JsonObject json, String key) {
        List<Trigger> result = new ArrayList<>();
        if (!json.has(key)) {
            return result;
        }
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            Trigger trigger = Trigger.parse(element);
            if (trigger != null) {
                result.add(trigger);
            }
        }
        return result;
    }

    private static List<ResourceLocation> readIds(JsonObject json, String key) {
        List<ResourceLocation> result = new ArrayList<>();
        if (!json.has(key)) {
            return result;
        }
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private static JsonArray writeIds(List<ResourceLocation> ids) {
        JsonArray array = new JsonArray();
        for (ResourceLocation id : ids) {
            array.add(id.toString());
        }
        return array;
    }
}
