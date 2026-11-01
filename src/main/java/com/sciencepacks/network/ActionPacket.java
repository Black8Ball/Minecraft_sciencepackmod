package com.sciencepacks.network;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.research.Research;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端 -> 服务端：开始/取消研究、清空队列、请求同步。 */
public class ActionPacket implements CustomPacketPayload {

    public static final String START = "start";
    public static final String STOP = "stop";
    public static final String CLEAR = "clear";
    public static final String REQUEST = "request";

    public static final CustomPacketPayload.Type<ActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SciencePacks.MOD_ID, "action"));

    public static final StreamCodec<FriendlyByteBuf, ActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> msg.encode(buf), ActionPacket::new);

    private final String action;
    private final String techId;

    public ActionPacket(String action, String techId) {
        this.action = action;
        this.techId = techId == null ? "" : techId;
    }

    public ActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf(32);
        this.techId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 32);
        buf.writeUtf(techId, 256);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            switch (packet.action) {
                case START -> {
                    ResourceLocation id = ResourceLocation.tryParse(packet.techId);
                    if (id != null) {
                        Research.start(player, id);
                    }
                }
                case STOP -> {
                    ResourceLocation id = ResourceLocation.tryParse(packet.techId);
                    if (id != null) {
                        Research.stop(player, id);
                    }
                }
                case CLEAR -> Research.clearQueue(player);
                case REQUEST -> Research.sync(player);
                default -> {
                }
            }
        });
    }
}
