package com.sciencepacks.events;

import com.sciencepacks.research.PlayerResearch;
import com.sciencepacks.research.Research;
import com.sciencepacks.research.ResearchEffects;
import com.sciencepacks.research.TechLibrary;
import com.sciencepacks.research.Technology;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 数值型研究效果：挖掘速度（客户端 + 服务端各算一次）。 */
public final class ResearchEffectEvents {

    private ResearchEffectEvents() {
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().level().isClientSide()) {
            // 只在客户端加载：专用服务端永远不会走到这里
            com.sciencepacks.client.ClientEffectHooks.onBreakSpeed(event);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerResearch research = Research.of(player);
        double bonus = ResearchEffects.bonus(TechLibrary.ordered(), research.unlocked(),
                research.levels(), Technology.Effect.MINING_SPEED);
        if (bonus > 0) {
            event.setNewSpeed((float) (event.getNewSpeed() * (1.0 + bonus)));
        }
    }
}
