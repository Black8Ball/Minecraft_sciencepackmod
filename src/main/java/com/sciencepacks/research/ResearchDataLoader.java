package com.sciencepacks.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sciencepacks.SciencePacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从数据包加载科技定义：data/&lt;namespace&gt;/sciencepacks/technologies/*.json
 * 支持 /reload 热重载。 */
public final class ResearchDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    public static final String DIRECTORY = "sciencepacks/technologies";

    public ResearchDataLoader() {
        super(GSON, DIRECTORY);
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ResearchDataLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        List<Technology> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            try {
                loaded.add(Technology.fromJson(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (Exception exception) {
                SciencePacks.LOGGER.error("Failed to parse technology {}", entry.getKey(), exception);
            }
        }
        TechLibrary.load(loaded);

        // 数据包之上再叠一层「配置文件研究树」：config/sciencepacks-research.json
        // 与 <存档>/serverconfig/sciencepacks-research.json
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ResearchFiles.load(server);

        List<ResourceLocation> cycles = TechLibrary.findCycles();
        if (!cycles.isEmpty()) {
            SciencePacks.LOGGER.warn("Cyclic technology prerequisites detected: {}", cycles);
        }
        LockBridge.clearFurnaceCache();
        String disabled = TechLibrary.modHidden().isEmpty()
                ? ""
                : String.format(", %d disabled by mod conditions: %s",
                        TechLibrary.modHidden().size(), TechLibrary.modHidden());
        SciencePacks.LOGGER.info("Loaded {} technologies ({} locked recipes{})", TechLibrary.size(),
                TechLibrary.ordered().stream().mapToInt(t -> t.unlocks().size()).sum(), disabled);

        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Research.sync(player);
            }
        }
    }

    /** 供客户端解析网络同步的科技 JSON。 */
    public static List<Technology> parseAll(String json) {
        List<Technology> list = new ArrayList<>();
        try {
            for (JsonElement element : GSON.fromJson(json, com.google.gson.JsonArray.class)) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                ResourceLocation id = ResourceLocation.tryParse(object.get("id").getAsString());
                if (id == null) {
                    continue;
                }
                list.add(Technology.fromJson(id, object));
            }
        } catch (Exception exception) {
            SciencePacks.LOGGER.error("Failed to parse synced technologies", exception);
        }
        return list;
    }
}

