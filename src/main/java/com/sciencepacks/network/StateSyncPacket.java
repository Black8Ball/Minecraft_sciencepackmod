package com.sciencepacks.network;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端 -> 客户端：研究组状态。
 *
 * <p>队列里每一项都带上「每种材料已消耗的数量」，这样调整队列顺序后
 * 每个研究方块都能显示自己真实保存的进度，而不是只有队首有进度。</p>
 *
 * <p>另外带上「当前已满足显示条件的隐藏研究」与「无限研究等级」。</p>
 */
public class StateSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StateSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SciencePacks.MOD_ID, "state_sync"));

    public static final StreamCodec<FriendlyByteBuf, StateSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> msg.encode(buf), StateSyncPacket::new);

    private final List<ResourceLocation> unlocked;
    private final List<ResourceLocation> queue;
    /** 与 queue 一一对应：每项研究按 cost 顺序的已消耗数量。 */
    private final List<List<Integer>> queueConsumed;
    /** 已满足显示条件、可以从隐藏状态显示出来的研究。 */
    private final List<ResourceLocation> revealed;
    /** 无限研究等级：研究 id -> 已完成等级。 */
    private final Map<ResourceLocation, Integer> levels;

    public StateSyncPacket(List<ResourceLocation> unlocked, List<ResourceLocation> queue,
                           List<List<Integer>> queueConsumed, List<ResourceLocation> revealed,
                           Map<ResourceLocation, Integer> levels) {
        this.unlocked = unlocked;
        this.queue = queue;
        this.queueConsumed = queueConsumed;
        this.revealed = revealed;
        this.levels = levels;
    }

    public StateSyncPacket(FriendlyByteBuf buf) {
        this.unlocked = buf.readCollection(ArrayList::new, FriendlyByteBuf::readResourceLocation);
        this.queue = buf.readCollection(ArrayList::new, FriendlyByteBuf::readResourceLocation);
        this.queueConsumed = buf.readCollection(ArrayList::new,
                inner -> inner.readCollection(ArrayList::new, FriendlyByteBuf::readVarInt));
        this.revealed = buf.readCollection(ArrayList::new, FriendlyByteBuf::readResourceLocation);
        this.levels = buf.readMap(LinkedHashMap::new, FriendlyByteBuf::readResourceLocation,
                FriendlyByteBuf::readVarInt);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(unlocked, FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(queue, FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(queueConsumed,
                (inner, list) -> inner.writeCollection(list, FriendlyByteBuf::writeVarInt));
        buf.writeCollection(revealed, FriendlyByteBuf::writeResourceLocation);
        buf.writeMap(levels, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StateSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientHooks.onState(packet.unlocked, packet.queue, packet.queueConsumed,
                        packet.revealed, packet.levels);
            }
        });
    }
}
