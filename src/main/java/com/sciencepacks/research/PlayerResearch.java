package com.sciencepacks.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个研究组的状态：已解锁科技、研究队列，以及**每一项研究各自**的材料消耗进度。
 *
 * <p>进度按「研究 id → 材料 id → 已消耗数量」分开记录，所以调整队列顺序、
 * 把某项研究后置、或者暂时取消某项研究，都不会丢掉它已经积累的进度。</p>
 */
public final class PlayerResearch {

    public static final String NBT_UNLOCKED = "Unlocked";
    public static final String NBT_QUEUE = "Queue";
    /** 旧版存档：所有研究共用一张消耗表，读取后迁移到队首研究上。 */
    public static final String NBT_CONSUMED_OLD = "Consumed";
    public static final String NBT_CONSUMED = "ConsumedByTech";
    public static final String NBT_TRIGGER_COUNTS = "TriggerCounts";
    /** 无限研究的等级：研究 id → 已完成的等级数。 */
    public static final String NBT_LEVELS = "Levels";

    private final String groupKey;
    private final Set<ResourceLocation> unlocked = new LinkedHashSet<>();
    private final List<ResourceLocation> queue = new ArrayList<>();
    /** 每项研究各自的材料消耗进度：研究 id → (材料 id → 已消耗数量)。 */
    private final Map<ResourceLocation, Map<ResourceLocation, Integer>> consumed = new LinkedHashMap<>();
    /** 事件类触发条件的累计次数，键见 {@link Technology.Trigger#key()}。 */
    private final Map<String, Integer> triggerCounts = new LinkedHashMap<>();
    /** 无限研究等级：研究 id → 已完成等级数。 */
    private final Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();

    public PlayerResearch(String groupKey) {
        this.groupKey = groupKey;
    }

    public String groupKey() {
        return groupKey;
    }

    public Set<ResourceLocation> unlocked() {
        return unlocked;
    }

    public List<ResourceLocation> queue() {
        return queue;
    }

    /** 取某项研究的材料消耗表（会自动创建）。 */
    public Map<ResourceLocation, Integer> consumed(ResourceLocation techId) {
        return consumed.computeIfAbsent(techId, id -> new LinkedHashMap<>());
    }

    /** 某项研究对某种材料已经消耗了多少个。 */
    public int consumedOf(ResourceLocation techId, ResourceLocation item) {
        Map<ResourceLocation, Integer> map = consumed.get(techId);
        return map == null ? 0 : map.getOrDefault(item, 0);
    }

    /** 某项研究已消耗的材料总数（命令调试用）。 */
    public int progressOf(ResourceLocation techId) {
        Map<ResourceLocation, Integer> map = consumed.get(techId);
        if (map == null) {
            return 0;
        }
        int sum = 0;
        for (int value : map.values()) {
            sum += value;
        }
        return sum;
    }

    /** 清掉某项研究的进度（研究完成或被取消时调用）。 */
    public void clearProgress(ResourceLocation techId) {
        consumed.remove(techId);
    }

    /** 清掉所有研究的进度。 */
    public void resetAllProgress() {
        consumed.clear();
    }

    /** 所有研究 id → 消耗表（存档用）。 */
    public Map<ResourceLocation, Map<ResourceLocation, Integer>> consumedByTech() {
        return consumed;
    }

    public int triggerCount(String key) {
        return triggerCounts.getOrDefault(key, 0);
    }

    public void addTriggerCount(String key, int amount) {
        if (amount > 0) {
            triggerCounts.merge(key, amount, Integer::sum);
        }
    }

    public Map<String, Integer> triggerCounts() {
        return triggerCounts;
    }

    /** 无限研究已经完成到第几级（0 = 还没完成过）。 */
    public int levelOf(ResourceLocation techId) {
        return levels.getOrDefault(techId, 0);
    }

    /** 设置无限研究的等级（不会低于 0）。 */
    public void setLevel(ResourceLocation techId, int level) {
        if (level <= 0) {
            levels.remove(techId);
        } else {
            levels.put(techId, level);
        }
    }

    /** 无限研究等级 +1，返回新等级。 */
    public int addLevel(ResourceLocation techId) {
        int next = levelOf(techId) + 1;
        setLevel(techId, next);
        return next;
    }

    /** 所有无限研究的等级（存档 / 网络同步用）。 */
    public Map<ResourceLocation, Integer> levels() {
        return levels;
    }

    public boolean isUnlocked(ResourceLocation tech) {
        return unlocked.contains(tech);
    }

    public boolean unlock(ResourceLocation tech) {
        return unlocked.add(tech);
    }

    /** 收回解锁（脚本/命令用）。 */
    public boolean lock(ResourceLocation tech) {
        queue.remove(tech);
        consumed.remove(tech);
        levels.remove(tech);
        return unlocked.remove(tech);
    }

