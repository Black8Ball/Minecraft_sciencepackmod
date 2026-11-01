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

/** 服务端 -> 客户端：完整科技定义（JSON 数组）。 */
public class TechSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TechSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SciencePacks.MOD_ID, "tech_sync"));

    public static final StreamCodec<FriendlyByteBuf, TechSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> msg.encode(buf), TechSyncPacket::new);

    private final String json;

    public TechSyncPacket(String json) {
        this.json = json;
    }

    public TechSyncPacket(FriendlyByteBuf buf) {
        this.json = buf.readUtf(8_000_000);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(json, 8_000_000);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TechSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientHooks.onTechs(packet.json);
            }
        });
    }
}
