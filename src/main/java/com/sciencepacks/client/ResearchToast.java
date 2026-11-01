package com.sciencepacks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 右上角的「研究完成」提示框。
 *
 * <p>直接用原版 {@link SystemToast}，版式与原版提示完全一致（1.21 已移除
 * {@code Toast.TEXTURE}，不再自绘底图）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ResearchToast {

    private ResearchToast() {
    }

    public static void show(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.add(minecraft.getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("gui.sciencepacks.toast.complete"),
                Component.literal(name == null ? "" : name));
    }
}
