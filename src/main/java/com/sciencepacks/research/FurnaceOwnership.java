package com.sciencepacks.research;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** 记录「方块位置 -> 所属研究组」，用于熔炉这类没有归属信息的方块。 */
public final class FurnaceOwnership extends SavedData {

    public static final String DATA_NAME = "sciencepacks_ownership";

    private static final SavedData.Factory<FurnaceOwnership> FACTORY =
            new SavedData.Factory<>(FurnaceOwnership::new, FurnaceOwnership::load, null);

    private final Map<String, String> owners = new HashMap<>();

    public static FurnaceOwnership get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.asLong();
    }

    public void set(Level level, BlockPos pos, String groupKey) {
        owners.put(key(level.dimension(), pos), groupKey);
        setDirty();
    }

    public void remove(Level level, BlockPos pos) {
        if (owners.remove(key(level.dimension(), pos)) != null) {
            setDirty();
        }
    }

    @Nullable
    public String get(Level level, BlockPos pos) {
        return owners.get(key(level.dimension(), pos));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            data.putString(entry.getKey(), entry.getValue());
        }
        tag.put("Owners", data);
        return tag;
    }

    public static FurnaceOwnership load(CompoundTag tag, HolderLookup.Provider registries) {
        FurnaceOwnership ownership = new FurnaceOwnership();
        CompoundTag data = tag.getCompound("Owners");
        for (String key : data.getAllKeys()) {
            ownership.owners.put(key, data.getString(key));
        }
        return ownership;
    }
}
