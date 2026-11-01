package com.sciencepacks.events;

import com.sciencepacks.Config;
import com.sciencepacks.research.FurnaceOwnership;
import com.sciencepacks.research.Research;
import com.sciencepacks.research.ResearchGroups;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 服务端事件：登录同步、触发物品扫描、菜单追踪、熔炉/酿造台归属。 */
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 登录时补一次免费科技级联，兼容改过配置/数据包的老存档
            Research.cascadeFree(player.getServer(), Research.of(player));
            Research.refreshRecipeBook(player);
            Research.sync(player);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        int interval = Config.COMMON.triggerScanInterval.get();
        if (interval > 0 && player.tickCount % interval == 0) {
            Research.scanTriggers(player);
        }
    }

    @SubscribeEvent
    public static void onMenuOpen(PlayerContainerEvent.Open event) {
        MenuTracker.onOpen(event);
    }

    @SubscribeEvent
    public static void onMenuClose(PlayerContainerEvent.Close event) {
        MenuTracker.onClose(event);
    }

    /** 服务端启动完成：应用配置文件里的研究树（此时存档目录已经就绪）。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        com.sciencepacks.research.ResearchFiles.load(event.getServer());
        // 模组联动：按模组条件替换配方（必须在研究树加载之后，配方表已就绪时执行）
        com.sciencepacks.research.RecipeOverrides.apply(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Research.sync(player);
        }
    }

    /** 玩家放置熔炉/酿造台时，把方块绑定到该玩家的研究组。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean furnace = event.getPlacedBlock().getBlock() instanceof AbstractFurnaceBlock;
        boolean brewing = event.getPlacedBlock().getBlock() instanceof BrewingStandBlock;
        if (!furnace && !brewing) {
            return;
        }
        FurnaceOwnership.get(player.getServer())
                .set(event.getEntity().level(), event.getPos(), ResearchGroups.keyFor(player));
    }
}
