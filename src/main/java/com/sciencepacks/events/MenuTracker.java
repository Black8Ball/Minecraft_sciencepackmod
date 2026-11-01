package com.sciencepacks.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** 记录「菜单 -> 玩家」，供 Mixin 在不带玩家参数的菜单方法里反查玩家。 */
public final class MenuTracker {

    private static final Map<AbstractContainerMenu, UUID> OPEN = new IdentityHashMap<>();

    private MenuTracker() {
    }

    public static void onOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OPEN.put(event.getContainer(), player.getUUID());
        }
    }

    public static void onClose(PlayerContainerEvent.Close event) {
        OPEN.remove(event.getContainer());
    }

    public static ServerPlayer playerOf(AbstractContainerMenu menu) {
        UUID id = OPEN.get(menu);
        if (id == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }
}
