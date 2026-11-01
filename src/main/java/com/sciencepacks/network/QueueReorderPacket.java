package com.sciencepacks.network;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.research.Research;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端 -> 服务端：拖动研究队列后提交新的顺序。 */
public class QueueReorderPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QueueReorderPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SciencePacks.MOD_ID, "queue_reorder"));

    public static final StreamCodec<FriendlyByteBuf, QueueReorderPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> msg.encode(buf), QueueReorderPacket::new);

    private final int from;
    private final int to;

    public QueueReorderPacket(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public QueueReorderPacket(FriendlyByteBuf buf) {
        this.from = buf.readVarInt();
        this.to = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.from);
        buf.writeVarInt(this.to);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QueueReorderPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                Research.moveInQueue(player, packet.from, packet.to);
            }
        });
    }
}
