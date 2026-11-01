package com.sciencepacks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;

/** 客户端入口（只会在客户端加载）。 */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void openResearchScreen() {
        Minecraft.getInstance().setScreen(new ResearchScreen());
    }

    public static void onTechs(String json) {
        ClientResearchState.setTechs(com.sciencepacks.research.ResearchDataLoader.parseAll(json));
    }

    public static void onState(List<ResourceLocation> unlocked, List<ResourceLocation> queue,
                               List<List<Integer>> queueConsumed, List<ResourceLocation> revealed,
                               Map<ResourceLocation, Integer> levels) {
        ClientResearchState.setState(unlocked, queue, queueConsumed, revealed, levels);
    }

    /** 研究完成：右上角弹提示框。 */
    public static void onToast(String name, String techId) {
        ResearchToast.show(name);
    }

    public static void requestSync() {
        com.sciencepacks.network.Network.toServer(new com.sciencepacks.network.ActionPacket(
                com.sciencepacks.network.ActionPacket.REQUEST, ""));
    }
}
