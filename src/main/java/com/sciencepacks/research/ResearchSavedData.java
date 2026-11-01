package com.sciencepacks.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** 世界存档中的研究数据，按「研究组」保存。 */
public final class ResearchSavedData extends SavedData {

    public static final String DATA_NAME = "sciencepacks_research";

    private static final SavedData.Factory<ResearchSavedData> FACTORY =
            new SavedData.Factory<>(ResearchSavedData::new, ResearchSavedData::load, null);

    private final Map<String, PlayerResearch> groups = new HashMap<>();

    public static ResearchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public PlayerResearch forGroup(String groupKey) {
        return groups.computeIfAbsent(groupKey, PlayerResearch::new);
    }

    @Nullable
    public PlayerResearch existing(String groupKey) {
        return groups.get(groupKey);
    }

    public Map<String, PlayerResearch> all() {
        return groups;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PlayerResearch research : groups.values()) {
            CompoundTag entry = research.save();
            entry.putString("Group", research.groupKey());
            list.add(entry);
        }
        tag.put("Groups", list);
        return tag;
    }

    public static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData data = new ResearchSavedData();
        ListTag list = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String group = entry.getString("Group");
            if (group.isBlank()) {
                continue;
            }
            data.groups.put(group, PlayerResearch.load(group, entry));
        }
        return data;
    }
}