    public void reset() {
        unlocked.clear();
        queue.clear();
        consumed.clear();
        triggerCounts.clear();
        levels.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag unlockedTag = new ListTag();
        for (ResourceLocation id : unlocked) {
            unlockedTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put(NBT_UNLOCKED, unlockedTag);
        ListTag queueTag = new ListTag();
        for (ResourceLocation id : queue) {
            queueTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put(NBT_QUEUE, queueTag);
        ListTag consumedTag = new ListTag();
        for (Map.Entry<ResourceLocation, Map<ResourceLocation, Integer>> entry : consumed.entrySet()) {
            CompoundTag tech = new CompoundTag();
            tech.putString("Tech", entry.getKey().toString());
            ListTag items = new ListTag();
            for (Map.Entry<ResourceLocation, Integer> material : entry.getValue().entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putString("Item", material.getKey().toString());
                item.putInt("Count", material.getValue());
                items.add(item);
            }
            tech.put("Items", items);
            consumedTag.add(tech);
        }
        tag.put(NBT_CONSUMED, consumedTag);
        ListTag triggerTag = new ListTag();
        for (Map.Entry<String, Integer> entry : triggerCounts.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("Key", entry.getKey());
            item.putInt("Count", entry.getValue());
            triggerTag.add(item);
        }
        tag.put(NBT_TRIGGER_COUNTS, triggerTag);
        ListTag levelTag = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> entry : levels.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("Tech", entry.getKey().toString());
            item.putInt("Level", entry.getValue());
            levelTag.add(item);
        }
        tag.put(NBT_LEVELS, levelTag);
        return tag;
    }

    public static PlayerResearch load(String groupKey, CompoundTag tag) {
        PlayerResearch research = new PlayerResearch(groupKey);
        ListTag unlockedTag = tag.getList(NBT_UNLOCKED, Tag.TAG_STRING);
        for (int i = 0; i < unlockedTag.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(unlockedTag.getString(i));
            if (id != null) {
                research.unlocked.add(id);
            }
        }
        ListTag queueTag = tag.getList(NBT_QUEUE, Tag.TAG_STRING);
        for (int i = 0; i < queueTag.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(queueTag.getString(i));
            if (id != null) {
                research.queue.add(id);
            }
        }
        ListTag consumedTag = tag.getList(NBT_CONSUMED, Tag.TAG_COMPOUND);
        for (int i = 0; i < consumedTag.size(); i++) {
            CompoundTag tech = consumedTag.getCompound(i);
            ResourceLocation techId = ResourceLocation.tryParse(tech.getString("Tech"));
            if (techId == null) {
                continue;
            }
            Map<ResourceLocation, Integer> materials = new LinkedHashMap<>();
            ListTag items = tech.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag item = items.getCompound(j);
                ResourceLocation itemId = ResourceLocation.tryParse(item.getString("Item"));
                if (itemId != null) {
                    materials.put(itemId, Math.max(0, item.getInt("Count")));
                }
            }
            if (!materials.isEmpty()) {
                research.consumed.put(techId, materials);
            }
        }
        // 旧存档迁移：把共用的消耗表挂到队首研究上
        if (tag.contains(NBT_CONSUMED_OLD, Tag.TAG_LIST) && !research.queue.isEmpty()) {
            ListTag oldTag = tag.getList(NBT_CONSUMED_OLD, Tag.TAG_COMPOUND);
            Map<ResourceLocation, Integer> materials = research.consumed(research.queue.get(0));
            for (int i = 0; i < oldTag.size(); i++) {
                CompoundTag entry = oldTag.getCompound(i);
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getString("Item"));
                if (itemId != null) {
                    materials.put(itemId, Math.max(0, entry.getInt("Count")));
                }
            }
            if (materials.isEmpty()) {
                research.consumed.remove(research.queue.get(0));
            }
        }
        ListTag triggerTag = tag.getList(NBT_TRIGGER_COUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < triggerTag.size(); i++) {
            CompoundTag entry = triggerTag.getCompound(i);
            String key = entry.getString("Key");
            if (!key.isBlank()) {
                research.triggerCounts.put(key, Math.max(0, entry.getInt("Count")));
            }
        }
        ListTag levelTag = tag.getList(NBT_LEVELS, Tag.TAG_COMPOUND);
        for (int i = 0; i < levelTag.size(); i++) {
            CompoundTag entry = levelTag.getCompound(i);
            ResourceLocation techId = ResourceLocation.tryParse(entry.getString("Tech"));
            if (techId != null) {
                int level = entry.getInt("Level");
                if (level > 0) {
                    research.levels.put(techId, level);
                }
            }
        }
        return research;
    }
}
