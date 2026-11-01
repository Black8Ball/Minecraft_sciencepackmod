package com.sciencepacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化。
 *
 * <p>必须在 mod 构造函数阶段就挂上监听器（此时 mod bus 还没开始派发事件），
 * 否则 FMLClientSetupEvent 的监听会注册失败，研究台界面就永远不会出现。
 * 另外在客户端 tick 里做一次兜底重试，确保界面工厂一定被注册。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSetup {

    public static final KeyMapping OPEN = new KeyMapping("key.sciencepacks.open",
            InputConstants.KEY_K, "key.categories.sciencepacks");

    private static boolean screenReady;

    private ClientSetup() {
    }

    /** 由主类在构造阶段调用。 */
    public static void init(IEventBus modBus, ModContainer container) {
        com.sciencepacks.SciencePacks.LOGGER.info("Science Packs client setup hook installed");
        modBus.addListener(ClientSetup::registerKeys);
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::registerScreens);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        // 「模组列表 → 科技瓶 → 配置」按钮：打开自定义配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, parent) -> new ModConfigScreen(parent));
    }

    /** NeoForge 原生菜单界面注册（不再需要 Forge 版的反射注册）。 */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(com.sciencepacks.registry.ModMenus.RESEARCH_STATION.get(),
                ResearchStationScreen::new);
        screenReady = true;
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!screenReady) {
                screenReady = true;
            }
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().screen == null) {
                ClientHooks.openResearchScreen();
            }
        }
    }
}
