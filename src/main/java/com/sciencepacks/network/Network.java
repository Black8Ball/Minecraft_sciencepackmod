package com.sciencepacks.network;

import com.sciencepacks.SciencePacks;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 网络通道（NeoForge 1.21：RegisterPayloadHandlersEvent + CustomPacketPayload）。
 *
 * <p>科技定义同步、玩家状态同步、客户端操作。</p>
 */
public final class Network {

    public static final String VERSION = "1";

    private Network() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(TechSyncPacket.TYPE, TechSyncPacket.STREAM_CODEC, TechSyncPacket::handle);
        registrar.playToClient(StateSyncPacket.TYPE, StateSyncPacket.STREAM_CODEC, StateSyncPacket::handle);
        registrar.playToServer(ActionPacket.TYPE, ActionPacket.STREAM_CODEC, ActionPacket::handle);
        registrar.playToClient(ToastPacket.TYPE, ToastPacket.STREAM_CODEC, ToastPacket::handle);
        registrar.playToServer(QueueReorderPacket.TYPE, QueueReorderPacket.STREAM_CODEC, QueueReorderPacket::handle);
        SciencePacks.LOGGER.info("Science Packs payload handlers registered (version {})", VERSION);
    }

    public static void toPlayer(CustomPacketPayload message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static void toServer(CustomPacketPayload message) {
        PacketDistributor.sendToServer(message);
    }
}
