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

/** 服务端 -> 客户端：研究完成时在右上角弹出提示框。 */
public class ToastPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToastPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SciencePacks.MOD_ID, "toast"));

    public static final StreamCodec<FriendlyByteBuf, ToastPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> msg.encode(buf), ToastPacket::new);

    private final String name;
    private final String techId;

    public ToastPacket(String name, String techId) {
        this.name = name == null ? "" : name;
        this.techId = techId == null ? "" : techId;
    }

    public ToastPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(256);
        this.techId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.name, 256);
        buf.writeUtf(this.techId, 256);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToastPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientHooks.onToast(packet.name, packet.techId);
            }
        });
    }
}
