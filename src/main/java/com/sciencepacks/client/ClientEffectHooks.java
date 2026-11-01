package com.sciencepacks.client;

import com.sciencepacks.research.ResearchEffects;
import com.sciencepacks.research.Technology;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** 客户端研究效果：挖掘速度预测。 */
@OnlyIn(Dist.CLIENT)
public final class ClientEffectHooks {

    private ClientEffectHooks() {
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        double bonus = ResearchEffects.bonus(ClientResearchState.techs(),
                ClientResearchState.unlocked(), ClientResearchState.levels(),
                Technology.Effect.MINING_SPEED);
        if (bonus > 0) {
            event.setNewSpeed((float) (event.getNewSpeed() * (1.0 + bonus)));
        }
    }
}
